package com.example.taskora.domain

import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskSortMode
import java.time.LocalDate
import java.time.LocalTime

private val byCreated: Comparator<Task> = compareBy({ it.sortOrder }, { it.createdAt }, { it.id })

// Undated tasks go last; on the same day, all-day tasks come after timed ones.
private val byDue: Comparator<Task> = compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }
    .thenBy { it.dueTime ?: LocalTime.MAX }

private val byPriority: Comparator<Task> = compareByDescending { it.priority.level }

fun List<Task>.sortedFor(mode: TaskSortMode): List<Task> = sortedWith(
    when (mode) {
        TaskSortMode.CREATED -> byCreated
        TaskSortMode.DUE_DATE -> byDue.then(byPriority).then(byCreated)
        TaskSortMode.PRIORITY -> byPriority.then(byDue).then(byCreated)
        TaskSortMode.TITLE -> compareBy<Task, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.then(byCreated)
    },
)
