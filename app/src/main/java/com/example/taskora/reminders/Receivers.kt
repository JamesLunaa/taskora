package com.example.taskora.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper
import com.example.taskora.data.repository.TaskRepository
import com.example.taskora.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Hilt injects receivers in a generated onReceive that must be reached through super, but
 * BroadcastReceiver.onReceive is abstract, so Kotlin can't call it. This gives it a body.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    @CallSuper
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Runs [block] off the main thread while keeping the receiver alive until it finishes. */
private fun BroadcastReceiver.launchAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

@AndroidEntryPoint
class ReminderReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REMIND) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val scheduledAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SCHEDULED_AT, -1))
        launchAsync(scope) {
            val task = taskRepository.getTask(taskId)
            if (task.shouldNotify(scheduledAt)) notifier.show(task!!)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.taskora.action.REMIND"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }
}

@AndroidEntryPoint
class ReminderActionReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var notifier: ReminderNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val taskId = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1)
        if (taskId == -1L) return
        notifier.cancel(taskId)
        launchAsync(scope) {
            when (intent.action) {
                ACTION_COMPLETE -> taskRepository.setTaskCompleted(taskId, true)
                ACTION_SNOOZE -> taskRepository.snoozeReminder(taskId, SNOOZE)
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.example.taskora.action.COMPLETE"
        const val ACTION_SNOOZE = "com.example.taskora.action.SNOOZE"
        val SNOOZE: Duration = Duration.ofMinutes(10)
    }
}

/** Alarms don't survive reboots or app updates, and become exact once that permission is granted. */
@AndroidEntryPoint
class RescheduleRemindersReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var taskRepository: TaskRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in RESCHEDULE_ACTIONS) return
        launchAsync(scope) { taskRepository.rescheduleReminders() }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+).
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
