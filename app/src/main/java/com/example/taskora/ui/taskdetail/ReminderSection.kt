package com.example.taskora.ui.taskdetail

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.taskora.R
import com.example.taskora.reminders.BackgroundRestrictions
import com.example.taskora.ui.common.dueText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private enum class ReminderStep { NONE, DATE, TIME }

/**
 * Picks a reminder as date then time. Asks for the notification permission when a reminder is
 * set, and warns (most serious first) when notifications are off, when the phone may delete
 * alarms of background apps, or when alarms can't be exact.
 */
@Composable
fun ReminderSection(
    reminderAt: Instant?,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onReminderChange: (Instant?) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val current = reminderAt?.atZone(zone)?.toLocalDateTime()
    var step by rememberSaveable { mutableStateOf(ReminderStep.NONE) }
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    // Re-checked on resume, since both can be changed in system settings.
    var notificationsEnabled by remember { mutableStateOf(true) }
    var exactAlarmsAllowed by remember { mutableStateOf(true) }
    var backgroundRestricted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactAlarmsAllowed = canScheduleExactAlarms(context)
        backgroundRestricted = BackgroundRestrictions.mayBlockReminders(context)
        onPauseOrDispose {}
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    ListItem(
        modifier = Modifier.clickable { step = ReminderStep.DATE },
        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
        headlineContent = {
            Text(
                if (current != null) {
                    dueText(current.toLocalDate(), current.toLocalTime())
                } else {
                    stringResource(R.string.remind_me)
                },
            )
        },
        trailingContent = if (reminderAt != null) {
            {
                IconButton(onClick = { onReminderChange(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_reminder))
                }
            }
        } else {
            null
        },
    )

    if (reminderAt != null) {
        when {
            !notificationsEnabled -> ReminderWarning(
                text = stringResource(R.string.notifications_off),
                action = stringResource(R.string.open_settings),
                isError = true,
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )
            // Worse than late alarms: these phones delete them outright.
            backgroundRestricted -> ReminderWarning(
                text = stringResource(R.string.reminders_blocked_in_background),
                action = stringResource(R.string.fix),
                isError = true,
                onAction = { context.startActivity(BackgroundRestrictions.settingsIntent(context)) },
            )
            !exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> ReminderWarning(
                text = stringResource(R.string.reminders_may_be_late),
                action = stringResource(R.string.allow),
                isError = false,
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                    )
                },
            )
        }
    }

    when (step) {
        ReminderStep.NONE -> Unit
        ReminderStep.DATE -> DueDatePickerDialog(
            initialDate = current?.toLocalDate() ?: dueDate,
            onConfirm = {
                pickedDate = it
                step = ReminderStep.TIME
            },
            onDismiss = { if (step == ReminderStep.DATE) step = ReminderStep.NONE },
        )
        ReminderStep.TIME -> DueTimePickerDialog(
            initialTime = current?.toLocalTime() ?: dueTime ?: nextFullHour(),
            onConfirm = { time ->
                val date = pickedDate ?: LocalDate.now()
                onReminderChange(LocalDateTime.of(date, time).atZone(zone).toInstant())
                if (needsNotificationPermission(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { step = ReminderStep.NONE },
        )
    }
}

@Composable
private fun ReminderWarning(text: String, action: String, isError: Boolean, onAction: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 56.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun nextFullHour(): LocalTime = LocalTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
