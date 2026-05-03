package com.dumbermini.apps.installer.installers

import com.dumbermini.apps.data.model.PackageName
import com.dumbermini.apps.installer.model.InstallItem
import com.dumbermini.apps.installer.model.InstallState

interface Installer : AutoCloseable {

    suspend fun install(installItem: InstallItem): InstallState

    suspend fun uninstall(packageName: PackageName)

}
