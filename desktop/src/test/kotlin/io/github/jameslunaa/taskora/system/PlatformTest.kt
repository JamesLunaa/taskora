package io.github.jameslunaa.taskora.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTest {
    @Test
    fun detectsGnome() {
        assertTrue(Platform.isGnome("GNOME"))
        assertTrue(Platform.isGnome("ubuntu:GNOME"))
        assertFalse(Platform.isGnome("KDE"))
        assertFalse(Platform.isGnome("GNOME-Flashback-ish"))
        assertFalse(Platform.isGnome(null))
    }
}
