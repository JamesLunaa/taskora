package io.github.jameslunaa.taskora.ui.window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.WindowBounds
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The app's windows: the main window's and sticky note's stacking level and opacity, and the note itself. */
class WindowsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    private val stored = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Opacity while its slider is being dragged, applied live but only saved on release. */
    private val opacityPreview = MutableStateFlow<Map<AppWindow, Float>>(emptyMap())

    /** Null until loaded. */
    val settings: StateFlow<AppSettings?> = combine(stored, opacityPreview) { settings, preview ->
        settings?.copy(
            mainWindow = settings.mainWindow.withPreview(preview[AppWindow.MAIN]),
            noteWindow = settings.noteWindow.withPreview(preview[AppWindow.NOTE]),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLevel(window: AppWindow, level: WindowLevel) {
        viewModelScope.launch { settingsRepository.setWindowLevel(window, level) }
    }

    fun previewOpacity(window: AppWindow, opacity: Float) {
        opacityPreview.update { it + (window to opacity.coerceIn(WindowLook.MIN_OPACITY, 1f)) }
    }

    fun commitOpacity(window: AppWindow) {
        val opacity = opacityPreview.value[window] ?: return
        viewModelScope.launch {
            settingsRepository.setWindowOpacity(window, opacity)
            // Drop the preview only once the saved value is in, so the window doesn't flicker back.
            stored.first { it?.look(window)?.opacity == opacity }
            opacityPreview.update { if (it[window] == opacity) it - window else it }
        }
    }

    fun setNoteOpen(open: Boolean) {
        viewModelScope.launch { settingsRepository.setNoteOpen(open) }
    }

    fun setNoteListId(listId: Long) {
        viewModelScope.launch { settingsRepository.setNoteListId(listId) }
    }

    fun setNoteBounds(bounds: WindowBounds) {
        if (bounds == stored.value?.note?.bounds) return
        viewModelScope.launch { settingsRepository.setNoteBounds(bounds) }
    }

    private fun WindowLook.withPreview(opacity: Float?) = if (opacity == null) this else copy(opacity = opacity)
}
