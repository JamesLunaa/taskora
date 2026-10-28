package io.github.jameslunaa.taskora.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.priority_high
import io.github.jameslunaa.taskora.resources.priority_low
import io.github.jameslunaa.taskora.resources.priority_medium
import io.github.jameslunaa.taskora.resources.priority_none
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import org.jetbrains.compose.resources.StringResource

val Priority.label: StringResource
    get() = when (this) {
        Priority.NONE -> Res.string.priority_none
        Priority.LOW -> Res.string.priority_low
        Priority.MEDIUM -> Res.string.priority_medium
        Priority.HIGH -> Res.string.priority_high
    }

// Lamp purple is kept for the screen's main action, so LOW stays neutral.
@Composable
fun Priority.color(): Color = when (this) {
    Priority.NONE, Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    Priority.MEDIUM -> TaskoraTheme.colors.warning
    Priority.HIGH -> MaterialTheme.colorScheme.error
}
