package io.github.jameslunaa.taskora.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.ui.theme.listColor

/** A list's color dot; a null [color] (no list) draws an empty ring. */
@Composable
fun ListDot(color: Int?, modifier: Modifier = Modifier) {
    Box(
        if (color != null) {
            modifier.background(listColor(color), CircleShape)
        } else {
            modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
        },
    )
}
