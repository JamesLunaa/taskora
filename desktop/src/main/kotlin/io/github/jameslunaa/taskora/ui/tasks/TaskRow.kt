package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import io.github.jameslunaa.taskora.ui.icons.filled.Flag
import io.github.jameslunaa.taskora.ui.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import io.github.jameslunaa.taskora.ui.icons.outlined.DragIndicator
import io.github.jameslunaa.taskora.ui.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.DueStatus
import io.github.jameslunaa.taskora.domain.dueStatus
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.delete_task
import io.github.jameslunaa.taskora.resources.drag_to_reorder
import io.github.jameslunaa.taskora.resources.repeats
import io.github.jameslunaa.taskora.resources.subtask_progress
import io.github.jameslunaa.taskora.ui.common.color
import io.github.jameslunaa.taskora.ui.common.dueText
import io.github.jameslunaa.taskora.ui.common.label
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDateTime

/** Hovering shows a delete button, the desktop stand-in for the phone's swipe-to-delete. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown in the meta line when tasks from several lists are mixed (all tasks). */
    list: TaskList? = null,
    /** The task open in the detail pane. */
    selected: Boolean = false,
    /** Makes the row draggable by a handle that shows on hover (from the reorderable item scope). */
    dragHandle: Modifier? = null,
    /** Right-click. */
    onContextMenu: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val hasFlag = task.priority != Priority.NONE && !task.isCompleted
    val hasMeta = list != null || task.dueDate != null || task.subtasks.isNotEmpty() || task.recurrence != null
    ListItem(
        modifier = modifier
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) {
                if (it.buttons.isSecondaryPressed) onContextMenu?.invoke()
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                hovered -> MaterialTheme.colorScheme.surfaceContainerLow
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragHandle != null) {
                    Box(dragHandle.size(24.dp, 40.dp), contentAlignment = Alignment.Center) {
                        if (hovered) {
                            Icon(
                                Icons.Outlined.DragIndicator,
                                contentDescription = stringResource(Res.string.drag_to_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Checkbox(checked = task.isCompleted, onCheckedChange = onCheckedChange)
            }
        },
        headlineContent = {
            Text(
                text = task.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = if (hasMeta) {
            { TaskMeta(task, list) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasFlag) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = stringResource(task.priority.label),
                        tint = task.priority.color(),
                    )
                }
                // Keeps its space when hidden, so rows don't shift under the pointer.
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (hovered) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.delete_task))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun TaskMeta(task: Task, list: TaskList?) {
    val metaStyle = MaterialTheme.typography.labelMedium
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        list?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(listColor(it.color), CircleShape),
                )
                Text(it.name, style = metaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        task.dueDate?.let { date ->
            val overdue = !task.isCompleted &&
                dueStatus(date, task.dueTime, LocalDateTime.now()) == DueStatus.OVERDUE
            Text(
                text = dueText(date, task.dueTime),
                style = metaStyle,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (task.subtasks.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(
                        Res.string.subtask_progress,
                        task.subtasks.count { it.isCompleted },
                        task.subtasks.size,
                    ),
                    style = metaStyle,
                )
            }
        }
        if (task.recurrence != null) {
            Icon(
                Icons.Outlined.Repeat,
                contentDescription = stringResource(Res.string.repeats),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
