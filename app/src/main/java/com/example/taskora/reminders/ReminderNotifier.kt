package com.example.taskora.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.taskora.MainActivity
import com.example.taskora.R
import com.example.taskora.domain.model.Task
import com.example.taskora.ui.navigation.TASK_DEEP_LINK_BASE
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminders_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.reminders_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(task: Task) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(task.notes.takeIf { it.isNotBlank() })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openTaskIntent(task.id))
            .addAction(0, context.getString(R.string.complete), actionIntent(task.id, ReminderActionReceiver.ACTION_COMPLETE))
            .addAction(0, context.getString(R.string.snooze), actionIntent(task.id, ReminderActionReceiver.ACTION_SNOOZE))
            .build()
        manager.notify(notificationId(task.id), notification)
    }

    fun cancel(taskId: Long) = manager.cancel(notificationId(taskId))

    // NEW_TASK + CLEAR_TASK makes Navigation build a back stack of list -> task.
    private fun openTaskIntent(taskId: Long): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "$TASK_DEEP_LINK_BASE/$taskId".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(taskId: Long, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData("taskora://reminder/$taskId".toUri())
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(taskId: Long): Int = taskId.hashCode()

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}
