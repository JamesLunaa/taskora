package io.github.jameslunaa.taskora.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.ui.common.headerBandTopAppBarColors
import io.github.jameslunaa.taskora.ui.lists.ListColors
import io.github.jameslunaa.taskora.ui.lists.ListEditorDialog
import io.github.jameslunaa.taskora.ui.lists.ListsDrawer
import io.github.jameslunaa.taskora.ui.lists.SortDialog
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TaskListScreen(
    onTaskClick: (Long) -> Unit,
    onOpenList: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TaskListEvent.OpenList -> onOpenList(event.listId)
            }
        }
    }
    LaunchedEffect(uiState.isListMissing) {
        if (uiState.isListMissing) onOpenList(TaskList.INBOX_ID)
    }
    TaskListContent(
        uiState = uiState,
        actions = TaskListActions(
            onTaskClick = { onTaskClick(it.id) },
            onAddTask = viewModel::addTask,
            onCheckedChange = viewModel::setCompleted,
            onDeleteTask = viewModel::deleteTask,
            onUndoDelete = viewModel::restoreTask,
            onClearCompleted = viewModel::clearCompleted,
            onOpenList = { if (it.id != uiState.list?.id) onOpenList(it.id) },
            onCreateList = viewModel::createList,
            onEditList = viewModel::editList,
            onDeleteList = viewModel::deleteList,
            onSortModeChange = viewModel::setSortMode,
            onSearch = onSearch,
            onOpenSettings = onOpenSettings,
        ),
    )
}

