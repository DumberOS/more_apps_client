package com.dumbermini.apps.di

import android.content.Context
import com.dumbermini.apps.datastore.SettingsRepository
import com.dumbermini.apps.installer.InstallManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InstallModule {

    @Singleton
    @Provides
    fun providesInstaller(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): InstallManager = InstallManager(context, settingsRepository)
}
