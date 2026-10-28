package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TaskSortingTest {
    private val day = LocalDate.of(2026, 10, 20)

    private fun task(
        id: Long,
        title: String = "t$id",
        due: LocalDate? = null,
        time: LocalTime? = null,
        priority: Priority = Priority.NONE,
    ) = Task(
        id = id,
        listId = 1,
        title = title,
        dueDate = due,
        dueTime = time,
        priority = priority,
        sortOrder = id.toInt(),
        createdAt = Instant.EPOCH,
    )

    private fun List<Task>.ids(mode: TaskSortMode, descending: Boolean = false) =
        sortedFor(mode, descending).map { it.id }

    @Test
    fun manual_usesSortOrder() {
        assertEquals(listOf(1L, 2L, 3L), listOf(task(3), task(1), task(2)).ids(TaskSortMode.MANUAL))
    }

    @Test
    fun created_usesCreationTime_notSortOrder() {
        val tasks = listOf(
            task(1).copy(createdAt = Instant.ofEpochSecond(30)),
            task(2).copy(createdAt = Instant.ofEpochSecond(10)),
            task(3).copy(createdAt = Instant.ofEpochSecond(20)),
        )
        assertEquals(listOf(2L, 3L, 1L), tasks.ids(TaskSortMode.CREATED))
        assertEquals(listOf(1L, 3L, 2L), tasks.ids(TaskSortMode.CREATED, descending = true))
    }

    @Test
    fun manual_ignoresDescending() {
        assertEquals(listOf(1L, 2L), listOf(task(2), task(1)).ids(TaskSortMode.MANUAL, descending = true))
    }

    @Test
    fun dueDate_descending_latestFirst_undatedStillLast() {
        val tasks = listOf(
            task(1),
            task(2, due = day.plusDays(1)),
            task(3, due = day),
            task(4, due = day, time = LocalTime.of(9, 0)),
        )
        assertEquals(listOf(2L, 3L, 4L, 1L), tasks.ids(TaskSortMode.DUE_DATE, descending = true))
    }

    @Test
    fun priority_descending_lowestFirst() {
        val tasks = listOf(task(1, priority = Priority.LOW), task(2, priority = Priority.HIGH), task(3))
        assertEquals(listOf(3L, 1L, 2L), tasks.ids(TaskSortMode.PRIORITY, descending = true))
    }

    @Test
    fun title_descending_zToA() {
        val tasks = listOf(task(1, "banana"), task(2, "Apple"), task(3, "cherry"))
        assertEquals(listOf(3L, 1L, 2L), tasks.ids(TaskSortMode.TITLE, descending = true))
    }

    @Test
    fun dueDate_undatedLast_allDayAfterTimed_thenPriority() {
        val tasks = listOf(
            task(1),
            task(2, due = day.plusDays(1)),
            task(3, due = day),
            task(4, due = day, time = LocalTime.of(9, 0)),
            task(5, due = day.plusDays(1), priority = Priority.HIGH),
        )
        assertEquals(listOf(4L, 3L, 5L, 2L, 1L), tasks.ids(TaskSortMode.DUE_DATE))
    }

    @Test
    fun priority_highestFirst_thenDueDate() {
        val tasks = listOf(
            task(1, priority = Priority.LOW),
            task(2, priority = Priority.HIGH),
            task(3, priority = Priority.HIGH, due = day),
            task(4),
        )
        assertEquals(listOf(3L, 2L, 1L, 4L), tasks.ids(TaskSortMode.PRIORITY))
    }

    @Test
    fun title_isCaseInsensitive() {
        val tasks = listOf(task(1, "banana"), task(2, "Apple"), task(3, "cherry"))
        assertEquals(listOf(2L, 1L, 3L), tasks.ids(TaskSortMode.TITLE))
    }
}
