package io.github.jameslunaa.taskora.ui.common

import androidx.compose.runtime.Composable
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.today
import io.github.jameslunaa.taskora.resources.tomorrow
import io.github.jameslunaa.taskora.resources.yesterday
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

data class RelativeDayLabels(val yesterday: String, val today: String, val tomorrow: String)

fun formatDue(
    date: LocalDate,
    time: LocalTime?,
    today: LocalDate,
    labels: RelativeDayLabels,
    locale: Locale,
): String {
    val day = when (date) {
        today.minusDays(1) -> labels.yesterday
        today -> labels.today
        today.plusDays(1) -> labels.tomorrow
        else -> {
            val pattern = if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy"
            DateTimeFormatter.ofPattern(pattern, locale).format(date)
        }
    }
    if (time == null) return day
    return "$day, " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)
}

@Composable
fun dueText(date: LocalDate, time: LocalTime?, today: LocalDate = LocalDate.now()): String {
    val labels = RelativeDayLabels(
        yesterday = stringResource(Res.string.yesterday),
        today = stringResource(Res.string.today),
        tomorrow = stringResource(Res.string.tomorrow),
    )
    return formatDue(date, time, today, labels, Locale.getDefault())
}

fun timeText(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(time)

fun dateText(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)

/** Whether the locale's short time format uses a 12-hour clock with AM/PM. */
fun uses24HourClock(locale: Locale = Locale.getDefault()): Boolean =
    !DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
        .contains('a')
