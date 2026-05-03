package com.dumbermini.apps.di

import android.content.Context
import com.dumbermini.apps.data.PrivacyRepository
import com.dumbermini.apps.data.local.DroidifyDatabase
import com.dumbermini.apps.data.local.dao.AppDao
import com.dumbermini.apps.data.local.dao.AuthDao
import com.dumbermini.apps.data.local.dao.DownloadStatsDao
import com.dumbermini.apps.data.local.dao.IndexDao
import com.dumbermini.apps.data.local.dao.InstalledDao
import com.dumbermini.apps.data.local.dao.RBLogDao
import com.dumbermini.apps.data.local.dao.RepoDao
import com.dumbermini.apps.data.local.droidifyDatabase
import com.dumbermini.apps.datastore.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext
        context: Context,
    ): DroidifyDatabase = droidifyDatabase(context)

    @Singleton
    @Provides
    fun provideAppDao(
        db: DroidifyDatabase,
    ): AppDao = db.appDao()

    @Singleton
    @Provides
    fun provideRepoDao(
        db: DroidifyDatabase,
    ): RepoDao = db.repoDao()

    @Singleton
    @Provides
    fun provideAuthDao(
        db: DroidifyDatabase,
    ): AuthDao = db.authDao()

    @Singleton
    @Provides
    fun provideInstallDao(
        db: DroidifyDatabase,
    ): InstalledDao = db.installedDao()

    @Singleton
    @Provides
    fun provideIndexDao(
        db: DroidifyDatabase,
    ): IndexDao = db.indexDao()

    @Singleton
    @Provides
    fun provideRBLogDao(
        db: DroidifyDatabase,
    ): RBLogDao = db.rbLogDao()

    @Singleton
    @Provides
    fun provideDownloadStatsDao(
        db: DroidifyDatabase,
    ): DownloadStatsDao = db.downloadStatsDao()

    @Singleton
    @Provides
    fun providePrivacyRepository(
        rblDao: RBLogDao,
        downloadStatsDao: DownloadStatsDao,
        settingsRepository: SettingsRepository,
    ): PrivacyRepository = PrivacyRepository(
        rbDao = rblDao,
        downloadStatsDao = downloadStatsDao,
        settingsRepo = settingsRepository
    )
}
