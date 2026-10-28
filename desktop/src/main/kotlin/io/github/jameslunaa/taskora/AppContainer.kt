package io.github.jameslunaa.taskora

import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.data.repository.OfflineTaskListRepository
import io.github.jameslunaa.taskora.data.repository.OfflineTaskRepository
import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.reminders.DesktopReminderScheduler
import io.github.jameslunaa.taskora.reminders.Notifier
import io.github.jameslunaa.taskora.reminders.ReminderNotifications
import io.github.jameslunaa.taskora.system.Autostart
import io.github.jameslunaa.taskora.system.XdgAutostart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock

/** Manual dependency wiring: the desktop app is small enough not to need a DI framework. */
class AppContainer(
    database: TaskoraDatabase,
    val settingsRepository: SettingsRepository,
    notifier: Notifier,
    val autostart: Autostart = XdgAutostart(),
    val clock: Clock = Clock.systemDefaultZone(),
    /** Lives as long as the app, for work that outlasts any one screen (reminders). */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val reminderScheduler = DesktopReminderScheduler(scope, clock)

    val taskListRepository: TaskListRepository = OfflineTaskListRepository(database.taskListDao())
    val taskRepository: TaskRepository = OfflineTaskRepository(
        database.taskDao(),
        database.subtaskDao(),
        reminderScheduler,
        clock,
    )
    val reminders = ReminderNotifications(taskRepository, notifier, scope, clock)

    init {
        reminderScheduler.onFire = reminders::fire
    }
}

/** Per-user data folder: $XDG_DATA_HOME/taskora, falling back to ~/.local/share/taskora. */
fun dataDir(): File {
    val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
    val base = xdg?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "taskora")
}
