package com.dumbermini.apps.installer.model

import com.dumbermini.apps.data.model.PackageName
import com.dumbermini.apps.data.model.toPackageName

class InstallItem(
    val packageName: PackageName,
    val installFileName: String,
    val unarchiveId: Int? = null
)

infix fun String.installFrom(fileName: String) = InstallItem(this.toPackageName(), fileName)
