package io.github.jameslunaa.taskora.domain

import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import java.time.LocalTime

private val byManual: Comparator<Task> = compareBy({ it.sortOrder }, { it.createdAt }, { it.id })

private val byCreated: Comparator<Task> = compareBy({ it.createdAt }, { it.id })

private fun Comparator<Task>.direction(descending: Boolean) = if (descending) reversed() else this

// Undated tasks go last whichever way it's sorted; on the same day, all-day tasks come after timed ones.
private fun byDue(descending: Boolean): Comparator<Task> {
    val dated = Comparator<Task> { a, b ->
        val byDate = a.dueDate!!.compareTo(b.dueDate!!)
        if (byDate != 0) byDate else (a.dueTime ?: LocalTime.MAX).compareTo(b.dueTime ?: LocalTime.MAX)
    }.direction(descending)
    return Comparator { a, b ->
        when {
            a.dueDate == null && b.dueDate == null -> 0
            a.dueDate == null -> 1
            b.dueDate == null -> -1
            else -> dated.compare(a, b)
        }
    }
}

private fun byPriority(descending: Boolean): Comparator<Task> =
    compareByDescending<Task> { it.priority.level }.direction(descending)

private val byTitle: Comparator<Task> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }

/**
 * Sorts for a list's [mode]. [descending] reverses the mode's own key only: ties still fall back to the
 * same secondary keys, and undated tasks stay last. [TaskSortMode.PRIORITY] starts with the highest priority.
 */
fun List<Task>.sortedFor(mode: TaskSortMode, descending: Boolean = false): List<Task> = sortedWith(
    when (mode) {
        TaskSortMode.MANUAL -> byManual
        TaskSortMode.CREATED -> byCreated.direction(descending)
        TaskSortMode.DUE_DATE -> byDue(descending).then(byPriority(false)).then(byManual)
        TaskSortMode.PRIORITY -> byPriority(descending).then(byDue(false)).then(byManual)
        TaskSortMode.TITLE -> byTitle.direction(descending).then(byManual)
    },
)
