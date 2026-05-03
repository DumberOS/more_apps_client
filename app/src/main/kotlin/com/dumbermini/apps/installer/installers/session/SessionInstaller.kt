package com.dumbermini.apps.installer.installers.session

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dumbermini.apps.data.model.PackageName
import com.dumbermini.apps.installer.installers.Installer
import com.dumbermini.apps.installer.model.InstallItem
import com.dumbermini.apps.installer.model.InstallState
import com.dumbermini.apps.utility.common.SdkCheck
import com.dumbermini.apps.utility.common.cache.Cache
import com.dumbermini.apps.utility.common.extension.isSplitApkArchive
import com.dumbermini.apps.utility.common.log
import com.dumbermini.apps.utility.common.sdkAbove
import java.io.File
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class SessionInstaller(private val context: Context) : Installer {

    private val installer = context.packageManager.packageInstaller
    private val intent = Intent(context, SessionInstallerReceiver::class.java)

    companion object {
        private var installerCallbacks: PackageInstaller.SessionCallback? = null
        private val flags = if (SdkCheck.isSnowCake) PendingIntent.FLAG_MUTABLE else 0
    }

    override suspend fun install(
        installItem: InstallItem
    ): InstallState = suspendCancellableCoroutine { cont ->
        val cacheFile = Cache.getReleaseFile(context, installItem.installFileName)
        val isSplitArchive = context.isSplitApkArchive(cacheFile)
        val sessionParams = createSessionParams(installItem)

        val id = installer.createSession(sessionParams)
        val installerCallback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) {}
            override fun onBadgingChanged(sessionId: Int) {}
            override fun onActiveChanged(sessionId: Int, active: Boolean) {}
            override fun onProgressChanged(sessionId: Int, progress: Float) {}
            override fun onFinished(sessionId: Int, success: Boolean) {
                if (sessionId == id) cont.resume(
                    if (success) InstallState.Installed else InstallState.Failed
                )
            }
        }
        installerCallbacks = installerCallback

        installer.registerSessionCallback(
            installerCallbacks!!,
            Handler(Looper.getMainLooper())
        )

        val session = installer.openSession(id)

        session.use { activeSession ->
            if (isSplitArchive) {
                writeSplitArchive(activeSession, cacheFile, cont.isActive)
            } else {
                writeApk(activeSession, cacheFile, cont.isActive)
            }

            val pendingIntent = PendingIntent.getBroadcast(context, id, intent, flags)

            if (cont.isActive) activeSession.commit(pendingIntent.intentSender)
        }

        cont.invokeOnCancellation {
            try {
                installer.abandonSession(id)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun uninstall(packageName: PackageName) =
        suspendCancellableCoroutine { cont ->
            intent.putExtra(SessionInstallerReceiver.ACTION_UNINSTALL, true)
            val pendingIntent = PendingIntent.getBroadcast(context, -1, intent, flags)

            installer.uninstall(packageName.name, pendingIntent.intentSender)
            cont.resume(Unit)
        }

    override fun close() {
        installerCallbacks?.let {
            installer.unregisterSessionCallback(it)
            installerCallbacks = null
        }
        try {
            installer.mySessions.forEach { installer.abandonSession(it.sessionId) }
        } catch (e: SecurityException) {
            log(e.message, type = Log.ERROR)
        }
    }

    private fun createSessionParams(installItem: InstallItem) =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            sdkAbove(sdk = Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sdkAbove(sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setRequestUpdateOwnership(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                installItem.unarchiveId != null
            ) {
                setUnarchiveId(installItem.unarchiveId)
            }
        }

    private fun writeApk(
        session: PackageInstaller.Session,
        cacheFile: File,
        isActive: Boolean,
    ) {
        val sizeBytes = cacheFile.length()
        cacheFile.inputStream().use { fileStream ->
            session.openWrite(cacheFile.name, 0, sizeBytes).use { outputStream ->
                if (isActive) {
                    fileStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
            }
        }
    }

    private fun writeSplitArchive(
        session: PackageInstaller.Session,
        cacheFile: File,
        isActive: Boolean,
    ) {
        ZipFile(cacheFile).use { zipFile ->
            val entries = buildList {
                val zipEntries = zipFile.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        add(entry)
                    }
                }
            }.sortedBy { it.name.substringAfterLast('/') }
            require(entries.isNotEmpty()) { "Split archive does not contain APK files" }
            entries.forEachIndexed { index, entry ->
                val entryName = entry.name.substringAfterLast('/').ifEmpty { "split-$index.apk" }
                zipFile.getInputStream(entry).use { inputStream ->
                    session.openWrite(entryName, 0, entry.size).use { outputStream ->
                        if (isActive) {
                            inputStream.copyTo(outputStream)
                            session.fsync(outputStream)
                        }
                    }
                }
            }
        }
    }
}
