package com.example.taskora.domain

import com.example.taskora.domain.model.Frequency
import com.example.taskora.domain.model.Recurrence
import com.example.taskora.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The first occurrence after [date]. Months and years use java.time's clamping, so the 31st
 * becomes the last day of shorter months (and stays there from then on).
 */
fun Recurrence.nextDate(date: LocalDate): LocalDate = when (frequency) {
    Frequency.DAILY -> date.plusDays(interval.toLong())
    Frequency.WEEKLY -> nextWeeklyDate(date)
    Frequency.MONTHLY -> date.plusMonths(interval.toLong())
    Frequency.YEARLY -> date.plusYears(interval.toLong())
}

private fun Recurrence.nextWeeklyDate(date: LocalDate): LocalDate {
    if (daysOfWeek.isEmpty()) return date.plusWeeks(interval.toLong())
    // A later selected day in the same week, else the first selected day `interval` weeks on.
    daysOfWeek.filter { it > date.dayOfWeek }.minOrNull()?.let { return date.with(it) }
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return weekStart.plusWeeks(interval.toLong()).with(daysOfWeek.min())
}

/**
 * The task to create when this recurring task is completed, or null if it doesn't repeat.
 * The due date moves forward until it's no earlier than [today], and the reminder keeps the
 * same wall-clock time and distance from the due date. Subtasks are copied unchecked.
 */
fun Task.nextOccurrence(today: LocalDate, zone: ZoneId): Task? {
    val recurrence = recurrence ?: return null
    val due = dueDate ?: today
    var next = recurrence.nextDate(due)
    while (next < today) next = recurrence.nextDate(next)
    val shiftDays = ChronoUnit.DAYS.between(due, next)

    return copy(
        id = 0,
        dueDate = next,
        reminderAt = reminderAt?.let { shiftReminder(it, shiftDays, zone) },
        isCompleted = false,
        completedAt = null,
        subtasks = subtasks.map { it.copy(id = 0, taskId = 0, isCompleted = false) },
    )
}

private fun shiftReminder(reminderAt: Instant, days: Long, zone: ZoneId): Instant =
    reminderAt.atZone(zone).toLocalDateTime().plusDays(days).atZone(zone).toInstant()
