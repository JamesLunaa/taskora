package io.github.jameslunaa.taskora.ui.theme

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
 * Compose's isSystemInDarkTheme() doesn't know about it on Linux.
 */
object SystemTheme {
    private const val POLL_MILLIS = 5_000L

    /** Null when the portal isn't available or has no preference. */
    fun readIsDark(): Boolean? {
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

    /** Polls the portal, so switching the desktop theme is picked up within a few seconds. */
    fun observeIsDark(fallback: Boolean): Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            emit(readIsDark() ?: fallback)
            kotlinx.coroutines.delay(POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
}
