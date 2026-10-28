package io.github.jameslunaa.taskora.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsAutostartTest {
    private class FakeRunKey : RegistryValues {
        val values = mutableMapOf<String, String>()
        override fun get(name: String) = values[name]
        override fun set(name: String, value: String) {
            values[name] = value
        }
        override fun delete(name: String) {
            values.remove(name)
        }
    }

    @Test
    fun writesAndRemovesRunValue() {
        val runKey = FakeRunKey()
        val autostart = WindowsAutostart(runKey, launcher = """C:\Program Files\Taskora\Taskora.exe""")
        assertFalse(autostart.isEnabled())

        autostart.setEnabled(true)
        assertTrue(autostart.isEnabled())
        assertEquals("\"C:\\Program Files\\Taskora\\Taskora.exe\" --minimized", runKey.values["Taskora"])

        autostart.setEnabled(false)
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun unavailableWithoutLauncher() {
        val autostart = WindowsAutostart(FakeRunKey(), launcher = null)
        assertFalse(autostart.isAvailable)
        autostart.setEnabled(true)
        assertFalse(autostart.isEnabled())
    }
}
