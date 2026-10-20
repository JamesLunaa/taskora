package com.example.taskora.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Wallpaper-based Material You colors instead of the Taskora palette (Android 12+). */
    val dynamicColor: Boolean = false,
) {
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
}
