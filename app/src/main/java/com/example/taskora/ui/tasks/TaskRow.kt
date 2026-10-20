package com.example.taskora.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import com.example.taskora.R
import com.example.taskora.domain.model.Priority
import com.example.taskora.domain.model.Task
import com.example.taskora.ui.common.DueStatus
import com.example.taskora.ui.common.color
import com.example.taskora.ui.common.dueStatus
import com.example.taskora.ui.common.dueText
import com.example.taskora.ui.common.labelRes
import java.time.LocalDateTime

@Composable
fun TaskRow(
    task: Task,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMeta = task.dueDate != null || task.subtasks.isNotEmpty() || task.recurrence != null
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
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
            { TaskMeta(task) }
        } else {
            null
        },
        trailingContent = if (task.priority != Priority.NONE && !task.isCompleted) {
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
private fun TaskMeta(task: Task) {
    val metaStyle = MaterialTheme.typography.labelMedium
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
