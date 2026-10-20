package io.github.jameslunaa.taskora.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class Task(
    val id: Long = 0,
    val listId: Long,
    val title: String,
    val notes: String = "",
    val dueDate: LocalDate? = null,
    /** Null means an all-day task (or no due date). */
    val dueTime: LocalTime? = null,
    val reminderAt: Instant? = null,
    val priority: Priority = Priority.NONE,
    val recurrence: Recurrence? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val sortOrder: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
)

data class Subtask(
    val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
)
