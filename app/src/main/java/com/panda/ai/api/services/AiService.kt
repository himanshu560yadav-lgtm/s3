package com.panda.ai.api.services

import android.util.Log
import com.panda.ai.api.models.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AiResponse(val content: String, val totalTokens: Int)

class AiService {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
        const val NVIDIA_DEFAULT_MODEL = "z-ai/glm-5.2"

        val NVIDIA_FREE_CHAT_MODELS = listOf(
            "z-ai/glm-5.2", "nvidia/nemotron-3-nano-30b-a3b",
            "nvidia/nemotron-3-super-120b-a12b", "nvidia/nemotron-3-ultra-550b-a55b",
            "nvidia/nvidia-nemotron-nano-9b-v2", "openai/gpt-oss-20b",
            "openai/gpt-oss-120b", "meta/llama-3.3-70b-instruct",
            "meta/llama-3.2-3b-instruct", "meta/llama-3.1-8b-instruct",
            "mistralai/mistral-nemotron", "deepseek-ai/deepseek-v4-flash",
            "deepseek-ai/deepseek-v4-pro"
        )

        fun isNvidiaBaseUrl(baseUrl: String): Boolean {
            return try {
                val uri = android.net.Uri.parse(baseUrl.trim())
                uri.host?.lowercase() == "integrate.api.nvidia.com"
            } catch (_: Exception) { false }
        }

