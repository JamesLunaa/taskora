package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import java.time.LocalDate

/** Open tasks due today or earlier (overdue), by due date. */
fun List<Task>.dueTodayOrOverdue(today: LocalDate): List<Task> =
    filter { !it.isCompleted && it.dueDate != null && it.dueDate <= today }.sortedFor(TaskSortMode.DUE_DATE)
