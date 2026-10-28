package io.github.jameslunaa.taskora.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.sticky_note
import io.github.jameslunaa.taskora.resources.window_hints_unavailable
import io.github.jameslunaa.taskora.resources.window_level_above
import io.github.jameslunaa.taskora.resources.window_level_below
import io.github.jameslunaa.taskora.resources.window_level_normal
import io.github.jameslunaa.taskora.resources.window_opacity
import io.github.jameslunaa.taskora.resources.window_opacity_unavailable
import io.github.jameslunaa.taskora.system.WindowCapabilities
import io.github.jameslunaa.taskora.resources.window_options
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

class WindowLookActions(
    val onLevelChange: (WindowLevel) -> Unit,
    /** While the opacity slider moves. */
    val onOpacityChange: (Float) -> Unit,
    /** When it's released. */
    val onOpacityChangeFinished: () -> Unit,
)

val WindowLevel.label: StringResource
    get() = when (this) {
        WindowLevel.ABOVE -> Res.string.window_level_above
        WindowLevel.NORMAL -> Res.string.window_level_normal
        WindowLevel.BELOW -> Res.string.window_level_below
    }

/**
 * A pin button with the window's stacking level and opacity. The pin is filled while the window
 * is pinned above or below the others. [capabilities] says whether "always below" and opacity
 * work here (see `WindowHints`). [extraItems] go at the end of the menu.
 */
@Composable
fun WindowLookButton(
    look: WindowLook,
    capabilities: WindowCapabilities,
    actions: WindowLookActions,
    tint: Color = LocalContentColor.current,
    extraItems: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (look.level == WindowLevel.NORMAL) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                contentDescription = stringResource(Res.string.window_options),
                tint = tint,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WindowLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(stringResource(level.label)) },
                    onClick = {
                        actions.onLevelChange(level)
                        expanded = false
                    },
                    enabled = level != WindowLevel.BELOW || capabilities.below,
                    leadingIcon = {
                        if (level == look.level) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        } else {
                            Box(Modifier.padding(12.dp))
                        }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            OpacitySlider(look.opacity, capabilities.opacity, actions)
            val unavailable = when {
                !capabilities.below -> Res.string.window_hints_unavailable
                !capabilities.opacity -> Res.string.window_opacity_unavailable
                else -> null
            }
            if (unavailable != null) {
                Text(
                    stringResource(unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            extraItems { expanded = false }
        }
    }
}

@Composable
private fun OpacitySlider(opacity: Float, enabled: Boolean, actions: WindowLookActions) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.window_opacity), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = opacity,
            onValueChange = actions.onOpacityChange,
            onValueChangeFinished = actions.onOpacityChangeFinished,
            valueRange = WindowLook.MIN_OPACITY..1f,
            enabled = enabled,
            modifier = Modifier.width(140.dp).padding(horizontal = 12.dp),
        )
        Text(
            "${(opacity * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
    }
}

/** Shows or hides the sticky note; for the main window's pin menu. */
@Composable
fun StickyNoteMenuItem(open: Boolean, onToggle: () -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.sticky_note)) },
        onClick = onToggle,
        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.StickyNote2, contentDescription = null) },
        trailingIcon = { if (open) Icon(Icons.Filled.Check, contentDescription = null) },
    )
}
