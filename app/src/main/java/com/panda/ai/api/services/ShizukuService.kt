package com.panda.ai.api.services

import android.content.Context

object ShizukuService {
    var isAvailable = false
    var hasPermission = false

    fun checkAvailability(): Boolean {
        isAvailable = try {
            val p = Runtime.getRuntime().exec("sh -c 'echo ok'")
            p.waitFor() == 0
        } catch (_: Exception) { false }
        return isAvailable
    }

    fun requestPermission(context: Context) {
        hasPermission = true
    }

    fun runCommand(command: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            if (out.isNotEmpty()) out else if (err.isNotEmpty()) "Error: $err" else "Command executed."
        } catch (e: Exception) {
            "Shizuku/shell not available: $e"
        }
    }

    fun toggleWifi(enable: Boolean): String = runCommand("svc wifi ${if (enable) "enable" else "disable"}")

    fun toggleBluetooth(enable: Boolean): String = runCommand(
        "service call bluetooth_manager 6 ${if (enable) "1" else "0"}"
    )

    fun forceStopApp(packageName: String): String = runCommand("am force-stop $packageName")

    fun clearAppData(packageName: String): String = runCommand("pm clear $packageName")
}
