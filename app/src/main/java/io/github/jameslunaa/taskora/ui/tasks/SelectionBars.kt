package io.github.jameslunaa.taskora.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.jameslunaa.taskora.R

/** Bulk actions on the picked tasks, shown while selecting. */
class SelectionActions(
    val onClear: () -> Unit,
    val onSelectAll: () -> Unit,
    val onComplete: () -> Unit,
    val onDelete: () -> Unit,
    val onMove: () -> Unit,
    val onSetPriority: () -> Unit,
    val onToday: () -> Unit,
    val onTomorrow: () -> Unit,
    val onPickDate: () -> Unit,
    val onRemoveDate: () -> Unit,
)

// Neutral colors on purpose: the screen's one lamp-purple action is the add button, which is hidden here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(count: Int, actions: SelectionActions) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.n_selected, count, count)) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = actions.onClear) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_selection))
            }
        },
        actions = {
            IconButton(onClick = actions.onSelectAll) {
                Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(R.string.select_all))
            }
            IconButton(onClick = actions.onComplete) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.complete))
            }
            IconButton(onClick = actions.onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_list)) },
                        onClick = {
                            menuOpen = false
                            actions.onMove()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.set_priority)) },
                        onClick = {
                            menuOpen = false
                            actions.onSetPriority()
                        },
                    )
                }
            }
        },
    )
}

/** Due date shortcuts for the picked tasks. */
@Composable
fun SelectionBottomBar(actions: SelectionActions) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        BarButton(R.string.today, actions.onToday)
        BarButton(R.string.tomorrow, actions.onTomorrow)
        BarButton(R.string.pick_date, actions.onPickDate)
        BarButton(R.string.no_date, actions.onRemoveDate)
    }
}

@Composable
private fun RowScope.BarButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(stringResource(label)) }
}
