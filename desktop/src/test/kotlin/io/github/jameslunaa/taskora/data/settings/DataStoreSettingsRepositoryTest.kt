package io.github.jameslunaa.taskora.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.NoteSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.domain.model.WindowBounds
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
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
    fun windowLooks_arePerWindow() = runWithRepository { repo, _ ->
        assertEquals(WindowLook(WindowLevel.NORMAL, 1f), repo.settings.first().mainWindow)
        assertEquals(WindowLook(WindowLevel.ABOVE, 1f), repo.settings.first().noteWindow)

        repo.setWindowLevel(AppWindow.MAIN, WindowLevel.BELOW)
        repo.setWindowOpacity(AppWindow.NOTE, 0.6f)
        val settings = repo.settings.first()
        assertEquals(WindowLook(WindowLevel.BELOW, 1f), settings.mainWindow)
        assertEquals(WindowLook(WindowLevel.ABOVE, 0.6f), settings.noteWindow)
    }

    @Test
    fun opacity_isClamped() = runWithRepository { repo, _ ->
        repo.setWindowOpacity(AppWindow.MAIN, 0f)
        assertEquals(WindowLook.MIN_OPACITY, repo.settings.first().mainWindow.opacity)
    }

    @Test
    fun note_isPersisted() = runWithRepository { repo, _ ->
        assertEquals(NoteSettings(), repo.settings.first().note)
        repo.setNoteOpen(true)
        repo.setNoteListId(7)
        repo.setNoteBounds(WindowBounds(-20, 40, 320, 420))
        assertEquals(NoteSettings(true, 7, WindowBounds(-20, 40, 320, 420)), repo.settings.first().note)
    }

    @Test
    fun brokenNoteBounds_areIgnored() = runWithRepository { repo, dataStore ->
        dataStore.edit { it[stringPreferencesKey("note_bounds")] = "1,2,x" }
        assertEquals(null, repo.settings.first().note.bounds)
    }

    @Test
    fun isDark_followsModeAndSystem() {
        assertTrue(AppSettings(themeMode = ThemeMode.DARK).isDark(systemDark = false))
        assertFalse(AppSettings(themeMode = ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(AppSettings(themeMode = ThemeMode.SYSTEM).isDark(systemDark = true))
        assertFalse(AppSettings(themeMode = ThemeMode.SYSTEM).isDark(systemDark = false))
    }
}
