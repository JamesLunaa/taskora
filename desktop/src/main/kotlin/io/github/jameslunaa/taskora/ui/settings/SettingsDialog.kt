package io.github.jameslunaa.taskora.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.close
import io.github.jameslunaa.taskora.resources.close_to_background
import io.github.jameslunaa.taskora.resources.close_to_background_description
import io.github.jameslunaa.taskora.resources.close_to_tray
import io.github.jameslunaa.taskora.resources.close_to_tray_description
import io.github.jameslunaa.taskora.resources.font_license
import io.github.jameslunaa.taskora.resources.font_license_title
import io.github.jameslunaa.taskora.resources.license
import io.github.jameslunaa.taskora.resources.license_title
import io.github.jameslunaa.taskora.resources.settings
import io.github.jameslunaa.taskora.resources.settings_about
import io.github.jameslunaa.taskora.resources.settings_appearance
import io.github.jameslunaa.taskora.resources.settings_window
import io.github.jameslunaa.taskora.resources.start_on_login
import io.github.jameslunaa.taskora.resources.start_on_login_description
import io.github.jameslunaa.taskora.resources.start_on_login_unavailable
import io.github.jameslunaa.taskora.resources.theme_dark
import io.github.jameslunaa.taskora.resources.theme_light
import io.github.jameslunaa.taskora.resources.theme_system
import io.github.jameslunaa.taskora.resources.use_system_accent
import io.github.jameslunaa.taskora.resources.use_system_accent_description
import io.github.jameslunaa.taskora.resources.version
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.DARK -> Res.string.theme_dark
        ThemeMode.LIGHT -> Res.string.theme_light
        ThemeMode.SYSTEM -> Res.string.theme_system
    }

class SettingsActions(
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onCloseToTrayChange: (Boolean) -> Unit,
    val onUseSystemAccentChange: (Boolean) -> Unit,
    val onAutostartChange: (Boolean) -> Unit,
)

@Composable
fun SettingsDialog(
    settings: AppSettings?,
    trayAvailable: Boolean,
    /** Whether the desktop reports an accent color, so the switch can do anything. */
    systemAccentAvailable: Boolean,
    autostartAvailable: Boolean,
    autostartEnabled: Boolean,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(520.dp),
        title = { Text(stringResource(Res.string.settings)) },
        text = {
            if (settings != null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SectionHeader(Res.string.settings_appearance)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == settings.themeMode,
                                onClick = { actions.onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) { Text(stringResource(mode.label)) }
                        }
                    }
                    if (systemAccentAvailable) {
                        SwitchRow(
                            title = stringResource(Res.string.use_system_accent),
                            description = stringResource(Res.string.use_system_accent_description),
                            checked = settings.useSystemAccent,
                            enabled = true,
                            onCheckedChange = actions.onUseSystemAccentChange,
                        )
                    }

                    SectionHeader(Res.string.settings_window)
                    // Without a tray, a closed window comes back when Taskora is opened again
                    // (see SingleInstance).
                    SwitchRow(
                        title = stringResource(if (trayAvailable) Res.string.close_to_tray else Res.string.close_to_background),
                        description = stringResource(
                            if (trayAvailable) Res.string.close_to_tray_description else Res.string.close_to_background_description,
                        ),
                        checked = settings.closeToTray,
                        enabled = true,
                        onCheckedChange = actions.onCloseToTrayChange,
                    )
                    SwitchRow(
                        title = stringResource(Res.string.start_on_login),
                        description = stringResource(
                            if (autostartAvailable) {
                                Res.string.start_on_login_description
                            } else {
                                Res.string.start_on_login_unavailable
                            },
                        ),
                        checked = autostartEnabled,
                        enabled = autostartAvailable,
                        onCheckedChange = actions.onAutostartChange,
                    )

                    SectionHeader(Res.string.settings_about)
                    InfoRow(stringResource(Res.string.version), appVersion())
                    InfoRow(stringResource(Res.string.license_title), stringResource(Res.string.license))
                    InfoRow(stringResource(Res.string.font_license_title), stringResource(Res.string.font_license))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

/** The installed app's version (set by jpackage); "dev" when run from Gradle. */
fun appVersion(): String = System.getProperty("jpackage.app-version") ?: "dev"

@Composable
private fun SectionHeader(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
    )
}
