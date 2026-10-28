package io.github.jameslunaa.taskora.ui.taskdetail

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.cancel
import io.github.jameslunaa.taskora.resources.ok
import io.github.jameslunaa.taskora.ui.common.uses24HourClock
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// The Material date picker works in UTC midnight millis.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initialDate ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueTimePickerDialog(
    initialTime: LocalTime?,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val time = initialTime ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = uses24HourClock(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(Res.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}
