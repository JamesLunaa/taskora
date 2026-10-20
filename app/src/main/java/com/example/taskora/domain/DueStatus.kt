package com.example.taskora.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class DueStatus { OVERDUE, TODAY, UPCOMING }

/** A task due today with no time is never overdue until the day is over. */
fun dueStatus(date: LocalDate, time: LocalTime?, now: LocalDateTime): DueStatus {
    val today = now.toLocalDate()
    return when {
        date < today -> DueStatus.OVERDUE
        date > today -> DueStatus.UPCOMING
        time != null && time < now.toLocalTime() -> DueStatus.OVERDUE
        else -> DueStatus.TODAY
    }
}
