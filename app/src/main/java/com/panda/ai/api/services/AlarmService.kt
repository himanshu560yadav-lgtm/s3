package com.panda.ai.api.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmService {

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String?): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0) }
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label ?: "PrivateAgent Alarm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); "Alarm set for $hour:$minute" }
        catch (e: Exception) { "Could not set alarm: $e" }
    }

    fun setTimer(context: Context, seconds: Int, label: String?): String {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label ?: "PrivateAgent Timer")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try { context.startActivity(intent); "Timer set for $seconds seconds" }
        catch (e: Exception) { "Could not set timer: $e" }
    }
}
