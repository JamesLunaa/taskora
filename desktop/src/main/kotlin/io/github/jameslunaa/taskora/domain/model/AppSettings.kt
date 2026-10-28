package io.github.jameslunaa.taskora.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Closing the window hides it in the system tray instead of quitting (when there is a tray). */
    val closeToTray: Boolean = true,
    val mainWindow: WindowLook = WindowLook(),
    /** A sticky note is pinned on top until the user says otherwise. */
    val noteWindow: WindowLook = WindowLook(level = WindowLevel.ABOVE),
    val note: NoteSettings = NoteSettings(),
) {
    fun look(window: AppWindow): WindowLook = when (window) {
        AppWindow.MAIN -> mainWindow
        AppWindow.NOTE -> noteWindow
    }

    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
}
