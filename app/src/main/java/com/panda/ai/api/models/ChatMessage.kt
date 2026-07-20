package com.panda.ai.api.models

import org.json.JSONObject

data class AgentActionResult(
    val actionType: String,
    val success: Boolean,
    val details: String?
)

data class ChatMessage(
    val role: String, // 'user' or 'assistant'
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionResult: AgentActionResult? = null
) {
    fun isUser(): Boolean = role == "user"

    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        put("timestamp", timestamp)
        actionResult?.let {
            put("actionResult", JSONObject().apply {
                put("actionType", it.actionType)
                put("success", it.success)
                put("details", it.details)
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ChatMessage {
            val ar = json.optJSONObject("actionResult")
            return ChatMessage(
                role = json.optString("role", "user"),
                content = json.optString("content", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                actionResult = ar?.let {
                    AgentActionResult(
                        actionType = it.optString("actionType", ""),
                        success = it.optBoolean("success", false),
                        details = it.optString("details", null)
                    )
                }
            )
        }
    }
}
