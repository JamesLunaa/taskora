package io.github.jameslunaa.taskora.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.sort_ascending
import io.github.jameslunaa.taskora.resources.sort_by
import io.github.jameslunaa.taskora.resources.sort_created
import io.github.jameslunaa.taskora.resources.sort_descending
import io.github.jameslunaa.taskora.resources.sort_due_date
import io.github.jameslunaa.taskora.resources.sort_manual
import io.github.jameslunaa.taskora.resources.sort_priority
import io.github.jameslunaa.taskora.resources.sort_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val TaskSortMode.label: StringResource
    get() = when (this) {
        TaskSortMode.MANUAL -> Res.string.sort_manual
        TaskSortMode.CREATED -> Res.string.sort_created
        TaskSortMode.DUE_DATE -> Res.string.sort_due_date
        TaskSortMode.PRIORITY -> Res.string.sort_priority
        TaskSortMode.TITLE -> Res.string.sort_title
    }

/** The phone's sort dialog as a menu: pick a mode, then a direction (My order has none). */
@Composable
fun SortMenu(mode: TaskSortMode, descending: Boolean, onSelect: (TaskSortMode, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(Res.string.sort_by))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskSortMode.entries.forEach { option ->
                CheckItem(stringResource(option.label), option == mode) {
                    // Changing mode keeps the direction, as the phone's dialog does.
                    onSelect(option, descending)
                    expanded = false
                }
            }
            if (mode != TaskSortMode.MANUAL) {
                HorizontalDivider()
                CheckItem(stringResource(Res.string.sort_ascending), !descending) {
                    onSelect(mode, false)
                    expanded = false
                }
                CheckItem(stringResource(Res.string.sort_descending), descending) {
                    onSelect(mode, true)
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null) else Box(Modifier.size(24.dp))
        },
    )
}
