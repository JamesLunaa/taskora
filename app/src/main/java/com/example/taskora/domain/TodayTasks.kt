package com.example.taskora.domain

import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskSortMode
import java.time.LocalDate

/** Open tasks due today or earlier (overdue), by due date. */
fun List<Task>.dueTodayOrOverdue(today: LocalDate): List<Task> =
    filter { !it.isCompleted && it.dueDate != null && it.dueDate <= today }.sortedFor(TaskSortMode.DUE_DATE)
