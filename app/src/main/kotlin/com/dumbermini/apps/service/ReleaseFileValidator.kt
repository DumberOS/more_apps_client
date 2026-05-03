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
import com.dumbermini.apps.utility.common.extension.singleSignature
import com.dumbermini.apps.utility.common.extension.versionCodeCompat
import java.io.File
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
            ?: invalid(getString(strings.file_format_error_DESC))
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
