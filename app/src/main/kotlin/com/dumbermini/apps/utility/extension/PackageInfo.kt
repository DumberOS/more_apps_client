package com.dumbermini.apps.utility.extension

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.dumbermini.apps.utility.common.extension.calculateHash
import com.dumbermini.apps.utility.common.extension.singleSignature
import com.dumbermini.apps.utility.common.extension.versionCodeCompat
import com.dumbermini.apps.model.InstalledItem

// The ROM reserves AA's privileged package with a code-free version-1 placeholder.
// It is not a user installation: exclude it from installed lists and update selection.
private fun PackageInfo.isAndroidAutoPlaceholder(): Boolean {
    val flags = applicationInfo?.flags ?: return false
    return packageName == "com.google.android.projection.gearhead" &&
        versionCodeCompat == 1L &&
        flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
        flags and ApplicationInfo.FLAG_HAS_CODE == 0 &&
        flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
}

fun PackageInfo.toInstalledItem(): InstalledItem? {
    if (isAndroidAutoPlaceholder()) return null
    val signatureString = singleSignature?.calculateHash().orEmpty()
    return InstalledItem(
        packageName,
        versionName.orEmpty(),
        versionCodeCompat,
        signatureString
    )
}
