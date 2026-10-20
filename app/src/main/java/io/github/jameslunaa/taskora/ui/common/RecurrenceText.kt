package io.github.jameslunaa.taskora.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Recurrence
import java.time.DayOfWeek
import java.time.format.TextStyle

/** e.g. "Every day", "Every 2 weeks on Mon, Thu". */
@Composable
fun recurrenceText(recurrence: Recurrence): String {
    val n = recurrence.interval
    val every = when (recurrence.frequency) {
        Frequency.DAILY -> pluralStringResource(R.plurals.every_n_days, n, n)
        Frequency.WEEKLY -> pluralStringResource(R.plurals.every_n_weeks, n, n)
        Frequency.MONTHLY -> pluralStringResource(R.plurals.every_n_months, n, n)
        Frequency.YEARLY -> pluralStringResource(R.plurals.every_n_years, n, n)
    }
    if (recurrence.frequency != Frequency.WEEKLY || recurrence.daysOfWeek.isEmpty()) return every
    val locale = LocalConfiguration.current.locales[0]
    val days = recurrence.daysOfWeek.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    return stringResource(R.string.recurrence_on_days, every, days)
}

@Composable
fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])
