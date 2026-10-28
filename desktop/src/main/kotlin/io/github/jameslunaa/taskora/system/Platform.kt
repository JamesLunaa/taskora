package io.github.jameslunaa.taskora.system

/** The operating system Taskora runs on; Linux is the default, Windows has its own integrations. */
object Platform {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** GNOME (also Ubuntu's "ubuntu:GNOME"): no tray without an extension, and no window opacity. */
    val isGnome: Boolean = !isWindows && isGnome(System.getenv("XDG_CURRENT_DESKTOP"))

    /** `XDG_CURRENT_DESKTOP` is a colon-separated list of desktop names. */
    fun isGnome(currentDesktop: String?): Boolean =
        currentDesktop.orEmpty().split(':').any { it.equals("GNOME", ignoreCase = true) }
}
