package io.github.jameslunaa.taskora

import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.data.repository.OfflineTaskListRepository
import io.github.jameslunaa.taskora.data.repository.OfflineTaskRepository
import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.reminders.NoReminderScheduler
import java.io.File
import java.time.Clock

/** Manual dependency wiring: the desktop app is small enough not to need a DI framework. */
class AppContainer(database: TaskoraDatabase, val clock: Clock = Clock.systemDefaultZone()) {
    val taskListRepository: TaskListRepository = OfflineTaskListRepository(database.taskListDao())
    val taskRepository: TaskRepository = OfflineTaskRepository(
        database.taskDao(),
        database.subtaskDao(),
        NoReminderScheduler,
        clock,
    )
}

/** Per-user data folder: $XDG_DATA_HOME/taskora, falling back to ~/.local/share/taskora. */
fun dataDir(): File {
    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
    val base = xdg?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "taskora")
}
