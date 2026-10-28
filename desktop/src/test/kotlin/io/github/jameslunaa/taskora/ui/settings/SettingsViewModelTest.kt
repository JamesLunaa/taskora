package io.github.jameslunaa.taskora.ui.settings

import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.testing.FakeAutostart
import io.github.jameslunaa.taskora.testing.FakeSettingsRepository
import io.github.jameslunaa.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsAndUpdatesSettings() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsRepository(), FakeAutostart())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.settings.collect() }

        assertEquals(AppSettings(), viewModel.settings.value)
        viewModel.setThemeMode(ThemeMode.LIGHT)
        viewModel.setCloseToTray(false)
        assertEquals(AppSettings(themeMode = ThemeMode.LIGHT, closeToTray = false), viewModel.settings.value)
    }

    @Test
    fun autostartToggle() {
        val viewModel = SettingsViewModel(FakeSettingsRepository(), FakeAutostart())
        assertFalse(viewModel.autostartEnabled.value)
        viewModel.setAutostart(true)
        assertTrue(viewModel.autostartEnabled.value)
    }
}
