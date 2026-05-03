package com.dumbermini.apps.service

import android.content.Context
import androidx.annotation.StringRes
import com.dumbermini.apps.data.encryption.sha256
import com.dumbermini.apps.data.model.hex
import com.dumbermini.apps.model.Release
import com.dumbermini.apps.network.validation.FileValidator
import com.dumbermini.apps.network.validation.invalid
import com.dumbermini.apps.utility.common.extension.calculateHash
import com.dumbermini.apps.utility.common.extension.getPackageArchiveInfoCompat
import com.dumbermini.apps.utility.common.extension.isSplitApkArchive
import com.dumbermini.apps.utility.common.extension.singleSignature
import com.dumbermini.apps.utility.common.extension.versionCodeCompat
import android.content.pm.PackageInfo
import java.io.File
import java.util.zip.ZipFile
import com.dumbermini.apps.R.string as strings

class ReleaseFileValidator(
    private val context: Context,
    private val packageName: String,
    private val release: Release,
) : FileValidator {

    override suspend fun validate(file: File) {
        val checksum = sha256(file).hex()
        if (!checksum.equals(release.hash, ignoreCase = true)) {
            invalid(getString(strings.integrity_check_error_DESC))
        }
        val packageInfo = context.packageManager.getPackageArchiveInfoCompat(file.path)
        if (packageInfo != null) {
            validateApk(packageInfo)
            return
        }
        if (context.isSplitApkArchive(file)) {
            validateSplitArchive(file)
            return
        }
        invalid(getString(strings.file_format_error_DESC))
    }

    private fun validateSplitArchive(file: File) {
        val hasApkEntries = ZipFile(file).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    return@use true
                }
            }
            false
        }
        if (!hasApkEntries) {
            invalid(getString(strings.file_format_error_DESC))
        }
    }

    private fun validateApk(packageInfo: PackageInfo) {
        if (packageInfo.packageName != packageName ||
            packageInfo.versionCodeCompat != release.versionCode
        ) {
            invalid(getString(strings.invalid_metadata_error_DESC))
        }

        packageInfo.singleSignature
            ?.calculateHash()
            ?.takeIf { it.isNotBlank() || it == release.signature }
            ?: invalid(getString(strings.invalid_signature_error_DESC))

        packageInfo.permissions
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
            .takeIf { release.permissions.containsAll(it) }
            ?: invalid(getString(strings.invalid_permissions_error_DESC))
    }

    private fun getString(@StringRes id: Int): String = context.getString(id)
}
