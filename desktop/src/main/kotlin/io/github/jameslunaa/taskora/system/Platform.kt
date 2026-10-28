package io.github.jameslunaa.taskora.system

/** The operating system Taskora runs on; Linux is the default, Windows has its own integrations. */
object Platform {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
}
