package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Task
import java.time.LocalDate

/** Clearing the due date also clears the time and the recurrence, which needs a date. */
fun Task.withDueDate(date: LocalDate?): Task =
    if (date == null) copy(dueDate = null, dueTime = null, recurrence = null) else copy(dueDate = date)
