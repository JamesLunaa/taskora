package com.example.taskora.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.taskora.R
import com.example.taskora.domain.model.Priority

@get:StringRes
val Priority.labelRes: Int
    get() = when (this) {
        Priority.NONE -> R.string.priority_none
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
    }

@Composable
fun Priority.color(): Color = when (this) {
    Priority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    Priority.LOW -> MaterialTheme.colorScheme.primary
    Priority.MEDIUM -> MaterialTheme.colorScheme.tertiary
    Priority.HIGH -> MaterialTheme.colorScheme.error
}
