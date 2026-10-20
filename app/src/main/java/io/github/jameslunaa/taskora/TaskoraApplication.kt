package io.github.jameslunaa.taskora

import android.app.Application
import io.github.jameslunaa.taskora.reminders.ReminderNotifier
import io.github.jameslunaa.taskora.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TaskoraApplication : Application() {
    @Inject lateinit var reminderNotifier: ReminderNotifier
    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.createChannel()
        widgetUpdater.start()
    }
}
