package com.dumbermini.apps.installer.installers.shizuku

import android.content.Context
import com.dumbermini.apps.data.model.PackageName
import com.dumbermini.apps.installer.installers.Installer
import com.dumbermini.apps.installer.installers.session.SessionInstaller
import com.dumbermini.apps.installer.installers.uninstallPackage
import com.dumbermini.apps.installer.model.InstallItem
import com.dumbermini.apps.installer.model.InstallState
import com.dumbermini.apps.utility.common.SdkCheck
import com.dumbermini.apps.utility.common.cache.Cache
import com.dumbermini.apps.utility.common.extension.isSplitApkArchive
import com.dumbermini.apps.utility.common.extension.size
import java.io.BufferedReader
import java.io.InputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ShizukuInstaller(private val context: Context) : Installer {

    companion object {
        private val SESSION_ID_REGEX = Regex("(?<=\\[).+?(?=])")
    }

    override suspend fun install(
        installItem: InstallItem,
    ): InstallState {
        val file = Cache.getReleaseFile(context, installItem.installFileName)
        if (context.isSplitApkArchive(file)) {
            return SessionInstaller(context).use { it.install(installItem) }
        }
        return suspendCancellableCoroutine { cont ->
            var sessionId: String? = null
            try {
                val fileSize = file.length()
                if (fileSize == 0L) {
                    cont.cancel()
                    error("File is not valid: Size ${file.size}")
                }
                if (cont.isCompleted) return@suspendCancellableCoroutine
                val installerPackage = context.packageName
                file.inputStream().use {
                    val createCommand =
                        if (SdkCheck.isNougat) {
                            "pm install-create --user current -i $installerPackage -S $fileSize"
                        } else {
                            "pm install-create -i $installerPackage -S $fileSize"
                        }
                    val createResult = exec(createCommand)
                    sessionId = SESSION_ID_REGEX.find(createResult.out)?.value
                        ?: run {
                            cont.cancel()
                            error("Failed to create install session")
                        }
                    if (cont.isCompleted) return@suspendCancellableCoroutine

                    val writeResult = exec("pm install-write -S $fileSize $sessionId base -", it)
                    if (writeResult.resultCode != 0) {
                        cont.cancel()
                        error("Failed to write APK to session $sessionId")
                    }
                    if (cont.isCompleted) return@suspendCancellableCoroutine

                    val commitResult = exec("pm install-commit $sessionId")
                    if (commitResult.resultCode != 0) {
                        cont.cancel()
                        error("Failed to commit install session $sessionId")
                    }
                    if (cont.isCompleted) return@suspendCancellableCoroutine
                    cont.resume(InstallState.Installed)
                }
            } catch (_: Exception) {
                if (sessionId != null) exec("pm install-abandon $sessionId")
                cont.resume(InstallState.Failed)
            }
        }
    }

    override suspend fun uninstall(packageName: PackageName) =
        context.uninstallPackage(packageName)

    override fun close() = Unit

    private data class ShellResult(val resultCode: Int, val out: String)

    private fun exec(command: String, stdin: InputStream? = null): ShellResult {
        val process = rikka.shizuku.Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        if (stdin != null) {
            process.outputStream.use { stdin.copyTo(it) }
        }
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val resultCode = process.waitFor()
        return ShellResult(resultCode, output)
    }
}
