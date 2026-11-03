package io.github.jameslunaa.taskora.ui.settings

import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.testing.FakeSettingsRepository
import io.github.jameslunaa.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsAndUpdatesSettings() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.settings.collect() }

        assertEquals(AppSettings(), viewModel.settings.value)
        viewModel.setThemeMode(ThemeMode.LIGHT)
        viewModel.setDynamicColor(true)
        assertEquals(AppSettings(themeMode = ThemeMode.LIGHT, dynamicColor = true), viewModel.settings.value)
    }
}
