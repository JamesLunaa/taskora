package io.github.jameslunaa.taskora.reminders

import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.complete
import io.github.jameslunaa.taskora.resources.open
import io.github.jameslunaa.taskora.resources.snooze
import io.github.jameslunaa.taskora.resources.today
import io.github.jameslunaa.taskora.resources.tomorrow
import io.github.jameslunaa.taskora.resources.yesterday
import io.github.jameslunaa.taskora.ui.common.RelativeDayLabels
import io.github.jameslunaa.taskora.ui.common.formatDue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** The localized words a notification needs. */
data class ReminderStrings(
    val days: RelativeDayLabels,
    val complete: String,
    val snooze: String,
    val open: String,
)

/** Turns due reminders into notifications and carries out the action picked on them. */
class ReminderNotifications(
    private val taskRepository: TaskRepository,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val strings: suspend () -> ReminderStrings = ::loadReminderStrings,
) {
    private val _openRequests = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** Task ids the user asked to open from a notification; the window shows itself and the task. */
    val openRequests: SharedFlow<Long> = _openRequests.asSharedFlow()

    val isAvailable: Boolean get() = notifier.isAvailable

    suspend fun fire(taskId: Long, scheduledAt: Instant) {
        val task = taskRepository.getTask(taskId)
        // Stale alarms (task gone, completed, or reminder moved) are dropped, as on the phone.
        if (!task.shouldNotify(scheduledAt)) return
        checkNotNull(task)
        // Waiting for the action mustn't hold up the scheduler.
        scope.launch {
            when (notifier.show(content(task))) {
                NotificationAction.COMPLETE -> taskRepository.setTaskCompleted(taskId, true)
                NotificationAction.SNOOZE -> taskRepository.snoozeReminder(taskId, SNOOZE)
                NotificationAction.OPEN -> _openRequests.emit(taskId)
                null -> Unit
            }
        }
    }

    private suspend fun content(task: Task): NotificationContent {
        val strings = strings()
        val due = task.dueDate?.let { date ->
            formatDue(date, task.dueTime, LocalDate.now(clock), strings.days, Locale.getDefault())
        }
        val notes = task.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        return NotificationContent(
            title = task.title,
            body = listOfNotNull(due, notes).joinToString("\n"),
            completeLabel = strings.complete,
            snoozeLabel = strings.snooze,
            openLabel = strings.open,
        )
    }

    companion object {
        val SNOOZE: Duration = Duration.ofMinutes(10)
    }
}

private suspend fun loadReminderStrings() = ReminderStrings(
    days = RelativeDayLabels(getString(Res.string.yesterday), getString(Res.string.today), getString(Res.string.tomorrow)),
    complete = getString(Res.string.complete),
    snooze = getString(Res.string.snooze),
    open = getString(Res.string.open),
)
