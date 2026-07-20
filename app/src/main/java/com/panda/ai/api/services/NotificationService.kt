package com.panda.ai.api.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object NotificationService {
    private const val CHANNEL_ID = "task_completion_channel"
    private var nextId = 1000

    fun showTaskCompleteNotification(context: Context, title: String, body: String) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Task Completions", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for when a task completes"
        }
        mgr.createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        mgr.notify(nextId++, notif)
    }
}
