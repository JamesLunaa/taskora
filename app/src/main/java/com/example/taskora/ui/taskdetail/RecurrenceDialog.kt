package com.example.taskora.ui.taskdetail

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.taskora.R
import com.example.taskora.domain.model.Frequency
import com.example.taskora.domain.model.Recurrence
import com.example.taskora.ui.common.shortName
import java.time.DayOfWeek

private const val MAX_INTERVAL = 99

@PluralsRes
private fun Frequency.unitRes(): Int = when (this) {
    Frequency.DAILY -> R.plurals.unit_days
    Frequency.WEEKLY -> R.plurals.unit_weeks
    Frequency.MONTHLY -> R.plurals.unit_months
    Frequency.YEARLY -> R.plurals.unit_years
}

private fun Frequency.labelRes(): Int = when (this) {
    Frequency.DAILY -> R.string.repeat_daily
    Frequency.WEEKLY -> R.string.repeat_weekly
    Frequency.MONTHLY -> R.string.repeat_monthly
    Frequency.YEARLY -> R.string.repeat_yearly
}

/** [dueDay] preselects the weekday when switching to weekly. */
@Composable
fun RecurrenceDialog(
    initial: Recurrence?,
    dueDay: DayOfWeek,
    onConfirm: (Recurrence?) -> Unit,
    onDismiss: () -> Unit,
) {
    var frequency by rememberSaveable { mutableStateOf(initial?.frequency) }
    var intervalText by rememberSaveable { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by rememberSaveable { mutableStateOf(initial?.daysOfWeek ?: emptySet()) }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 1..MAX_INTERVAL }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = frequency == null,
                        onClick = { frequency = null },
                        label = { Text(stringResource(R.string.repeat_never)) },
                    )
                    Frequency.entries.forEach { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = {
                                frequency = option
                                if (option == Frequency.WEEKLY && days.isEmpty()) days = setOf(dueDay)
                            },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }
                frequency?.let { selected ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.repeat_every))
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { text -> intervalText = text.filter(Char::isDigit).take(2) },
                            modifier = Modifier.width(72.dp),
                            singleLine = true,
                            isError = interval == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text(pluralStringResource(selected.unitRes(), interval ?: 1))
                    }
                    if (selected == Frequency.WEEKLY) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                FilterChip(
                                    selected = day in days,
                                    onClick = { days = if (day in days) days - day else days + day },
                                    label = { Text(day.shortName()) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        frequency?.let {
                            Recurrence(
                                frequency = it,
                                interval = interval ?: 1,
                                daysOfWeek = if (it == Frequency.WEEKLY) days else emptySet(),
                            )
                        },
                    )
                    onDismiss()
                },
                enabled = frequency == null || interval != null,
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
