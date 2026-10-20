package com.example.taskora.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(taskId: Long, at: Instant) {
        val pendingIntent = pendingIntent(taskId, at)
        val millis = at.toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
    }

    override fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, null))
    }

    // The data URI makes each task's PendingIntent distinct (extras don't count for matching).
    private fun pendingIntent(taskId: Long, at: Instant?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .setData("taskora://reminder/$taskId".toUri())
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        at?.let { intent.putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, it.toEpochMilli()) }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
