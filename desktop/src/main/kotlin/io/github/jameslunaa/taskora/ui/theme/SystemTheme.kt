package io.github.jameslunaa.taskora.ui.theme

import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.github.jameslunaa.taskora.system.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit

/** The desktop's appearance preferences; null where the desktop doesn't say. */
data class SystemAppearance(val isDark: Boolean? = null, val accent: Color? = null)

/**
 * The desktop's light/dark preference and accent color, read from the XDG desktop portal
 * (`org.freedesktop.appearance`: `color-scheme` 1 = dark, 2 = light, 0 = no preference;
 * `accent-color` as RGB from 0 to 1). GNOME and KDE Plasma both provide it. Compose's
 * isSystemInDarkTheme() doesn't know about it on Linux. On Windows it's the "app mode" in the
 * registry (no accent).
 */
object SystemTheme {
    private const val POLL_MILLIS = 5_000L

    fun read(): SystemAppearance =
        if (Platform.isWindows) SystemAppearance(isDark = readWindowsIsDark()) else readPortal()?.let(::parsePortal) ?: SystemAppearance()

    /** `AppsUseLightTheme`: 0 = dark, 1 = light; missing on old Windows 10 builds. */
    private fun readWindowsIsDark(): Boolean? = runCatching {
        val key = """Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"""
        if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "AppsUseLightTheme")) return null
        Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, key, "AppsUseLightTheme") == 0
    }.getOrNull()

    private fun readPortal(): String? = runCatching {
        val process = ProcessBuilder(
            "gdbus", "call", "--session",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", "/org/freedesktop/portal/desktop",
            "--method", "org.freedesktop.portal.Settings.ReadAll",
            "['org.freedesktop.appearance']",
        ).redirectErrorStream(true).start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroy()
            return null
        }
        process.inputStream.bufferedReader().readText()
    }.getOrNull()

    /**
     * Parses gdbus's text output, e.g.
     * `({'org.freedesktop.appearance': {'color-scheme': <uint32 1>, 'accent-color': <(0.2, 0.5, 0.9)>}},)`.
     * An accent outside 0..1 means "not set".
     */
    fun parsePortal(output: String): SystemAppearance {
        val isDark = when (Regex("""'color-scheme': <uint32 (\d)>""").find(output)?.groupValues?.get(1)) {
            "1" -> true
            "2" -> false
            else -> null
        }
        val number = """(-?[\d.]+(?:e-?\d+)?)"""
        val accent = Regex("""'accent-color': <\($number, $number, $number\)>""").find(output)
            ?.groupValues?.drop(1)?.map { it.toFloatOrNull() ?: -1f }
            ?.takeIf { rgb -> rgb.all { it in 0f..1f } }
            ?.let { (r, g, b) -> Color(r, g, b) }
        return SystemAppearance(isDark, accent)
    }

    /** Polls the settings, so changing them on the desktop is picked up within a few seconds. */
    fun observe(): Flow<SystemAppearance> = flow {
        while (currentCoroutineContext().isActive) {
            emit(read())
            delay(POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
}
