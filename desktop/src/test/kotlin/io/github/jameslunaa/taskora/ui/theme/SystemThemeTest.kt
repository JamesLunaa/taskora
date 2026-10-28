package io.github.jameslunaa.taskora.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SystemThemeTest {
    @Test
    fun parsesDarkAndAccent() {
        val output = "({'org.freedesktop.appearance': {'contrast': <uint32 0>, 'color-scheme': <uint32 1>, " +
            "'accent-color': <(0.20784313725490197, 0.51764705882352946, 0.89411764705882357)>}},)"
        val appearance = SystemTheme.parsePortal(output)
        assertEquals(true, appearance.isDark)
        assertEquals(Color(0.20784314f, 0.5176471f, 0.89411765f), appearance.accent)
    }

    @Test
    fun missingOrUnsetValues_areNull() {
        assertEquals(SystemAppearance(), SystemTheme.parsePortal("Error: GDBus.Error:org.freedesktop.DBus.Error.ServiceUnknown"))
        // Light, with an out-of-range accent (unset).
        val appearance = SystemTheme.parsePortal("({'org.freedesktop.appearance': {'color-scheme': <uint32 2>, 'accent-color': <(-1.0, -1.0, -1.0)>}},)")
        assertEquals(SystemAppearance(isDark = false), appearance)
    }

    @Test
    fun accentTones_keepTheHue() {
        // GNOME's blue (#3584e4).
        val tones = AccentTones(Color(0xFF3584E4))
        val light = tones.tone(0.77f)
        assertTrue(light.blue > light.red && light.blue > light.green)
        val lightness = (maxOf(light.red, light.green, light.blue) + minOf(light.red, light.green, light.blue)) / 2
        assertTrue(abs(lightness - 0.77f) < 0.01f)
    }
}
