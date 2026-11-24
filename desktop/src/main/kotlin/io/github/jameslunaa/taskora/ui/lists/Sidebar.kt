package io.github.jameslunaa.taskora.ui.lists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.all_tasks
import io.github.jameslunaa.taskora.resources.app_name
import io.github.jameslunaa.taskora.resources.lists
import io.github.jameslunaa.taskora.resources.new_list
import io.github.jameslunaa.taskora.resources.search
import io.github.jameslunaa.taskora.resources.settings
import io.github.jameslunaa.taskora.resources.taskora_logo
import io.github.jameslunaa.taskora.ui.theme.listColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The always-visible list column, the desktop counterpart of the phone's drawer. */
@Composable
fun Sidebar(
    lists: List<TaskList>,
    /** Null while searching. */
    selectedListId: Long?,
    searching: Boolean,
    onSelect: (Long) -> Unit,
    /** Long-press and drag a list to move it; called on release with every list's id in the new order. */
    onReorder: (List<Long>) -> Unit,
    onSearch: () -> Unit,
    onNewList: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** The window's pin menu, next to Settings. */
    windowButton: @Composable () -> Unit = {},
    /** Below the lists: paired devices (see ui/devices). */
    devicesSection: @Composable () -> Unit = {},
) {
    val itemColors = NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painterResource(Res.drawable.taskora_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
            )
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleLarge)
        }

        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.all_tasks)) },
            icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
            badge = { CountBadge(lists.sumOf { it.activeTaskCount }) },
            selected = selectedListId == TaskList.ALL_TASKS_ID,
            onClick = { onSelect(TaskList.ALL_TASKS_ID) },
            colors = itemColors,
            modifier = Modifier.padding(top = 16.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.search)) },
            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            badge = { Text("Ctrl+F", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            selected = searching,
            onClick = onSearch,
            colors = itemColors,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 12.dp))
        Text(
            stringResource(Res.string.lists),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Drag order is kept locally while a drag is in progress and saved on release.
        val lazyListState = rememberLazyListState()
        var dragLists by remember(lists) { mutableStateOf(lists) }
        var moved by remember { mutableStateOf(false) }
        // The item also sees the release that ends a long press as a click; that click must not open the list.
        var longPressEndedAt by remember { mutableLongStateOf(0L) }
        var longPressing by remember { mutableStateOf(false) }
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = dragLists.indexOfFirst { it.id == from.key }
            val toIndex = dragLists.indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                moved = true
                dragLists = dragLists.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
        LazyColumn(state = lazyListState, modifier = Modifier.weight(1f)) {
            items(dragLists, key = { it.id }) { list ->
                ReorderableItem(reorderState, key = list.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag elevation")
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = elevation) {
                        NavigationDrawerItem(
                            label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { ColorDot(list.color) },
                            badge = { CountBadge(list.activeTaskCount) },
                            selected = list.id == selectedListId,
                            onClick = {
                                if (!longPressing && System.currentTimeMillis() - longPressEndedAt >= 500) onSelect(list.id)
                            },
                            colors = itemColors,
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    moved = false
                                    longPressing = true
                                },
                                onDragStopped = {
                                    longPressing = false
                                    longPressEndedAt = System.currentTimeMillis()
                                    if (moved) onReorder(dragLists.map { it.id })
                                },
                            ),
                        )
                    }
                }
            }
        }

        devicesSection()

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onNewList, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(Res.string.new_list), modifier = Modifier.padding(start = 8.dp))
            }
            windowButton()
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    }
}

@Composable
private fun ColorDot(color: Int) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(12.dp).background(listColor(color), CircleShape))
    }
}

@Composable
private fun CountBadge(count: Int) {
    if (count > 0) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
