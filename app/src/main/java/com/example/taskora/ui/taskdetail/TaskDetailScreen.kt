package com.example.taskora.ui.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskora.R
import com.example.taskora.domain.model.Priority
import com.example.taskora.domain.model.Recurrence
import com.example.taskora.domain.model.Subtask
import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskList
import com.example.taskora.ui.common.color
import com.example.taskora.ui.common.dueText
import com.example.taskora.ui.common.labelRes
import com.example.taskora.ui.common.recurrenceText
import com.example.taskora.ui.common.timeText
import com.example.taskora.ui.theme.TaskoraTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isClosed) {
        if (uiState.isClosed) onBack()
    }
    TaskDetailContent(
        task = uiState.task,
        lists = uiState.lists,
        onBack = onBack,
        actions = TaskDetailActions(
            onTitleChange = viewModel::setTitle,
            onNotesChange = viewModel::setNotes,
            onCompletedChange = viewModel::setCompleted,
            onDueDateChange = viewModel::setDueDate,
            onDueTimeChange = viewModel::setDueTime,
            onReminderChange = viewModel::setReminder,
            onRecurrenceChange = viewModel::setRecurrence,
            onPriorityChange = viewModel::setPriority,
            onListChange = viewModel::moveToList,
            onAddSubtask = viewModel::addSubtask,
            onSubtaskCompletedChange = viewModel::setSubtaskCompleted,
            onDeleteSubtask = viewModel::deleteSubtask,
            onDeleteTask = viewModel::deleteTask,
        ),
    )
}

class TaskDetailActions(
    val onTitleChange: (String) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onCompletedChange: (Boolean) -> Unit = {},
    val onDueDateChange: (LocalDate?) -> Unit = {},
    val onDueTimeChange: (LocalTime?) -> Unit = {},
    val onReminderChange: (Instant?) -> Unit = {},
    val onRecurrenceChange: (Recurrence?) -> Unit = {},
    val onPriorityChange: (Priority) -> Unit = {},
    val onListChange: (Long) -> Unit = {},
    val onAddSubtask: (String) -> Unit = {},
    val onSubtaskCompletedChange: (Subtask, Boolean) -> Unit = { _, _ -> },
    val onDeleteSubtask: (Subtask) -> Unit = {},
    val onDeleteTask: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailContent(
    task: Task?,
    lists: List<TaskList>,
    onBack: () -> Unit,
    actions: TaskDetailActions,
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_task))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (task == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            TitleRow(task, actions)
            OutlinedTextField(
                value = task.notes,
                onValueChange = actions.onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.notes)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            ListSection(task.listId, lists, actions.onListChange)
            DueSection(task, actions)
            RecurrenceSection(task, actions.onRecurrenceChange)
            ReminderSection(task.reminderAt, task.dueDate, task.dueTime, actions.onReminderChange)
            PrioritySection(task.priority, actions.onPriorityChange)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SubtaskSection(task.subtasks, actions)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_task)) },
            text = { Text(stringResource(R.string.delete_task_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    actions.onDeleteTask()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun TitleRow(task: Task, actions: TaskDetailActions) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = actions.onCompletedChange)
        TextField(
            value = task.title,
            onValueChange = actions.onTitleChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            ),
            placeholder = { Text(stringResource(R.string.task_title_hint), style = MaterialTheme.typography.titleLarge) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ListSection(listId: Long, lists: List<TaskList>, onListChange: (Long) -> Unit) {
    val current = lists.find { it.id == listId } ?: return
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier.clickable { expanded = true },
            leadingContent = {
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(16.dp)
                        .background(Color(current.color), CircleShape),
                )
            },
            overlineContent = { Text(stringResource(R.string.list)) },
            headlineContent = { Text(current.name) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        expanded = false
                        onListChange(list.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun DueSection(task: Task, actions: TaskDetailActions) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val date = task.dueDate

    ListItem(
        modifier = Modifier.clickable { showDatePicker = true },
        leadingContent = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
        headlineContent = {
            Text(if (date != null) dueText(date, null) else stringResource(R.string.add_due_date))
        },
        trailingContent = if (date != null) {
            {
                IconButton(onClick = { actions.onDueDateChange(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_due_date))
                }
            }
        } else {
            null
        },
    )
    if (date != null) {
        val time = task.dueTime
        ListItem(
            modifier = Modifier.clickable { showTimePicker = true },
            leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            headlineContent = {
                Text(
                    time?.let { timeText(it) }
                        ?: stringResource(R.string.add_time),
                )
            },
            trailingContent = if (time != null) {
                {
                    IconButton(onClick = { actions.onDueTimeChange(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_time))
                    }
                }
            } else {
                null
            },
        )
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initialDate = date,
            onConfirm = actions.onDueDateChange,
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        DueTimePickerDialog(
            initialTime = task.dueTime,
            onConfirm = actions.onDueTimeChange,
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun RecurrenceSection(task: Task, onRecurrenceChange: (Recurrence?) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val recurrence = task.recurrence
    ListItem(
        modifier = Modifier.clickable { showDialog = true },
        leadingContent = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
        headlineContent = {
            Text(if (recurrence != null) recurrenceText(recurrence) else stringResource(R.string.repeat))
        },
        trailingContent = if (recurrence != null) {
            {
                IconButton(onClick = { onRecurrenceChange(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_repeat))
                }
            }
        } else {
            null
        },
    )
    if (showDialog) {
        RecurrenceDialog(
            initial = recurrence,
            dueDay = (task.dueDate ?: LocalDate.now()).dayOfWeek,
            onConfirm = onRecurrenceChange,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun PrioritySection(priority: Priority, onPriorityChange: (Priority) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.priority), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { option ->
                FilterChip(
                    selected = option == priority,
                    onClick = { onPriorityChange(option) },
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
}

@Composable
private fun SubtaskSection(subtasks: List<Subtask>, actions: TaskDetailActions) {
    var newSubtask by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (newSubtask.isNotBlank()) {
            actions.onAddSubtask(newSubtask)
            newSubtask = ""
        }
    }

    Text(
        stringResource(R.string.subtasks),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    subtasks.forEach { subtask ->
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete_subtask))
            }
        }
    }
    OutlinedTextField(
        value = newSubtask,
        onValueChange = { newSubtask = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text(stringResource(R.string.add_subtask_hint)) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = submit, enabled = newSubtask.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_subtask_hint))
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
}

@Preview(showBackground = true)
@Composable
private fun TaskDetailPreview() {
    TaskoraTheme {
        TaskDetailContent(
            task = Task(
                id = 1,
                listId = 1,
                title = "Plan the trip",
                notes = "Book flights before Friday",
                dueDate = LocalDate.now().plusDays(1),
                priority = Priority.MEDIUM,
                subtasks = listOf(
                    Subtask(id = 1, taskId = 1, title = "Flights", isCompleted = true),
                    Subtask(id = 2, taskId = 1, title = "Hotel"),
                ),
            ),
            lists = listOf(TaskList(id = 1, name = "Inbox", color = TaskList.DEFAULT_COLOR)),
            onBack = {},
            actions = TaskDetailActions(),
        )
    }
}
