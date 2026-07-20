package com.panda.ai.api.services

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

object SystemControlService {

    fun setVolume(context: Context, level: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val v = (level.coerceIn(0, 100) / 100.0 * max).roundToInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        return "Volume set to $level%"
    }

    fun setBrightness(context: Context, level: Int): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (level.coerceIn(0, 100) * 255 / 100)
                )
                "Brightness set to $level%"
            } else "Brightness permission not granted"
        } catch (e: Exception) { "Could not set brightness: $e" }
    }
}
