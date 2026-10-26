package io.github.jameslunaa.taskora.ui.tasks

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import io.github.jameslunaa.taskora.ui.taskdetail.DueDatePickerDialog
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import io.github.jameslunaa.taskora.ui.theme.listColor
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
            onDeleteTasks = viewModel::deleteTasks,
            onUndoDelete = viewModel::restoreTasks,
            onClearCompleted = viewModel::clearCompleted,
            onOpenList = { if (it.id != uiState.list?.id) onOpenList(it.id) },
            onOpenAllTasks = { if (!uiState.isAllTasks) onOpenList(TaskList.ALL_TASKS_ID) },
            onCreateList = viewModel::createList,
            onEditList = viewModel::editList,
            onDeleteList = viewModel::deleteList,
            onSortModeChange = viewModel::setSortMode,
            onSearch = onSearch,
            onOpenSettings = onOpenSettings,
            onToggleSelection = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAll,
            onClearSelection = viewModel::clearSelection,
            onSetDueDate = viewModel::setDueDate,
            onSetPriority = viewModel::setPriority,
            onSetCompleted = viewModel::setTasksCompleted,
            onMoveTasks = viewModel::moveTasks,
            onReorder = viewModel::reorder,
        ),
    )
}

class TaskListActions(
    val onTaskClick: (Task) -> Unit = {},
    val onAddTask: (String) -> Unit = {},
    val onCheckedChange: (Task, Boolean) -> Unit = { _, _ -> },
    val onDeleteTasks: (List<Task>) -> Unit = {},
    val onUndoDelete: (List<Task>) -> Unit = {},
    val onClearCompleted: () -> Unit = {},
    val onOpenList: (TaskList) -> Unit = {},
    val onOpenAllTasks: () -> Unit = {},
    val onCreateList: (String, Int) -> Unit = { _, _ -> },
    val onEditList: (String, Int) -> Unit = { _, _ -> },
    val onDeleteList: () -> Unit = {},
    val onSortModeChange: (TaskSortMode, Boolean) -> Unit = { _, _ -> },
    val onSearch: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleSelection: (Long) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onSetDueDate: (Collection<Long>, LocalDate?) -> Unit = { _, _ -> },
    val onSetPriority: (Collection<Long>, Priority) -> Unit = { _, _ -> },
    val onSetCompleted: (Collection<Long>, Boolean) -> Unit = { _, _ -> },
    val onMoveTasks: (Collection<Long>, Long) -> Unit = { _, _ -> },
    val onReorder: (List<Long>) -> Unit = {},
)

/** A dialog opened for [ids], either from the long-press sheet (one task) or from selection mode. */
private enum class BulkKind { DATE, PRIORITY, MOVE }

