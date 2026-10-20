package com.example.taskora.domain

import com.example.taskora.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TodayTasksTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun task(id: Long, due: LocalDate?, completed: Boolean = false) = Task(
        id = id, listId = id, title = "t$id", dueDate = due, isCompleted = completed,
        sortOrder = 0, createdAt = Instant.EPOCH,
    )

    @Test
    fun includesOpenOverdueAndToday_acrossLists_byDueDate() {
        val tasks = listOf(
            task(1, today),
            task(2, today.minusDays(2)),
            task(3, today.plusDays(1)),
            task(4, null),
            task(5, today, completed = true),
        )
        assertEquals(listOf(2L, 1L), tasks.dueTodayOrOverdue(today).map { it.id })
    }
}
