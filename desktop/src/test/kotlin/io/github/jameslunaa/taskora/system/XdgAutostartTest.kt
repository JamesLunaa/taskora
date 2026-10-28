package io.github.jameslunaa.taskora.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class XdgAutostartTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writesAndRemovesDesktopEntry() {
        val dir = File(tmp.root, "autostart")
        val autostart = XdgAutostart(dir, launcher = "/opt/taskora/bin/Taskora")
        assertFalse(autostart.isEnabled())

        autostart.setEnabled(true)
        assertTrue(autostart.isEnabled())
        val entry = dir.listFiles()!!.single().readText()
        assertTrue(entry.contains("Exec=\"/opt/taskora/bin/Taskora\" --minimized"))

        autostart.setEnabled(false)
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun unavailableWithoutLauncher() {
        val autostart = XdgAutostart(File(tmp.root, "autostart"), launcher = null)
        assertFalse(autostart.isAvailable)
        autostart.setEnabled(true)
        assertFalse(autostart.isEnabled())
    }
}
