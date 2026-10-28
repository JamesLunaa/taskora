package io.github.jameslunaa.taskora.ui.theme

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.jameslunaa.taskora.system.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.TimeUnit

/**
 * The desktop's light/dark preference, read from the XDG desktop portal (`org.freedesktop.appearance
 * color-scheme`: 1 = dark, 2 = light, 0 = no preference). KDE Plasma and GNOME both provide it.
 * Compose's isSystemInDarkTheme() doesn't know about it on Linux. On Windows it's the "app mode"
 * in the registry.
 */
object SystemTheme {
    private const val POLL_MILLIS = 5_000L

    /** Null when the portal isn't available or has no preference. */
    fun readIsDark(): Boolean? = if (Platform.isWindows) readWindowsIsDark() else readPortalIsDark()

    /** `AppsUseLightTheme`: 0 = dark, 1 = light; missing on old Windows 10 builds. */
    private fun readWindowsIsDark(): Boolean? = runCatching {
        val key = """Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"""
        if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "AppsUseLightTheme")) return null
        Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, key, "AppsUseLightTheme") == 0
    }.getOrNull()

    private fun readPortalIsDark(): Boolean? {
        val output = runCatching {
            val process = ProcessBuilder(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.portal.Desktop",
                "--object-path", "/org/freedesktop/portal/desktop",
                "--method", "org.freedesktop.portal.Settings.ReadOne",
                "org.freedesktop.appearance", "color-scheme",
            ).redirectErrorStream(true).start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            process.inputStream.bufferedReader().readText()
        }.getOrNull() ?: return null
        // e.g. "(<uint32 1>,)"
        return when (Regex("""uint32 (\d)""").find(output)?.groupValues?.get(1)) {
            "1" -> true
            "2" -> false
            else -> null
        }
    }

    /** Polls the setting, so switching the desktop theme is picked up within a few seconds. */
    fun observeIsDark(fallback: Boolean): Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            emit(readIsDark() ?: fallback)
            kotlinx.coroutines.delay(POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
}
