package com.example.taskora

import android.app.Application
import com.example.taskora.reminders.ReminderNotifier
import com.example.taskora.widget.WidgetUpdater
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
