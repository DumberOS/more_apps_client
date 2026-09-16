package com.dumbermini.apps.datastore.migration

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.dumbermini.apps.datastore.PreferenceSettingsRepository.PreferencesKeys.SORT_ORDER
import com.dumbermini.apps.datastore.model.SortOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularitySortOrderMigrationTest {
    private val migration = PopularitySortOrderMigration()

    @Test
    fun `missing sort order migrates to popularity`() = runTest {
        val migrated = migration.migrate(emptyPreferences())

        assertEquals(SortOrder.POPULARITY.name, migrated[SORT_ORDER])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `updated sort order migrates to popularity`() = runTest {
        val preferences = mutablePreferencesOf(SORT_ORDER to SortOrder.UPDATED.name)

        val migrated = migration.migrate(preferences)

        assertEquals(SortOrder.POPULARITY.name, migrated[SORT_ORDER])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `other explicit sort order is preserved`() = runTest {
        val preferences = mutablePreferencesOf(SORT_ORDER to SortOrder.NAME.name)

        val migrated = migration.migrate(preferences)

        assertEquals(SortOrder.NAME.name, migrated[SORT_ORDER])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun `selecting updated after migration does not migrate again`() = runTest {
        val migrated = migration.migrate(
            mutablePreferencesOf(SORT_ORDER to SortOrder.UPDATED.name),
        )
        val userPreferences = migrated.toMutablePreferences().apply {
            this[SORT_ORDER] = SortOrder.UPDATED.name
        }

        assertFalse(migration.shouldMigrate(userPreferences))
        assertEquals(SortOrder.UPDATED.name, userPreferences[SORT_ORDER])
    }

    @Test
    fun `new preferences require migration`() = runTest {
        assertTrue(migration.shouldMigrate(emptyPreferences()))
    }
}
