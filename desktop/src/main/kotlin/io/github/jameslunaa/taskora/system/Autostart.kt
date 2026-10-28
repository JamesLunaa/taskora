package io.github.jameslunaa.taskora.system

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/** Starting Taskora when the user logs in. */
interface Autostart {
    /** False when there's no installed launcher to start, e.g. when run from Gradle. */
    val isAvailable: Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)

    companion object {
        /** Autostart launches with it, so the app starts hidden in the tray. */
        const val MINIMIZED_ARG = "--minimized"

        /** Also the .desktop entry's name, the icon name and the X11 window class. */
        const val APP_ID = "io.github.jameslunaa.taskora"

        /**
         * An AppImage runs from a new mount point each time, so it's the AppImage file itself
         * (`APPIMAGE`); otherwise jpackage's launcher (`jpackage.app-path`, `Taskora.exe` on
         * Windows). Null when run from Gradle.
         */
        fun installedLauncher(): String? =
            System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() } ?: System.getProperty("jpackage.app-path")

        fun forThisPlatform(): Autostart = if (Platform.isWindows) WindowsAutostart() else XdgAutostart()
    }
}

/**
 * The freedesktop way: a `.desktop` entry in `$XDG_CONFIG_HOME/autostart`. [launcher] is the
 * installed app's executable; the app starts hidden in the tray.
 */
class XdgAutostart(
    private val autostartDir: File = defaultAutostartDir(),
    private val launcher: String? = Autostart.installedLauncher(),
) : Autostart {
    private val entry = File(autostartDir, "${Autostart.APP_ID}.desktop")

    override val isAvailable: Boolean get() = launcher != null

    override fun isEnabled(): Boolean = entry.exists()

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            entry.delete()
            return
        }
        val exec = launcher ?: return
        autostartDir.mkdirs()
        entry.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Taskora
            Comment=Offline to-do and task lists
            Exec="${exec.replace("\"", "\\\"")}" ${Autostart.MINIMIZED_ARG}
            Icon=${Autostart.APP_ID}
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n",
        )
    }

    companion object {
        fun defaultAutostartDir(): File {
            val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            return File(xdg?.let(::File) ?: File(System.getProperty("user.home"), ".config"), "autostart")
        }
    }
}

/** The values under a registry key; [WindowsAutostart] uses the user's `Run` key. */
interface RegistryValues {
    fun get(name: String): String?
    fun set(name: String, value: String)
    fun delete(name: String)
}

/**
 * The Windows way: a value under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, which
 * Settings → Apps → Startup also lists. [launcher] is the installed `Taskora.exe`.
 */
class WindowsAutostart(
    private val runKey: RegistryValues = CurrentUserRunKey,
    private val launcher: String? = Autostart.installedLauncher(),
) : Autostart {
    override val isAvailable: Boolean get() = launcher != null

    override fun isEnabled(): Boolean = runKey.get(VALUE_NAME) != null

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            runKey.delete(VALUE_NAME)
            return
        }
        val exe = launcher ?: return
        runKey.set(VALUE_NAME, "\"$exe\" ${Autostart.MINIMIZED_ARG}")
    }

    private object CurrentUserRunKey : RegistryValues {
        private const val KEY = """Software\Microsoft\Windows\CurrentVersion\Run"""

        override fun get(name: String): String? = runCatching {
            if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, name)) return null
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, KEY, name)
        }.getOrNull()

        override fun set(name: String, value: String) =
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, name, value)

        override fun delete(name: String) {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, name)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, name)
            }
        }
    }

    companion object {
        const val VALUE_NAME = "Taskora"
    }
}
