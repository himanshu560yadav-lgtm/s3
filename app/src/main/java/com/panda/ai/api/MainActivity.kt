package com.panda.ai.api

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.panda.ai.api.databinding.ActivityMainBinding
import com.panda.ai.api.models.AgentAction
import com.panda.ai.api.models.AgentActionResult
import com.panda.ai.api.models.ChatMessage
import com.panda.ai.api.services.*
import com.panda.ai.api.ui.ChatAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var aiService: AiService
    private lateinit var actionHandler: ActionHandler
    private lateinit var voiceService: VoiceService
    private lateinit var telegramService: TelegramService
    private val messages = mutableListOf<ChatMessage>()
    private var mode = "chat"
    private var sessionId = System.currentTimeMillis().toString()
    private var sessionTitle = ""
    private var isLoading = false
    private var isListening = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(this, if (granted) "Mic granted" else "Mic denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aiService = AiService().apply { init(appPrefs()) }
        actionHandler = ActionHandler(this)
        voiceService = VoiceService(this).apply { init() }
        telegramService = TelegramService(this) { chatId, cmd ->
            runOnUiThread { sendMessage(cmd, chatId) }
        }.apply {
            init(appPrefs())
            start()
        }

        adapter = ChatAdapter()
        binding.chatList.layoutManager = LinearLayoutManager(this)
        binding.chatList.adapter = adapter

        binding.btnModeChat.setOnClickListener { setMode("chat") }
        binding.btnModeAgent.setOnClickListener { setMode("agent") }
        setMode("chat")

        binding.btnMic.setOnClickListener { toggleVoice() }
        binding.btnSend.setOnClickListener { sendMessage(binding.inputText.text.toString()) }
        binding.inputText.setOnEditorActionListener { _, _, _ ->
            sendMessage(binding.inputText.text.toString()); true
        }

        binding.btnStop.setOnClickListener {
            actionHandler.cancelTask()
            isLoading = false
            binding.thinking.visibility = View.GONE
        }

        binding.btnConfigure.setOnClickListener { openSettings() }
        binding.topBar.setNavigationOnClickListener { binding.drawer.openDrawer(binding.navView) }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_new_chat -> startNewChat()
                R.id.nav_history -> startActivity(Intent(this, TaskHistoryActivity::class.java))
                R.id.nav_settings -> openSettings()
            }
            binding.drawer.closeDrawer(binding.navView)
            true
        }

        updateApiWarning()
    }

    private fun setMode(m: String) {
        mode = m
        binding.btnModeChat.isActivated = m == "chat"
        binding.btnModeAgent.isActivated = m == "agent"
    }

    private fun updateApiWarning() {
        binding.apiWarning.visibility =
            if (aiService.isConfigured) View.GONE else View.VISIBLE
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun startNewChat() {
        sessionId = System.currentTimeMillis().toString()
        sessionTitle = ""
        messages.clear()
        adapter.setMessages(messages)
        aiService.clearHistory()
    }

    private fun saveSession() {
        if (messages.isEmpty()) return
        if (sessionTitle.isEmpty()) {
            val firstUser = messages.firstOrNull { it.isUser() }
            val base = firstUser?.content ?: "New Chat"
            sessionTitle = if (base.length > 28) "${base.substring(0, 25)}..." else base
        }
        val session = ChatSession(sessionId, sessionTitle, System.currentTimeMillis(), messages.toList())
        ChatHistoryService.saveSession(this, session)
    }

    private fun sendMessage(text: String, telegramChatId: Long? = null) {
        if (text.trim().isEmpty()) return

        val userMsg = ChatMessage("user", text.trim())
        messages.add(userMsg)
        adapter.addMessage(userMsg)
        binding.inputText.text.clear()
        scrollBottom()
        saveSession()

        val assistantMsg = ChatMessage("assistant", "")
        messages.add(assistantMsg)
        val assistantIdx = messages.lastIndex

        isLoading = true
        binding.thinking.visibility = View.VISIBLE

        scope.launch {
            try {
                val isAgent = mode == "agent"
                val job = launch {
                    aiService.sendMessageStream(text.trim(), isAgentMode = isAgent)
                        .catch { e -> showError(e.message ?: "Error") }
                        .collect { chunk ->
                            val cur = messages[assistantIdx].content + chunk
                            messages[assistantIdx] = ChatMessage("assistant", cur)
                            adapter.updateLast(messages[assistantIdx])
                            scrollBottom()
                        }
                }
                job.join()
                saveSession()

                val accumulated = messages[assistantIdx].content
                telegramChatId?.let { telegramService.sendMessage(it, accumulated) }

                val action = aiService.parseAction(accumulated)
                if (action != null) {
                    messages.removeAt(assistantIdx)
                    adapter.removeLast()
                    handleAction(action, telegramChatId)
                } else {
                    voiceService.speak(accumulated)
                }
            } catch (e: Exception) {
                if (messages.isNotEmpty()) messages.removeAt(messages.lastIndex)
                adapter.removeLast()
                val msg = e.message?.replaceFirst("Exception: ", "") ?: "Unknown"
                messages.add(ChatMessage("assistant", "Error: $msg"))
                adapter.addMessage(messages.last())
            } finally {
                isLoading = false
                binding.thinking.visibility = View.GONE
            }
        }
    }

    private fun handleAction(action: AgentAction, telegramChatId: Long? = null) {
        scope.launch {
            val result = actionHandler.execute(action, aiService = aiService) { progress ->
                runOnUiThread {
                    messages.add(ChatMessage("assistant", "⏳ $progress"))
                    adapter.addMessage(messages.last())
                    scrollBottom()
                }
            }
            runOnUiThread {
                val formatted = formatActionResult(action, result)
                messages.add(ChatMessage("assistant", formatted, actionResult = result))
                adapter.addMessage(messages.last())
                scrollBottom()
                telegramChatId?.let { telegramService.sendMessage(it, formatted) }
                if (action.action != "execute_task") {
                    NotificationService.showTaskCompleteNotification(
                        this@MainActivity,
                        if (result.success) "Task Completed" else "Task Failed",
                        result.details
                            ?: (if (result.success) "Agent finished its goal."
                            else "Agent could not complete the task.")
                    )
                }
                saveSession()
            }
        }
    }

    private fun showError(msg: String) {
        runOnUiThread {
            if (messages.isNotEmpty()) messages.removeAt(messages.lastIndex)
            adapter.removeLast()
            messages.add(ChatMessage("assistant", "Error: $msg"))
            adapter.addMessage(messages.last())
            isLoading = false
            binding.thinking.visibility = View.GONE
        }
    }

    private fun formatActionResult(action: AgentAction, result: AgentActionResult): String {
        return if (result.success) {
            if (action.response.isNotEmpty()) action.response
            else (result.details ?: "Done.")
        } else {
            if (action.response.isNotEmpty()) "${action.response}\n\n⚠️ ${result.details}"
            else "⚠️ ${result.details}"
        }
    }

    private fun toggleVoice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
        }
        if (isListening) {
            voiceService.stopListening()
            isListening = false
            return
        }
        isListening = true
        voiceService.startListening(
            onResult = { text ->
                isListening = false
                sendMessage(text)
            },
            onDone = { isListening = false }
        )
    }

    private fun scrollBottom() {
        binding.chatList.post {
            if (adapter.itemCount > 0) binding.chatList.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun onResume() {
        super.onResume()
        aiService.init(appPrefs())
        updateApiWarning()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        voiceService.dispose()
        telegramService.stop()
    }
}
