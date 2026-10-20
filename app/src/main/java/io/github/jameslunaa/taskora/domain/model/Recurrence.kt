package io.github.jameslunaa.taskora.domain.model

import java.time.DayOfWeek

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Repeats every [interval] [frequency] units. [daysOfWeek] only applies to WEEKLY;
 * empty means "same weekday as the due date".
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
    }
}
