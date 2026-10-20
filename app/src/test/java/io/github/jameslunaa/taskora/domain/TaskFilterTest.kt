package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskFilterTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val now = LocalDateTime.of(today, LocalTime.NOON)

    private fun task(
        id: Long,
        title: String = "Task $id",
        notes: String = "",
        listId: Long = 1,
        due: LocalDate? = null,
        time: LocalTime? = null,
        priority: Priority = Priority.NONE,
        completed: Boolean = false,
    ) = Task(
        id = id, listId = listId, title = title, notes = notes, dueDate = due, dueTime = time,
        priority = priority, isCompleted = completed, sortOrder = id.toInt(), createdAt = Instant.EPOCH,
        completedAt = if (completed) Instant.ofEpochSecond(id) else null,
    )

    private fun List<Task>.ids(filter: TaskFilter) = search(filter, now).map { it.id }

    @Test
    fun query_matchesTitleOrNotes_caseInsensitive() {
        val tasks = listOf(task(1, title = "Pay RENT"), task(2, notes = "rent for october"), task(3, title = "Other"))
        assertEquals(setOf(1L, 2L), tasks.ids(TaskFilter(query = "  rent ")).toSet())
    }

    @Test
    fun status_defaultsToOpen() {
        val tasks = listOf(task(1), task(2, completed = true))
        assertEquals(listOf(1L), tasks.ids(TaskFilter(query = "task")))
        assertEquals(listOf(2L), tasks.ids(TaskFilter(status = StatusFilter.COMPLETED)))
        assertEquals(listOf(1L, 2L), tasks.ids(TaskFilter(status = StatusFilter.ALL)))
    }

    @Test
    fun listAndPriorityFilters() {
        val tasks = listOf(
            task(1, listId = 1, priority = Priority.HIGH),
            task(2, listId = 2, priority = Priority.HIGH),
            task(3, listId = 2, priority = Priority.LOW),
        )
        assertEquals(listOf(2L, 3L), tasks.ids(TaskFilter(listId = 2)))
        assertEquals(listOf(1L, 2L), tasks.ids(TaskFilter(priorities = setOf(Priority.HIGH))))
        assertEquals(listOf(2L), tasks.ids(TaskFilter(listId = 2, priorities = setOf(Priority.HIGH))))
    }

    @Test
    fun dueFilters() {
        val tasks = listOf(
            task(1, due = today.minusDays(1)),
            task(2, due = today, time = LocalTime.of(9, 0)), // earlier today: overdue
            task(3, due = today),
            task(4, due = today.plusDays(6)),
            task(5, due = today.plusDays(7)),
            task(6),
        )
        assertEquals(listOf(1L, 2L), tasks.ids(TaskFilter(due = DueFilter.OVERDUE)))
        assertEquals(listOf(2L, 3L), tasks.ids(TaskFilter(due = DueFilter.TODAY)))
        assertEquals(listOf(2L, 3L, 4L), tasks.ids(TaskFilter(due = DueFilter.NEXT_7_DAYS)))
        assertEquals(listOf(6L), tasks.ids(TaskFilter(due = DueFilter.NO_DATE)))
    }

    @Test
    fun results_openByDueDate_thenCompletedNewestFirst() {
        val tasks = listOf(
            task(1, title = "a", due = today.plusDays(2)),
            task(2, title = "a", due = today),
            task(3, title = "a", completed = true),
            task(4, title = "a", completed = true),
        )
        assertEquals(listOf(2L, 1L, 4L, 3L), tasks.ids(TaskFilter(query = "a", status = StatusFilter.ALL)))
    }

    @Test
    fun emptiness() {
        assertTrue(TaskFilter().isEmpty)
        assertTrue(TaskFilter(query = "  ").isEmpty)
        assertFalse(TaskFilter(query = "x").isEmpty)
        assertFalse(TaskFilter(due = DueFilter.TODAY).isEmpty)
        assertFalse(TaskFilter(query = "x").hasFilters)
        assertTrue(TaskFilter(status = StatusFilter.ALL).hasFilters)
    }
}
