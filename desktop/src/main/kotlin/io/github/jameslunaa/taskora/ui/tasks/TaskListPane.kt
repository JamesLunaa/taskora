package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.add
import io.github.jameslunaa.taskora.resources.all_tasks
import io.github.jameslunaa.taskora.resources.cancel
import io.github.jameslunaa.taskora.resources.clear_completed
import io.github.jameslunaa.taskora.resources.clear_completed_confirm
import io.github.jameslunaa.taskora.resources.completed_count
import io.github.jameslunaa.taskora.resources.delete
import io.github.jameslunaa.taskora.resources.delete_list
import io.github.jameslunaa.taskora.resources.delete_list_confirm
import io.github.jameslunaa.taskora.resources.edit_list
import io.github.jameslunaa.taskora.resources.more_options
import io.github.jameslunaa.taskora.resources.new_task_hint
import io.github.jameslunaa.taskora.resources.no_tasks_hint
import io.github.jameslunaa.taskora.resources.no_tasks_title
import io.github.jameslunaa.taskora.ui.common.headerBandTopAppBarColors
import io.github.jameslunaa.taskora.ui.lists.ListEditorDialog
import io.github.jameslunaa.taskora.ui.lists.SortMenu
import io.github.jameslunaa.taskora.ui.taskdetail.DueDatePickerDialog
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class ListDialog { EDIT, DELETE, CLEAR_COMPLETED }

