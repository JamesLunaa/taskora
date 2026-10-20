package com.example.taskora.ui.settings

import com.example.taskora.data.settings.SettingsRepository
import com.example.taskora.domain.model.AppSettings
import com.example.taskora.domain.model.ThemeMode
import com.example.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) = settings.update { it.copy(dynamicColor = enabled) }
    }

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
