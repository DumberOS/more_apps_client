package com.dumbermini.apps.utility.extension

import android.content.pm.PackageInfo
import com.dumbermini.apps.utility.common.extension.calculateHash
import com.dumbermini.apps.utility.common.extension.singleSignature
import com.dumbermini.apps.utility.common.extension.versionCodeCompat
import com.dumbermini.apps.model.InstalledItem

fun PackageInfo.toInstalledItem(): InstalledItem {
    val signatureString = singleSignature?.calculateHash().orEmpty()
    return InstalledItem(
        packageName,
        versionName.orEmpty(),
        versionCodeCompat,
        signatureString
    )
}
