package io.github.jameslunaa.taskora.ui.lists

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.TaskSortMode

@get:StringRes
val TaskSortMode.labelRes: Int
    get() = when (this) {
        TaskSortMode.CREATED -> R.string.sort_created
        TaskSortMode.DUE_DATE -> R.string.sort_due_date
        TaskSortMode.PRIORITY -> R.string.sort_priority
        TaskSortMode.TITLE -> R.string.sort_title
    }

@Composable
fun SortDialog(
    current: TaskSortMode,
    onSelect: (TaskSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_by)) },
        text = {
            Column(Modifier.selectableGroup()) {
                TaskSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == current,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(mode)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Text(stringResource(mode.labelRes), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
