package io.github.jameslunaa.taskora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val DarkColors = darkColorScheme(
    primary = UbeLamp,
    onPrimary = OnLamp,
    primaryContainer = LampContainer,
    onPrimaryContainer = OnLampContainer,
    inversePrimary = DayLamp,
    secondary = DuskInk,
    onSecondary = OnLamp,
    secondaryContainer = OperatorPlum,
    onSecondaryContainer = OnOperator,
    tertiary = FieldLilac,
    onTertiary = OnLamp,
    tertiaryContainer = LampContainer,
    onTertiaryContainer = OnLampContainer,
    background = NightGround,
    onBackground = PriceCardInk,
    surface = NightGround,
    onSurface = PriceCardInk,
    surfaceVariant = StallHighest,
    onSurfaceVariant = DuskInk,
    surfaceTint = UbeLamp,
    surfaceDim = StallShadow,
    surfaceBright = StallHighest,
    surfaceContainerLowest = StallShadow,
    surfaceContainerLow = StallLow,
    surfaceContainer = Stall,
    surfaceContainerHigh = StallHigh,
    surfaceContainerHighest = StallHighest,
    inverseSurface = PriceCardInk,
    inverseOnSurface = NightGround,
    error = ExpenseCoral,
    onError = OnLamp,
    errorContainer = Color(0xFF4A1C18),
    onErrorContainer = Color(0xFFFFDAD5),
    outline = OutlineDusk,
    outlineVariant = OutlineFaint,
    scrim = Color.Black,
)

internal val LightColors = lightColorScheme(
    primary = DayLamp,
    onPrimary = Color.White,
    primaryContainer = DayLampContainer,
    onPrimaryContainer = UbeField,
    inversePrimary = UbeLamp,
    secondary = DayMutedInk,
    onSecondary = Color.White,
    secondaryContainer = DayOperator,
    onSecondaryContainer = DayInk,
    tertiary = DayField,
    onTertiary = DayOnField,
    tertiaryContainer = DayLampContainer,
    onTertiaryContainer = UbeField,
    background = DayGround,
    onBackground = DayInk,
    surface = DayGround,
    onSurface = DayInk,
    surfaceVariant = DayStallHighest,
    onSurfaceVariant = DayMutedInk,
    surfaceTint = DayLamp,
    surfaceDim = DayStallHighest,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DayStallLow,
    surfaceContainer = Color(0xFFECE7E3),
    surfaceContainerHigh = DayStallHigh,
    surfaceContainerHighest = DayStallHighest,
    inverseSurface = DayInk,
    inverseOnSurface = DayGround,
    error = DayExpense,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A8296),
    outlineVariant = DayStallHighest,
    scrim = Color.Black,
)

/** Roles that Material's color scheme doesn't have. */
@Immutable
data class TaskoraColors(
    val isDark: Boolean,
    /** Large fields only (the top app bar), never small elements. */
    val headerBand: Color,
    val onHeaderBand: Color,
    /** Icons and secondary text on the header band. */
    val headerBandAccent: Color,
    /** Warnings only, not decoration. */
    val warning: Color,
)

private val DarkTaskoraColors = TaskoraColors(
    isDark = true,
    headerBand = UbeField,
    onHeaderBand = PriceCardInk,
    headerBandAccent = FieldLilac,
    warning = SodiumAmber,
)

private val LightTaskoraColors = TaskoraColors(
    isDark = false,
    headerBand = DayField,
    onHeaderBand = DayOnField,
    headerBandAccent = DayFieldLilac,
    warning = DayAmber,
)

val LocalTaskoraColors = staticCompositionLocalOf { DarkTaskoraColors }

/**
 * Dark is the default look. Desktop has no wallpaper-based dynamic color, but can take the
 * desktop's [accent] color in place of the purple (see `withAccent`).
 */
@Composable
fun TaskoraTheme(
    darkTheme: Boolean = true,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkTaskoraColors else LightTaskoraColors
    val scheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalTaskoraColors provides (accent?.let(colors::withAccent) ?: colors)) {
        MaterialTheme(
            colorScheme = accent?.let { scheme.withAccent(it, darkTheme) } ?: scheme,
            typography = taskoraTypography(),
            content = content,
        )
    }
}

object TaskoraTheme {
    val colors: TaskoraColors
        @Composable get() = LocalTaskoraColors.current
}

/** A list's stored (dark) color, shown as its day variant in light mode. */
@Composable
fun listColor(stored: Int): Color {
    val dark = Color(stored)
    if (TaskoraTheme.colors.isDark) return dark
    return CategoryPalette.firstOrNull { it.first == dark }?.second ?: dark
}
