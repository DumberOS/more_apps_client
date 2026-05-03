package com.dumbermini.apps.model

import androidx.core.net.toUri

data class Release(
    val selected: Boolean,
    val version: String,
    val versionCode: Long,
    val added: Long,
    val size: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val maxSdkVersion: Int,
    val source: String,
    val release: String,
    val hash: String,
    val hashType: String,
    val signature: String,
    val obbMain: String,
    val obbMainHash: String,
    val obbMainHashType: String,
    val obbPatch: String,
    val obbPatchHash: String,
    val obbPatchHashType: String,
    val permissions: List<String>,
    val features: List<String>,
    val platforms: List<String>,
    val incompatibilities: List<Incompatibility>,
) {
    sealed class Incompatibility {
        object MinSdk : Incompatibility()
        object MaxSdk : Incompatibility()
        object Platform : Incompatibility()
        class Feature(val feature: String) : Incompatibility()
    }

    val identifier: String
        get() = "$versionCode.$hash"

    fun getDownloadUrl(repository: Repository): String {
        return repository.address.toUri().buildUpon().appendPath(release).build().toString()
    }

    val isSplitArchive: Boolean
        get() = release.endsWith(".zip", ignoreCase = true)

    val cacheFileName: String
        get() {
            val extension = release
                .substringAfterLast('/', "")
                .substringAfterLast('.', "")
                .takeIf(String::isNotEmpty)
                ?: "apk"
            return "${hash.replace('/', '-')}.${extension.lowercase()}"
        }
}
