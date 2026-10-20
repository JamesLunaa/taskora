package com.example.taskora.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.taskora.data.settings.DataStoreSettingsRepository
import com.example.taskora.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        // Exactly one DataStore per file in the process, hence the singleton.
        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
            @ApplicationScope scope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("settings")
        }
    }
}
