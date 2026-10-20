package com.example.taskora

import android.app.Application
import com.example.taskora.reminders.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TaskoraApplication : Application() {
    @Inject lateinit var reminderNotifier: ReminderNotifier

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.createChannel()
    }
}
