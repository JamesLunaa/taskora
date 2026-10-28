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
import io.github.jameslunaa.taskora.system.Platform
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
    val autostart: Autostart = Autostart.forThisPlatform(),
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

/**
 * Per-user data folder: `%LOCALAPPDATA%\Taskora` on Windows, otherwise `$XDG_DATA_HOME/taskora`
 * falling back to `~/.local/share/taskora`.
 */
fun dataDir(
    windows: Boolean = Platform.isWindows,
    env: (String) -> String? = System::getenv,
    home: File = File(System.getProperty("user.home")),
): File {
    fun variable(name: String) = env(name)?.takeIf { it.isNotBlank() }?.let(::File)
    return if (windows) {
        File(variable("LOCALAPPDATA") ?: File(home, "AppData/Local"), "Taskora")
    } else {
        File(variable("XDG_DATA_HOME") ?: File(home, ".local/share"), "taskora")
    }
}
