package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import java.time.LocalDateTime

enum class StatusFilter { OPEN, COMPLETED, ALL }

enum class DueFilter { ANY, OVERDUE, TODAY, NEXT_7_DAYS, NO_DATE }

data class TaskFilter(
    val query: String = "",
    val status: StatusFilter = StatusFilter.OPEN,
    /** Null means every list. */
    val listId: Long? = null,
    /** Empty means any priority. */
    val priorities: Set<Priority> = emptySet(),
    val due: DueFilter = DueFilter.ANY,
) {
    /** Whether any filter (not counting the query) differs from the defaults. */
    val hasFilters: Boolean get() = copy(query = "") != TaskFilter()

    /** Nothing to search for yet: blank query and default filters. */
    val isEmpty: Boolean get() = query.isBlank() && !hasFilters
}

/** Matches [filter] against title and notes (case-insensitive) plus the structured filters. */
fun Task.matches(filter: TaskFilter, now: LocalDateTime): Boolean {
    val query = filter.query.trim()
    if (query.isNotEmpty() && !title.contains(query, ignoreCase = true) && !notes.contains(query, ignoreCase = true)) {
        return false
    }
    val statusOk = when (filter.status) {
        StatusFilter.OPEN -> !isCompleted
        StatusFilter.COMPLETED -> isCompleted
        StatusFilter.ALL -> true
    }
    if (!statusOk) return false
    if (filter.listId != null && listId != filter.listId) return false
    if (filter.priorities.isNotEmpty() && priority !in filter.priorities) return false
    val date = dueDate
    val today = now.toLocalDate()
    return when (filter.due) {
        DueFilter.ANY -> true
        DueFilter.OVERDUE -> date != null && dueStatus(date, dueTime, now) == DueStatus.OVERDUE
        DueFilter.TODAY -> date == today
        DueFilter.NEXT_7_DAYS -> date != null && date >= today && date < today.plusDays(7)
        DueFilter.NO_DATE -> date == null
    }
}

/** Open tasks by due date first, then completed ones (most recent first). */
fun List<Task>.search(filter: TaskFilter, now: LocalDateTime): List<Task> {
    val (completed, open) = filter { it.matches(filter, now) }.partition { it.isCompleted }
    return open.sortedFor(TaskSortMode.DUE_DATE) + completed.sortedByDescending { it.completedAt }
}
