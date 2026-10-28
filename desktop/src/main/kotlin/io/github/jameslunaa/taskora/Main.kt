package io.github.jameslunaa.taskora

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.app_name
import io.github.jameslunaa.taskora.resources.inbox
import io.github.jameslunaa.taskora.resources.taskora_logo
import io.github.jameslunaa.taskora.ui.AppShortcuts
import io.github.jameslunaa.taskora.ui.TaskoraApp
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.io.File

fun main() {
    val database = TaskoraDatabase.create(
        file = File(dataDir(), TaskoraDatabase.NAME),
        inboxName = runBlocking { getString(Res.string.inbox) },
    )
    val container = AppContainer(database)

    application {
        val shortcuts = remember { AppShortcuts() }
        Window(
            onCloseRequest = {
                database.close()
                exitApplication()
            },
            state = rememberWindowState(width = 1200.dp, height = 780.dp),
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.taskora_logo),
            onKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                when {
                    event.isCtrlPressed && event.key == Key.N -> {
                        shortcuts.addFieldFocus.requestFocus()
                        true
                    }
                    event.key == Key.Escape -> {
                        shortcuts.closeDetail()
                        true
                    }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(880, 560) }
            TaskoraTheme {
                TaskoraApp(container, shortcuts)
            }
        }
    }
}
