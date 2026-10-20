package io.github.jameslunaa.taskora.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.ui.common.headerBandTopAppBarColors
import io.github.jameslunaa.taskora.ui.theme.TaskoraTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
    )
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.SYSTEM -> R.string.theme_system
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings?,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = headerBandTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (settings == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(R.string.settings_appearance)
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = mode == settings.themeMode,
                            role = Role.RadioButton,
                            onClick = { onThemeModeChange(mode) },
                        ),
                        leadingContent = { RadioButton(selected = mode == settings.themeMode, onClick = null) },
                        headlineContent = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    modifier = Modifier.toggleable(
                        value = settings.dynamicColor,
                        role = Role.Switch,
                        onValueChange = onDynamicColorChange,
                    ),
                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.dynamic_color_description)) },
                    trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(R.string.settings_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.version)) },
                supportingContent = { Text(appVersion()) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.license_title)) },
                supportingContent = { Text(stringResource(R.string.license)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.font_license_title)) },
                supportingContent = { Text(stringResource(R.string.font_license)) },
            )
        }
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    TaskoraTheme {
        SettingsContent(
            settings = AppSettings(),
            onBack = {},
            onThemeModeChange = {},
            onDynamicColorChange = {},
        )
    }
}
