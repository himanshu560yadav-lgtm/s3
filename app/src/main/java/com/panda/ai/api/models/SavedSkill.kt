package com.panda.ai.api.models

import org.json.JSONArray
import org.json.JSONObject

data class ActionStep(
    val action: String,
    val params: Map<String, Any?>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action", action)
        put("params", JSONObject(params))
    }

    companion object {
        fun fromJson(json: JSONObject): ActionStep {
            val raw = json.optJSONObject("params")
            val params = mutableMapOf<String, Any?>()
            raw?.keys()?.forEach { params[it] = raw.get(it) }
            return ActionStep(
                action = json.optString("action", ""),
                params = params
            )
        }
    }
}

data class SavedSkill(
    val id: String,
    val task: String,
    val taskKeywords: List<String>,
    var successCount: Int = 0,
    var failCount: Int = 0,
    var lastUsed: Long = System.currentTimeMillis(),
    val steps: List<ActionStep>
) {
    val isReliable: Boolean
        get() = successCount >= 1 && (failCount.toDouble() / (successCount + failCount)) < 0.3

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("task", task)
        put("task_keywords", JSONArray(taskKeywords))
        put("success_count", successCount)
        put("fail_count", failCount)
        put("last_used", lastUsed)
        put("steps", JSONArray(steps.map { it.toJson() }))
    }

    companion object {
        fun fromJson(json: JSONObject): SavedSkill {
            val kw = json.optJSONArray("task_keywords")
            val keywords = mutableListOf<String>()
            for (i in 0 until (kw?.length() ?: 0)) keywords.add(kw!!.getString(i))

            val st = json.optJSONArray("steps")
            val steps = mutableListOf<ActionStep>()
            for (i in 0 until (st?.length() ?: 0)) steps.add(ActionStep.fromJson(st!!.getJSONObject(i)))

            return SavedSkill(
                id = json.optString("id"),
                task = json.optString("task"),
                taskKeywords = keywords,
                successCount = json.optInt("success_count", 0),
                failCount = json.optInt("fail_count", 0),
                lastUsed = json.optLong("last_used", System.currentTimeMillis()),
                steps = steps
            )
        }
    }
}
