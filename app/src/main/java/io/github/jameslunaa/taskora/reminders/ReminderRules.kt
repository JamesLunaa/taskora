package io.github.jameslunaa.taskora.reminders

import io.github.jameslunaa.taskora.domain.model.Task
import java.time.Instant

/** The time to schedule [task]'s reminder at, or null if it shouldn't have one. */
fun Task.pendingReminder(now: Instant): Instant? =
    reminderAt?.takeIf { !isCompleted && it.isAfter(now) }

/**
 * Whether an alarm scheduled for [scheduledAt] should still notify. Alarms are never
 * cleaned up for deleted lists, so stale ones are filtered out here.
 */
fun Task?.shouldNotify(scheduledAt: Instant): Boolean =
    this != null && !isCompleted && reminderAt == scheduledAt
