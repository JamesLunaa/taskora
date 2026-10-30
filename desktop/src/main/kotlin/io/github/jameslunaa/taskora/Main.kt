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
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.data.settings.DataStoreSettingsRepository
import io.github.jameslunaa.taskora.reminders.NotifySendNotifier
import io.github.jameslunaa.taskora.reminders.TrayNotifier
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.app_name
import io.github.jameslunaa.taskora.resources.inbox
import io.github.jameslunaa.taskora.resources.open_taskora
import io.github.jameslunaa.taskora.resources.quit
import io.github.jameslunaa.taskora.resources.taskora_logo
import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.resources.sticky_note
import io.github.jameslunaa.taskora.system.WindowHints
import io.github.jameslunaa.taskora.system.Autostart
import io.github.jameslunaa.taskora.system.Platform
import io.github.jameslunaa.taskora.system.SingleInstance
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.ui.theme.SystemAppearance
import io.github.jameslunaa.taskora.ui.common.scopedViewModel
import io.github.jameslunaa.taskora.ui.note.StickyNoteWindow
import io.github.jameslunaa.taskora.ui.window.ApplyWindowLook
import io.github.jameslunaa.taskora.ui.window.StickyNoteMenuItem
import io.github.jameslunaa.taskora.ui.window.WindowLookActions
import io.github.jameslunaa.taskora.ui.window.WindowLookButton
import io.github.jameslunaa.taskora.ui.window.WindowsViewModel
import io.github.jameslunaa.taskora.ui.AppShortcuts
import io.github.jameslunaa.taskora.ui.TaskoraApp
import io.github.jameslunaa.taskora.ui.theme.SystemTheme
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import io.github.jameslunaa.taskora.system.tray.SniTray
import io.github.jameslunaa.taskora.system.tray.TrayItem
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import java.awt.SystemTray
import java.awt.Toolkit
import java.io.File

