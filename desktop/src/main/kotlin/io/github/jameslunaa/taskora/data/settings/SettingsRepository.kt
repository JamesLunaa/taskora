package io.github.jameslunaa.taskora.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.NoteSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.domain.model.WindowBounds
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCloseToTray(enabled: Boolean)
    suspend fun setWindowLevel(window: AppWindow, level: WindowLevel)
    suspend fun setWindowOpacity(window: AppWindow, opacity: Float)
    suspend fun setNoteOpen(open: Boolean)
    suspend fun setNoteListId(listId: Long)
    suspend fun setNoteBounds(bounds: WindowBounds)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                // Unknown values (e.g. from a newer version) fall back to the default.
                themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } }
                    ?: defaults.themeMode,
                closeToTray = prefs[CLOSE_TO_TRAY] ?: defaults.closeToTray,
                mainWindow = prefs.look(AppWindow.MAIN, defaults.mainWindow),
                noteWindow = prefs.look(AppWindow.NOTE, defaults.noteWindow),
                note = NoteSettings(
                    isOpen = prefs[NOTE_OPEN] ?: defaults.note.isOpen,
                    listId = prefs[NOTE_LIST_ID] ?: defaults.note.listId,
                    bounds = prefs[NOTE_BOUNDS]?.let(::parseBounds),
                ),
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setCloseToTray(enabled: Boolean) {
        dataStore.edit { it[CLOSE_TO_TRAY] = enabled }
    }

    override suspend fun setWindowLevel(window: AppWindow, level: WindowLevel) {
        dataStore.edit { it[levelKey(window)] = level.name }
    }

    override suspend fun setWindowOpacity(window: AppWindow, opacity: Float) {
        dataStore.edit { it[opacityKey(window)] = opacity.coerceIn(WindowLook.MIN_OPACITY, 1f) }
    }

    override suspend fun setNoteOpen(open: Boolean) {
        dataStore.edit { it[NOTE_OPEN] = open }
    }

    override suspend fun setNoteListId(listId: Long) {
        dataStore.edit { it[NOTE_LIST_ID] = listId }
    }

    override suspend fun setNoteBounds(bounds: WindowBounds) {
        dataStore.edit { it[NOTE_BOUNDS] = with(bounds) { "$x,$y,$width,$height" } }
    }

    private fun Preferences.look(window: AppWindow, default: WindowLook) = WindowLook(
        level = this[levelKey(window)]?.let { name -> WindowLevel.entries.find { it.name == name } }
            ?: default.level,
        opacity = this[opacityKey(window)]?.coerceIn(WindowLook.MIN_OPACITY, 1f) ?: default.opacity,
    )

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val CLOSE_TO_TRAY = booleanPreferencesKey("close_to_tray")
        private val NOTE_OPEN = booleanPreferencesKey("note_open")
        private val NOTE_LIST_ID = longPreferencesKey("note_list_id")
        private val NOTE_BOUNDS = stringPreferencesKey("note_bounds")

        private fun levelKey(window: AppWindow) = stringPreferencesKey("${window.name.lowercase()}_window_level")
        private fun opacityKey(window: AppWindow) = floatPreferencesKey("${window.name.lowercase()}_window_opacity")

        private fun parseBounds(value: String): WindowBounds? {
            val parts = value.split(',').map { it.toIntOrNull() ?: return null }
            return if (parts.size == 4) WindowBounds(parts[0], parts[1], parts[2], parts[3]) else null
        }

        fun create(file: File, scope: CoroutineScope): DataStoreSettingsRepository =
            DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }
}
