package io.github.jameslunaa.taskora

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.data.settings.DataStoreSettingsRepository
import io.github.jameslunaa.taskora.reminders.NotifySendNotifier
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.app_name
import io.github.jameslunaa.taskora.resources.inbox
import io.github.jameslunaa.taskora.resources.open_taskora
import io.github.jameslunaa.taskora.resources.quit
import io.github.jameslunaa.taskora.resources.taskora_logo
import io.github.jameslunaa.taskora.system.XdgAutostart
import io.github.jameslunaa.taskora.ui.AppShortcuts
import io.github.jameslunaa.taskora.ui.TaskoraApp
import io.github.jameslunaa.taskora.ui.theme.SystemTheme
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.awt.SystemTray
import java.io.File

fun main(args: Array<String>) {
    val dataDir = dataDir()
    val database = TaskoraDatabase.create(
        file = File(dataDir, TaskoraDatabase.NAME),
        inboxName = runBlocking { getString(Res.string.inbox) },
    )
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = DataStoreSettingsRepository.create(File(dataDir, "settings.preferences_pb"), appScope)
    val container = AppContainer(
        database,
        settingsRepository,
        NotifySendNotifier(iconPath = notificationIcon(dataDir)?.path),
        scope = appScope,
    )
    // Alarms only live in memory, so every future reminder is registered again at each start.
    appScope.launch { container.taskRepository.rescheduleReminders() }

    val trayAvailable = SystemTray.isSupported()
    // Autostart launches with --minimized, so the app waits in the tray.
    val startHidden = XdgAutostart.MINIMIZED_ARG in args && trayAvailable

    application {
        val shortcuts = remember { AppShortcuts() }
        var windowVisible by remember { mutableStateOf(!startHidden) }
        // Bumped to bring the window to the front, e.g. from a notification.
        var raiseRequests by remember { mutableIntStateOf(0) }
        val settings by settingsRepository.settings.collectAsState(initial = null)
        val show: () -> Unit = {
            windowVisible = true
            raiseRequests += 1
        }
        val quit = {
            database.close()
            exitApplication()
        }

        LaunchedEffect(Unit) { container.reminders.openRequests.collect { show() } }

        if (trayAvailable) {
            Tray(
                icon = painterResource(Res.drawable.taskora_logo),
                tooltip = stringResource(Res.string.app_name),
                onAction = show,
                menu = {
                    Item(stringResource(Res.string.open_taskora), onClick = show)
                    Separator()
                    Item(stringResource(Res.string.quit), onClick = quit)
                },
            )
        }

        Window(
            onCloseRequest = {
                // Reminders only fire while Taskora runs, so closing hides it in the tray by default.
                if (trayAvailable && settings?.closeToTray != false) windowVisible = false else quit()
            },
            visible = windowVisible,
            state = rememberWindowState(width = 1200.dp, height = 780.dp),
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.taskora_logo),
            onKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                when {
                    event.isCtrlPressed && event.key == Key.F -> {
                        shortcuts.openSearch()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.N -> {
                        shortcuts.addFieldFocus.requestFocus()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Q -> {
                        quit()
                        true
                    }
                    event.key == Key.Escape -> {
                        shortcuts.escape()
                        true
                    }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(880, 560) }
            LaunchedEffect(raiseRequests) {
                if (raiseRequests > 0) {
                    window.toFront()
                    window.requestFocus()
                }
            }
            val fallbackDark = isSystemInDarkTheme()
            val systemDark by remember { SystemTheme.observeIsDark(fallbackDark) }.collectAsState(initial = fallbackDark)
            // Wait for the stored settings, so a light-theme user doesn't see a dark flash.
            val current = settings ?: return@Window
            TaskoraTheme(darkTheme = current.isDark(systemDark)) {
                TaskoraApp(container, shortcuts)
            }
        }
    }
}

/** The logo as a file, for notify-send, which takes an icon path. */
private fun notificationIcon(dataDir: File): File? = runCatching {
    File(dataDir, "icon.png").apply {
        if (!exists()) {
            parentFile.mkdirs()
            writeBytes(runBlocking { Res.readBytes("drawable/taskora_logo.png") })
        }
    }
}.getOrNull()
