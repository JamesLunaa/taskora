package io.github.jameslunaa.taskora.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.AppContainer
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.n_tasks_deleted
import io.github.jameslunaa.taskora.resources.undo
import io.github.jameslunaa.taskora.ui.common.scopedViewModel
import io.github.jameslunaa.taskora.ui.lists.ListEditorDialog
import io.github.jameslunaa.taskora.ui.lists.ListColors
import io.github.jameslunaa.taskora.ui.lists.Sidebar
import io.github.jameslunaa.taskora.ui.taskdetail.TaskDetailActions
import io.github.jameslunaa.taskora.ui.taskdetail.TaskDetailPane
import io.github.jameslunaa.taskora.ui.taskdetail.TaskDetailViewModel
import io.github.jameslunaa.taskora.ui.tasks.TaskBulkActions
import io.github.jameslunaa.taskora.ui.tasks.TaskListEvent
import io.github.jameslunaa.taskora.ui.tasks.TaskListPane
import io.github.jameslunaa.taskora.ui.tasks.TaskListViewModel
import io.github.jameslunaa.taskora.ui.search.SearchActions
import io.github.jameslunaa.taskora.ui.search.SearchPane
import io.github.jameslunaa.taskora.ui.search.SearchViewModel
import io.github.jameslunaa.taskora.ui.settings.SettingsActions
import io.github.jameslunaa.taskora.ui.settings.SettingsDialog
import io.github.jameslunaa.taskora.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.awt.SystemTray
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** State the window's keyboard shortcuts act on. */
class AppShortcuts {
    val addFieldFocus = FocusRequester()
    val searchFocus = FocusRequester()
    var openSearch: () -> Unit = {}

    /** Esc: closes the innermost thing that's open. */
    var escape: () -> Unit = {}
}

/**
 * Three panes: lists on the left, the current list in the middle, and the selected task on the
 * right. The phone's navigation (list -> detail) becomes selection here.
 */
@Composable
fun TaskoraApp(
    container: AppContainer,
    shortcuts: AppShortcuts,
    /** Tasks to open from outside the window, e.g. the sticky note. */
    openTaskRequests: Flow<Long> = emptyFlow(),
    windowButton: @Composable () -> Unit = {},
) {
    var listId by remember { mutableStateOf(TaskList.INBOX_ID) }
    var taskId by remember { mutableStateOf<Long?>(null) }
    var searching by remember { mutableStateOf(false) }
    var creatingList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    shortcuts.openSearch = {
        if (searching) shortcuts.searchFocus.requestFocus() else searching = true
    }

    val listViewModel = scopedViewModel(listId) {
        TaskListViewModel(listId, container.taskListRepository, container.taskRepository)
    }
    val listState by listViewModel.uiState.collectAsStateWithLifecycle()

    // "Open" on a reminder notification, or a task clicked on the sticky note.
    LaunchedEffect(Unit) {
        merge(container.reminders.openRequests, openTaskRequests).collect { taskId = it }
    }

    LaunchedEffect(listViewModel) {
        listViewModel.events.collect { event ->
            when (event) {
                is TaskListEvent.OpenList -> {
                    listId = event.listId
                    taskId = null
                }
            }
        }
    }
    // The current list was deleted.
    LaunchedEffect(listState.isListMissing) {
        if (listState.isListMissing) {
            listId = TaskList.INBOX_ID
            taskId = null
        }
    }

    val deleteTasks: (List<Task>) -> Unit = { tasks ->
        if (tasks.any { it.id == taskId }) taskId = null
        listViewModel.deleteTasks(tasks)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = getPluralString(Res.plurals.n_tasks_deleted, tasks.size, tasks.size),
                actionLabel = getString(Res.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) listViewModel.restoreTasks(tasks)
        }
    }
    val deleteTask: (Task) -> Unit = { deleteTasks(listOf(it)) }
    val bulkActions = TaskBulkActions(
        onOpen = { taskId = it.id },
        onSelect = { listViewModel.toggleSelection(it.id) },
        onSetDueDate = listViewModel::setDueDate,
        onSetPriority = listViewModel::setPriority,
        onMove = listViewModel::moveTasks,
        onSetCompleted = listViewModel::setTasksCompleted,
        onDelete = deleteTasks,
    )
    // Esc clears the selection first, then closes the task pane.
    shortcuts.escape = {
        if (listState.isSelecting) listViewModel.clearSelection() else taskId = null
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                lists = listState.lists,
                selectedListId = listId.takeUnless { searching },
                searching = searching,
                onSelect = {
                    if (it != listId || searching) taskId = null
                    listId = it
                    searching = false
                },
                onSearch = shortcuts.openSearch,
                onNewList = { creatingList = true },
                onSettings = { showSettings = true },
                modifier = Modifier.width(260.dp),
                windowButton = windowButton,
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (searching) {
                    SearchPaneHost(container, taskId, shortcuts, onOpenTask = { taskId = it }, onDelete = deleteTask)
                } else {
                    TaskListPane(
                        uiState = listState,
                        selectedTaskId = taskId,
                        addFieldFocus = shortcuts.addFieldFocus,
                        onAddTask = listViewModel::addTask,
                        onOpenTask = { taskId = it },
                        onCheckedChange = listViewModel::setCompleted,
                        onDeleteTask = deleteTask,
                        onEditList = listViewModel::editList,
                        onDeleteList = listViewModel::deleteList,
                        onClearCompleted = listViewModel::clearCompleted,
                        onSortChange = listViewModel::setSortMode,
                        onReorder = listViewModel::reorder,
                        bulkActions = bulkActions,
                        onToggleSelection = listViewModel::toggleSelection,
                        onSetSelection = listViewModel::setSelection,
                        onSelectAll = listViewModel::selectAll,
                        onClearSelection = listViewModel::clearSelection,
                    )
                }
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
            taskId?.let { id ->
                VerticalDivider()
                DetailPane(id, container, onDelete = deleteTask, onClose = { taskId = null })
            }
        }
    }

    if (creatingList) {
        ListEditorDialog(
            isNew = true,
            initialName = "",
            initialColor = ListColors.first(),
            onConfirm = listViewModel::createList,
            onDismiss = { creatingList = false },
        )
    }
    if (showSettings) SettingsHost(container, onDismiss = { showSettings = false })
}

