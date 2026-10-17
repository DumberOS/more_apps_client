package com.dumbermini.apps.installer.installers.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.dumbermini.apps.R
import com.dumbermini.apps.data.model.toPackageName
import com.dumbermini.apps.installer.InstallManager
import com.dumbermini.apps.installer.model.InstallState
import com.dumbermini.apps.utility.common.Constants.NOTIFICATION_CHANNEL_INSTALL
import com.dumbermini.apps.utility.common.createNotificationChannel
import com.dumbermini.apps.utility.common.extension.getPackageName
import com.dumbermini.apps.utility.common.extension.notificationManager
import com.dumbermini.apps.utility.notifications.createInstallNotification
import com.dumbermini.apps.utility.notifications.installNotification
import com.dumbermini.apps.utility.notifications.removeInstallNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SessionInstallerReceiver : BroadcastReceiver() {

    // This is a cyclic dependency injection, I know but this is the best option for now
    @Inject
    lateinit var installManager: InstallManager

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android may require confirmation for unknown sources or update ownership.
            val promptIntent: Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)

            promptIntent?.let {
                if (sessionId >= 0) {
                    InstallConfirmation.save(context, sessionId, it)
                    return
                }
                // Uninstallation has no install session; retain its existing prompt handling.
                it.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                it.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending")
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(it)
            }
        } else {
            InstallConfirmation.remove(context, sessionId)
            notifyStatus(intent, context)
        }
    }

    private fun notifyStatus(intent: Intent, context: Context) {
        val packageManager = context.packageManager
        val notificationManager = context.notificationManager

        context.createNotificationChannel(
            id = NOTIFICATION_CHANNEL_INSTALL,
            name = context.getString(R.string.install)
        )

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val isUninstall = intent.getBooleanExtra(ACTION_UNINSTALL, false)

        val appName = packageManager.getPackageName(packageName)

        if (packageName != null) {
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    notificationManager?.removeInstallNotification(packageName)
                    val notification = context.createInstallNotification(
                        appName = (appName ?: packageName.substringAfterLast('.')).toString(),
                        state = InstallState.Installed,
                        isUninstall = isUninstall,
                    ) {
                        setTimeoutAfter(SUCCESS_TIMEOUT)
                    }
                    notificationManager?.installNotification(
                        packageName = packageName,
                        notification = notification,
                    )
                }

                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    notificationManager?.removeInstallNotification(packageName)
                    installManager.setFailed(packageName.toPackageName())
                }

                else -> {
                    installManager.remove(packageName.toPackageName())
                    val notification = context.createInstallNotification(
                        appName = appName.toString(),
                        state = InstallState.Failed,
                        isUninstall = isUninstall,
                    ) {
                        setContentText(message)
                    }
                    notificationManager?.installNotification(
                        packageName = packageName,
                        notification = notification
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_UNINSTALL = "action_uninstall"

        private const val SUCCESS_TIMEOUT = 5_000L
    }
}
