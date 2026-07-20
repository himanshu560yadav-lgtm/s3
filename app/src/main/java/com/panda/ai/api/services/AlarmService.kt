package com.panda.ai.api.services

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

object AlarmService {

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String?): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
            "Alarm set for $timeStr${if (label != null) " ($label)" else ""}"
        } catch (e: Exception) { "Error setting alarm: $e" }
    }

    fun setTimer(context: Context, seconds: Int, label: String?): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val minutes = seconds / 60
            val secs = seconds % 60
            "Timer set for ${minutes}m ${secs}s${if (label != null) " ($label)" else ""}"
        } catch (e: Exception) { "Error setting timer: $e" }
    }
}
