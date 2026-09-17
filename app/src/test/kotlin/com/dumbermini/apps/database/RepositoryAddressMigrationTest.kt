package com.dumbermini.apps.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.dumbermini.apps.database.Database.jsonGenerate
import com.dumbermini.apps.database.Database.jsonParse
import com.dumbermini.apps.database.table.DatabaseHelper
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.utility.serialization.repository
import com.dumbermini.apps.utility.serialization.serialize
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RepositoryAddressMigrationTest {
    @Test
    fun `pre-popularity installation migrates URL in the same upgrade`() = verifyUpgrade(6)

    @Test
    fun `version nine installation also migrates URL`() = verifyUpgrade(9)

    private fun verifyUpgrade(oldVersion: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("droidify")
        val file = context.getDatabasePath("droidify")
        file.parentFile?.mkdirs()
        val original = Repository.defaultRepository(
            address = "https://apps.dumbermini.com/repo/",
            name = "Custom name", description = "Description", enabled = true,
            fingerprint = "existing-fingerprint", authentication = "existing-authentication",
        ).copy(id = 1, lastModified = "old-date", entityTag = "old-etag", timestamp = 123,
            updated = 456, mirrors = listOf("https://apps.dumbermini.com/repo/"))
        val rows = listOf(
            original,
            original.copy(id = 2, address = original.address.trimEnd('/'), enabled = false),
            original.copy(id = 3), // A deleted repository must stay deleted.
            original.copy(id = 4, address = "https://apps.dumbermini.com/staging/repo/"),
            original.copy(id = 5, address = "https://example.com/repo/"),
        )
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(Database.Schema.Repository.formatCreateTable(Database.Schema.Repository.name))
            var productSql = Database.Schema.Product.formatCreateTable(Database.Schema.Product.name)
                .replace("country TEXT NOT NULL DEFAULT '',", "")
            if (oldVersion < 7) productSql = productSql.replace("popularity INTEGER NOT NULL,", "")
            db.execSQL(productSql)
            db.execSQL(Database.Schema.Category.formatCreateTable(Database.Schema.Category.name))
            rows.forEach { repo ->
                db.insertOrThrow("repository", null, ContentValues().apply {
                    put("_id", repo.id)
                    put("enabled", if (repo.enabled) 1 else 0)
                    put("deleted", if (repo.id == 3L) 1 else 0)
                    put("data", jsonGenerate(repo::serialize))
                })
            }
            db.version = oldVersion
        }
        DatabaseHelper(context).use { helper ->
            val db = helper.writableDatabase
            assertTrue(helper.updated)
            assertEquals(10, db.version)
            rows.forEach { originalRow ->
                db.rawQuery("SELECT enabled, deleted, data FROM repository WHERE _id = ?",
                    arrayOf(originalRow.id.toString())).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(if (originalRow.enabled) 1 else 0, cursor.getInt(0))
                    assertEquals(if (originalRow.id == 3L) 1 else 0, cursor.getInt(1))
                    // Repository IDs come from the row, not the serialized metadata parser.
                    val migrated = cursor.getBlob(2).jsonParse { it.repository() }
                        .copy(id = originalRow.id)
                    val expected = if (originalRow.id <= 3) originalRow.copy(
                        address = "https://apps.dumbermini.com/v2/repo/", mirrors = emptyList(),
                        lastModified = "", entityTag = "", updated = 0, timestamp = 0,
                    ) else originalRow
                    assertEquals(expected, migrated)
                }
            }
        }
    }
}
