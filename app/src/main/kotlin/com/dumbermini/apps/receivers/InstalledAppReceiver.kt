package com.dumbermini.apps.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.utility.common.Constants
import com.dumbermini.apps.utility.common.extension.getPackageInfoCompat
import com.dumbermini.apps.utility.common.extension.notificationManager
import com.dumbermini.apps.utility.extension.toInstalledItem

class InstalledAppReceiver(
    private val packageManager: PackageManager,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName =
            intent.data?.let { if (it.scheme == "package") it.schemeSpecificPart else null }
        if (packageName != null) {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                // Sent after successful installs, including updates, for every installer.
                context.notificationManager?.cancel(
                    "download-$packageName",
                    Constants.NOTIFICATION_ID_DOWNLOADING,
                )
            }
            when (intent.action.orEmpty()) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                    -> {
                    val packageInfo = packageManager.getPackageInfoCompat(packageName)
                    if (packageInfo != null) {
                        Database.InstalledAdapter.put(packageInfo.toInstalledItem())
                    } else {
                        Database.InstalledAdapter.delete(packageName)
                    }
                }
            }
        }
    }
}
