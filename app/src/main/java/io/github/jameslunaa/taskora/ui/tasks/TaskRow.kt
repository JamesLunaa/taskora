package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.DueStatus
import io.github.jameslunaa.taskora.domain.dueStatus
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.ui.common.color
import io.github.jameslunaa.taskora.ui.common.dueText
import io.github.jameslunaa.taskora.ui.common.labelRes
import io.github.jameslunaa.taskora.ui.theme.listColor
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown in the meta line when tasks from several lists are mixed (search, all tasks). */
    list: TaskList? = null,
    onLongClick: (() -> Unit)? = null,
    /** Highlights the row while it's picked in selection mode. */
    selected: Boolean = false,
) {
    val hasFlag = task.priority != Priority.NONE && !task.isCompleted
    val hasMeta = list != null || task.dueDate != null || task.subtasks.isNotEmpty() || task.recurrence != null
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        leadingContent = {
            Checkbox(checked = task.isCompleted, onCheckedChange = onCheckedChange)
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
        trailingContent = if (hasFlag) {
            {
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = stringResource(task.priority.labelRes),
                    tint = task.priority.color(),
                )
            }
        } else {
            null
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
                        R.string.subtask_progress,
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
                contentDescription = stringResource(R.string.repeats),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
