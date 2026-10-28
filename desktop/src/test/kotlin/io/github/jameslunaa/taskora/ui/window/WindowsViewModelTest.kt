package io.github.jameslunaa.taskora.ui.window

import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.NoteSettings
import io.github.jameslunaa.taskora.domain.model.WindowBounds
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.testing.FakeSettingsRepository
import io.github.jameslunaa.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class WindowsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    @Test
    fun levels_arePerWindow() = runTest {
        val viewModel = WindowsViewModel(repository)
        viewModel.setLevel(AppWindow.MAIN, WindowLevel.BELOW)
        viewModel.setLevel(AppWindow.NOTE, WindowLevel.NORMAL)
        assertEquals(WindowLevel.BELOW, viewModel.settings.value?.mainWindow?.level)
        assertEquals(WindowLevel.NORMAL, viewModel.settings.value?.noteWindow?.level)
    }

    @Test
    fun opacityPreview_showsLive_butIsSavedOnlyOnRelease() = runTest {
        val viewModel = WindowsViewModel(repository)
        viewModel.previewOpacity(AppWindow.NOTE, 0.5f)
        assertEquals(0.5f, viewModel.settings.value?.noteWindow?.opacity)
        assertEquals(1f, repository.settings.value.noteWindow.opacity)

        viewModel.commitOpacity(AppWindow.NOTE)
        assertEquals(0.5f, repository.settings.value.noteWindow.opacity)
        assertEquals(0.5f, viewModel.settings.value?.noteWindow?.opacity)
        // The main window is untouched.
        assertEquals(WindowLook(), viewModel.settings.value?.mainWindow)
    }

    @Test
    fun opacityPreview_isClamped() = runTest {
        val viewModel = WindowsViewModel(repository)
        viewModel.previewOpacity(AppWindow.MAIN, 0f)
        assertEquals(WindowLook.MIN_OPACITY, viewModel.settings.value?.mainWindow?.opacity)
    }

    @Test
    fun note_opensPicksListAndRemembersBounds() = runTest {
        val viewModel = WindowsViewModel(repository)
        viewModel.setNoteOpen(true)
        viewModel.setNoteListId(4)
        viewModel.setNoteBounds(WindowBounds(10, 20, 300, 400))
        assertEquals(NoteSettings(true, 4, WindowBounds(10, 20, 300, 400)), viewModel.settings.value?.note)
    }

    @Test
    fun sameBounds_areNotSavedAgain() = runTest {
        val viewModel = WindowsViewModel(repository)
        viewModel.setNoteBounds(WindowBounds(10, 20, 300, 400))
        val saved = repository.settings.value
        viewModel.setNoteBounds(WindowBounds(10, 20, 300, 400))
        assertSame(saved, repository.settings.value)
    }
}
