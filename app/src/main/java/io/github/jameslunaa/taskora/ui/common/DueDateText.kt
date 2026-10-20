package io.github.jameslunaa.taskora.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.jameslunaa.taskora.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
    val locale = currentLocale()
    val labels = RelativeDayLabels(
        yesterday = stringResource(R.string.yesterday),
        today = stringResource(R.string.today),
        tomorrow = stringResource(R.string.tomorrow),
    )
    return formatDue(date, time, today, labels, locale)
}

@Composable
fun timeText(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(time)

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
