package com.dumbermini.apps.utility.extension

import com.dumbermini.apps.model.InstalledItem
import com.dumbermini.apps.model.Product
import com.dumbermini.apps.model.Repository
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

    val (product, repository) = products.findSuggested(installedItem) ?: return

    val compatibleReleases = product.selectedReleases
        .filter { installedItem == null || installedItem.signature == it.signature }
        .ifEmpty { return }

    val selectedRelease = compatibleReleases.singleOrNull() ?: compatibleReleases.run {
        filter { Android.primaryPlatform in it.platforms }.minByOrNull { it.platforms.size }
            ?: minByOrNull { it.platforms.size }
            ?: firstOrNull()
    } ?: return

    requireNotNull(binder).enqueue(
        packageName = packageName,
        name = product.name,
        repository = repository,
        release = selectedRelease,
        isUpdate = installedItem != null
    )
}
