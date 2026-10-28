package io.github.jameslunaa.taskora.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCloseToTray(enabled: Boolean)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                // Unknown values (e.g. from a newer version) fall back to the default.
                themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } }
                    ?: AppSettings().themeMode,
                closeToTray = prefs[CLOSE_TO_TRAY] ?: AppSettings().closeToTray,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setCloseToTray(enabled: Boolean) {
        dataStore.edit { it[CLOSE_TO_TRAY] = enabled }
    }

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val CLOSE_TO_TRAY = booleanPreferencesKey("close_to_tray")

        fun create(file: File, scope: CoroutineScope): DataStoreSettingsRepository =
            DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }
}
