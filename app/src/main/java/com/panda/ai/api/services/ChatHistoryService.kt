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
}
