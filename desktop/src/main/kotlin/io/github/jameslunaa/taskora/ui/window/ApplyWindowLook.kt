package io.github.jameslunaa.taskora.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.WindowScope
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.system.WindowHints
import kotlinx.coroutines.delay

/**
 * Applies [look]'s "always below" and opacity whenever it changes or the window is shown again
 * ("always on top" is the window's `alwaysOnTop`). The window manager drops its state when a
 * window is hidden, so showing it again needs the hints again.
 */
@Composable
fun WindowScope.ApplyWindowLook(look: WindowLook, visible: Boolean, hints: WindowHints, skipTaskbar: Boolean = false) {
    LaunchedEffect(look, visible, hints) {
        if (!visible) return@LaunchedEffect
        // A window that was only just shown isn't managed yet; give the window manager a moment.
        repeat(40) {
            if (hints.apply(window, look, skipTaskbar)) return@LaunchedEffect
            delay(50)
        }
    }
}
