package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import io.github.jameslunaa.taskora.ui.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.cancel_selection
import io.github.jameslunaa.taskora.resources.delete
import io.github.jameslunaa.taskora.resources.mark_complete
import io.github.jameslunaa.taskora.resources.more_options
import io.github.jameslunaa.taskora.resources.n_selected
import io.github.jameslunaa.taskora.resources.no_date
import io.github.jameslunaa.taskora.resources.pick_date
import io.github.jameslunaa.taskora.resources.select_all
import io.github.jameslunaa.taskora.resources.today
import io.github.jameslunaa.taskora.resources.tomorrow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate

/**
 * Replaces the header band while tasks are selected. Neutral colors on purpose: the screen's one
 * lamp-purple action is the Add button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    selected: List<Task>,
    lists: List<TaskList>,
    actions: TaskBulkActions,
    onPickDate: (Collection<Long>) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val ids = selected.map { it.id }
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(Res.plurals.n_selected, selected.size, selected.size)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel_selection))
            }
        },
        actions = {
            TextButton(onClick = { actions.onSetDueDate(ids, LocalDate.now()) }) { Text(stringResource(Res.string.today)) }
            TextButton(onClick = { actions.onSetDueDate(ids, LocalDate.now().plusDays(1)) }) {
                Text(stringResource(Res.string.tomorrow))
            }
            TextButton(onClick = { onPickDate(ids) }) { Text(stringResource(Res.string.pick_date)) }
            TextButton(onClick = { actions.onSetDueDate(ids, null) }) { Text(stringResource(Res.string.no_date)) }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(Res.string.select_all))
            }
            IconButton(onClick = {
                actions.onSetCompleted(ids, true)
                onClear()
            }) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(Res.string.mark_complete))
            }
            IconButton(onClick = {
                actions.onDelete(selected)
                onClear()
            }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.delete))
            }
            // Priority and move: the same menu as a right-click, for the whole selection.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.more_options))
                }
                if (menuOpen) TaskContextMenu(selected, lists, actions, onPickDate, onDismiss = { menuOpen = false })
            }
        },
    )
}
