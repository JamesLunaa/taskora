package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Task
import java.time.LocalDate

/** The day filter on the task lists. It's global: switching lists keeps it. */
enum class DayFilter { ALL, TODAY, TOMORROW }

/** Today also keeps open overdue tasks, as the widget does, so they don't drop out of sight. */
fun Task.matchesDay(filter: DayFilter, today: LocalDate): Boolean {
    val date = dueDate
    return when (filter) {
        DayFilter.ALL -> true
        DayFilter.TODAY -> date != null && (date == today || (!isCompleted && date < today))
        DayFilter.TOMORROW -> date == today.plusDays(1)
    }
}
