package io.github.jameslunaa.taskora.ui.taskdetail

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.cancel
import io.github.jameslunaa.taskora.resources.ok
import io.github.jameslunaa.taskora.resources.repeat
import io.github.jameslunaa.taskora.resources.repeat_daily
import io.github.jameslunaa.taskora.resources.repeat_every
import io.github.jameslunaa.taskora.resources.repeat_monthly
import io.github.jameslunaa.taskora.resources.repeat_never
import io.github.jameslunaa.taskora.resources.repeat_weekly
import io.github.jameslunaa.taskora.resources.repeat_yearly
import io.github.jameslunaa.taskora.resources.unit_days
import io.github.jameslunaa.taskora.resources.unit_months
import io.github.jameslunaa.taskora.resources.unit_weeks
import io.github.jameslunaa.taskora.resources.unit_years
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.ui.common.shortName
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek

private const val MAX_INTERVAL = 99

private fun Frequency.unitRes(): PluralStringResource = when (this) {
    Frequency.DAILY -> Res.plurals.unit_days
    Frequency.WEEKLY -> Res.plurals.unit_weeks
    Frequency.MONTHLY -> Res.plurals.unit_months
    Frequency.YEARLY -> Res.plurals.unit_years
}

private fun Frequency.labelRes(): StringResource = when (this) {
    Frequency.DAILY -> Res.string.repeat_daily
    Frequency.WEEKLY -> Res.string.repeat_weekly
    Frequency.MONTHLY -> Res.string.repeat_monthly
    Frequency.YEARLY -> Res.string.repeat_yearly
}

/** [dueDay] preselects the weekday when switching to weekly. */
@Composable
fun RecurrenceDialog(
    initial: Recurrence?,
    dueDay: DayOfWeek,
    onConfirm: (Recurrence?) -> Unit,
    onDismiss: () -> Unit,
) {
    var frequency by remember { mutableStateOf(initial?.frequency) }
    var intervalText by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by remember { mutableStateOf(initial?.daysOfWeek ?: emptySet()) }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 1..MAX_INTERVAL }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.repeat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = frequency == null,
                        onClick = { frequency = null },
                        label = { Text(stringResource(Res.string.repeat_never)) },
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
                        Text(stringResource(Res.string.repeat_every))
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
            ) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
