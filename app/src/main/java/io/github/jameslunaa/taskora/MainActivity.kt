package io.github.jameslunaa.taskora

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.ui.navigation.TaskoraApp
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Draw nothing until settings load (a few ms), so a light theme doesn't flash dark.
            val settings = settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null).value
                ?: return@setContent
            val darkTheme = settings.isDark(systemDark = isSystemInDarkTheme())
            DisposableEffect(darkTheme) {
                // The status bar sits on the header band, which is dark in both themes.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }
            TaskoraTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                TaskoraApp()
            }
        }
    }
}
