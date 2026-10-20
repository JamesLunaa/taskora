package com.example.taskora.ui.tasks

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.example.taskora.R
import com.example.taskora.domain.model.Priority
import com.example.taskora.domain.model.Task
import com.example.taskora.ui.theme.TaskoraTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TaskListScreen(
    onTaskClick: (Long) -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TaskListContent(
        uiState = uiState,
        onTaskClick = { onTaskClick(it.id) },
        onAddTask = viewModel::addTask,
        onCheckedChange = viewModel::setCompleted,
        onDeleteTask = viewModel::deleteTask,
        onUndoDelete = viewModel::restoreTask,
        onClearCompleted = viewModel::clearCompleted,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListContent(
    uiState: TaskListUiState,
    onTaskClick: (Task) -> Unit,
    onAddTask: (String) -> Unit,
    onCheckedChange: (Task, Boolean) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onUndoDelete: (Task) -> Unit,
    onClearCompleted: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.task_deleted)
    val undoLabel = stringResource(R.string.undo)

    val delete: (Task) -> Unit = { task ->
        onDeleteTask(task)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndoDelete(task)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.listName) },
                actions = {
                    OverflowMenu(
                        clearCompletedEnabled = uiState.completedTasks.isNotEmpty(),
                        onClearCompleted = { showClearDialog = true },
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
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
                        onCheckedChange = { onCheckedChange(task, it) },
                        onClick = { onTaskClick(task) },
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
                                onCheckedChange = { onCheckedChange(task, it) },
                                onClick = { onTaskClick(task) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddTaskSheet(onAdd = onAddTask, onDismiss = { showAddSheet = false })
    }
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_completed)) },
            text = {
                val count = uiState.completedTasks.size
                Text(pluralStringResource(R.plurals.clear_completed_confirm, count, count))
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearCompleted()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun OverflowMenu(clearCompletedEnabled: Boolean, onClearCompleted: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.clear_completed)) },
                enabled = clearCompletedEnabled,
                onClick = {
                    expanded = false
                    onClearCompleted()
                },
            )
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
    TaskoraTheme {
        TaskListContent(
            uiState = TaskListUiState(
                isLoading = false,
                listName = "Inbox",
                activeTasks = listOf(
                    Task(id = 1, listId = 1, title = "Buy groceries", dueDate = LocalDate.now(), priority = Priority.HIGH),
                    Task(id = 2, listId = 1, title = "Call the dentist"),
                ),
                completedTasks = listOf(Task(id = 3, listId = 1, title = "Pay rent", isCompleted = true)),
            ),
            onTaskClick = {},
            onAddTask = {},
            onCheckedChange = { _, _ -> },
            onDeleteTask = {},
            onUndoDelete = {},
            onClearCompleted = {},
        )
    }
}
