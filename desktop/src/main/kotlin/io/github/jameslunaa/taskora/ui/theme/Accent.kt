package io.github.jameslunaa.taskora.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Taskora's purple roles redone in the desktop's accent color: the accent's hue and saturation at
 * the lightness of each purple it replaces, so contrast stays as designed. That includes the
 * selection color (secondary container). Surfaces, text, warnings and errors keep the brand palette.
 */
internal class AccentTones(accent: Color) {
    private val hue: Float
    private val saturation: Float

    init {
        val r = accent.red
        val g = accent.green
        val b = accent.blue
        val high = max(r, max(g, b))
        val low = min(r, min(g, b))
        val chroma = high - low
        val lightness = (high + low) / 2
        hue = when {
            chroma == 0f -> 0f
            high == r -> 60 * (((g - b) / chroma).mod(6f))
            high == g -> 60 * ((b - r) / chroma + 2)
            else -> 60 * ((r - g) / chroma + 4)
        }
        saturation = if (chroma == 0f) 0f else chroma / (1 - abs(2 * lightness - 1))
    }

    /** The accent at [lightness]; [muted] lowers the saturation for large fields. */
    fun tone(lightness: Float, muted: Boolean = false): Color =
        Color.hsl(hue, (if (muted) saturation * 0.55f else saturation).coerceIn(0f, 1f), lightness)
}

internal fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val tones = AccentTones(accent)
    return if (dark) {
        copy(
            primary = tones.tone(0.77f),
            onPrimary = tones.tone(0.11f),
            primaryContainer = tones.tone(0.27f, muted = true),
            onPrimaryContainer = tones.tone(0.93f),
            inversePrimary = tones.tone(0.45f),
            secondaryContainer = tones.tone(0.21f, muted = true),
            onSecondaryContainer = tones.tone(0.92f),
            tertiary = tones.tone(0.89f),
            tertiaryContainer = tones.tone(0.27f, muted = true),
            onTertiaryContainer = tones.tone(0.93f),
            surfaceTint = tones.tone(0.77f),
        )
    } else {
        copy(
            primary = tones.tone(0.45f),
            primaryContainer = tones.tone(0.93f),
            onPrimaryContainer = tones.tone(0.18f, muted = true),
            inversePrimary = tones.tone(0.77f),
            secondaryContainer = tones.tone(0.90f, muted = true),
            onSecondaryContainer = tones.tone(0.15f, muted = true),
            tertiary = tones.tone(0.32f, muted = true),
            tertiaryContainer = tones.tone(0.93f),
            onTertiaryContainer = tones.tone(0.18f, muted = true),
            surfaceTint = tones.tone(0.45f),
        )
    }
}

internal fun TaskoraColors.withAccent(accent: Color): TaskoraColors {
    val tones = AccentTones(accent)
    return copy(
        headerBand = tones.tone(if (isDark) 0.18f else 0.32f, muted = true),
        headerBandAccent = tones.tone(0.89f),
    )
}
