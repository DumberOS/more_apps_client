package com.dumbermini.apps.ui.favourites

import androidx.lifecycle.ViewModel
import com.dumbermini.apps.database.Database
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.datastore.get
import com.dumbermini.apps.model.ProductItem
import com.dumbermini.apps.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val favouriteApps: StateFlow<List<ProductItem>> =
        settingsRepository
            .get { favouriteApps }
            .map { favourites ->
                favourites.mapNotNull { app ->
                    val products = Database.ProductAdapter.get(app, null)
                    val product = products.firstOrNull() ?: return@mapNotNull null
                    val installed = Database.InstalledAdapter.get(app, null)

                    product.item().apply {
                        this.installedVersion = installed?.version.orEmpty()
                        this.canUpdate = product.canUpdate(installed)
                    }
                }
            }.asStateFlow(emptyList())

}
