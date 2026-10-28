package io.github.jameslunaa.taskora.ui.common

import androidx.compose.runtime.Composable
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.every_n_days
import io.github.jameslunaa.taskora.resources.every_n_months
import io.github.jameslunaa.taskora.resources.every_n_weeks
import io.github.jameslunaa.taskora.resources.every_n_years
import io.github.jameslunaa.taskora.resources.recurrence_on_days
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Recurrence
import java.time.DayOfWeek
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.format.TextStyle
import java.util.Locale

/** e.g. "Every day", "Every 2 weeks on Mon, Thu". */
@Composable
fun recurrenceText(recurrence: Recurrence): String {
    val n = recurrence.interval
    val every = when (recurrence.frequency) {
        Frequency.DAILY -> pluralStringResource(Res.plurals.every_n_days, n, n)
        Frequency.WEEKLY -> pluralStringResource(Res.plurals.every_n_weeks, n, n)
        Frequency.MONTHLY -> pluralStringResource(Res.plurals.every_n_months, n, n)
        Frequency.YEARLY -> pluralStringResource(Res.plurals.every_n_years, n, n)
    }
    if (recurrence.frequency != Frequency.WEEKLY || recurrence.daysOfWeek.isEmpty()) return every
    val locale = Locale.getDefault()
    val days = recurrence.daysOfWeek.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    return stringResource(Res.string.recurrence_on_days, every, days)
}

@Composable
fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())
