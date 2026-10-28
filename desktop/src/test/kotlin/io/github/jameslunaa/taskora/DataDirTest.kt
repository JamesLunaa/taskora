package io.github.jameslunaa.taskora

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DataDirTest {
    private val home = File("/home/someone")

    @Test
    fun linux_usesXdgDataHome_orLocalShare() {
        assertEquals(File("/data/taskora"), dataDir(windows = false, env = { if (it == "XDG_DATA_HOME") "/data" else null }, home = home))
        assertEquals(File(home, ".local/share/taskora"), dataDir(windows = false, env = { "" }, home = home))
    }

    @Test
    fun windows_usesLocalAppData() {
        assertEquals(File("C:/Users/someone/AppData/Local/Taskora"), dataDir(windows = true, env = { if (it == "LOCALAPPDATA") "C:/Users/someone/AppData/Local" else null }, home = home))
        assertEquals(File(home, "AppData/Local/Taskora"), dataDir(windows = true, env = { null }, home = home))
    }
}
