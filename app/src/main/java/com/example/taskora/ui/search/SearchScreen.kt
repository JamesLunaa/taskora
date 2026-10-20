package com.example.taskora.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskora.R
import com.example.taskora.domain.DueFilter
import com.example.taskora.domain.StatusFilter
import com.example.taskora.domain.TaskFilter
import com.example.taskora.domain.model.Priority
import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskList
import com.example.taskora.ui.common.headerBandTopAppBarColors
import com.example.taskora.ui.common.labelRes
import com.example.taskora.ui.tasks.TaskRow
import com.example.taskora.ui.theme.TaskoraTheme
import java.time.LocalDate

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onTaskClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        filter = filter,
        uiState = uiState,
        onBack = onBack,
        actions = SearchActions(
            onQueryChange = viewModel::setQuery,
            onStatusChange = viewModel::setStatus,
            onListChange = viewModel::setList,
            onPriorityToggle = viewModel::togglePriority,
            onDueChange = viewModel::setDue,
            onClearFilters = viewModel::clearFilters,
            onTaskClick = { onTaskClick(it.id) },
            onCheckedChange = viewModel::setCompleted,
        ),
    )
}

class SearchActions(
    val onQueryChange: (String) -> Unit = {},
    val onStatusChange: (StatusFilter) -> Unit = {},
    val onListChange: (Long?) -> Unit = {},
    val onPriorityToggle: (Priority) -> Unit = {},
    val onDueChange: (DueFilter) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onTaskClick: (Task) -> Unit = {},
    val onCheckedChange: (Task, Boolean) -> Unit = { _, _ -> },
)

@StringRes
private fun StatusFilter.labelRes(): Int = when (this) {
    StatusFilter.OPEN -> R.string.filter_open
    StatusFilter.COMPLETED -> R.string.filter_completed
    StatusFilter.ALL -> R.string.filter_all_tasks
}

@StringRes
private fun DueFilter.labelRes(): Int = when (this) {
    DueFilter.ANY -> R.string.filter_any_date
    DueFilter.OVERDUE -> R.string.filter_overdue
    DueFilter.TODAY -> R.string.today
    DueFilter.NEXT_7_DAYS -> R.string.filter_next_7_days
    DueFilter.NO_DATE -> R.string.filter_no_date
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    filter: TaskFilter,
    uiState: SearchUiState,
    onBack: () -> Unit,
    actions: SearchActions,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listsById = remember(uiState.lists) { uiState.lists.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = headerBandTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    val colors = TaskoraTheme.colors
                    TextField(
                        value = filter.query,
                        onValueChange = actions.onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = colors.onHeaderBand,
                            unfocusedTextColor = colors.onHeaderBand,
                            cursorColor = colors.headerBandAccent,
                            focusedPlaceholderColor = colors.headerBandAccent,
                            unfocusedPlaceholderColor = colors.headerBandAccent,
                        ),
                    )
                },
                actions = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FilterRow(filter, uiState.lists, actions)
            when {
                filter.isEmpty -> CenteredHint(stringResource(R.string.search_empty_hint))
                uiState.results.isEmpty() -> CenteredHint(stringResource(R.string.search_no_results))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(uiState.results, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            list = listsById[task.listId],
                            onCheckedChange = { actions.onCheckedChange(task, it) },
                            onClick = { actions.onTaskClick(task) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun FilterRow(filter: TaskFilter, lists: List<TaskList>, actions: SearchActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuChip(
            label = stringResource(filter.status.labelRes()),
            selected = filter.status != StatusFilter.OPEN,
        ) { dismiss ->
            StatusFilter.entries.forEach { status ->
                CheckedItem(stringResource(status.labelRes()), status == filter.status) {
                    actions.onStatusChange(status)
                    dismiss()
                }
            }
        }
        MenuChip(
            label = lists.find { it.id == filter.listId }?.name ?: stringResource(R.string.filter_all_lists),
            selected = filter.listId != null,
        ) { dismiss ->
            CheckedItem(stringResource(R.string.filter_all_lists), filter.listId == null) {
                actions.onListChange(null)
                dismiss()
            }
            lists.forEach { list ->
                CheckedItem(list.name, list.id == filter.listId) {
                    actions.onListChange(list.id)
                    dismiss()
                }
            }
        }
        MenuChip(
            label = when (filter.priorities.size) {
                0 -> stringResource(R.string.filter_any_priority)
                1 -> stringResource(filter.priorities.single().labelRes)
                else -> pluralStringResource(R.plurals.filter_n_priorities, filter.priorities.size, filter.priorities.size)
            },
            selected = filter.priorities.isNotEmpty(),
        ) { _ ->
            // Multi-select: the menu stays open.
            Priority.entries.reversed().forEach { priority ->
                CheckedItem(stringResource(priority.labelRes), priority in filter.priorities) {
                    actions.onPriorityToggle(priority)
                }
            }
        }
        MenuChip(
            label = stringResource(filter.due.labelRes()),
            selected = filter.due != DueFilter.ANY,
        ) { dismiss ->
            DueFilter.entries.forEach { due ->
                CheckedItem(stringResource(due.labelRes()), due == filter.due) {
                    actions.onDueChange(due)
                    dismiss()
                }
            }
        }
        if (filter.hasFilters) {
            FilterChip(
                selected = false,
                onClick = actions.onClearFilters,
                label = { Text(stringResource(R.string.filter_clear)) },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    selected: Boolean,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

@Composable
private fun CheckedItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null) else Box(Modifier.padding(12.dp))
        },
    )
}

@Composable
private fun CenteredHint(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchPreview() {
    val inbox = TaskList(id = 1, name = "Inbox", color = TaskList.DEFAULT_COLOR)
    TaskoraTheme {
        SearchContent(
            filter = TaskFilter(query = "rent", priorities = setOf(Priority.HIGH)),
            uiState = SearchUiState(
                results = listOf(
                    Task(id = 1, listId = 1, title = "Pay rent", dueDate = LocalDate.now(), priority = Priority.HIGH),
                ),
                lists = listOf(inbox),
            ),
            onBack = {},
            actions = SearchActions(),
        )
    }
}
