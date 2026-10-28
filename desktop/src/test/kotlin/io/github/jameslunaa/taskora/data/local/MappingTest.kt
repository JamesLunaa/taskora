package io.github.jameslunaa.taskora.data.local

import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity
import io.github.jameslunaa.taskora.data.local.entity.TaskWithSubtasks
import io.github.jameslunaa.taskora.data.local.entity.daysFromMask
import io.github.jameslunaa.taskora.data.local.entity.daysToMask
import io.github.jameslunaa.taskora.data.local.entity.toDomain
import io.github.jameslunaa.taskora.data.local.entity.toEntity
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class MappingTest {
    private val converters = Converters()

    @Test
    fun converters_roundTrip() {
        val instant = Instant.parse("2026-10-20T08:30:00Z")
        assertEquals(instant, converters.millisToInstant(converters.instantToMillis(instant)))
        val date = LocalDate.of(2026, 10, 20)
        assertEquals(date, converters.epochDayToLocalDate(converters.localDateToEpochDay(date)))
        val time = LocalTime.of(14, 45)
        assertEquals(time, converters.secondOfDayToLocalTime(converters.localTimeToSecondOfDay(time)))
        Priority.entries.forEach { assertEquals(it, converters.levelToPriority(converters.priorityToLevel(it))) }
        assertNull(converters.millisToInstant(null))
    }

    @Test
    fun priority_levelsSortLowToHigh() {
        assertEquals(Priority.entries.sortedBy { it.level }, listOf(Priority.NONE, Priority.LOW, Priority.MEDIUM, Priority.HIGH))
        assertEquals(Priority.NONE, Priority.fromLevel(99))
    }

    @Test
    fun weekdayMask_roundTrip() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(0b1000101, daysToMask(days))
        assertEquals(days, daysFromMask(daysToMask(days)))
        assertEquals(emptySet<DayOfWeek>(), daysFromMask(0))
    }

    @Test
    fun task_roundTripsThroughEntity() {
        val task = Task(
            id = 7,
            listId = 1,
            title = "Pay rent",
            notes = "Transfer",
            dueDate = LocalDate.of(2026, 11, 1),
            dueTime = LocalTime.of(9, 0),
            reminderAt = Instant.parse("2026-11-01T00:00:00Z"),
            priority = Priority.HIGH,
            recurrence = Recurrence(Frequency.WEEKLY, interval = 2, daysOfWeek = setOf(DayOfWeek.FRIDAY)),
            createdAt = Instant.parse("2026-10-20T00:00:00Z"),
        )
        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun task_withoutRecurrence_mapsToNullColumns() {
        val entity = Task(listId = 1, title = "x").toEntity()
        assertNull(entity.recurrenceFrequency)
        assertNull(entity.recurrenceInterval)
        assertNull(entity.recurrenceDays)
        assertNull(entity.toDomain().recurrence)
    }

    @Test
    fun subtasks_areSortedBySortOrder() {
        val entity = Task(id = 1, listId = 1, title = "t").toEntity()
        val withSubtasks = TaskWithSubtasks(
            entity,
            listOf(
                SubtaskEntity(id = 2, taskId = 1, title = "b", isCompleted = false, sortOrder = 1),
                SubtaskEntity(id = 1, taskId = 1, title = "a", isCompleted = true, sortOrder = 0),
            ),
        )
        assertEquals(listOf("a", "b"), withSubtasks.toDomain().subtasks.map { it.title })
    }
}