fun main(args: Array<String>) {
    val minimized = Autostart.MINIMIZED_ARG in args
    val dataDir = dataDir()
    // Taskora already running: it shows its window (unless this is autostart) and this copy ends.
    val instance = SingleInstance.claim(dataDir, showWindow = !minimized) ?: return
    setWindowClass(Autostart.APP_ID)
    val database = TaskoraDatabase.create(
        file = File(dataDir, TaskoraDatabase.NAME),
        inboxName = runBlocking { getString(Res.string.inbox) },
    )
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository = DataStoreSettingsRepository.create(
        File(dataDir, "settings.preferences_pb"),
        appScope,
        // GNOME apps follow its accent color; elsewhere Taskora keeps its purple unless asked.
        defaults = AppSettings(useSystemAccent = Platform.isGnome),
    )
    // Linux: a StatusNotifierItem the desktop draws itself, when a tray host runs; otherwise
    // (and on Windows) AWT's tray.
    val nativeTray = if (Platform.isWindows) null else trayLogo()?.let { SniTray.start(runBlocking { getString(Res.string.app_name) }, it) }
    val trayAvailable = nativeTray != null || SystemTray.isSupported()
    // Windows has no notify-send; its toasts come from the tray icon.
    val trayNotifier = if (Platform.isWindows) TrayNotifier(isAvailable = trayAvailable) else null
    val container = AppContainer(
        database,
        settingsRepository,
        trayNotifier ?: NotifySendNotifier(iconPath = notificationIcon(dataDir)?.path),
        scope = appScope,
    )
    // Alarms only live in memory, so every future reminder is registered again at each start.
    appScope.launch { container.taskRepository.rescheduleReminders() }

    // Autostart launches with --minimized, so the app waits in the tray (or, with no tray, until
    // Taskora is opened again).
    val startHidden = minimized
    val hints = WindowHints.forThisDesktop()
    // Tasks clicked on the sticky note, to open in the main window.
    val noteOpenRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    application {
        val shortcuts = remember { AppShortcuts() }
        var windowVisible by remember { mutableStateOf(!startHidden) }
        // Bumped to bring the window to the front, e.g. from a notification.
        var raiseRequests by remember { mutableIntStateOf(0) }
        val windows = scopedViewModel("windows") { WindowsViewModel(settingsRepository) }
        val settings by windows.settings.collectAsState()
        val fallbackDark = isSystemInDarkTheme()
        val appearance by remember { SystemTheme.observe() }.collectAsState(initial = SystemAppearance())
        val systemDark = appearance.isDark ?: fallbackDark
        val accent = appearance.accent?.takeIf { settings?.useSystemAccent == true }
        val show: () -> Unit = {
            windowVisible = true
            raiseRequests += 1
        }
        val quit = {
            nativeTray?.close()
            instance.close()
            database.close()
            exitApplication()
        }

        LaunchedEffect(Unit) { container.reminders.openRequests.collect { show() } }
        LaunchedEffect(Unit) { instance.showRequests.collect { show() } }

        if (nativeTray != null) {
            val noteOpen = settings?.note?.isOpen == true
            val currentNoteOpen by rememberUpdatedState(noteOpen)
            val menu = listOf(
                TrayItem.Action(TRAY_OPEN, stringResource(Res.string.open_taskora)),
                TrayItem.Toggle(TRAY_NOTE, stringResource(Res.string.sticky_note), noteOpen),
                TrayItem.Separator,
                TrayItem.Action(TRAY_QUIT, stringResource(Res.string.quit)),
            )
            LaunchedEffect(menu) { nativeTray.setMenu(menu) }
            LaunchedEffect(Unit) {
                nativeTray.events.collect { key ->
                    when (key) {
                        SniTray.ACTIVATE, TRAY_OPEN -> show()
                        TRAY_NOTE -> windows.setNoteOpen(!currentNoteOpen)
                        TRAY_QUIT -> quit()
                    }
                }
            }
        } else if (trayAvailable) {
            val trayState = rememberTrayState()
            if (trayNotifier != null) {
                LaunchedEffect(Unit) {
                    trayNotifier.messages.collect {
                        trayState.sendNotification(Notification(it.title, it.body, Notification.Type.Info))
                    }
                }
            }
            Tray(
                icon = painterResource(Res.drawable.taskora_logo),
                state = trayState,
                tooltip = stringResource(Res.string.app_name),
                // On Windows a click on a reminder's message arrives here too; that opens its task.
                onAction = {
                    trayNotifier?.onTrayAction()
                    show()
                },
                menu = {
                    Item(stringResource(Res.string.open_taskora), onClick = show)
                    CheckboxItem(
                        stringResource(Res.string.sticky_note),
                        checked = settings?.note?.isOpen == true,
                        onCheckedChange = windows::setNoteOpen,
                    )
                    Separator()
                    Item(stringResource(Res.string.quit), onClick = quit)
                },
            )
        }

        val mainLook = settings?.mainWindow ?: WindowLook()
        Window(
            onCloseRequest = {
                // Reminders only fire while Taskora runs, so closing hides it in the tray by default.
                // Without a tray, opening Taskora again brings the window back.
                if (settings?.closeToTray != false) windowVisible = false else quit()
            },
            visible = windowVisible,
            state = rememberWindowState(width = 1200.dp, height = 780.dp),
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.taskora_logo),
            alwaysOnTop = mainLook.level == WindowLevel.ABOVE,
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
            ApplyWindowLook(mainLook, windowVisible, hints)
            // Wait for the stored settings, so a light-theme user doesn't see a dark flash.
            val current = settings ?: return@Window
            TaskoraTheme(darkTheme = current.isDark(systemDark), accent = accent) {
                TaskoraApp(
                    container,
                    shortcuts,
                    openTaskRequests = noteOpenRequests,
                    trayAvailable = trayAvailable,
                    systemAccentAvailable = appearance.accent != null,
                    windowButton = {
                        WindowLookButton(
                            look = current.mainWindow,
                            capabilities = hints.capabilities,
                            actions = remember(windows) {
                                WindowLookActions(
                                    onLevelChange = { windows.setLevel(AppWindow.MAIN, it) },
                                    onOpacityChange = { windows.previewOpacity(AppWindow.MAIN, it) },
                                    onOpacityChangeFinished = { windows.commitOpacity(AppWindow.MAIN) },
                                )
                            },
                        ) { dismiss ->
                            StickyNoteMenuItem(open = current.note.isOpen) {
                                windows.setNoteOpen(!current.note.isOpen)
                                dismiss()
                            }
                        }
                    },
                )
            }
        }

        settings?.let { current ->
            StickyNoteWindow(
                container = container,
                windows = windows,
                settings = current,
                hints = hints,
                darkTheme = current.isDark(systemDark),
                accent = accent,
                onOpenTask = {
                    noteOpenRequests.tryEmit(it)
                    show()
                },
            )
        }
    }
}

/**
 * AWT names every window's X11 class after the main class. Taskbars and docks match windows to
 * their .desktop entry by it, so it should be the entry's name. Needs
 * `--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED` (set in build.gradle.kts); without it the
 * default name stays.
 */
private fun setWindowClass(name: String) {
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(null, name)
    }
}

private const val TRAY_OPEN = "open"
private const val TRAY_NOTE = "note"
private const val TRAY_QUIT = "quit"

private fun trayLogo(): BufferedImage? =
    runCatching { ImageIO.read(ByteArrayInputStream(runBlocking { Res.readBytes("drawable/taskora_logo.png") })) }.getOrNull()

/** The logo as a file, for notify-send, which takes an icon path. */
private fun notificationIcon(dataDir: File): File? = runCatching {
    File(dataDir, "icon.png").apply {
        if (!exists()) {
            parentFile.mkdirs()
            writeBytes(runBlocking { Res.readBytes("drawable/taskora_logo.png") })
        }
    }
}.getOrNull()
