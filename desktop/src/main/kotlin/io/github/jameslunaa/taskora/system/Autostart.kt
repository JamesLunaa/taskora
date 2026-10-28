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
 * installed app's executable (jpackage sets `jpackage.app-path`); the app starts hidden in the tray.
 */
class XdgAutostart(
    private val autostartDir: File = defaultAutostartDir(),
    private val launcher: String? = System.getProperty("jpackage.app-path"),
) : Autostart {
    private val entry = File(autostartDir, "io.github.jameslunaa.taskora.desktop")

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
            Icon=taskora
            Terminal=false
            X-GNOME-Autostart-enabled=true
            """.trimIndent() + "\n",
        )
    }

    companion object {
        const val MINIMIZED_ARG = "--minimized"

        fun defaultAutostartDir(): File {
            val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            return File(xdg?.let(::File) ?: File(System.getProperty("user.home"), ".config"), "autostart")
        }
    }
}
