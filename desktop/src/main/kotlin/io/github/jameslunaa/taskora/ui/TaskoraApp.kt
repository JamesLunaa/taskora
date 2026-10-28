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
import io.github.jameslunaa.taskora.ui.tasks.TaskListEvent
import io.github.jameslunaa.taskora.ui.tasks.TaskListPane
import io.github.jameslunaa.taskora.ui.tasks.TaskListViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** State the window's keyboard shortcuts act on. */
class AppShortcuts {
    val addFieldFocus = FocusRequester()
    var closeDetail: () -> Unit = {}
}

/**
 * Three panes: lists on the left, the current list in the middle, and the selected task on the
 * right. The phone's navigation (list -> detail) becomes selection here.
 */
@Composable
fun TaskoraApp(container: AppContainer, shortcuts: AppShortcuts) {
    var listId by remember { mutableStateOf(TaskList.INBOX_ID) }
    var taskId by remember { mutableStateOf<Long?>(null) }
    var creatingList by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    shortcuts.closeDetail = { taskId = null }

    val listViewModel = scopedViewModel(listId) {
        TaskListViewModel(listId, container.taskListRepository, container.taskRepository)
    }
    val listState by listViewModel.uiState.collectAsStateWithLifecycle()

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

    val deleteTask: (Task) -> Unit = { task ->
        if (task.id == taskId) taskId = null
        listViewModel.deleteTasks(listOf(task))
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = getPluralString(Res.plurals.n_tasks_deleted, 1, 1),
                actionLabel = getString(Res.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) listViewModel.restoreTasks(listOf(task))
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                lists = listState.lists,
                selectedListId = listId,
                onSelect = {
                    if (it != listId) taskId = null
                    listId = it
                },
                onNewList = { creatingList = true },
                modifier = Modifier.width(260.dp),
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
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
                )
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
            onAddSubtask = viewModel::addSubtask,
            onSubtaskCompletedChange = viewModel::setSubtaskCompleted,
            onDeleteSubtask = viewModel::deleteSubtask,
            onDelete = onDelete,
            onClose = onClose,
        )
    }
    TaskDetailPane(uiState, actions, Modifier.width(400.dp))
}
