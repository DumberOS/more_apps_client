package com.dumbermini.apps.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.dumbermini.apps.model.Product
import com.dumbermini.apps.model.Repository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LegacyProductMigrationTest {

    @Test
    fun `sync copy maps columns by name after popularity migration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                Database.Schema.Repository.formatCreateTable(Database.Schema.Repository.name),
            )
            database.execSQL(legacyProductTableSql())
            database.execSQL(
                Database.Schema.Category.formatCreateTable(Database.Schema.Category.name),
            )
            database.version = 7
        }

        val initState = Database.init(context)
        assertTrue(initState.updated)

        val repository = Database.RepositoryAdapter.put(
            Repository.defaultRepository(
                address = "https://example.com/fdroid/repo",
                name = "Example",
                description = "",
                enabled = true,
                fingerprint = "fingerprint",
            ),
        )
        val product = Product(
            repositoryId = repository.id,
            packageName = "com.example.popular",
            name = "Popular",
            summary = "",
            description = "",
            whatsNew = "",
            icon = "",
            metadataIcon = "",
            author = Product.Author("", "", ""),
            source = "",
            changelog = "",
            web = "",
            tracker = "",
            added = 0L,
            updated = 0L,
            popularity = 10_000_000_000L,
            country = "Sweden",
            suggestedVersionCode = 0L,
            categories = emptyList(),
            antiFeatures = emptyList(),
            licenses = emptyList(),
            donates = emptyList(),
            screenshots = emptyList(),
            releases = emptyList(),
        )

        Database.UpdaterAdapter.createTemporaryTable()
        Database.UpdaterAdapter.putTemporary(listOf(
            product,
            product.copy(packageName = "com.example.global", country = ""),
            product.copy(packageName = "com.example.polish", country = "Poland"),
            product.copy(packageName = "com.example.invalid", country = "Unknown"),
        ))
        Database.UpdaterAdapter.finishTemporary(repository, success = true)

        val restored = Database.ProductAdapter.get(product.packageName, signal = null).single()
        assertEquals(product.popularity, restored.popularity)
        assertEquals(product.packageName, restored.packageName)
        assertEquals("Sweden", restored.country)

        fun query(countries: Set<String>?, search: String = ""): Int = Database.ProductAdapter.query(
            installed = false,
            updates = false,
            searchQuery = search,
            section = com.dumbermini.apps.model.ProductItem.Section.All,
            order = com.dumbermini.apps.datastore.model.SortOrder.POPULARITY,
            signal = null,
            countries = countries,
        ).use { it.count }

        assertEquals(setOf("SE", "PL"), Database.CountryAdapter.getAll())
        assertEquals(4, query(null)) // Explore continues to include every app.
        assertEquals(1, query(setOf("SE")))
        assertEquals(2, query(setOf("SE", "PL")))
        assertEquals(0, query(setOf("FR")))
        assertEquals(0, query(emptySet()))
        assertEquals(1, query(setOf("SE", "PL"), "com.example.polish"))
        assertEquals(0, query(setOf("SE"), "com.example.polish"))
        Database.RepositoryAdapter.put(repository.enable(false))
        assertEquals(emptySet(), Database.CountryAdapter.getAll())
    }

    private fun legacyProductTableSql(): String = with(Database.Schema.Product) {
        """
        CREATE TABLE $name (
            $ROW_REPOSITORY_ID INTEGER NOT NULL,
            $ROW_PACKAGE_NAME TEXT NOT NULL,
            $ROW_NAME TEXT NOT NULL,
            $ROW_SUMMARY TEXT NOT NULL,
            $ROW_DESCRIPTION TEXT NOT NULL,
            $ROW_ADDED INTEGER NOT NULL,
            $ROW_UPDATED INTEGER NOT NULL,
            $ROW_VERSION_CODE INTEGER NOT NULL,
            $ROW_SIGNATURES TEXT NOT NULL,
            $ROW_COMPATIBLE INTEGER NOT NULL,
            $ROW_DATA BLOB NOT NULL,
            $ROW_DATA_ITEM BLOB NOT NULL,
            $ROW_POPULARITY INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY ($ROW_REPOSITORY_ID, $ROW_PACKAGE_NAME)
        )
        """.trimIndent()
    }

    private companion object {
        const val DATABASE_NAME = "droidify"
    }
}