class TaskListActions(
    val onTaskClick: (Task) -> Unit = {},
    val onAddTask: (String) -> Unit = {},
    val onCheckedChange: (Task, Boolean) -> Unit = { _, _ -> },
    val onDeleteTask: (Task) -> Unit = {},
    val onUndoDelete: (Task) -> Unit = {},
    val onClearCompleted: () -> Unit = {},
    val onOpenList: (TaskList) -> Unit = {},
    val onCreateList: (String, Int) -> Unit = { _, _ -> },
    val onEditList: (String, Int) -> Unit = { _, _ -> },
    val onDeleteList: () -> Unit = {},
    val onSortModeChange: (TaskSortMode) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

private enum class TaskListDialog { NONE, CLEAR_COMPLETED, NEW_LIST, EDIT_LIST, DELETE_LIST, SORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListContent(
    uiState: TaskListUiState,
    actions: TaskListActions,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(TaskListDialog.NONE) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.undo)

    val delete: (Task) -> Unit = { task ->
        actions.onDeleteTask(task)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete(task)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ListsDrawer(
                lists = uiState.lists,
                selectedListId = uiState.list?.id,
                onListClick = { list ->
                    scope.launch { drawerState.close() }
                    actions.onOpenList(list)
                },
                onNewList = {
                    scope.launch { drawerState.close() }
                    dialog = TaskListDialog.NEW_LIST
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    actions.onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.list?.name.orEmpty()) },
                    colors = headerBandTopAppBarColors(),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.open_lists))
                        }
                    },
                    actions = {
                        IconButton(onClick = actions.onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                        }
                        OverflowMenu(
                            canEditList = uiState.list != null,
                            canDeleteList = uiState.list?.isInbox == false,
                            clearCompletedEnabled = uiState.completedTasks.isNotEmpty(),
                            onSelect = { dialog = it },
                        )
                    },
                )
            },
            floatingActionButton = {
                // The screen's one lamp-purple action.
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_task))
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            when {
                uiState.isLoading -> Unit
                uiState.isEmpty -> EmptyState(Modifier.padding(innerPadding))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        // Keep the last row clear of the FAB.
                        bottom = innerPadding.calculateBottomPadding() + 88.dp,
                    ),
                ) {
                    items(uiState.activeTasks, key = { it.id }) { task ->
                        SwipeToDeleteRow(
                            task = task,
                            onDelete = { delete(task) },
                            onCheckedChange = { actions.onCheckedChange(task, it) },
                            onClick = { actions.onTaskClick(task) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (uiState.completedTasks.isNotEmpty()) {
                        item(key = "completed-header") {
                            CompletedHeader(
                                count = uiState.completedTasks.size,
                                expanded = completedExpanded,
                                onToggle = { completedExpanded = !completedExpanded },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (completedExpanded) {
                            items(uiState.completedTasks, key = { it.id }) { task ->
                                SwipeToDeleteRow(
                                    task = task,
                                    onDelete = { delete(task) },
                                    onCheckedChange = { actions.onCheckedChange(task, it) },
                                    onClick = { actions.onTaskClick(task) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddTaskSheet(onAdd = actions.onAddTask, onDismiss = { showAddSheet = false })
    }
    val dismiss = { dialog = TaskListDialog.NONE }
    when (dialog) {
        TaskListDialog.NONE -> Unit
        TaskListDialog.CLEAR_COMPLETED -> {
            val count = uiState.completedTasks.size
            ConfirmDeleteDialog(
                title = stringResource(R.string.clear_completed),
                text = pluralStringResource(R.plurals.clear_completed_confirm, count, count),
                onConfirm = actions.onClearCompleted,
                onDismiss = dismiss,
            )
        }
        TaskListDialog.NEW_LIST -> ListEditorDialog(
            isNew = true,
            initialName = "",
            initialColor = ListColors.first(),
            onConfirm = actions.onCreateList,
            onDismiss = dismiss,
        )
        TaskListDialog.EDIT_LIST -> uiState.list?.let { list ->
            ListEditorDialog(
                isNew = false,
                initialName = list.name,
                initialColor = list.color,
                onConfirm = actions.onEditList,
                onDismiss = dismiss,
            )
        }
        TaskListDialog.DELETE_LIST -> uiState.list?.let { list ->
            val count = uiState.activeTasks.size + uiState.completedTasks.size
            ConfirmDeleteDialog(
                title = stringResource(R.string.delete_list),
                text = pluralStringResource(R.plurals.delete_list_confirm, count, list.name, count),
                onConfirm = actions.onDeleteList,
                onDismiss = dismiss,
            )
        }
        TaskListDialog.SORT -> SortDialog(
            current = uiState.list?.sortMode ?: TaskSortMode.CREATED,
            onSelect = actions.onSortModeChange,
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun OverflowMenu(
    canEditList: Boolean,
    canDeleteList: Boolean,
    clearCompletedEnabled: Boolean,
    onSelect: (TaskListDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    @Composable
    fun Item(@StringRes label: Int, dialog: TaskListDialog, enabled: Boolean = true) {
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            enabled = enabled,
            onClick = {
                expanded = false
                onSelect(dialog)
            },
        )
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Item(R.string.sort_by, TaskListDialog.SORT, enabled = canEditList)
            Item(R.string.edit_list, TaskListDialog.EDIT_LIST, enabled = canEditList)
            if (canDeleteList) Item(R.string.delete_list, TaskListDialog.DELETE_LIST)
            Item(R.string.clear_completed, TaskListDialog.CLEAR_COMPLETED, enabled = clearCompletedEnabled)
        }
    }
}

@Composable
private fun SwipeToDeleteRow(
    task: Task,
    onDelete: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        TaskRow(task = task, onCheckedChange = onCheckedChange, onClick = onClick)
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onToggle),
        headlineContent = {
            Text(
                stringResource(R.string.completed_count, count),
                style = MaterialTheme.typography.titleSmall,
            )
        },
        trailingContent = {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        },
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.no_tasks_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.no_tasks_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskListPreview() {
    val inbox = TaskList(id = 1, name = "Inbox", color = TaskList.DEFAULT_COLOR, activeTaskCount = 2)
    TaskoraTheme {
        TaskListContent(
            uiState = TaskListUiState(
                isLoading = false,
                list = inbox,
                lists = listOf(inbox),
                activeTasks = listOf(
                    Task(id = 1, listId = 1, title = "Buy groceries", dueDate = LocalDate.now(), priority = Priority.HIGH),
                    Task(id = 2, listId = 1, title = "Call the dentist"),
                ),
                completedTasks = listOf(Task(id = 3, listId = 1, title = "Pay rent", isCompleted = true)),
            ),
            actions = TaskListActions(),
        )
    }
}
