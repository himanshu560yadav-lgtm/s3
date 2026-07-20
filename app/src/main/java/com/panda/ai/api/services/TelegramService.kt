package com.panda.ai.api.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TelegramService(
    private val context: Context,
    private val onCommand: suspend (String) -> Unit
) {
    private var botToken: String = ""
    var isEnabled: Boolean = false
    private var lastUpdateId: Long = 0
    private var running = false

    fun init(prefs: SharedPreferences) {
        botToken = prefs.getString("telegram_token", "") ?: ""
        isEnabled = prefs.getBoolean("telegram_enabled", false)
    }

    fun saveSettings(prefs: SharedPreferences, token: String, enabled: Boolean) {
        botToken = token.trim()
        isEnabled = enabled
        prefs.edit().putString("telegram_token", botToken).putBoolean("telegram_enabled", enabled).apply()
    }

    fun start() {
        if (!isEnabled || botToken.isEmpty() || running) return
        running = true
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            while (running && isActive && isEnabled) {
                try { pollUpdates() } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    fun stop() { running = false }

    private suspend fun pollUpdates() = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=10"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        val code = conn.responseCode
        if (code != 200) return@withContext
        val text = conn.inputStream.bufferedReader().readText()
        val data = JSONObject(text)
        val ok = data.optBoolean("ok", false)
        if (!ok) return@withContext
        val arr = data.optJSONArray("result") ?: return@withContext
        for (i in 0 until arr.length()) {
            val upd = arr.getJSONObject(i)
            lastUpdateId = upd.optLong("update_id", lastUpdateId)
            val msg = upd.optJSONObject("message") ?: continue
            val textMsg = msg.optString("text", "")
            if (textMsg.isNotEmpty()) onCommand(textMsg)
        }
    }

    fun sendMessage(text: String) {
        if (!isEnabled || botToken.isEmpty()) return
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=me&text=${android.net.Uri.encode(text)}"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 15000
                conn.responseCode
            } catch (_: Exception) {}
        }
    }
}
