package com.example.taskora.domain

import com.example.taskora.domain.model.Frequency
import com.example.taskora.domain.model.Recurrence
import com.example.taskora.domain.model.Subtask
import com.example.taskora.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceRulesTest {
    private val tue = LocalDate.of(2026, 10, 20) // a Tuesday

    @Test
    fun daily_and_interval() {
        assertEquals(tue.plusDays(1), Recurrence(Frequency.DAILY).nextDate(tue))
        assertEquals(tue.plusDays(3), Recurrence(Frequency.DAILY, interval = 3).nextDate(tue))
    }

    @Test
    fun weekly_withoutDays_keepsWeekday() {
        assertEquals(tue.plusWeeks(2), Recurrence(Frequency.WEEKLY, interval = 2).nextDate(tue))
    }

    @Test
    fun weekly_withDays_goesToNextSelectedDay() {
        val mwf = Recurrence(Frequency.WEEKLY, daysOfWeek = setOf(MONDAY, WEDNESDAY, FRIDAY))
        assertEquals(LocalDate.of(2026, 10, 21), mwf.nextDate(tue)) // Wed same week
        assertEquals(LocalDate.of(2026, 10, 26), mwf.nextDate(LocalDate.of(2026, 10, 23))) // Fri -> next Mon
    }

    @Test
    fun weekly_withDaysAndInterval_skipsWeeksOnlyWhenWrapping() {
        val biweekly = Recurrence(Frequency.WEEKLY, interval = 2, daysOfWeek = setOf(MONDAY, FRIDAY))
        assertEquals(LocalDate.of(2026, 10, 23), biweekly.nextDate(tue)) // Fri same week
        assertEquals(LocalDate.of(2026, 11, 2), biweekly.nextDate(LocalDate.of(2026, 10, 23))) // Mon two weeks on
    }

    @Test
    fun monthly_and_yearly_clampToShorterMonths() {
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence(Frequency.MONTHLY).nextDate(LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2029, 2, 28), Recurrence(Frequency.YEARLY).nextDate(LocalDate.of(2028, 2, 29)))
    }

    private val zone = ZoneId.of("America/New_York")

    private fun recurringTask(due: LocalDate?, recurrence: Recurrence = Recurrence(Frequency.DAILY)) = Task(
        id = 5,
        listId = 2,
        title = "Water plants",
        dueDate = due,
        recurrence = recurrence,
        isCompleted = true,
        completedAt = tue.atStartOfDay(zone).toInstant(),
        subtasks = listOf(Subtask(id = 9, taskId = 5, title = "Balcony", isCompleted = true)),
    )

    @Test
    fun nextOccurrence_isAFreshOpenCopy() {
        val next = recurringTask(due = tue).nextOccurrence(today = tue, zone = zone)!!
        assertEquals(0L, next.id)
        assertEquals(tue.plusDays(1), next.dueDate)
        assertFalse(next.isCompleted)
        assertNull(next.completedAt)
        assertEquals(listOf(Subtask(id = 0, taskId = 0, title = "Balcony", isCompleted = false)), next.subtasks)
        assertEquals("Water plants", next.title)
        assertEquals(2L, next.listId)
    }

    @Test
    fun nextOccurrence_catchesUpToToday_butNotPastIt() {
        val overdue = recurringTask(due = tue.minusDays(3))
        assertEquals(tue, overdue.nextOccurrence(today = tue, zone = zone)!!.dueDate)
        val weekly = recurringTask(due = tue.minusDays(10), recurrence = Recurrence(Frequency.WEEKLY))
        assertEquals(tue.plusDays(4), weekly.nextOccurrence(today = tue, zone = zone)!!.dueDate)
    }

    @Test
    fun nextOccurrence_withoutDueDate_startsFromToday() {
        assertEquals(tue.plusDays(1), recurringTask(due = null).nextOccurrence(today = tue, zone = zone)!!.dueDate)
    }

    @Test
    fun nextOccurrence_shiftsReminderKeepingWallClockTime_acrossDst() {
        // US DST ends Nov 1, 2026; the reminder should stay at 08:00 local.
        val due = LocalDate.of(2026, 10, 31)
        val task = recurringTask(due = due).copy(
            reminderAt = LocalDateTime.of(due.minusDays(1), LocalTime.of(8, 0)).atZone(zone).toInstant(),
        )
        val next = task.nextOccurrence(today = due, zone = zone)!!
        assertEquals(LocalDateTime.of(2026, 10, 31, 8, 0), next.reminderAt!!.atZone(zone).toLocalDateTime())
    }

    @Test
    fun nextOccurrence_nullWhenNotRecurring() {
        assertNull(recurringTask(due = tue).copy(recurrence = null).nextOccurrence(today = tue, zone = zone))
    }
}
