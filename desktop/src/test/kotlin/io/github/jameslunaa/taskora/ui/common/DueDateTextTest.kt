package io.github.jameslunaa.taskora.ui.common

import io.github.jameslunaa.taskora.domain.DueStatus
import io.github.jameslunaa.taskora.domain.dueStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class DueDateTextTest {
    private val today = LocalDate.of(2026, 10, 20) // a Tuesday
    private val labels = RelativeDayLabels(yesterday = "Yesterday", today = "Today", tomorrow = "Tomorrow")

    private fun format(date: LocalDate, time: LocalTime? = null) = formatDue(date, time, today, labels, Locale.US)

    @Test
    fun relativeDays() {
        assertEquals("Yesterday", format(today.minusDays(1)))
        assertEquals("Today", format(today))
        assertEquals("Tomorrow", format(today.plusDays(1)))
    }

    @Test
    fun otherDates_includeYearOnlyWhenDifferent() {
        assertEquals("Fri, Oct 23", format(today.plusDays(3)))
        assertEquals("Jan 5, 2027", format(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun time_isAppended() {
        val text = format(today, LocalTime.of(14, 30))
        assert(text.startsWith("Today, ") && text.contains("2:30")) { text }
    }

    @Test
    fun dueStatus_respectsTimeOnlyOnTheDueDay() {
        val now = LocalDateTime.of(today, LocalTime.of(12, 0))
        assertEquals(DueStatus.OVERDUE, dueStatus(today.minusDays(1), null, now))
        assertEquals(DueStatus.TODAY, dueStatus(today, null, now))
        assertEquals(DueStatus.OVERDUE, dueStatus(today, LocalTime.of(11, 0), now))
        assertEquals(DueStatus.TODAY, dueStatus(today, LocalTime.of(13, 0), now))
        assertEquals(DueStatus.UPCOMING, dueStatus(today.plusDays(1), LocalTime.of(1, 0), now))
    }
}