        fun filterNvidiaFreeModels(models: List<String>): List<String> {
            val available = models.toSet()
            return NVIDIA_FREE_CHAT_MODELS.filter { available.contains(it) }
        }
    }

    private var apiKey: String? = null
    private var baseUrl: String = DEFAULT_BASE_URL
    private var model: String = DEFAULT_MODEL
    private var maxSteps: Int = 15
    private var disableMaxSteps: Boolean = false
    private var temperature: Double = 1.0
    private var maxTokens: Int = 1024
    private var useScreenCompression: Boolean = true
    private var useSystemPrompt: Boolean = true

    private val conversationHistory = mutableListOf<Pair<String, String>>() // role, content

    private val systemPrompt = """
You are PrivateAgent, a helpful AI assistant that controls an Android phone. You can perform device actions and also have normal conversations.

When the user wants to perform a device action, you MUST respond with ONLY a JSON object (no markdown, no code fences, no extra text) in this exact format:
{"action": "action_name", "params": {"key": "value"}, "response": "What you say to the user"}

Available actions and their params:

SIMPLE ACTIONS (single step only):
- open_app: {"app_name": "YouTube"} - ONLY use this when the user JUST wants to open an app and nothing else
- make_call: {"contact_name": "Mom"} OR {"phone_number": "1234567890"} - Makes a phone call
- send_sms: {"contact_name": "John", "message": "Hello"} OR {"phone_number": "123", "message": "Hi"} - Sends SMS
- search_contact: {"query": "John"} - Searches contacts
- set_alarm: {"hour": 7, "minute": 30, "label": "Wake up"} - Sets an alarm
- set_volume: {"level": 50} - Sets volume (0-100)
- set_brightness: {"level": 50} - Sets brightness (0-100)
- read_screen: {} - Read what's currently on the screen
- press_back: {} - Press the back button

MULTI-STEP TASK (for anything that requires more than one action):
- execute_task: {"goal": "description of the full task"} - Automatically reads screen, taps, scrolls, types step by step

CRITICAL RULES:
1. If the user request contains "and" or involves MULTIPLE steps (open + search, open + send, open + find, etc.), you MUST use execute_task. NEVER use open_app for these.
2. execute_task handles everything: opening apps, finding elements, clicking, typing, scrolling.

Examples of when to use execute_task:
- "Create a new alarm for 7 AM" → execute_task with goal "Create a new alarm for 7 AM"
- "Go to YouTube and search for cats" → execute_task
- "Open WhatsApp and send hello to John" → execute_task
- "Open Settings and turn on WiFi" → execute_task
- "Search for restaurants on Google Maps" → execute_task

Examples of when to use open_app:
- "Open YouTube" → open_app (just opening, no further action)
- "Open Settings" → open_app (just opening)

For normal conversation (questions, chat, info requests), just respond with plain text naturally.
""".trimIndent()

    private val chatSystemPrompt = """
You are PrivateAgent, a helpful conversational AI assistant. 
Provide direct, natural, and friendly text responses. You cannot perform device actions or run tools. 
Answer questions, explain concepts, brainstorm, write emails/messages, and chat with the user in plain text or markdown format.
""".trimIndent()

    // ---- Preferences ----
    fun init(prefs: android.content.SharedPreferences) {
        apiKey = prefs.getString("api_key", null)
        baseUrl = prefs.getString("api_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        model = prefs.getString("api_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        maxSteps = prefs.getInt("api_max_steps", 15)
        disableMaxSteps = prefs.getBoolean("api_disable_max_steps", false)
        temperature = prefs.getFloat("api_temperature", 1.0f).toDouble()
        maxTokens = prefs.getInt("api_max_tokens", 1024)
        useScreenCompression = prefs.getBoolean("api_use_screen_compression", true)
        useSystemPrompt = prefs.getBoolean("api_use_system_prompt", true)
    }

    fun saveSettings(prefs: android.content.SharedPreferences, apiKey: String, baseUrl: String?, model: String?) {
        var clean = apiKey.trim()
        if (clean.lowercase().startsWith("bearer ")) clean = clean.substring(7).trim()
        this.apiKey = clean
        prefs.edit().putString("api_key", clean).apply()
        if (!baseUrl.isNullOrEmpty()) {
            this.baseUrl = baseUrl
            prefs.edit().putString("api_base_url", baseUrl).apply()
        }
        if (!model.isNullOrEmpty()) {
            this.model = model
            prefs.edit().putString("api_model", model).apply()
        }
    }

    fun saveMaxSteps(prefs: android.content.SharedPreferences, steps: Int) {
        maxSteps = steps
        prefs.edit().putInt("api_max_steps", steps).apply()
    }

    fun saveDisableMaxSteps(prefs: android.content.SharedPreferences, disable: Boolean) {
        disableMaxSteps = disable
        prefs.edit().putBoolean("api_disable_max_steps", disable).apply()
    }

    fun saveAdvancedSettings(prefs: android.content.SharedPreferences, temperature: Double, maxTokens: Int, useScreenCompression: Boolean, useSystemPrompt: Boolean) {
        this.temperature = temperature
        this.maxTokens = maxTokens
        this.useScreenCompression = useScreenCompression
        this.useSystemPrompt = useSystemPrompt
        prefs.edit()
            .putFloat("api_temperature", temperature.toFloat())
            .putInt("api_max_tokens", maxTokens)
            .putBoolean("api_use_screen_compression", useScreenCompression)
            .putBoolean("api_use_system_prompt", useSystemPrompt)
            .apply()
    }

    val isConfigured: Boolean get() = !apiKey.isNullOrEmpty()
    val currentBaseUrl: String get() = baseUrl
    val currentModel: String get() = model
    val currentApiKey: String get() = apiKey ?: ""
    val rawMaxSteps: Int get() = maxSteps
    val currentDisableMaxSteps: Boolean get() = disableMaxSteps
    val maxStepsValue: Int get() = if (disableMaxSteps) 999 else maxSteps
    val currentTemperature: Double get() = temperature
    val currentMaxTokens: Int get() = maxTokens
    val currentUseScreenCompression: Boolean get() = useScreenCompression
    val currentUseSystemPrompt: Boolean get() = useSystemPrompt

    private val effectiveMaxTokens: Int
        get() {
            if (isNvidiaBaseUrl(baseUrl) && model == NVIDIA_DEFAULT_MODEL && maxTokens < 4096) return 4096
            return maxTokens
        }

    fun clearHistory() = conversationHistory.clear()

    fun addHistoryMessage(role: String, content: String) {
        conversationHistory.add(role to content)
        if (conversationHistory.size > 20) conversationHistory.removeAt(0)
    }

    private fun buildMessages(isAgentMode: Boolean): JSONArray {
        val arr = JSONArray()
        if (useSystemPrompt) {
            arr.put(JSONObject().apply {
                put("role", "system")
                put("content", if (isAgentMode) systemPrompt else chatSystemPrompt)
            })
        }
        for ((role, content) in conversationHistory) {
            arr.put(JSONObject().apply { put("role", role); put("content", content) })
        }
        return arr
    }

    private fun resolveUrl(): String {
        var url = baseUrl
        url = if (url.endsWith("/chat/completions")) url
        else if (url.endsWith("/")) "$url chat/completions".replace(" ", "")
        else "$url/chat/completions"
        return url
    }

    private fun stripThink(text: String): String {
        return text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    // Non-streaming
    suspend fun sendMessage(message: String, isAgentMode: Boolean): String = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) throw Exception("API Key is not configured. Please go to Settings.")

        conversationHistory.add("user" to message)
        if (conversationHistory.size > 20) conversationHistory.removeAt(0)

        val body = JSONObject().apply {
            put("model", model)
            put("messages", buildMessages(isAgentMode))
            put("temperature", temperature)
            put("max_tokens", effectiveMaxTokens)
        }.toString()

        val url = resolveUrl()
        Log.d("AiService", "API Request: $url\n$body")

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("HTTP-Referer", "https://github.com/orailnoor/private-agent")
        conn.setRequestProperty("X-Title", "PrivateAgent")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30 * 60 * 1000
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

        val code = conn.responseCode
        val respText = if (code in 200..299) conn.inputStream.bufferedReader().readText()
        else conn.errorStream?.bufferedReader()?.readText() ?: ""

        if (code != 200) {
            var msg = respText
            try {
                val d = JSONObject(respText)
                val err = d.opt("error")
                msg = when (err) {
                    is JSONObject -> err.optString("message", respText)
                    is String -> err
                    else -> respText
                }
            } catch (_: Exception) {}
            throw Exception("API error ($code): $msg")
        }

        val data = JSONObject(respText)
        if (!data.has("choices")) throw Exception("Unexpected API response format: $data")

        var assistant = data.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        assistant = stripThink(assistant)
        if (assistant.trim().isEmpty()) throw Exception("API returned an empty response. This may be due to rate limits or API instability.")

        conversationHistory.add("assistant" to assistant)
        assistant
    }

    // Streaming
    fun sendMessageStream(message: String, isAgentMode: Boolean): Flow<String> = flow {
        if (apiKey.isNullOrEmpty()) throw Exception("API Key is not configured. Please go to Settings.")

        conversationHistory.add("user" to message)
        if (conversationHistory.size > 20) conversationHistory.removeAt(0)

        val body = JSONObject().apply {
            put("model", model)
            put("messages", buildMessages(isAgentMode))
            put("temperature", temperature)
            put("max_tokens", effectiveMaxTokens)
            put("stream", true)
        }.toString()

        val url = resolveUrl()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("HTTP-Referer", "https://github.com/orailnoor/private-agent")
        conn.setRequestProperty("X-Title", "PrivateAgent")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 30 * 60 * 1000
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            var msg = err
            try {
                val d = JSONObject(err)
                val e = d.opt("error")
                msg = when (e) {
                    is JSONObject -> e.optString("message", err)
                    is String -> e
                    else -> err
                }
            } catch (_: Exception) {}
            throw Exception("API error ($code): $msg")
        }

        val sb = StringBuilder()
        var inThink = false
        conn.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (!trimmed.startsWith("data:")) continue
                val dataStr = trimmed.substring(5).trim()
                if (dataStr == "[DONE]") break
                try {
                    val json = JSONObject(dataStr)
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.getJSONObject(0)
                    val delta = choice.optJSONObject("delta") ?: continue
                    val content = delta.optString("content", "")
                    if (content.isEmpty()) continue

                    if (content.contains("<think>")) {
                        inThink = true
                        val parts = content.split("<think>")
                        if (parts[0].isNotEmpty()) { sb.append(parts[0]); emit(parts[0]) }
                    } else if (content.contains("</think>")) {
                        inThink = false
                        val parts = content.split("</think>")
                        if (parts.size > 1 && parts[1].isNotEmpty()) { sb.append(parts[1]); emit(parts[1]) }
                    } else if (!inThink) {
                        sb.append(content); emit(content)
                    }
                    if (choice.has("finish_reason")) break
                } catch (_: Exception) {}
            }
        }

        var finalResp = stripThink(sb.toString())
        if (finalResp.isEmpty()) throw Exception("The model finished without a visible answer. Increase Max Tokens or try another NVIDIA model.")
        conversationHistory.add("assistant" to finalResp)
    }

    // Task execution (no history)
    suspend fun sendTaskMessage(systemPromptText: String, prompt: String): AiResponse = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) throw Exception("API Key is not configured. Please go to Settings.")
        var lastErr: Exception? = null
        for (attempt in 1..4) {
            try {
                val arr = JSONArray()
                if (useSystemPrompt) arr.put(JSONObject().apply { put("role", "system"); put("content", systemPromptText) })
                arr.put(JSONObject().apply { put("role", "user"); put("content", prompt) })

                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", arr)
                    put("temperature", temperature)
                    put("max_tokens", effectiveMaxTokens)
                }.toString()

                val url = resolveUrl()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("HTTP-Referer", "https://github.com/orailnoor/private-agent")
                conn.setRequestProperty("X-Title", "PrivateAgent")
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 30 * 60 * 1000
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))

                val code = conn.responseCode
                val respText = if (code in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText() ?: ""
                if (code != 200) {
                    var msg = respText
                    try {
                        val d = JSONObject(respText)
                        val err = d.opt("error")
                        msg = when (err) {
                            is JSONObject -> err.optString("message", respText)
                            is String -> err
                            else -> respText
                        }
                    } catch (_: Exception) {}
                    throw Exception("API error ($code): $msg")
                }

                val data = JSONObject(respText)
                if (!data.has("choices")) throw Exception("Unexpected API response format: $data")
                var content = data.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                content = stripThink(content)
                if (content.trim().isEmpty()) throw Exception("API returned an empty response. This may be due to strict rate limits or safety filters.")

                val tokens = data.optJSONObject("usage")?.optInt("total_tokens") ?: 0
                return@withContext AiResponse(content, tokens)
            } catch (e: Exception) {
                lastErr = e
                if (attempt >= 4) break
                kotlinx.coroutines.delay(3000L * attempt)
            }
        }
        throw lastErr ?: Exception("Network error")
    }

    fun parseAction(response: String): AgentAction? {
        return try {
            var trimmed = response.trim()
            if (trimmed.startsWith("```")) {
                val lines = trimmed.split("\n").toMutableList()
                lines.removeAt(0)
                if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeAt(lines.lastIndex)
                trimmed = lines.joinToString("\n").trim()
            }
            if (trimmed.startsWith("{") && !trimmed.endsWith("}")) trimmed += "\n}"
            if (trimmed.startsWith("{") && trimmed.contains("\"action\"")) {
                val json = JSONObject(trimmed)
                if (json.has("action")) {
                    val paramsObj = json.optJSONObject("params")
                    val params = mutableMapOf<String, Any?>()
                    paramsObj?.keys()?.forEach { params[it] = paramsObj.get(it) }
                    return AgentAction(
                        action = json.optString("action", "general_query"),
                        params = params,
                        response = json.optString("response", "")
                    )
                }
            }
            null
        } catch (_: Exception) { null }
    }

    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            var clean = baseUrl
            if (clean.endsWith("/chat/completions")) clean = clean.replace("/chat/completions", "")
            val conn = URL("$clean/models").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val code = conn.responseCode
            val text = if (code in 200..299) conn.inputStream.bufferedReader().readText()
            else return@withContext emptyList()
            val data = JSONObject(text)
            val models = if (data.has("data")) {
                val list = data.getJSONArray("data")
                (0 until list.length()).map { list.getJSONObject(it).getString("id") }
            } else if (data is JSONArray) {
                (0 until data.length()).map { data.getJSONObject(it).getString("id") }
            } else emptyList()

            if (isNvidiaBaseUrl(clean)) return@withContext filterNvidiaFreeModels(models)
            return@withContext models.sorted()
        } catch (e: Exception) {
            Log.e("AiService", "Error fetching models: $e")
            emptyList()
        }
    }
}
