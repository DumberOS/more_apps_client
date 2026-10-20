import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.android.manifmerger.*;
import com.android.utils.StdLogger;
import org.objectweb.asm.*;

/** Standalone manifest merger and Hilt superclass transformation. No Gradle APIs. */
public class NativeTools {
  public static void main(String[] args) throws Exception {
    if (args[0].equals("manifest")) {
      var logger = new StdLogger(StdLogger.Level.WARNING);
      var merger = ManifestMerger2.newMerger(new File(args[1]), logger, ManifestMerger2.MergeType.APPLICATION)
          .setNamespace("com.dumbermini.apps")
          .setOverride(ManifestSystemProperty.valueOf("MIN_SDK_VERSION"), args[3])
          .setOverride(ManifestSystemProperty.valueOf("TARGET_SDK_VERSION"), args[4])
          .setPlaceHolderValue("applicationId", "com.dumbermini.apps")
          .withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
      for (int i = 5; i < args.length; i++) merger.addLibraryManifest(new File(args[i]));
      var report = merger.merge();
      if (report.getResult().isError()) throw new IllegalStateException(report.getReportString());
      Files.writeString(Path.of(args[2]), report.getMergedDocument(MergingReport.MergedManifestKind.MERGED));
    } else if (args[0].equals("hilt")) {
      Path root = Path.of(args[1]);
      try (var files = Files.walk(root)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
          byte[] original = Files.readAllBytes(file);
          boolean[] entry = {false};
          new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9) {
            public AnnotationVisitor visitAnnotation(String d, boolean v) {
              if (d.equals("Ldagger/hilt/android/AndroidEntryPoint;") || d.equals("Ldagger/hilt/android/HiltAndroidApp;")) entry[0] = true;
              return null;
            }
          }, ClassReader.SKIP_CODE);
          if (!entry[0]) continue;
          ClassReader reader = new ClassReader(original);
          ClassWriter writer = new ClassWriter(0);
          reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            String oldSuper, newSuper;
            public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
              oldSuper = parent;
              int slash = name.lastIndexOf('/');
              newSuper = name.substring(0, slash + 1) + "Hilt_" + name.substring(slash + 1).replace('$', '_');
              // Generic signatures must describe the new superclass as well.
              if (signature != null) signature = signature.replace("L" + oldSuper, "L" + newSuper);
              super.visit(version, access, name, signature, newSuper, interfaces);
            }
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
              var mv = super.visitMethod(access, name, desc, sig, exceptions);
              return new MethodVisitor(Opcodes.ASM9, mv) {
                public void visitCode() {
                  super.visitCode();
                  // BroadcastReceiver.onReceive is abstract before Hilt adds the injecting base.
                  if (oldSuper.equals("android/content/BroadcastReceiver") && name.equals("onReceive") && desc.equals("(Landroid/content/Context;Landroid/content/Intent;)V")) {
                    super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 1); super.visitVarInsn(Opcodes.ALOAD, 2);
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, newSuper, name, desc, false);
                  }
                }
                public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                  if (opcode == Opcodes.INVOKESPECIAL && owner.equals(oldSuper)) owner = newSuper;
                  super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                }
                public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(Math.max(maxStack, 3), maxLocals); }
              };
            }
          }, 0);
          Files.write(file, writer.toByteArray());
        }
      }
    } else throw new IllegalArgumentException(args[0]);
  }
}