private data class BulkTarget(val kind: BulkKind, val ids: List<Long>)

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
    val resources = LocalContext.current.resources
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(TaskListDialog.NONE) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var actionTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bulkTarget by remember { mutableStateOf<BulkTarget?>(null) }
    val undoLabel = stringResource(R.string.undo)

    val allTasks = uiState.activeTasks + uiState.completedTasks
    val selecting = uiState.isSelecting
    val listsById = remember(uiState.lists) { uiState.lists.associateBy { it.id } }

    val delete: (List<Task>) -> Unit = { tasks ->
        actions.onDeleteTasks(tasks)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resources.getQuantityString(R.plurals.n_tasks_deleted, tasks.size, tasks.size),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete(tasks)
        }
    }

    BackHandler(enabled = selecting, onBack = actions.onClearSelection)

    // Drag order is kept locally while a drag is in progress and saved when the finger lifts.
    val lazyListState = rememberLazyListState()
    val canDrag = !uiState.isAllTasks && !selecting && uiState.list != null
    // A long press that never moves is a plain long press, so it opens the actions sheet on release.
    var moved by remember { mutableStateOf(false) }
    // The row also sees the release that ends a long press as a tap; that tap must not open the task.
    var longPressEndedAt by remember { mutableLongStateOf(0L) }
    var longPressing by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var dragTasks by remember(uiState.activeTasks) { mutableStateOf(uiState.activeTasks) }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = dragTasks.indexOfFirst { it.id == from.key }
        val toIndex = dragTasks.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            moved = true
            dragTasks = dragTasks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val selectionActions = SelectionActions(
        onClear = actions.onClearSelection,
        onSelectAll = actions.onSelectAll,
        onComplete = {
            actions.onSetCompleted(uiState.selectedIds, true)
            actions.onClearSelection()
        },
        onDelete = {
            delete(allTasks.filter { it.id in uiState.selectedIds })
            actions.onClearSelection()
        },
        onMove = { bulkTarget = BulkTarget(BulkKind.MOVE, uiState.selectedIds.toList()) },
        onSetPriority = { bulkTarget = BulkTarget(BulkKind.PRIORITY, uiState.selectedIds.toList()) },
        onToday = {
            actions.onSetDueDate(uiState.selectedIds, LocalDate.now())
            actions.onClearSelection()
        },
        onTomorrow = {
            actions.onSetDueDate(uiState.selectedIds, LocalDate.now().plusDays(1))
            actions.onClearSelection()
        },
        onPickDate = { bulkTarget = BulkTarget(BulkKind.DATE, uiState.selectedIds.toList()) },
        onRemoveDate = {
            actions.onSetDueDate(uiState.selectedIds, null)
            actions.onClearSelection()
        },
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !selecting,
        drawerContent = {
            ListsDrawer(
                lists = uiState.lists,
                selectedListId = if (uiState.isAllTasks) TaskList.ALL_TASKS_ID else uiState.list?.id,
                onListClick = { list ->
                    scope.launch { drawerState.close() }
                    actions.onOpenList(list)
                },
                onAllTasks = {
                    scope.launch { drawerState.close() }
                    actions.onOpenAllTasks()
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
                if (selecting) {
                    SelectionTopBar(count = uiState.selectedIds.size, actions = selectionActions)
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                if (uiState.isAllTasks) stringResource(R.string.all_tasks) else uiState.list?.name.orEmpty(),
                            )
                        },
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
                }
            },
            bottomBar = { if (selecting) SelectionBottomBar(selectionActions) },
            floatingActionButton = {
                if (!selecting) {
                    // The screen's one lamp-purple action.
                    FloatingActionButton(
                        onClick = { showAddSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_task))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            when {
                uiState.isLoading -> Unit
                uiState.isEmpty -> EmptyState(Modifier.padding(innerPadding))
                else -> LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        // Keep the last row clear of the FAB.
                        bottom = innerPadding.calculateBottomPadding() + if (selecting) 0.dp else 88.dp,
                    ),
                ) {
                    // Rows toggle in selection mode; long-pressing opens the actions sheet otherwise.
                    fun rowClick(task: Task) {
                        if (longPressing || SystemClock.uptimeMillis() - longPressEndedAt < 500) return
                        if (selecting) actions.onToggleSelection(task.id) else actions.onTaskClick(task)
                    }
                    fun rowLongClick(task: Task) =
                        if (selecting) actions.onToggleSelection(task.id) else actionTaskId = task.id

                    uiState.sections.forEach { section ->
                        section.list?.let { list ->
                            item(key = "section-${list.id}") {
                                SectionHeader(list, section.tasks.size, Modifier.animateItem())
                            }
                        }
                        val tasks = if (canDrag) dragTasks else section.tasks
                        items(tasks, key = { it.id }) { task ->
                            ReorderableItem(reorderState, key = task.id, enabled = canDrag) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag elevation")
                                Surface(shadowElevation = elevation) {
                                    SwipeToDeleteRow(
                                        task = task,
                                        swipeEnabled = !selecting,
                                        selected = task.id in uiState.selectedIds,
                                        onDelete = { delete(listOf(task)) },
                                        onCheckedChange = { actions.onCheckedChange(task, it) },
                                        onClick = { rowClick(task) },
                                        // While dragging is possible the drag handle owns the long press:
                                        // a row long-click would open the sheet before the drag can start.
                                        onLongClick = if (canDrag) null else ({ rowLongClick(task) }),
                                        modifier = if (canDrag) {
                                            Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    moved = false
                                                    longPressing = true
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    longPressing = false
                                                    longPressEndedAt = SystemClock.uptimeMillis()
                                                    if (moved) actions.onReorder(dragTasks.map { it.id }) else actionTaskId = task.id
                                                },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                }
                            }
                        }
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
                                    swipeEnabled = !selecting,
                                    selected = task.id in uiState.selectedIds,
                                    list = if (uiState.isAllTasks) listsById[task.listId] else null,
                                    onDelete = { delete(listOf(task)) },
                                    onCheckedChange = { actions.onCheckedChange(task, it) },
                                    onClick = { rowClick(task) },
                                    onLongClick = { rowLongClick(task) },
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

    allTasks.find { it.id == actionTaskId }?.let { task ->
        val ids = listOf(task.id)
        TaskActionsSheet(
            task = task,
            onDismiss = { actionTaskId = null },
            actions = TaskActions(
                onSelect = { actions.onToggleSelection(task.id) },
                onSetDueDate = { actions.onSetDueDate(ids, it) },
                onPickDate = { bulkTarget = BulkTarget(BulkKind.DATE, ids) },
                onSetPriority = { actions.onSetPriority(ids, it) },
                onMove = { bulkTarget = BulkTarget(BulkKind.MOVE, ids) },
                onToggleCompleted = { actions.onSetCompleted(ids, !task.isCompleted) },
                onDelete = { delete(listOf(task)) },
            ),
        )
    }
    bulkTarget?.let { target ->
        val close = {
            bulkTarget = null
            actions.onClearSelection()
        }
        when (target.kind) {
            BulkKind.DATE -> DueDatePickerDialog(
                initialDate = null,
                onConfirm = { actions.onSetDueDate(target.ids, it) },
                onDismiss = close,
            )
            BulkKind.PRIORITY -> PriorityDialog(
                onSelect = { actions.onSetPriority(target.ids, it) },
                onDismiss = close,
            )
            BulkKind.MOVE -> MoveToListDialog(
                lists = uiState.lists,
                onSelect = { actions.onMoveTasks(target.ids, it.id) },
                onDismiss = close,
            )
        }
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
            currentMode = uiState.list?.sortMode ?: TaskSortMode.CREATED,
            currentDescending = uiState.list?.sortDescending ?: false,
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
    swipeEnabled: Boolean,
    selected: Boolean,
    onDelete: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    list: TaskList? = null,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = swipeEnabled,
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
        TaskRow(
            task = task,
            onCheckedChange = onCheckedChange,
            onClick = onClick,
            onLongClick = onLongClick,
            selected = selected,
            list = list,
        )
    }
}

/** Names the list that the tasks below it belong to (All tasks view). */
@Composable
private fun SectionHeader(list: TaskList, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(listColor(list.color), CircleShape))
        Text(list.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                sections = listOf(
                    TaskSection(
                        list = null,
                        tasks = listOf(
                            Task(id = 1, listId = 1, title = "Buy groceries", dueDate = LocalDate.now(), priority = Priority.HIGH),
                            Task(id = 2, listId = 1, title = "Call the dentist"),
                        ),
                    ),
                ),
                completedTasks = listOf(Task(id = 3, listId = 1, title = "Pay rent", isCompleted = true)),
            ),
            actions = TaskListActions(),
        )
    }
}
