package com.panda.ai.api.services

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object TaskHistoryLogger {

    private fun file(context: Context): File = File(context.filesDir, "task_history.jsonl")

    fun logTask(context: Context, goal: String, status: String, totalTokens: Int, steps: Int, trace: List<String>) {
        try {
            val data = JSONObject().apply {
                put("goal", goal.trim())
                put("status", status)
                put("total_tokens", totalTokens)
                put("steps_taken", steps)
                put("trace", JSONArray(trace))
                put("timestamp", System.currentTimeMillis())
            }
            file(context).appendText("${data}\n")
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun readHistory(context: Context): List<Map<String, Any>> {
        try {
            val f = file(context)
            if (!f.exists()) return emptyList()
            return f.readLines().filter { it.trim().isNotEmpty() }
                .mapNotNull { line -> JSONObject(line).toMap() }
                .reversed()
        } catch (e: Exception) { e.printStackTrace(); return emptyList() }
    }

    fun clearHistory(context: Context) {
        file(context).delete()
    }

    fun getAnalytics(context: Context): Map<String, Any> {
        val history = readHistory(context)
        if (history.isEmpty()) return mapOf(
            "totalTasks" to 0, "successRate" to 0.0,
            "successCount" to 0, "failedCount" to 0
        )
        var success = 0; var failed = 0
        for (t in history) {
            when (t["status"]) {
                "Success" -> success++
                "Failed", "Cancelled" -> failed++
            }
        }
        return mapOf(
            "totalTasks" to history.size,
            "successRate" to (success.toDouble() / history.size),
            "successCount" to success,
            "failedCount" to failed
        )
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { map[it] = get(it) }
        return map
    }
}
