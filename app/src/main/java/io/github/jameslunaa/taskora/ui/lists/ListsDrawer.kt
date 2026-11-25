package io.github.jameslunaa.taskora.ui.lists

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.ui.theme.listColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ListsDrawer(
    lists: List<TaskList>,
    selectedListId: Long?,
    onListClick: (TaskList) -> Unit,
    /** Long-press and drag a list to move it; called on release with every list's id in the new order. */
    onReorder: (List<Long>) -> Unit,
    onAllTasks: () -> Unit,
    onNewList: () -> Unit,
    onSettings: () -> Unit,
) {
    // Drag order is kept locally while a drag is in progress and saved when the finger lifts.
    val lazyListState = rememberLazyListState()
    var dragLists by remember(lists) { mutableStateOf(lists) }
    var moved by remember { mutableStateOf(false) }
    // The item also sees the release that ends a long press as a tap; that tap must not open the list.
    var longPressEndedAt by remember { mutableLongStateOf(0L) }
    var longPressing by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = dragLists.indexOfFirst { it.id == from.key }
        val toIndex = dragLists.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            moved = true
            dragLists = dragLists.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    ModalDrawerSheet {
        LazyColumn(state = lazyListState) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painterResource(R.drawable.taskora_logo),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.all_tasks)) },
                    selected = selectedListId == TaskList.ALL_TASKS_ID,
                    onClick = onAllTasks,
                    icon = { Icon(Icons.Outlined.AllInbox, contentDescription = null) },
                    badge = {
                        val total = lists.sumOf { it.activeTaskCount }
                        if (total > 0) Text(total.toString())
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            items(dragLists, key = { it.id }) { list ->
                ReorderableItem(
                    reorderState,
                    key = list.id,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                ) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag elevation")
                    Surface(
                        shape = CircleShape,
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        shadowElevation = elevation,
                    ) {
                        NavigationDrawerItem(
                            label = { Text(list.name) },
                            selected = list.id == selectedListId,
                            onClick = {
                                if (!longPressing && SystemClock.uptimeMillis() - longPressEndedAt >= 500) onListClick(list)
                            },
                            icon = {
                                Box(
                                    Modifier
                                        .padding(4.dp)
                                        .size(16.dp)
                                        .background(listColor(list.color), CircleShape),
                                )
                            },
                            badge = {
                                if (list.activeTaskCount > 0) Text(list.activeTaskCount.toString())
                            },
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    moved = false
                                    longPressing = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    longPressing = false
                                    longPressEndedAt = SystemClock.uptimeMillis()
                                    if (moved) onReorder(dragLists.map { it.id })
                                },
                            ),
                        )
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.new_list)) },
                    selected = false,
                    onClick = onNewList,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings)) },
                    selected = false,
                    onClick = onSettings,
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
