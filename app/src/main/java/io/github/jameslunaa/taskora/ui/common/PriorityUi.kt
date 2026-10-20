package io.github.jameslunaa.taskora.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme

@get:StringRes
val Priority.labelRes: Int
    get() = when (this) {
        Priority.NONE -> R.string.priority_none
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
    }

// Lamp purple is kept for the screen's main action, so LOW stays neutral.
@Composable
fun Priority.color(): Color = when (this) {
    Priority.NONE, Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    Priority.MEDIUM -> TaskoraTheme.colors.warning
    Priority.HIGH -> MaterialTheme.colorScheme.error
}
