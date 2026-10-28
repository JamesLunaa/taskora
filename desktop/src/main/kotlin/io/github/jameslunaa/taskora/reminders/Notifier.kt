package io.github.jameslunaa.taskora.reminders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class NotificationAction { COMPLETE, SNOOZE, OPEN }

data class NotificationContent(
    val title: String,
    val body: String,
    val completeLabel: String,
    val snoozeLabel: String,
    val openLabel: String,
)

interface Notifier {
    val isAvailable: Boolean

    /** Shows the notification and suspends until it's closed; returns the action picked, if any. */
    suspend fun show(content: NotificationContent): NotificationAction?
}

/**
 * Desktop notifications through libnotify's `notify-send`, which talks to the freedesktop
 * notification service (KDE Plasma, GNOME and others). `--action` makes it wait and print the
 * chosen action's name; "default" is a click on the notification itself.
 */
class NotifySendNotifier(private val iconPath: String?) : Notifier {
    private val executable: File? = System.getenv("PATH").orEmpty()
        .split(File.pathSeparator)
        .map { File(it, "notify-send") }
        .firstOrNull { it.canExecute() }

    override val isAvailable: Boolean get() = executable != null

    override suspend fun show(content: NotificationContent): NotificationAction? = withContext(Dispatchers.IO) {
        val exe = executable ?: return@withContext null
        val command = buildList {
            add(exe.path)
            add("--app-name=Taskora")
            iconPath?.let { add("--icon=$it") }
            add("--action=complete=${content.completeLabel}")
            add("--action=snooze=${content.snoozeLabel}")
            add("--action=default=${content.openLabel}")
            add(content.title)
            add(content.body)
        }
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val chosen = process.inputStream.bufferedReader().readLine()?.trim()
        process.waitFor()
        when (chosen) {
            "complete" -> NotificationAction.COMPLETE
            "snooze" -> NotificationAction.SNOOZE
            "default" -> NotificationAction.OPEN
            else -> null
        }
    }
}