@Composable
private fun SearchPaneHost(
    container: AppContainer,
    selectedTaskId: Long?,
    shortcuts: AppShortcuts,
    onOpenTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
) {
    val viewModel = scopedViewModel("search") {
        SearchViewModel(container.taskRepository, container.taskListRepository, container.clock)
    }
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        SearchActions(
            onQueryChange = viewModel::setQuery,
            onStatusChange = viewModel::setStatus,
            onListChange = viewModel::setList,
            onPriorityToggle = viewModel::togglePriority,
            onDueChange = viewModel::setDue,
            onClearFilters = viewModel::clearFilters,
            onTaskClick = { onOpenTask(it.id) },
            onCheckedChange = viewModel::setCompleted,
            onDelete = onDelete,
        )
    }
    SearchPane(filter, uiState, selectedTaskId, shortcuts.searchFocus, actions)
}

@Composable
private fun SettingsHost(container: AppContainer, onDismiss: () -> Unit) {
    val viewModel = scopedViewModel("settings") { SettingsViewModel(container.settingsRepository, container.autostart) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val autostartEnabled by viewModel.autostartEnabled.collectAsStateWithLifecycle()
    SettingsDialog(
        settings = settings,
        trayAvailable = SystemTray.isSupported(),
        autostartAvailable = viewModel.autostartAvailable,
        autostartEnabled = autostartEnabled,
        actions = remember(viewModel) {
            SettingsActions(
                onThemeModeChange = viewModel::setThemeMode,
                onCloseToTrayChange = viewModel::setCloseToTray,
                onAutostartChange = viewModel::setAutostart,
            )
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun DetailPane(taskId: Long, container: AppContainer, onDelete: (Task) -> Unit, onClose: () -> Unit) {
    val viewModel = scopedViewModel(taskId) {
        TaskDetailViewModel(taskId, container.taskRepository, container.taskListRepository, container.clock)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isClosed) { if (uiState.isClosed) onClose() }
    val actions = remember(viewModel) {
        TaskDetailActions(
            onTitleChange = viewModel::setTitle,
            onNotesChange = viewModel::setNotes,
            onCompletedChange = viewModel::setCompleted,
            onListChange = viewModel::moveToList,
            onDueDateChange = viewModel::setDueDate,
            onDueTimeChange = viewModel::setDueTime,
            onPriorityChange = viewModel::setPriority,
            onRecurrenceChange = viewModel::setRecurrence,
            onReminderChange = viewModel::setReminder,
            onAddSubtask = viewModel::addSubtask,
            onSubtaskCompletedChange = viewModel::setSubtaskCompleted,
            onDeleteSubtask = viewModel::deleteSubtask,
            onDelete = onDelete,
            onClose = onClose,
        )
    }
    TaskDetailPane(uiState, actions, container.reminders.isAvailable, Modifier.width(400.dp))
}
