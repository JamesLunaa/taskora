package io.github.jameslunaa.taskora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.system.Autostart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val autostart: Autostart,
) : ViewModel() {
    /** Null until loaded, so the dialog doesn't flash the defaults. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val autostartAvailable: Boolean get() = autostart.isAvailable

    private val _autostartEnabled = MutableStateFlow(autostart.isEnabled())
    val autostartEnabled: StateFlow<Boolean> = _autostartEnabled.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCloseToTray(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloseToTray(enabled) }
    }

    fun setUseSystemAccent(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseSystemAccent(enabled) }
    }

    fun setAutostart(enabled: Boolean) {
        autostart.setEnabled(enabled)
        _autostartEnabled.value = autostart.isEnabled()
    }
}
