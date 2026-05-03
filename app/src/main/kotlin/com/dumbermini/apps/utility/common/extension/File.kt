package com.dumbermini.apps.utility.common.extension

import android.content.Context
import com.dumbermini.apps.utility.common.cache.Cache
import java.io.File
import java.util.zip.ZipFile

val File.size: Long?
    get() = if (exists()) length().takeIf { it > 0L } else null

fun Context.isApkArchive(file: File): Boolean =
    packageManager.getPackageArchiveInfoCompat(file.path) != null

fun Context.isSplitApkArchive(file: File): Boolean =
    !isApkArchive(file) && file.containsApkEntries()

private fun File.containsApkEntries(): Boolean = try {
    ZipFile(this).use { zipFile ->
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                return@use true
            }
        }
        false
    }
} catch (_: Exception) {
    false
}

inline fun <T> Context.tempFile(block: (file: File) -> T): T {
    val file = Cache.getTemporaryFile(this)
    return try {
        block(file)
    } finally {
        file.delete()
    }
}
