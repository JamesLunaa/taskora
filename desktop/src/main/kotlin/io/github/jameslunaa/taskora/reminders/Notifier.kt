package io.github.jameslunaa.taskora.reminders

import io.github.jameslunaa.taskora.system.Autostart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Duration

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
            // Lets GNOME and KDE tie the notification to Taskora's menu entry and icon.
            add("--hint=string:desktop-entry:${Autostart.APP_ID}")
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

/**
 * Notifications as tray-icon messages, which Windows shows as toasts. They can't carry buttons,
 * so the only action is opening the task: Windows reports a click on the message as the tray
 * icon's action, which the tray passes to [onTrayAction]. Main posts [messages] through the tray.
 */
class TrayNotifier(
    override val isAvailable: Boolean,
    /** How long after a message a tray action still counts as a click on it. */
    private val clickWindow: Duration = Duration.ofSeconds(15),
) : Notifier {
    private val _messages = MutableSharedFlow<NotificationContent>(extraBufferCapacity = 8)
    val messages: SharedFlow<NotificationContent> = _messages.asSharedFlow()

    /** Completes with true on a click, or false when a newer message replaces it. */
    private var pendingClick: CompletableDeferred<Boolean>? = null

    /** Returns true if the action was a click on the latest message. */
    @Synchronized
    fun onTrayAction(): Boolean {
        val pending = pendingClick ?: return false
        pendingClick = null
        return pending.complete(true)
    }

    override suspend fun show(content: NotificationContent): NotificationAction? {
        // Only the latest message can be clicked, since the tray doesn't say which one it was.
        val click = CompletableDeferred<Boolean>()
        synchronized(this) {
            pendingClick?.complete(false)
            pendingClick = click
        }
        _messages.emit(content)
        val clicked = withTimeoutOrNull(clickWindow.toMillis()) { click.await() }
        synchronized(this) { if (pendingClick === click) pendingClick = null }
        return if (clicked == true) NotificationAction.OPEN else null
    }
}
