package io.github.jameslunaa.taskora.ui.lists

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/** The always-visible list column, the desktop counterpart of the phone's drawer. */
@Composable
fun Sidebar(
    lists: List<TaskList>,
    /** Null while searching. */
    selectedListId: Long?,
    searching: Boolean,
    onSelect: (Long) -> Unit,
    onSearch: () -> Unit,
    onNewList: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** The window's pin menu, next to Settings. */
    windowButton: @Composable () -> Unit = {},
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

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lists, key = { it.id }) { list ->
                NavigationDrawerItem(
                    label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { ColorDot(list.color) },
                    badge = { CountBadge(list.activeTaskCount) },
                    selected = list.id == selectedListId,
                    onClick = { onSelect(list.id) },
                    colors = itemColors,
                )
            }
        }

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
