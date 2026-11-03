package io.github.jameslunaa.taskora.ui.devices

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.lan.DeviceState
import io.github.jameslunaa.taskora.lan.LanHost
import io.github.jameslunaa.taskora.lan.PairedDevice
import io.github.jameslunaa.taskora.lan.WireTask
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.all_tasks
import io.github.jameslunaa.taskora.resources.cancel
import io.github.jameslunaa.taskora.resources.copy_to_this_computer
import io.github.jameslunaa.taskora.resources.device_no_tasks
import io.github.jameslunaa.taskora.resources.device_not_paired_hint
import io.github.jameslunaa.taskora.resources.device_unavailable_hint
import io.github.jameslunaa.taskora.resources.retry
import io.github.jameslunaa.taskora.resources.unpair
import io.github.jameslunaa.taskora.resources.unpair_body
import io.github.jameslunaa.taskora.resources.unpair_title
import io.github.jameslunaa.taskora.ui.common.color
import io.github.jameslunaa.taskora.ui.common.dueText
import io.github.jameslunaa.taskora.ui.common.headerBandTopAppBarColors
import io.github.jameslunaa.taskora.ui.common.label
import io.github.jameslunaa.taskora.ui.icons.filled.Flag
import io.github.jameslunaa.taskora.ui.icons.outlined.ContentCopy
import io.github.jameslunaa.taskora.ui.icons.outlined.LinkOff
import io.github.jameslunaa.taskora.ui.icons.outlined.WifiOff
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.stringResource

class DeviceBrowserActions(
    val onSelectList: (Long) -> Unit,
    val onCheckedChange: (WireTask, Boolean) -> Unit,
    val onCopyHere: (WireTask) -> Unit,
    val onRetry: () -> Unit,
    val onUnpair: () -> Unit,
)

/** A paired device's lists and open tasks, live while it's online. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceBrowserPane(device: PairedDevice, state: DeviceBrowserState, actions: DeviceBrowserActions) {
    var confirmUnpair by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(device.name)
                    Text(state.connection.label(), style = MaterialTheme.typography.labelMedium)
                }
            },
            navigationIcon = { Icon(deviceIcon(device.os), contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp)) },
            actions = {
                IconButton(onClick = { confirmUnpair = true }) {
                    Icon(Icons.Outlined.LinkOff, contentDescription = stringResource(Res.string.unpair))
                }
            },
            colors = headerBandTopAppBarColors(),
        )

        if (state.connection == DeviceState.UNAVAILABLE || state.connection == DeviceState.NOT_PAIRED) {
            StatusBanner(device, state.connection, actions.onRetry)
        }

        if (state.lists.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.listId == LanHost.ALL_LISTS,
                    onClick = { actions.onSelectList(LanHost.ALL_LISTS) },
                    label = { Text(stringResource(Res.string.all_tasks)) },
                )
                state.lists.forEach { list ->
                    FilterChip(
                        selected = state.listId == list.id,
                        onClick = { actions.onSelectList(list.id) },
                        label = { Text("${list.name}  ${list.activeCount}") },
                        leadingIcon = {
                            Surface(color = listColor(list.color), shape = androidx.compose.foundation.shape.CircleShape) {
                                Box(Modifier.padding(5.dp))
                            }
                        },
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.isLoaded && state.connection != DeviceState.NOT_PAIRED ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.isLoaded && state.tasks.isEmpty() ->
                    Text(
                        stringResource(Res.string.device_no_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.tasks, key = { it.id ?: it.hashCode().toLong() }) { task ->
                        RemoteTaskRow(task, enabled = state.connection == DeviceState.ONLINE, actions)
                    }
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(stringResource(Res.string.unpair_title, device.name)) },
            text = { Text(stringResource(Res.string.unpair_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    actions.onUnpair()
                }) { Text(stringResource(Res.string.unpair)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}

@Composable
private fun StatusBanner(device: PairedDevice, connection: DeviceState, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    if (connection == DeviceState.NOT_PAIRED) Res.string.device_not_paired_hint else Res.string.device_unavailable_hint,
                    device.name,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (connection == DeviceState.UNAVAILABLE) TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteTaskRow(task: WireTask, enabled: Boolean, actions: DeviceBrowserActions) {
    val priority = Priority.valueOf(task.priority.name)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Checkbox(checked = task.completed, onCheckedChange = { actions.onCheckedChange(task, it) }, enabled = enabled)
        },
        headlineContent = {
            Text(
                task.title,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = task.dueDate?.let { date -> { Text(dueText(date, task.dueTime)) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (priority != Priority.NONE && !task.completed) {
                    Icon(Icons.Filled.Flag, contentDescription = stringResource(priority.label), tint = priority.color())
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(Res.string.copy_to_this_computer)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { actions.onCopyHere(task) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(Res.string.copy_to_this_computer))
                    }
                }
            }
        },
    )
}
