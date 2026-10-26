package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.ui.common.color
import io.github.jameslunaa.taskora.ui.common.labelRes
import io.github.jameslunaa.taskora.ui.theme.listColor
import java.time.LocalDate

/** What the long-press sheet can do to its task; every callback is for that one task. */
class TaskActions(
    val onSelect: () -> Unit,
    val onSetDueDate: (LocalDate?) -> Unit,
    val onPickDate: () -> Unit,
    val onSetPriority: (Priority) -> Unit,
    val onMove: () -> Unit,
    val onToggleCompleted: () -> Unit,
    val onDelete: () -> Unit,
)

/** The actions for one task, opened by long-pressing its row. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskActionsSheet(task: Task, actions: TaskActions, onDismiss: () -> Unit) {
    // Run the action, then close the sheet.
    fun act(block: () -> Unit): () -> Unit = {
        block()
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionRow(Icons.Outlined.SelectAll, R.string.select, act(actions.onSelect))
            ActionRow(Icons.Outlined.Today, R.string.today, act { actions.onSetDueDate(LocalDate.now()) })
            ActionRow(Icons.Outlined.Event, R.string.tomorrow, act { actions.onSetDueDate(LocalDate.now().plusDays(1)) })
            ActionRow(Icons.Outlined.CalendarMonth, R.string.pick_date, act(actions.onPickDate))
            if (task.dueDate != null) {
                ActionRow(Icons.Outlined.EventBusy, R.string.remove_due_date, act { actions.onSetDueDate(null) })
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.priority), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { option ->
                        FilterChip(
                            selected = option == task.priority,
                            onClick = act { actions.onSetPriority(option) },
                            label = { Text(stringResource(option.labelRes)) },
                            leadingIcon = if (option != Priority.NONE) {
                                { Icon(Icons.Filled.Flag, contentDescription = null, tint = option.color()) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            ActionRow(Icons.AutoMirrored.Outlined.DriveFileMove, R.string.move_to_list, act(actions.onMove))
            ActionRow(
                icon = if (task.isCompleted) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle,
                label = if (task.isCompleted) R.string.mark_incomplete else R.string.mark_complete,
                onClick = act(actions.onToggleCompleted),
            )
            ActionRow(Icons.Outlined.Delete, R.string.delete, act(actions.onDelete), destructive = true)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: Int, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        headlineContent = { Text(stringResource(label), color = tint) },
    )
}

@Composable
fun PriorityDialog(onSelect: (Priority) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_priority)) },
        text = {
            Column {
                Priority.entries.forEach { option ->
                    ListItem(
                        modifier = Modifier.clickable {
                            onSelect(option)
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            if (option != Priority.NONE) {
                                Icon(Icons.Filled.Flag, contentDescription = null, tint = option.color())
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        headlineContent = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MoveToListDialog(
    lists: List<TaskList>,
    onSelect: (TaskList) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_list)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lists.forEach { list ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(list)
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(listColor(list.color), CircleShape),
                            )
                        },
                        headlineContent = { Text(list.name) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
