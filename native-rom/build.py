#!/usr/bin/env python3
"""Compile the app with pinned standalone Android/Kotlin tools; never invokes Gradle."""
import argparse, hashlib, json, os, re, shutil, subprocess, sys, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

A='{http://schemas.android.com/apk/res/android}'
T='{http://schemas.android.com/tools}'
PKG='com.dumbermini.apps'
ET.register_namespace('android',A[1:-1]); ET.register_namespace('tools',T[1:-1])
def run(args):
    print('+',str(args[0]),str(args[1]) if len(args)>1 else '',flush=True)
    subprocess.run([str(a) for a in args],check=True)
def zipwrite(z,name,data):
    info=zipfile.ZipInfo(str(name),date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
    info.external_attr=0o644 << 16;z.writestr(info,data)
def mkdir(p): p.mkdir(parents=True,exist_ok=True); return p
def javafile(root,pkg,name,body):
    p=mkdir(root/Path(pkg.replace('.','/')))/(name+'.java');p.write_text('package '+pkg+';\n'+body);return p
def camel(s): return ''.join(x[:1].upper()+x[1:] for x in s.split('_'))
def viewtype(tag):
    if '.' in tag:return tag
    if tag in ['View','ViewStub','SurfaceView','TextureView']:return 'android.view.'+tag
    return 'android.widget.'+tag

def bindings(res,gen):
    variants=[p for p in res.glob('layout-*') if p.is_dir()]
    if variants:raise ValueError('Layout variants need binding-generator support: '+str(variants))
    layouts={p.stem:p for p in (res/'layout').glob('*.xml')}
    for name,p in layouts.items():
        node=ET.parse(p).getroot(); cls=camel(name)+'Binding'; typ=viewtype(node.tag)
        if node.tag in ['merge','layout']:raise ValueError('Unsupported binding root: '+str(p))
        fields={}
        for n in node.iter():
            id=n.get(A+'id','').split('/')[-1]
            rpackage='android' if n.get(A+'id','').startswith('@android:') else PKG
            if not id:continue
            if n is node:
                fields[id]=(typ,'root')
                continue
            if n.tag=='include':
                t=camel(n.attrib['layout'].split('/')[-1])+'Binding'; expr=f'{t}.bind(androidx.viewbinding.ViewBindings.findChildViewById(root, {rpackage}.R.id.{id}))'
            else:
                t=n.get(T+'viewBindingType',viewtype(n.tag));expr=f'androidx.viewbinding.ViewBindings.findChildViewById(root, {rpackage}.R.id.{id})'
            fields[id]=(t,expr)
        body=f'public final class {cls} implements androidx.viewbinding.ViewBinding {{\nprivate final {typ} root;\n'
        for id,(t,_) in fields.items():body+=f'public final {t} {camel(id)[:1].lower()+camel(id)[1:]};\n'
        body+=f'private {cls}({typ} root) {{ this.root=root;\n'
        for id,(t,expr) in fields.items():
            f=camel(id)[:1].lower()+camel(id)[1:];body+=f'this.{f}=java.util.Objects.requireNonNull({expr}, "Missing view: {id}");\n'
        body+='}\n'+f'public {typ} getRoot() {{ return root; }}\n'
        body+=f'public static {cls} bind(android.view.View root) {{ return new {cls}(({typ}) root); }}\n'
        body+=f'public static {cls} inflate(android.view.LayoutInflater i) {{ return inflate(i,null,false); }}\n'
        body+=f'public static {cls} inflate(android.view.LayoutInflater i, android.view.ViewGroup p, boolean attach) {{ android.view.View v=i.inflate({PKG}.R.layout.{name},p,false); if(attach) p.addView(v); return bind(v); }}\n}}\n'
        javafile(gen,PKG+'.databinding',cls,body)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);ap.add_argument('--phase',default='all');ap.add_argument('--apk',type=Path);args=ap.parse_args()
    root=args.source.resolve();out=mkdir(args.out.resolve());native=Path(__file__).resolve().parent;pre=native/'prebuilts';dep=json.loads((native/'dependencies.json').read_text());config=json.loads((native/'config.json').read_text())
    for name,info in dep['artifacts'].items():
        if hashlib.sha256((pre/name).read_bytes()).hexdigest()!=info['sha256']:
            raise ValueError('Prebuilt checksum mismatch: '+name)
    jdk=Path(os.environ.get('JAVA_HOME','/usr/lib/jvm/java-17-openjdk-amd64')).resolve();java=jdk/'bin/java';javac=jdk/'bin/javac'
    def cp(names):return os.pathsep.join(str(pre/n) for n in names)
    def jrun(names,main,*opts):run([java,'-Xmx4g','-cp',cp(names),main,*opts])
    for dirname in ['generated','classes','ksp','res','dex','aars','tools']:
        if (out/dirname).exists(): shutil.rmtree(out/dirname)
    unpack=mkdir(out/'aars'); gen=mkdir(out/'generated');classes=mkdir(out/'classes'); tools=mkdir(out/'tools');res=root/'app/src/main/res'
    runtime=[];compile=[];aars=[]
    for group,target in [('runtime',runtime)]:
        for name in dep[group]:
            f=pre/name
            if f.suffix=='.aar':
                d=unpack/f.stem
                if not d.exists():
                    mkdir(d)
                    with zipfile.ZipFile(f) as z:z.extractall(d)
                if d not in aars:aars.append(d)
                target.extend(p for p in [d/'classes.jar',*sorted((d/'libs').glob('*.jar'))] if p.exists())
            elif f.suffix=='.jar':target.append(f)
    # Runtime variants supply the implementations; compile variants can be API-only stubs.
    runtime=list(dict.fromkeys(runtime))
    compile=list(dict.fromkeys(compile+runtime))
    sdk=pre/'android-36.jar';classpath=os.pathsep.join(str(p) for p in [sdk,*compile])
    run([javac,'-cp',cp(dep['manifest']),'-d',tools,native/'NativeTools.java'])
    hostcp=str(tools)+os.pathsep+cp(dep['manifest'])
    tree=ET.parse(root/'app/src/main/AndroidManifest.xml');manifest=tree.getroot();manifest.set('package',PKG);manifest.set(A+'versionCode',str(config['version_code']));manifest.set(A+'versionName',config['version_name'])
    manifest.find('application').set(A+'extractNativeLibs','true')
    locales=sorted({p.parent.name.removeprefix('values-') if p.parent.name!='values' else 'en' for p in res.glob('values*/strings.xml') if '<string' in p.read_text()})
    extra=mkdir(out/'res/xml');lr=ET.Element('locale-config')
    for loc in locales: ET.SubElement(lr,'locale',{A+'name':loc.removeprefix('b+').replace('+','-').replace('-r','-')})
    ET.ElementTree(lr).write(extra/'_generated_res_locale_config.xml',encoding='utf-8',xml_declaration=True)
    manifest.find('application').set(A+'localeConfig','@xml/_generated_res_locale_config')
    tree.write(out/'main-manifest.xml',encoding='utf-8',xml_declaration=True)
    run([java,'-cp',hostcp,'NativeTools','manifest',out/'main-manifest.xml',out/'AndroidManifest.xml',str(config['min_sdk']),str(config['target_sdk']),*[p/'AndroidManifest.xml' for p in aars if (p/'AndroidManifest.xml').exists()]])
    flats=[]
    for i,d in enumerate([p/'res' for p in reversed(aars) if (p/'res').exists()]+[res,out/'res']):
        f=out/f'res-{i}.zip';run([pre/'aapt2','compile','--dir',d,'-o',f]);flats+=['-R',f]
    run([pre/'aapt2','link','-o',out/'resources.apk','--manifest',out/'AndroidManifest.xml','-I',sdk,'--auto-add-overlay','--java',gen,'--output-text-symbols',out/'R.txt','--proguard',out/'aapt.pro',*flats])
    # Generate each AAR's R class from its own symbol table, using the linked IDs.
    symbols={}
    for line in (out/'R.txt').read_text().splitlines():
        kind,typ,name,value=line.split(' ',3);symbols[(typ,name)]=(kind,value)
    for d in aars:
        if not (d/'R.txt').exists():continue
        package=ET.parse(d/'AndroidManifest.xml').getroot().get('package')
        if not package:continue
        nested={}
        for line in (d/'R.txt').read_text().splitlines():
            kind,typ,name,_=line.split(' ',3)
            if (typ,name) not in symbols:continue  # Legacy transitive R entries removed by newer dependencies.
            _,val=symbols[(typ,name)];nested.setdefault(typ,[]).append(f'public static final {kind} {name} = {val};')
        javafile(gen,package,'R','public final class R {\n'+ '\n'.join('public static final class '+t+' {\n'+'\n'.join(lines)+'\n}' for t,lines in nested.items())+'\n}')
    javafile(gen,PKG,'BuildConfig','public final class BuildConfig { public static final boolean DEBUG=false; public static final String APPLICATION_ID="'+PKG+'", BUILD_TYPE="release", VERSION_NAME="v'+config['version_name']+'"; public static final int VERSION_CODE='+str(config['version_code'])+'; public static final String[] DETECTED_LOCALES={'+','.join(json.dumps(l) for l in locales)+'}; }')
    bindings(res,gen)
    ksp=mkdir(out/'ksp')
    opts=['-jvm-target','17','-jvm-default-mode','enable','-module-name','more_apps','-source-roots',root/'app/src/main/kotlin','-java-source-roots',gen,'-project-base-dir',root,'-output-base-dir',ksp,'-caches-dir',mkdir(ksp/'cache'),'-class-output-dir',mkdir(ksp/'classes'),'-kotlin-output-dir',mkdir(ksp/'kotlin'),'-java-output-dir',mkdir(ksp/'java'),'-resource-output-dir',mkdir(ksp/'resources'),'-language-version','2.3','-api-version','2.3','-jdk-home',jdk,'-libraries',classpath,'-processor-options',f'room.schemaLocation={out}/schemas:room.generateKotlin=true:dagger.hilt.android.internal.disableAndroidSuperclassValidation=true:dagger.hilt.internal.useAggregatingRootProcessor=true',cp(dep['processors'])]
    jrun(dep['ksp'],'com.google.devtools.ksp.cmdline.KSPJvmMain',*opts)
    if args.phase=='ksp':return
    kt=list((root/'app/src/main/kotlin').rglob('*.kt'))+list(ksp.rglob('*.kt'));js=list(gen.rglob('*.java'))+list((ksp/'java').rglob('*.java'))
    flags=['-jvm-target','17','-no-stdlib','-no-reflect','-Xcontext-parameters','-opt-in=kotlin.RequiresOptIn','-classpath',classpath,'-d',classes]
    flags += ['-Xplugin='+str(pre/n) for n in dep['plugins'] if any(x in n for x in ['serialization-compiler-plugin','parcelize-compiler','compose-compiler-plugin'])]
    argfile=out/'kotlin.args';argfile.write_text('\n'.join(json.dumps(str(a)) for a in flags+kt+js))
    jrun(dep['kotlin'],'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile))
    argfile=out/'java.args';argfile.write_text('\n'.join(json.dumps(str(a)) for a in ['-source','17','-target','17','-proc:none','-classpath',str(classes)+os.pathsep+classpath,'-d',classes,*js]))
    run([javac,'-J-Xmx2g','@'+str(argfile)])
    run([java,'-cp',hostcp,'NativeTools','hilt',classes])
    with zipfile.ZipFile(out/'app.jar','w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(classes.rglob('*')):
            if p.is_file():zipwrite(z,p.relative_to(classes),p.read_bytes())
        # KSP emits resources as well as source: Hilt's LazyClassKey rules
        # prevent R8 from merging distinct ViewModel keys into the same class.
        for p in sorted((ksp/'resources').rglob('*')):
            if p.is_file():zipwrite(z,p.relative_to(ksp/'resources'),p.read_bytes())
    if args.phase=='classes':return
    rules=[native/'proguard-android-optimize.pro',root/'app/proguard.pro',out/'aapt.pro']
    rules += [d/'proguard.txt' for d in aars if (d/'proguard.txt').exists()]
    # Include processor-generated rules as well as dependency consumer rules.
    embedded=[]
    for jar in [out/'app.jar',*runtime]:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith('META-INF/proguard/') and n.endswith('.pro'):
                    embedded.append(z.read(n).decode())
    (out/'consumer.pro').write_text('\n'.join(embedded))
    rules.append(out/'consumer.pro')
    dex=mkdir(out/'dex')
    opts=['--release','--min-api',str(config['min_sdk']),'--lib',sdk,'--output',dex,'--thread-count','4']
    for f in rules:opts += ['--pg-conf',f]
    jrun(dep['r8'],'com.android.tools.r8.R8',*opts,out/'app.jar',*runtime)
    shutil.copy2(out/'resources.apk',out/'unsigned.apk')
    # Preserve service-provider registrations and other runtime resources.
    resources={}
    for jar in [out/'app.jar',*runtime]:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.endswith('/') or n.endswith(('.class','.kotlin_metadata','.kotlin_builtins','.kotlin_module','.SF','.RSA','.DSA')) or n=='META-INF/MANIFEST.MF':continue
                if n.startswith(('META-INF/proguard/','META-INF/versions/')):continue
                b=z.read(n)
                if n.startswith('META-INF/services/'):
                    resources[n]=b'\n'.join(sorted(set(resources.get(n,b'').splitlines()+b.splitlines())))+b'\n'
                elif n not in resources:resources[n]=b
                elif resources[n]!=b and not n.startswith('META-INF/'):raise ValueError('Conflicting runtime resource: '+n)
    with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
        for p in dex.glob('*.dex'):zipwrite(z,p.name,p.read_bytes())
        for n,b in sorted(resources.items()):zipwrite(z,n,b)
        seen=set()
        for d in aars:
            for p in (d/'jni').rglob('*.so'):
                n='lib/'+str(p.relative_to(d/'jni'))
                if n not in seen:zipwrite(z,n,p.read_bytes());seen.add(n)
            for p in (d/'assets').rglob('*'):
                if p.is_file():zipwrite(z,'assets/'+str(p.relative_to(d/'assets')),p.read_bytes())
    if args.apk:
        dest=args.apk.resolve();mkdir(dest.parent)
        if not dest.exists() or hashlib.sha256(dest.read_bytes()).digest()!=hashlib.sha256((out/'unsigned.apk').read_bytes()).digest():
            shutil.copy2(out/'unsigned.apk',dest)
    print('Built',args.apk or out/'unsigned.apk',flush=True)
if __name__=='__main__':main()
