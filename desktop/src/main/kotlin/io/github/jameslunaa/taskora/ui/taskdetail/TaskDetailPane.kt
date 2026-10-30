package io.github.jameslunaa.taskora.ui.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import io.github.jameslunaa.taskora.ui.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import io.github.jameslunaa.taskora.ui.icons.outlined.NotificationsNone
import io.github.jameslunaa.taskora.ui.icons.outlined.Repeat
import io.github.jameslunaa.taskora.ui.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.domain.model.Subtask
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.add_subtask
import io.github.jameslunaa.taskora.resources.add_time
import io.github.jameslunaa.taskora.resources.clear_reminder
import io.github.jameslunaa.taskora.resources.clear_repeat
import io.github.jameslunaa.taskora.resources.notifications_unavailable
import io.github.jameslunaa.taskora.resources.remind_me
import io.github.jameslunaa.taskora.resources.reminder
import io.github.jameslunaa.taskora.resources.reminder_in_past
import io.github.jameslunaa.taskora.resources.repeat
import io.github.jameslunaa.taskora.resources.repeat_never
import io.github.jameslunaa.taskora.resources.close
import io.github.jameslunaa.taskora.resources.created_on
import io.github.jameslunaa.taskora.resources.delete_subtask
import io.github.jameslunaa.taskora.resources.delete_task
import io.github.jameslunaa.taskora.resources.due_date
import io.github.jameslunaa.taskora.resources.list
import io.github.jameslunaa.taskora.resources.notes
import io.github.jameslunaa.taskora.resources.pick_date
import io.github.jameslunaa.taskora.resources.priority
import io.github.jameslunaa.taskora.resources.remove_due_date
import io.github.jameslunaa.taskora.resources.remove_time
import io.github.jameslunaa.taskora.resources.subtasks
import io.github.jameslunaa.taskora.resources.title
import io.github.jameslunaa.taskora.resources.today
import io.github.jameslunaa.taskora.resources.tomorrow
import io.github.jameslunaa.taskora.ui.common.dateText
import io.github.jameslunaa.taskora.ui.common.dueText
import io.github.jameslunaa.taskora.ui.common.label
import io.github.jameslunaa.taskora.ui.common.recurrenceText
import io.github.jameslunaa.taskora.ui.common.timeText
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything the pane can do; the app wires these to [TaskDetailViewModel] and the list's delete. */
class TaskDetailActions(
    val onTitleChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onCompletedChange: (Boolean) -> Unit,
    val onListChange: (Long) -> Unit,
    val onDueDateChange: (LocalDate?) -> Unit,
    val onDueTimeChange: (LocalTime?) -> Unit,
    val onPriorityChange: (Priority) -> Unit,
    val onRecurrenceChange: (Recurrence?) -> Unit,
    val onReminderChange: (Instant?) -> Unit,
    val onAddSubtask: (String) -> Unit,
    val onSubtaskCompletedChange: (Subtask, Boolean) -> Unit,
    val onDeleteSubtask: (Subtask) -> Unit,
    val onDelete: (Task) -> Unit,
    val onClose: () -> Unit,
)

private enum class PickerDialog { DATE, TIME, REPEAT, REMINDER_DATE, REMINDER_TIME }

