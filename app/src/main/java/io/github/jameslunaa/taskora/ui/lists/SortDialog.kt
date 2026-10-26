package io.github.jameslunaa.taskora.ui.lists

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        TaskSortMode.MANUAL -> R.string.sort_manual
        TaskSortMode.CREATED -> R.string.sort_created
        TaskSortMode.DUE_DATE -> R.string.sort_due_date
        TaskSortMode.PRIORITY -> R.string.sort_priority
        TaskSortMode.TITLE -> R.string.sort_title
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDialog(
    currentMode: TaskSortMode,
    currentDescending: Boolean,
    onSelect: (TaskSortMode, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(currentMode) }
    var descending by remember { mutableStateOf(currentDescending) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_by)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    TaskSortMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == mode,
                                    role = Role.RadioButton,
                                    onClick = { mode = option },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == mode, onClick = null)
                            Text(stringResource(option.labelRes), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
                // My order is whatever the user dragged, so it has no direction.
                if (mode != TaskSortMode.MANUAL) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        SegmentedButton(
                            selected = !descending,
                            onClick = { descending = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.sort_ascending)) }
                        SegmentedButton(
                            selected = descending,
                            onClick = { descending = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.sort_descending)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(mode, descending)
                onDismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
