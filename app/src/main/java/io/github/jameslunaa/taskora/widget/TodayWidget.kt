package io.github.jameslunaa.taskora.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.jameslunaa.taskora.MainActivity
import io.github.jameslunaa.taskora.R
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.domain.DueStatus
import io.github.jameslunaa.taskora.domain.dueStatus
import io.github.jameslunaa.taskora.domain.dueTodayOrOverdue
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.ui.common.RelativeDayLabels
import io.github.jameslunaa.taskora.ui.common.formatDue
import io.github.jameslunaa.taskora.ui.navigation.TASK_DEEP_LINK_BASE
import io.github.jameslunaa.taskora.ui.theme.DarkColors
import io.github.jameslunaa.taskora.ui.theme.DayField
import io.github.jameslunaa.taskora.ui.theme.DayFieldLilac
import io.github.jameslunaa.taskora.ui.theme.DayOnField
import io.github.jameslunaa.taskora.ui.theme.FieldLilac
import io.github.jameslunaa.taskora.ui.theme.LightColors
import io.github.jameslunaa.taskora.ui.theme.PriceCardInk
import io.github.jameslunaa.taskora.ui.theme.UbeField
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/** Glance widgets aren't Hilt entry points, so dependencies come through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskRepository(): TaskRepository
    fun settingsRepository(): SettingsRepository
}

private fun Context.entryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

private fun Context.taskRepository(): TaskRepository = entryPoint().taskRepository()

/** Header band colors for the widget, following the theme mode. */
private class WidgetBand(val background: ColorProvider, val title: ColorProvider, val accent: ColorProvider)

private fun widgetBand(mode: ThemeMode): WidgetBand = when (mode) {
    ThemeMode.DARK -> WidgetBand(ColorProvider(UbeField), ColorProvider(PriceCardInk), ColorProvider(FieldLilac))
    ThemeMode.LIGHT -> WidgetBand(ColorProvider(DayField), ColorProvider(DayOnField), ColorProvider(DayFieldLilac))
    ThemeMode.SYSTEM -> WidgetBand(
        DayNightColorProvider(day = DayField, night = UbeField),
        DayNightColorProvider(day = DayOnField, night = PriceCardInk),
        DayNightColorProvider(day = DayFieldLilac, night = FieldLilac),
    )
}

private fun widgetColors(mode: ThemeMode) = when (mode) {
    ThemeMode.DARK -> ColorProviders(scheme = DarkColors)
    ThemeMode.LIGHT -> ColorProviders(scheme = LightColors)
    ThemeMode.SYSTEM -> ColorProviders(light = LightColors, dark = DarkColors)
}

/**
 * Open tasks due today or overdue, across all lists. Follows the theme mode setting but always
 * uses the Taskora palette (no wallpaper colors).
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.taskRepository()
        val settingsRepository = context.entryPoint().settingsRepository()
        val initialTasks = repository.observeAllTasks().first()
        val initialSettings = settingsRepository.settings.first()
        provideContent {
            // Live while the session runs; WidgetUpdater restarts it after it ends.
            val tasks by remember { repository.observeAllTasks() }.collectAsState(initialTasks)
            val settings by remember { settingsRepository.settings }.collectAsState(initialSettings)
            GlanceTheme(colors = widgetColors(settings.themeMode)) {
                Content(tasks.dueTodayOrOverdue(LocalDate.now()), widgetBand(settings.themeMode))
            }
        }
    }

    @Composable
    private fun Content(tasks: List<Task>, band: WidgetBand) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp),
        ) {
            Header(count = tasks.size, band = band, onClick = actionStartActivity(openAppIntent(context)))
            if (tasks.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        context.getString(R.string.widget_empty),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(vertical = 4.dp)) {
                    items(tasks, itemId = { it.id }) { task -> TaskItem(task) }
                }
            }
        }
    }

    /** Tapping the header opens the app. */
    @Composable
    private fun Header(count: Int, band: WidgetBand, onClick: Action) {
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(band.background)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                context.getString(R.string.widget_title),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = band.title, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            if (count > 0) {
                Text(
                    count.toString(),
                    style = TextStyle(color = band.accent, fontSize = 14.sp),
                )
            }
        }
    }

    @Composable
    private fun TaskItem(task: Task) {
        val context = LocalContext.current
        val date = checkNotNull(task.dueDate)
        val overdue = dueStatus(date, task.dueTime, LocalDateTime.now()) == DueStatus.OVERDUE
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to task.id)),
            )
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(vertical = 6.dp)
                    .clickable(actionStartActivity(openTaskIntent(context, task.id))),
            ) {
                Text(
                    task.title,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    formatDue(date, task.dueTime, LocalDate.now(), context.dayLabels(), context.resources.configuration.locales[0]),
                    style = TextStyle(
                        color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

private fun Context.dayLabels() = RelativeDayLabels(
    yesterday = getString(R.string.yesterday),
    today = getString(R.string.today),
    tomorrow = getString(R.string.tomorrow),
)

private fun openAppIntent(context: Context) = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

// Same deep link as reminder notifications; NEW_TASK + CLEAR_TASK builds the list -> task back stack.
private fun openTaskIntent(context: Context, taskId: Long) =
    Intent(Intent.ACTION_VIEW, "$TASK_DEEP_LINK_BASE/$taskId".toUri(), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

private val TaskIdKey = ActionParameters.Key<Long>("taskId")

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        context.taskRepository().setTaskCompleted(taskId, true)
        TodayWidget().updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
