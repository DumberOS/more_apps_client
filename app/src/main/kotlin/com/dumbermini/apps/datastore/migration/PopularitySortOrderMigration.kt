package com.dumbermini.apps.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.dumbermini.apps.datastore.PreferenceSettingsRepository.PreferencesKeys.SORT_ORDER
import com.dumbermini.apps.datastore.model.SortOrder

class PopularitySortOrderMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[MIGRATED] != true

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            val currentSortOrder = this[SORT_ORDER]
            if (currentSortOrder == null || currentSortOrder == SortOrder.UPDATED.name) {
                this[SORT_ORDER] = SortOrder.POPULARITY.name
            }
            this[MIGRATED] = true
        }

    override suspend fun cleanUp() = Unit

    private companion object {
        val MIGRATED = booleanPreferencesKey("migration_popularity_sort_order_v1")
    }
}
