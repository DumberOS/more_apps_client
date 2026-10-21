package com.dumbermini.apps.utility.extension

import com.dumbermini.apps.model.InstalledItem
import com.dumbermini.apps.model.Product
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.model.Release
import com.dumbermini.apps.model.findSuggested
import com.dumbermini.apps.service.Connection
import com.dumbermini.apps.service.DownloadService
import com.dumbermini.apps.utility.extension.android.Android

fun Connection<DownloadService.Binder, DownloadService>.startUpdate(
    packageName: String,
    installedItem: InstalledItem?,
    products: List<Pair<Product, Repository>>
) {
    if (binder == null || products.isEmpty()) return

    val selection = products.selectDownload(installedItem) ?: return
    requireNotNull(binder).enqueue(
        packageName, selection.name, selection.repository, selection.release,
        isUpdate = installedItem != null,
    )
}

data class DownloadSelection(val name: String, val repository: Repository, val release: Release)

fun List<Pair<Product, Repository>>.selectDownload(
    installedItem: InstalledItem?,
): DownloadSelection? {
    val products = this

    val (product, repository) = products.findSuggested(installedItem) ?: return null

    val compatibleReleases = product.selectedReleases.ifEmpty { return null }

    val selectedRelease = compatibleReleases.singleOrNull() ?: compatibleReleases.run {
        filter { Android.primaryPlatform in it.platforms }.minByOrNull { it.platforms.size }
            ?: minByOrNull { it.platforms.size }
            ?: firstOrNull()
    } ?: return null

    return DownloadSelection(product.name, repository, selectedRelease)
}
