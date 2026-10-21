package com.dumbermini.apps.service

import com.dumbermini.apps.utility.extension.DownloadSelection

internal object AndroidAutoInstall {
    const val PACKAGE_NAME = "com.google.android.projection.gearhead"

    val dependencies = linkedMapOf(
        "com.google.android.apps.maps" to "Google Maps",
        "com.google.android.googlequicksearchbox" to "Google Searchbox",
        "com.google.android.tts" to "Google Speech recognition",
    )

    data class Plan(
        val downloads: Map<String, DownloadSelection>,
        val unavailable: List<String>,
    )

    fun planDependencies(
        installed: Set<String>,
        active: Set<String>,
        available: Map<String, DownloadSelection>,
        freeSpace: Long,
    ): Plan {
        val downloads = linkedMapOf<String, DownloadSelection>()
        val unavailable = mutableListOf<String>()
        dependencies.forEach { (packageName, name) ->
            if (packageName !in installed && packageName !in active) {
                val selection = available[packageName]
                if (selection == null || !selection.release.compatible || selection.release.size > freeSpace) {
                    unavailable += name
                } else {
                    downloads[packageName] = selection
                }
            }
        }
        return Plan(downloads, unavailable)
    }
}
