package io.github.jameslunaa.taskora.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun runWithRepository(block: suspend (DataStoreSettingsRepository, DataStore<Preferences>) -> Unit) =
        runTest {
            val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tmp.newFile("settings.preferences_pb").also { it.delete() }
            }
            block(DataStoreSettingsRepository(dataStore), dataStore)
        }

    @Test
    fun defaults_areDarkAndCloseToTray() = runWithRepository { repo, _ ->
        assertEquals(AppSettings(themeMode = ThemeMode.DARK, closeToTray = true), repo.settings.first())
    }

    @Test
    fun changes_arePersisted() = runWithRepository { repo, _ ->
        repo.setThemeMode(ThemeMode.SYSTEM)
        repo.setCloseToTray(false)
        assertEquals(AppSettings(themeMode = ThemeMode.SYSTEM, closeToTray = false), repo.settings.first())
    }

    @Test
    fun unknownThemeMode_fallsBackToDefault() = runWithRepository { repo, dataStore ->
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.DARK, repo.settings.first().themeMode)
    }

    @Test
    fun isDark_followsModeAndSystem() {
        assertTrue(AppSettings(themeMode = ThemeMode.DARK).isDark(systemDark = false))
        assertFalse(AppSettings(themeMode = ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(AppSettings(themeMode = ThemeMode.SYSTEM).isDark(systemDark = true))
        assertFalse(AppSettings(themeMode = ThemeMode.SYSTEM).isDark(systemDark = false))
    }
}
