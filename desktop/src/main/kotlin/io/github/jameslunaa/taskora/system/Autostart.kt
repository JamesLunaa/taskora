package io.github.jameslunaa.taskora.system

import java.io.File

/** Starting Taskora when the user logs in. */
interface Autostart {
    /** False when there's no installed launcher to start, e.g. when run from Gradle. */
    val isAvailable: Boolean
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}

/**
 * The freedesktop way: a `.desktop` entry in `$XDG_CONFIG_HOME/autostart`. [launcher] is the
 * installed app's executable; the app starts hidden in the tray.
 */
class XdgAutostart(
    private val autostartDir: File = defaultAutostartDir(),
    private val launcher: String? = installedLauncher(),
) : Autostart {
    private val entry = File(autostartDir, "$APP_ID.desktop")

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
            Exec="${exec.replace("\"", "\\\"")}" $MINIMIZED_ARG
            Icon=$APP_ID
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n",
        )
    }

    companion object {
        const val MINIMIZED_ARG = "--minimized"

        /** Also the .desktop entry's name, the icon name and the X11 window class. */
        const val APP_ID = "io.github.jameslunaa.taskora"

        /**
         * An AppImage runs from a new mount point each time, so it's the AppImage file itself
         * (`APPIMAGE`); otherwise jpackage's launcher (`jpackage.app-path`). Null when run from Gradle.
         */
        fun installedLauncher(): String? =
            System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() } ?: System.getProperty("jpackage.app-path")

        fun defaultAutostartDir(): File {
            val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            return File(xdg?.let(::File) ?: File(System.getProperty("user.home"), ".config"), "autostart")
        }
    }
}