/** The middle pane: the current list's header, the add-task field and the tasks. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TaskListPane(
    uiState: TaskListUiState,
    selectedTaskId: Long?,
    addFieldFocus: FocusRequester,
    onAddTask: (String) -> Unit,
    onOpenTask: (Long) -> Unit,
    onCheckedChange: (Task, Boolean) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onEditList: (name: String, color: Int) -> Unit,
    onDeleteList: () -> Unit,
    onClearCompleted: () -> Unit,
    onSortChange: (TaskSortMode, Boolean) -> Unit,
    /** Saves a drag: the list's active task ids in their new order. */
    onReorder: (List<Long>) -> Unit,
    bulkActions: TaskBulkActions,
    onToggleSelection: (Long) -> Unit,
    onSetSelection: (Set<Long>) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<ListDialog?>(null) }
    var completedExpanded by remember(uiState.list?.id, uiState.isAllTasks) { mutableStateOf(false) }
    val listsById = remember(uiState.lists) { uiState.lists.associateBy { it.id } }
    val list = uiState.list

    // While dragging, the order lives here; it's saved when the drag ends.
    val lazyListState = rememberLazyListState()
    val canDrag = list != null && !uiState.isAllTasks && !uiState.isSelecting
    var moved by remember { mutableStateOf(false) }
    var dragTasks by remember(uiState.activeTasks) { mutableStateOf(uiState.activeTasks) }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = dragTasks.indexOfFirst { it.id == from.key }
        val toIndex = dragTasks.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            moved = true
            dragTasks = dragTasks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    // Right-click menu, and the date picker it or the selection bar can open.
    var menuTask by remember { mutableStateOf<Task?>(null) }
    var pickDateFor by remember { mutableStateOf<Collection<Long>?>(null) }
    val allTasks = uiState.activeTasks + uiState.completedTasks
    val selectedTasks = allTasks.filter { it.id in uiState.selectedIds }

    // Ctrl+click toggles, Shift+click selects a range from the last toggled row, and plain clicks
    // toggle too while selecting (as on the phone); otherwise a click opens the task.
    val windowInfo = LocalWindowInfo.current
    var anchorId by remember { mutableStateOf<Long?>(null) }
    fun rowClick(task: Task) {
        val modifiers = windowInfo.keyboardModifiers
        val order = (if (canDrag) dragTasks else uiState.activeTasks) +
            if (completedExpanded) uiState.completedTasks else emptyList()
        val from = order.indexOfFirst { it.id == anchorId }
        val to = order.indexOfFirst { it.id == task.id }
        when {
            modifiers.isShiftPressed && from >= 0 && to >= 0 -> onSetSelection(
                uiState.selectedIds + order.subList(minOf(from, to), maxOf(from, to) + 1).map { it.id },
            )
            modifiers.isCtrlPressed || modifiers.isShiftPressed || uiState.isSelecting -> {
                onToggleSelection(task.id)
                anchorId = task.id
            }
            else -> onOpenTask(task.id)
        }
    }

    Column(modifier.fillMaxSize()) {
        if (uiState.isSelecting) {
            SelectionBar(
                selected = selectedTasks,
                lists = uiState.lists,
                actions = bulkActions,
                onPickDate = { pickDateFor = it },
                onSelectAll = onSelectAll,
                onClear = onClearSelection,
            )
        } else {
    TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (list != null) Box(Modifier.size(12.dp).background(listColor(list.color), CircleShape))
                        Text(
                            if (uiState.isAllTasks) stringResource(Res.string.all_tasks) else list?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (list != null) {
                        SortMenu(list.sortMode, list.sortDescending, onSortChange)
                        IconButton(onClick = { dialog = ListDialog.EDIT }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.edit_list))
                        }
                    }
                    ListMenu(
                        canDeleteList = list != null && !list.isInbox,
                        canClearCompleted = uiState.completedTasks.isNotEmpty(),
                        onSelect = { dialog = it },
                    )
                },
                colors = headerBandTopAppBarColors(),
            )
        }

        AddTaskField(onAdd = onAddTask, focusRequester = addFieldFocus)

        if (!uiState.isLoading && uiState.isEmpty) {
            EmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lazyListState) {
                uiState.sections.forEach { section ->
                    section.list?.let { header ->
                        item(key = "header-${header.id}") { SectionHeader(header) }
                    }
                    val tasks = if (canDrag) dragTasks else section.tasks
                    items(tasks, key = { it.id }) { task ->
                        ReorderableItem(reorderState, key = task.id, enabled = canDrag) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            Surface(shadowElevation = elevation) {
                                TaskRow(
                                    task = task,
                                    onCheckedChange = { onCheckedChange(task, it) },
                                    onClick = { rowClick(task) },
                                    onDelete = { onDeleteTask(task) },
                                    selected = task.id == selectedTaskId || task.id in uiState.selectedIds,
                                    onContextMenu = { menuTask = task },
                                    dragHandle = if (canDrag) {
                                        Modifier.draggableHandle(
                                            onDragStarted = { moved = false },
                                            onDragStopped = { if (moved) onReorder(dragTasks.map { it.id }) },
                                        )
                                    } else {
                                        null
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
                        )
                    }
                    if (completedExpanded) {
                        items(uiState.completedTasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                onCheckedChange = { onCheckedChange(task, it) },
                                onClick = { rowClick(task) },
                                onDelete = { onDeleteTask(task) },
                                list = if (uiState.isAllTasks) listsById[task.listId] else null,
                                selected = task.id == selectedTaskId || task.id in uiState.selectedIds,
                                onContextMenu = { menuTask = task },
                            )
                        }
                    }
                }
            }
        }
    }

    menuTask?.let { task ->
        TaskContextMenu(
            // A right-click on a selected row acts on the whole selection.
            tasks = if (task.id in uiState.selectedIds) selectedTasks else listOf(task),
            lists = uiState.lists,
            actions = bulkActions,
            onPickDate = { pickDateFor = it },
            onDismiss = { menuTask = null },
        )
    }
    pickDateFor?.let { ids ->
        DueDatePickerDialog(
            initialDate = allTasks.firstOrNull { it.id in ids }?.dueDate,
            onConfirm = { bulkActions.onSetDueDate(ids, it) },
            onDismiss = { pickDateFor = null },
        )
    }

    when (dialog) {
        ListDialog.EDIT -> if (list != null) {
            ListEditorDialog(
                isNew = false,
                initialName = list.name,
                initialColor = list.color,
                onConfirm = onEditList,
                onDismiss = { dialog = null },
            )
        }
        ListDialog.DELETE -> if (list != null) {
            val total = list.activeTaskCount + uiState.completedTasks.size
            ConfirmDialog(
                text = pluralStringResource(Res.plurals.delete_list_confirm, total, list.name, total),
                confirmLabel = stringResource(Res.string.delete),
                onConfirm = onDeleteList,
                onDismiss = { dialog = null },
            )
        }
        ListDialog.CLEAR_COMPLETED -> {
            val count = uiState.completedTasks.size
            ConfirmDialog(
                text = pluralStringResource(Res.plurals.clear_completed_confirm, count, count),
                confirmLabel = stringResource(Res.string.clear_completed),
                onConfirm = onClearCompleted,
                onDismiss = { dialog = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun ListMenu(canDeleteList: Boolean, canClearCompleted: Boolean, onSelect: (ListDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.clear_completed)) },
                enabled = canClearCompleted,
                onClick = {
                    expanded = false
                    onSelect(ListDialog.CLEAR_COMPLETED)
                },
            )
            if (canDeleteList) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.delete_list)) },
                    onClick = {
                        expanded = false
                        onSelect(ListDialog.DELETE)
                    },
                )
            }
        }
    }
}

/** Enter adds the task and keeps focus in the field, so several can be typed in a row. */
@Composable
private fun AddTaskField(onAdd: (String) -> Unit, focusRequester: FocusRequester) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text.trim())
            text = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(Res.string.new_task_hint)) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                        submit()
                        true
                    } else {
                        false
                    }
                },
        )
        // The screen's one lamp-purple action.
        Button(onClick = submit, enabled = text.isNotBlank()) { Text(stringResource(Res.string.add)) }
    }
}

@Composable
private fun SectionHeader(list: TaskList) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(listColor(list.color), CircleShape))
        Text(list.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        Text(stringResource(Res.string.completed_count, count), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.no_tasks_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.no_tasks_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
