package io.github.jameslunaa.taskora.ui.note

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.DueStatus
import io.github.jameslunaa.taskora.domain.dueStatus
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.all_tasks
import io.github.jameslunaa.taskora.resources.close
import io.github.jameslunaa.taskora.resources.note_add_hint
import io.github.jameslunaa.taskora.resources.note_choose_list
import io.github.jameslunaa.taskora.resources.note_empty
import io.github.jameslunaa.taskora.resources.note_resize
import io.github.jameslunaa.taskora.ui.common.color
import io.github.jameslunaa.taskora.ui.common.dueText
import io.github.jameslunaa.taskora.ui.common.label
import io.github.jameslunaa.taskora.ui.tasks.TaskListUiState
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import io.github.jameslunaa.taskora.ui.theme.listColor
import io.github.jameslunaa.taskora.system.WindowCapabilities
import io.github.jameslunaa.taskora.ui.window.WindowLookActions
import io.github.jameslunaa.taskora.ui.window.WindowLookButton
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor
import java.time.LocalDateTime

class StickyNoteActions(
    val onListChange: (Long) -> Unit,
    val onAddTask: (String) -> Unit,
    val onCheckedChange: (Task, Boolean) -> Unit,
    /** Opens the task in the main window. */
    val onOpenTask: (Task) -> Unit,
    val onClose: () -> Unit,
    val window: WindowLookActions,
    /** Drag on the corner grip. */
    val onResize: (DpOffset) -> Unit,
)

/**
 * A compact view of one list's open tasks, for the undecorated note window. [dragArea] wraps the
 * header so dragging it moves the window (it's `WindowDraggableArea` in a real window).
 */
@Composable
fun StickyNote(
    uiState: TaskListUiState,
    look: WindowLook,
    capabilities: WindowCapabilities,
    actions: StickyNoteActions,
    modifier: Modifier = Modifier,
    dragArea: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    Surface(
        modifier = modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box {
            Column(Modifier.fillMaxSize()) {
                dragArea { NoteHeader(uiState, look, capabilities, actions) }
                val tasks = uiState.activeTasks
                if (tasks.isEmpty() && !uiState.isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.note_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(tasks, key = { it.id }) { task ->
                            NoteTaskRow(
                                task = task,
                                onCheckedChange = { actions.onCheckedChange(task, it) },
                                onClick = { actions.onOpenTask(task) },
                            )
                        }
                    }
                }
                HorizontalDivider()
                AddTaskField(actions.onAddTask)
            }
            ResizeGrip(actions.onResize, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun NoteHeader(
    uiState: TaskListUiState,
    look: WindowLook,
    capabilities: WindowCapabilities,
    actions: StickyNoteActions,
) {
    val colors = TaskoraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.headerBand)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var picking by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .clickable { picking = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.list?.let { Box(Modifier.size(10.dp).background(listColor(it.color), CircleShape)) }
                Text(
                    uiState.list?.name ?: stringResource(Res.string.all_tasks),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onHeaderBand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(Res.string.note_choose_list),
                    tint = colors.headerBandAccent,
                )
            }
            ListPicker(
                expanded = picking,
                lists = uiState.lists,
                selectedId = uiState.list?.id ?: TaskList.ALL_TASKS_ID,
                onSelect = {
                    picking = false
                    actions.onListChange(it)
                },
                onDismiss = { picking = false },
            )
        }
        WindowLookButton(look, capabilities, actions.window, tint = colors.headerBandAccent)
        IconButton(onClick = actions.onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.close), tint = colors.headerBandAccent)
        }
    }
}

@Composable
private fun ListPicker(
    expanded: Boolean,
    lists: List<TaskList>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        PickerItem(stringResource(Res.string.all_tasks), null, selectedId == TaskList.ALL_TASKS_ID) {
            onSelect(TaskList.ALL_TASKS_ID)
        }
        lists.forEach { list ->
            PickerItem(list.name, listColor(list.color), list.id == selectedId) { onSelect(list.id) }
        }
    }
}

@Composable
private fun PickerItem(text: String, dot: Color?, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (dot != null) Box(Modifier.size(10.dp).background(dot, CircleShape))
            }
        },
        trailingIcon = { if (checked) Icon(Icons.Filled.Check, contentDescription = null) },
    )
}

@Composable
private fun NoteTaskRow(task: Task, onCheckedChange: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.dueDate?.let { date ->
                val overdue = dueStatus(date, task.dueTime, LocalDateTime.now()) == DueStatus.OVERDUE
                Text(
                    dueText(date, task.dueTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (task.priority != Priority.NONE) {
            Icon(
                Icons.Filled.Flag,
                contentDescription = stringResource(task.priority.label),
                tint = task.priority.color(),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AddTaskField(onAddTask: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAddTask(text.trim())
            text = ""
        }
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(Res.string.note_add_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            // Leaves room for the resize grip.
            .padding(end = 16.dp)
            .onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    submit()
                    true
                } else {
                    false
                }
            },
    )
}

/** Undecorated windows have no frame to resize by, so the corner gets a grip. */
@Composable
private fun ResizeGrip(onResize: (DpOffset) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(Res.string.note_resize)
    Canvas(
        modifier
            .size(16.dp)
            .semantics { contentDescription = description }
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    with(density) { onResize(DpOffset(drag.x.toDp(), drag.y.toDp())) }
                }
            },
    ) {
        // Two diagonal strokes in the corner.
        val stroke = 1.dp.toPx()
        for (inset in listOf(4.dp.toPx(), 9.dp.toPx())) {
            drawLine(color, Offset(size.width - 3.dp.toPx(), inset), Offset(inset, size.height - 3.dp.toPx()), stroke)
        }
    }
}
