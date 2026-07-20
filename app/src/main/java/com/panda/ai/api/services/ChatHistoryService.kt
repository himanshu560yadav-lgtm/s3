package com.panda.ai.api.services

import android.content.Context
import androidx.core.content.edit
import com.panda.ai.api.models.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatSession(
    val id: String,
    val title: String,
    val timestamp: Long,
    val messages: List<ChatMessage>
)

object ChatHistoryService {

    private fun file(context: Context): File {
        return File(context.filesDir, "chat_history_sessions.json")
    }

    fun saveSession(context: Context, session: ChatSession) {
        try {
            val sessions = loadSessions(context).toMutableList()
            val idx = sessions.indexOfFirst { it.id == session.id }
            if (idx >= 0) sessions[idx] = session else sessions.add(0, session)
            val arr = JSONArray(sessions.map { s ->
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("timestamp", s.timestamp)
                    put("messages", JSONArray(s.messages.map { it.toJson() }))
                }
            })
            file(context).writeText(arr.toString())
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadSessions(context: Context): List<ChatSession> {
        try {
            val f = file(context)
            if (!f.exists()) return emptyList()
            val text = f.readText().trim()
            if (text.isEmpty()) return emptyList()
            val arr = JSONArray(text)
            val list = mutableListOf<ChatSession>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msgs = o.optJSONArray("messages")
                val messages = mutableListOf<ChatMessage>()
                for (j in 0 until (msgs?.length() ?: 0)) messages.add(ChatMessage.fromJson(msgs!!.getJSONObject(j)))
                list.add(ChatSession(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    messages = messages
                ))
            }
            return list
        } catch (e: Exception) { e.printStackTrace(); return emptyList() }
    }

    fun deleteSession(context: Context, id: String) {
        val sessions = loadSessions(context).toMutableList()
        sessions.removeAll { it.id == id }
        val arr = JSONArray(sessions.map { s ->
            JSONObject().apply {
                put("id", s.id); put("title", s.title)
                put("timestamp", s.timestamp)
                put("messages", JSONArray(s.messages.map { it.toJson() }))
            }
        })
        file(context).writeText(arr.toString())
    }

    fun clearAll(context: Context) {
        file(context).delete()
    }

    private fun overlayDir(context: Context): File {
        return File(context.filesDir, "overlay_chat_handoff")
    }

    fun appendOverlayMessage(context: Context, message: Map<String, Any?>) {
        try {
            val dir = overlayDir(context)
            dir.mkdirs()
            val eventId = System.nanoTime()
            val temporary = File(dir, "$eventId.tmp")
            val event = File(dir, "$eventId.json")
            temporary.writeText(JSONObject(message).toString())
            temporary.renameTo(event)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun consumeOverlayMessages(context: Context): List<Map<String, Any?>> {
        try {
            val dir = overlayDir(context)
            if (!dir.exists()) return emptyList()
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.sortedBy { it.name } ?: return emptyList()
            val messages = mutableListOf<Map<String, Any?>>()
            for (file in files) {
                try {
                    val decoded = JSONObject(file.readText())
                    val map = mutableMapOf<String, Any?>()
                    decoded.keys().forEach { map[it] = decoded.get(it) }
                    messages.add(map)
                    file.delete()
                } catch (_: Exception) { /* leave for next sync */ }
            }
            return messages
        } catch (e: Exception) { e.printStackTrace(); return emptyList() }
    }
}
