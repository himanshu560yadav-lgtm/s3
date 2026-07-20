package com.panda.ai.api.services

import android.content.Context

object ShizukuService {
    var isAvailable = false
    var hasPermission = false

    fun checkAvailability(): Boolean = isAvailable

    fun requestPermission(context: Context) {}

    fun runCommand(command: String): String = "Shizuku not available"
}
