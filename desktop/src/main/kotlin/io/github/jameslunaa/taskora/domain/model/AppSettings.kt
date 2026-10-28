package io.github.jameslunaa.taskora.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Closing the window hides it in the system tray instead of quitting (when there is a tray). */
    val closeToTray: Boolean = true,
) {
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
}
