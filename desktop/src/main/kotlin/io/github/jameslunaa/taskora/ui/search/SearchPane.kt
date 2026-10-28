package io.github.jameslunaa.taskora.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.DueFilter
import io.github.jameslunaa.taskora.domain.StatusFilter
import io.github.jameslunaa.taskora.domain.TaskFilter
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.clear_search
import io.github.jameslunaa.taskora.resources.filter_all_lists
import io.github.jameslunaa.taskora.resources.filter_all_tasks
import io.github.jameslunaa.taskora.resources.filter_any_date
import io.github.jameslunaa.taskora.resources.filter_any_priority
import io.github.jameslunaa.taskora.resources.filter_clear
import io.github.jameslunaa.taskora.resources.filter_completed
import io.github.jameslunaa.taskora.resources.filter_n_priorities
import io.github.jameslunaa.taskora.resources.filter_next_7_days
import io.github.jameslunaa.taskora.resources.filter_no_date
import io.github.jameslunaa.taskora.resources.filter_open
import io.github.jameslunaa.taskora.resources.filter_overdue
import io.github.jameslunaa.taskora.resources.search_empty_hint
import io.github.jameslunaa.taskora.resources.search_hint
import io.github.jameslunaa.taskora.resources.search_no_results
import io.github.jameslunaa.taskora.resources.today
import io.github.jameslunaa.taskora.ui.common.headerBandTopAppBarColors
import io.github.jameslunaa.taskora.ui.common.label
import io.github.jameslunaa.taskora.ui.tasks.TaskRow
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onStatusChange: (StatusFilter) -> Unit,
    val onListChange: (Long?) -> Unit,
    val onPriorityToggle: (Priority) -> Unit,
    val onDueChange: (DueFilter) -> Unit,
    val onClearFilters: () -> Unit,
    val onTaskClick: (Task) -> Unit,
    val onCheckedChange: (Task, Boolean) -> Unit,
    val onDelete: (Task) -> Unit,
)

private val StatusFilter.label: StringResource
    get() = when (this) {
        StatusFilter.OPEN -> Res.string.filter_open
        StatusFilter.COMPLETED -> Res.string.filter_completed
        StatusFilter.ALL -> Res.string.filter_all_tasks
    }

private val DueFilter.label: StringResource
    get() = when (this) {
        DueFilter.ANY -> Res.string.filter_any_date
        DueFilter.OVERDUE -> Res.string.filter_overdue
        DueFilter.TODAY -> Res.string.today
        DueFilter.NEXT_7_DAYS -> Res.string.filter_next_7_days
        DueFilter.NO_DATE -> Res.string.filter_no_date
    }

/** Replaces the list pane while searching. [focusRequester] lets Ctrl+F jump back into the field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPane(
    filter: TaskFilter,
    uiState: SearchUiState,
    selectedTaskId: Long?,
    focusRequester: FocusRequester,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val listsById = remember(uiState.lists) { uiState.lists.associateBy { it.id } }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            colors = headerBandTopAppBarColors(),
            navigationIcon = { Icon(Icons.Outlined.Search, contentDescription = null, Modifier.padding(start = 12.dp)) },
            title = {
                val colors = TaskoraTheme.colors
                TextField(
                    value = filter.query,
                    onValueChange = actions.onQueryChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text(stringResource(Res.string.search_hint)) },
                    singleLine = true,
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
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.clear_search))
                    }
                }
            },
        )
        FilterRow(filter, uiState.lists, actions)
        when {
            filter.isEmpty -> CenteredHint(stringResource(Res.string.search_empty_hint))
            uiState.results.isEmpty() -> CenteredHint(stringResource(Res.string.search_no_results))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(uiState.results, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        list = listsById[task.listId],
                        onCheckedChange = { actions.onCheckedChange(task, it) },
                        onClick = { actions.onTaskClick(task) },
                        onDelete = { actions.onDelete(task) },
                        selected = task.id == selectedTaskId,
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun FilterRow(filter: TaskFilter, lists: List<TaskList>, actions: SearchActions) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuChip(
            label = stringResource(filter.status.label),
            selected = filter.status != StatusFilter.OPEN,
        ) { dismiss ->
            StatusFilter.entries.forEach { status ->
                CheckedItem(stringResource(status.label), status == filter.status) {
                    actions.onStatusChange(status)
                    dismiss()
                }
            }
        }
        MenuChip(
            label = lists.find { it.id == filter.listId }?.name ?: stringResource(Res.string.filter_all_lists),
            selected = filter.listId != null,
        ) { dismiss ->
            CheckedItem(stringResource(Res.string.filter_all_lists), filter.listId == null) {
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
                0 -> stringResource(Res.string.filter_any_priority)
                1 -> stringResource(filter.priorities.single().label)
                else -> pluralStringResource(Res.plurals.filter_n_priorities, filter.priorities.size, filter.priorities.size)
            },
            selected = filter.priorities.isNotEmpty(),
        ) { _ ->
            // Multi-select: the menu stays open.
            Priority.entries.reversed().forEach { priority ->
                CheckedItem(stringResource(priority.label), priority in filter.priorities) {
                    actions.onPriorityToggle(priority)
                }
            }
        }
        MenuChip(
            label = stringResource(filter.due.label),
            selected = filter.due != DueFilter.ANY,
        ) { dismiss ->
            DueFilter.entries.forEach { due ->
                CheckedItem(stringResource(due.label), due == filter.due) {
                    actions.onDueChange(due)
                    dismiss()
                }
            }
        }
        if (filter.hasFilters) {
            FilterChip(
                selected = false,
                onClick = actions.onClearFilters,
                label = { Text(stringResource(Res.string.filter_clear)) },
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
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
