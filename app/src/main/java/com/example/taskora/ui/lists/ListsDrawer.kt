package com.example.taskora.ui.lists

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.taskora.R
import com.example.taskora.domain.model.TaskList
import com.example.taskora.ui.theme.listColor

@Composable
fun ListsDrawer(
    lists: List<TaskList>,
    selectedListId: Long?,
    onListClick: (TaskList) -> Unit,
    onNewList: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        LazyColumn {
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
            items(lists, key = { it.id }) { list ->
                NavigationDrawerItem(
                    label = { Text(list.name) },
                    selected = list.id == selectedListId,
                    onClick = { onListClick(list) },
                    icon = {
                        if (list.isInbox) {
                            Icon(Icons.Outlined.Inbox, contentDescription = null, tint = listColor(list.color))
                        } else {
                            Box(
                                Modifier
                                    .padding(4.dp)
                                    .size(16.dp)
                                    .background(listColor(list.color), CircleShape),
                            )
                        }
                    },
                    badge = {
                        if (list.activeTaskCount > 0) Text(list.activeTaskCount.toString())
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
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
