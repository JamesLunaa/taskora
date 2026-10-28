package io.github.jameslunaa.taskora.ui.note

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.AppContainer
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.WindowBounds
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.sticky_note_title
import io.github.jameslunaa.taskora.resources.taskora_logo
import io.github.jameslunaa.taskora.system.WindowHints
import io.github.jameslunaa.taskora.ui.common.scopedViewModel
import io.github.jameslunaa.taskora.ui.tasks.TaskListViewModel
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import io.github.jameslunaa.taskora.ui.window.ApplyWindowLook
import io.github.jameslunaa.taskora.ui.window.WindowLookActions
import io.github.jameslunaa.taskora.ui.window.WindowsViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val DEFAULT_SIZE = DpSize(320.dp, 440.dp)
private val MIN_SIZE = DpSize(240.dp, 200.dp)

/**
 * The undecorated sticky-note window. It stays composed while closed, so its position and size
 * survive hiding it. It's left out of the taskbar; the tray and the main window's pin menu bring
 * it back.
 */
@OptIn(FlowPreview::class)
@Composable
fun StickyNoteWindow(
    container: AppContainer,
    windows: WindowsViewModel,
    settings: AppSettings,
    hints: WindowHints,
    darkTheme: Boolean,
    accent: Color?,
    onOpenTask: (Long) -> Unit,
) {
    val note = settings.note
    val look = settings.noteWindow
    val state = rememberWindowState(
        position = note.bounds?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition(Alignment.TopEnd),
        size = note.bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DEFAULT_SIZE,
    )
    // Saves where the note was moved or resized to, once it settles.
    LaunchedEffect(state) {
        snapshotFlow { state.position to state.size }
            .drop(1)
            .debounce(500)
            .collect { (position, size) ->
                if (position is WindowPosition.Absolute) {
                    windows.setNoteBounds(
                        WindowBounds(
                            position.x.value.toInt(),
                            position.y.value.toInt(),
                            size.width.value.toInt(),
                            size.height.value.toInt(),
                        ),
                    )
                }
            }
    }

    Window(
        onCloseRequest = { windows.setNoteOpen(false) },
        visible = note.isOpen,
        state = state,
        title = stringResource(Res.string.sticky_note_title),
        icon = painterResource(Res.drawable.taskora_logo),
        undecorated = true,
        alwaysOnTop = look.level == WindowLevel.ABOVE,
    ) {
        ApplyWindowLook(look, note.isOpen, hints, skipTaskbar = true)
        val viewModel = scopedViewModel(note.listId) {
            TaskListViewModel(note.listId, container.taskListRepository, container.taskRepository)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        // The note's list was deleted.
        LaunchedEffect(uiState.isListMissing) {
            if (uiState.isListMissing) windows.setNoteListId(TaskList.INBOX_ID)
        }
        val actions = remember(viewModel) {
            StickyNoteActions(
                onListChange = windows::setNoteListId,
                onAddTask = viewModel::addTask,
                onCheckedChange = viewModel::setCompleted,
                onOpenTask = { onOpenTask(it.id) },
                onClose = { windows.setNoteOpen(false) },
                window = WindowLookActions(
                    onLevelChange = { windows.setLevel(AppWindow.NOTE, it) },
                    onOpacityChange = { windows.previewOpacity(AppWindow.NOTE, it) },
                    onOpacityChangeFinished = { windows.commitOpacity(AppWindow.NOTE) },
                ),
                onResize = { delta ->
                    state.size = DpSize(
                        (state.size.width + delta.x).coerceAtLeast(MIN_SIZE.width),
                        (state.size.height + delta.y).coerceAtLeast(MIN_SIZE.height),
                    )
                },
            )
        }
        TaskoraTheme(darkTheme = darkTheme, accent = accent) {
            StickyNote(
                uiState = uiState,
                look = look,
                capabilities = hints.capabilities,
                actions = actions,
                dragArea = { content -> WindowDraggableArea { content() } },
            )
        }
    }
}
