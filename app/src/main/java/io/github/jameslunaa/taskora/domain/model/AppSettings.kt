package io.github.jameslunaa.taskora.domain.model

import io.github.jameslunaa.taskora.domain.DayFilter

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Wallpaper-based Material You colors instead of the Taskora palette (Android 12+). */
    val dynamicColor: Boolean = false,
    /** Which days' tasks the lists show; the same for every list. */
    val dayFilter: DayFilter = DayFilter.ALL,
) {
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
}
