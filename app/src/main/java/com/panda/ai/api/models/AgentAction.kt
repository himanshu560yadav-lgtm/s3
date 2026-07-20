package com.panda.ai.api.models

import org.json.JSONObject

data class AgentAction(
    val action: String,
    val params: Map<String, Any?>,
    val response: String
) {
    companion object {
        fun fromJson(json: Map<String, Any?>): AgentAction {
            return AgentAction(
                action = (json["action"] as? String) ?: "general_query",
                params = (json["params"] as? Map<String, Any?>) ?: emptyMap(),
                response = (json["response"] as? String) ?: ""
            )
        }
    }
}

val AVAILABLE_ACTIONS = listOf(
    "open_app", "make_call", "send_sms", "search_contact", "set_alarm",
    "set_volume", "set_brightness", "read_notifications", "read_screen",
    "run_adb_command", "general_query", "execute_task"
)
