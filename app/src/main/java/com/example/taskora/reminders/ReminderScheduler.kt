package com.example.taskora.reminders

import java.time.Instant

interface ReminderScheduler {
    /** Whether alarms will fire on time; without the exact-alarm permission they may be late. */
    fun canScheduleExact(): Boolean

    /** Schedules (or replaces) the reminder for [taskId]. */
    fun schedule(taskId: Long, at: Instant)

    fun cancel(taskId: Long)
}
