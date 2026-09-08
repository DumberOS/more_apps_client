package com.dumbermini.apps.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dumbermini.apps.data.local.converters.Converters
import com.dumbermini.apps.data.local.converters.PermissionConverter
import com.dumbermini.apps.data.local.dao.AppDao
import com.dumbermini.apps.data.local.dao.AuthDao
import com.dumbermini.apps.data.local.dao.DownloadStatsDao
import com.dumbermini.apps.data.local.dao.IndexDao
import com.dumbermini.apps.data.local.dao.InstalledDao
import com.dumbermini.apps.data.local.dao.RBLogDao
import com.dumbermini.apps.data.local.dao.RepoDao
import com.dumbermini.apps.data.local.model.AntiFeatureAppRelation
import com.dumbermini.apps.data.local.model.AntiFeatureEntity
import com.dumbermini.apps.data.local.model.AntiFeatureRepoRelation
import com.dumbermini.apps.data.local.model.AppEntity
import com.dumbermini.apps.data.local.model.AuthenticationEntity
import com.dumbermini.apps.data.local.model.AuthorEntity
import com.dumbermini.apps.data.local.model.CategoryAppRelation
import com.dumbermini.apps.data.local.model.CategoryEntity
import com.dumbermini.apps.data.local.model.CategoryRepoRelation
import com.dumbermini.apps.data.local.model.DonateEntity
import com.dumbermini.apps.data.local.model.DownloadStats
import com.dumbermini.apps.data.local.model.GraphicEntity
import com.dumbermini.apps.data.local.model.InstalledEntity
import com.dumbermini.apps.data.local.model.LinksEntity
import com.dumbermini.apps.data.local.model.LocalizedAppDescriptionEntity
import com.dumbermini.apps.data.local.model.LocalizedAppIconEntity
import com.dumbermini.apps.data.local.model.LocalizedAppNameEntity
import com.dumbermini.apps.data.local.model.LocalizedAppSummaryEntity
import com.dumbermini.apps.data.local.model.LocalizedRepoDescriptionEntity
import com.dumbermini.apps.data.local.model.LocalizedRepoIconEntity
import com.dumbermini.apps.data.local.model.LocalizedRepoNameEntity
import com.dumbermini.apps.data.local.model.MirrorEntity
import com.dumbermini.apps.data.local.model.RBLogEntity
import com.dumbermini.apps.data.local.model.RepoEntity
import com.dumbermini.apps.data.local.model.ScreenshotEntity
import com.dumbermini.apps.data.local.model.VersionEntity

@Database(
    version = 3,
    exportSchema = true,
    entities = [
        AntiFeatureEntity::class,
        AntiFeatureAppRelation::class,
        AntiFeatureRepoRelation::class,
        AuthenticationEntity::class,
        AuthorEntity::class,
        AppEntity::class,
        CategoryEntity::class,
        CategoryAppRelation::class,
        CategoryRepoRelation::class,
        DonateEntity::class,
        GraphicEntity::class,
        InstalledEntity::class,
        LinksEntity::class,
        MirrorEntity::class,
        RepoEntity::class,
        ScreenshotEntity::class,
        VersionEntity::class,
        RBLogEntity::class,
        DownloadStats::class,
        // Localized Data
        LocalizedAppNameEntity::class,
        LocalizedAppSummaryEntity::class,
        LocalizedAppDescriptionEntity::class,
        LocalizedAppIconEntity::class,
        LocalizedRepoNameEntity::class,
        LocalizedRepoDescriptionEntity::class,
        LocalizedRepoIconEntity::class,
    ],
)
@TypeConverters(
    PermissionConverter::class,
    Converters::class,
)
abstract class DroidifyDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun repoDao(): RepoDao
    abstract fun authDao(): AuthDao
    abstract fun indexDao(): IndexDao
    abstract fun rbLogDao(): RBLogDao
    abstract fun downloadStatsDao(): DownloadStatsDao
    abstract fun installedDao(): InstalledDao
}

private val migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app ADD COLUMN popularity INTEGER NOT NULL DEFAULT 0")
    }
}

fun droidifyDatabase(context: Context): DroidifyDatabase = Room
    .databaseBuilder(
        context = context,
        klass = DroidifyDatabase::class.java,
        name = "droidify_room",
    )
    .addMigrations(migration2To3)
    .fallbackToDestructiveMigration(true)
    .addCallback(
        object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.query("PRAGMA synchronous = OFF")
                db.query("PRAGMA journal_mode = WAL")
            }
        },
    )
    .build()