/** The right-hand pane for the selected task. Edits save as they're made, as on the phone. */
@Composable
fun TaskDetailPane(
    uiState: TaskDetailUiState,
    actions: TaskDetailActions,
    /** False when the desktop can't show notifications, so reminders get a warning. */
    notificationsAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val task = uiState.task
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = actions.onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.close))
            }
        }
        if (task != null) TaskDetailContent(task, uiState.lists, actions, notificationsAvailable)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailContent(
    task: Task,
    lists: List<TaskList>,
    actions: TaskDetailActions,
    notificationsAvailable: Boolean,
) {
    var picker by remember { mutableStateOf<PickerDialog?>(null) }
    // The reminder is picked as a date, then a time.
    var reminderDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = ZoneId.systemDefault()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isCompleted, onCheckedChange = actions.onCompletedChange)
            OutlinedTextField(
                value = task.title,
                onValueChange = actions.onTitleChange,
                label = { Text(stringResource(Res.string.title)) },
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = task.notes,
            onValueChange = actions.onNotesChange,
            label = { Text(stringResource(Res.string.notes)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        ListPicker(task.listId, lists, actions.onListChange)

        Section(stringResource(Res.string.due_date)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val date = task.dueDate
                if (date == null) {
                    val today = LocalDate.now(ZoneId.systemDefault())
                    AssistChip(onClick = { actions.onDueDateChange(today) }, label = { Text(stringResource(Res.string.today)) })
                    AssistChip(
                        onClick = { actions.onDueDateChange(today.plusDays(1)) },
                        label = { Text(stringResource(Res.string.tomorrow)) },
                    )
                    AssistChip(
                        onClick = { picker = PickerDialog.DATE },
                        label = { Text(stringResource(Res.string.pick_date)) },
                        leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                    )
                } else {
                    RemovableChip(
                        label = "${dueText(date, null)} · ${dateText(date)}",
                        icon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
                        removeLabel = stringResource(Res.string.remove_due_date),
                        onClick = { picker = PickerDialog.DATE },
                        onRemove = { actions.onDueDateChange(null) },
                    )
                    val time = task.dueTime
                    if (time == null) {
                        AssistChip(
                            onClick = { picker = PickerDialog.TIME },
                            label = { Text(stringResource(Res.string.add_time)) },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                        )
                    } else {
                        RemovableChip(
                            label = timeText(time),
                            icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                            removeLabel = stringResource(Res.string.remove_time),
                            onClick = { picker = PickerDialog.TIME },
                            onRemove = { actions.onDueTimeChange(null) },
                        )
                    }
                }
            }
        }

        Section(stringResource(Res.string.repeat)) {
            val recurrence = task.recurrence
            if (recurrence == null) {
                AssistChip(
                    onClick = { picker = PickerDialog.REPEAT },
                    label = { Text(stringResource(Res.string.repeat_never)) },
                    leadingIcon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                )
            } else {
                RemovableChip(
                    label = recurrenceText(recurrence),
                    icon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                    removeLabel = stringResource(Res.string.clear_repeat),
                    onClick = { picker = PickerDialog.REPEAT },
                    onRemove = { actions.onRecurrenceChange(null) },
                )
            }
        }

        Section(stringResource(Res.string.reminder)) {
            val reminder = task.reminderAt
            if (reminder == null) {
                AssistChip(
                    onClick = { picker = PickerDialog.REMINDER_DATE },
                    label = { Text(stringResource(Res.string.remind_me)) },
                    leadingIcon = { Icon(Icons.Outlined.NotificationsNone, contentDescription = null) },
                )
            } else {
                val at = reminder.atZone(zone)
                RemovableChip(
                    label = dueText(at.toLocalDate(), at.toLocalTime()),
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    removeLabel = stringResource(Res.string.clear_reminder),
                    onClick = { picker = PickerDialog.REMINDER_DATE },
                    onRemove = { actions.onReminderChange(null) },
                )
                val warning = when {
                    !notificationsAvailable -> Res.string.notifications_unavailable
                    !task.isCompleted && reminder.isBefore(Instant.now()) -> Res.string.reminder_in_past
                    else -> null
                }
                if (warning != null) {
                    Text(
                        stringResource(warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = TaskoraTheme.colors.warning,
                    )
                }
            }
        }

        Section(stringResource(Res.string.priority)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { index, priority ->
                    SegmentedButton(
                        selected = task.priority == priority,
                        onClick = { actions.onPriorityChange(priority) },
                        shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                    ) { Text(stringResource(priority.label)) }
                }
            }
        }

        Section(stringResource(Res.string.subtasks)) {
            Column {
                task.subtasks.forEach { subtask ->
                    SubtaskRow(subtask, actions)
                }
                AddSubtaskField(actions.onAddSubtask)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.created_on, dateText(task.createdAt.atZone(ZoneId.systemDefault()).toLocalDate())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { actions.onDelete(task) }) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(Res.string.delete_task),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    when (picker) {
        PickerDialog.DATE -> DueDatePickerDialog(
            initialDate = task.dueDate,
            onConfirm = actions.onDueDateChange,
            onDismiss = { picker = null },
        )
        PickerDialog.TIME -> DueTimePickerDialog(
            initialTime = task.dueTime,
            onConfirm = actions.onDueTimeChange,
            onDismiss = { picker = null },
        )
        PickerDialog.REPEAT -> RecurrenceDialog(
            initial = task.recurrence,
            dueDay = (task.dueDate ?: LocalDate.now(zone)).dayOfWeek,
            onConfirm = actions.onRecurrenceChange,
            onDismiss = { picker = null },
        )
        PickerDialog.REMINDER_DATE -> DueDatePickerDialog(
            initialDate = task.reminderAt?.atZone(zone)?.toLocalDate() ?: task.dueDate,
            onConfirm = {
                reminderDate = it
                picker = PickerDialog.REMINDER_TIME
            },
            // Confirming also dismisses; only close when the time picker isn't next.
            onDismiss = { if (picker == PickerDialog.REMINDER_DATE) picker = null },
        )
        PickerDialog.REMINDER_TIME -> DueTimePickerDialog(
            initialTime = task.reminderAt?.atZone(zone)?.toLocalTime() ?: task.dueTime,
            onConfirm = { time -> reminderDate?.let { actions.onReminderChange(it.atTime(time).atZone(zone).toInstant()) } },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun RemovableChip(
    label: String,
    icon: @Composable () -> Unit,
    removeLabel: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon,
        trailingIcon = {
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = removeLabel, modifier = Modifier.size(18.dp))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPicker(listId: Long, lists: List<TaskList>, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == listId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(Res.string.list)) },
            leadingIcon = current?.let { { ColorDot(it.color) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    leadingIcon = { ColorDot(list.color) },
                    onClick = {
                        expanded = false
                        if (list.id != listId) onSelect(list.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorDot(color: Int) {
    Box(Modifier.size(12.dp).background(listColor(color), CircleShape))
}

@Composable
private fun SubtaskRow(subtask: Subtask, actions: TaskDetailActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { actions.onSubtaskCompletedChange(subtask, it) },
        )
        Text(
            subtask.title,
            modifier = Modifier.weight(1f),
            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null,
            color = if (subtask.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        IconButton(onClick = { actions.onDeleteSubtask(subtask) }) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.delete_subtask))
        }
    }
}

@Composable
private fun AddSubtaskField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(Res.string.add_subtask)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                    if (text.isNotBlank()) {
                        onAdd(text.trim())
                        text = ""
                    }
                    true
                } else {
                    false
                }
            },
    )
}
