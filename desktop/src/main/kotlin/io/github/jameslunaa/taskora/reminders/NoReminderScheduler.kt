package io.github.jameslunaa.taskora.reminders

import java.time.Instant

/** Placeholder until desktop notifications exist; reminder times are stored but nothing fires. */
object NoReminderScheduler : ReminderScheduler {
    override fun canScheduleExact(): Boolean = true

    override fun schedule(taskId: Long, at: Instant) = Unit

    override fun cancel(taskId: Long) = Unit
}
