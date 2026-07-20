package com.panda.ai.api

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var aiService: AiService
    private lateinit var actionHandler: ActionHandler
    private lateinit var voiceService: VoiceService
    private lateinit var telegramService: TelegramService
    private lateinit var markwon: Markwon

    private val messages = mutableListOf<ChatMessage>()
    private var mode = "chat"
    private var sessionId = System.currentTimeMillis().toString()
    private var sessionTitle = ""
    private var isLoading = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Toast.makeText(this, if (granted) "Mic granted" else "Mic denied", Toast.LENGTH_SHORT).show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        markwon = Markwon.create(this)
        aiService = AiService().apply { init(getPreferences(MODE_PRIVATE)) }
        actionHandler = ActionHandler(this)
        voiceService = VoiceService(this).apply { init() }
        telegramService = TelegramService(this) { cmd -> runOnUiThread { sendMessage(cmd) } }.apply {
            init(getPreferences(MODE_PRIVATE))
            start()
        }

        adapter = ChatAdapter(markwon)
        binding.chatList.layoutManager = LinearLayoutManager(this)
        binding.chatList.adapter = adapter

        binding.btnModeChat.setOnClickListener { setMode("chat") }
        binding.btnModeAgent.setOnClickListener { setMode("agent") }
        setMode("chat")

        binding.btnMic.setOnClickListener { toggleVoice() }
        binding.btnSend.setOnClickListener { sendMessage(binding.inputText.text.toString()) }
        binding.inputText.setOnEditorActionListener { _, _, _ -> sendMessage(binding.inputText.text.toString()); true }

        binding.btnConfigure.setOnClickListener { openSettings() }
        binding.topBar.setNavigationOnClickListener { binding.drawer.openDrawer(binding.navView) }
        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_new_chat -> startNewChat()
                R.id.nav_history -> startActivity(android.content.Intent(this, TaskHistoryActivity::class.java))
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
        binding.apiWarning.visibility = if (aiService.isConfigured) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun openSettings() {
        startActivity(android.content.Intent(this, SettingsActivity::class.java))
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
            sessionTitle = messages.first { it.isUser() }.content.let {
                if (it.length > 25) "${it.substring(0, 25)}..." else it
            }
        }
        val session = ChatSession(sessionId, sessionTitle, System.currentTimeMillis(), messages.toList())
        ChatHistoryService.saveSession(this, session)
    }

    private fun sendMessage(text: String) {
        if (text.trim().isEmpty()) return
        val userMsg = ChatMessage("user", text.trim())
        messages.add(userMsg)
        adapter.addMessage(userMsg)
        binding.inputText.text.clear()
        scrollBottom()
        saveSession()

        val assistantMsg = ChatMessage("assistant", "")
        messages.add(assistantMsg)
        adapter.addMessage(assistantMsg)
        val assistantIdx = messages.lastIndex

        isLoading = true
        binding.thinking.visibility = android.view.View.VISIBLE

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
                val action = aiService.parseAction(accumulated)
                if (action != null) {
                    messages.removeAt(assistantIdx)
                    adapter.removeLast()
                    handleAction(action)
                } else {
                    voiceService.speak(accumulated)
                }
            } catch (e: Exception) {
                if (messages.isNotEmpty()) messages.removeAt(messages.lastIndex)
                adapter.removeLast()
                messages.add(ChatMessage("assistant", "Error: ${e.message ?: "Unknown"}"))
                adapter.addMessage(messages.last())
            } finally {
                isLoading = false
                binding.thinking.visibility = android.view.View.GONE
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
            binding.thinking.visibility = android.view.View.GONE
        }
    }

    private fun handleAction(action: AgentAction) {
        scope.launch {
            val result = actionHandler.execute(action, aiService = aiService) { progress ->
                runOnUiThread {
                    messages.add(ChatMessage("assistant", "⏳ $progress"))
                    adapter.addMessage(messages.last())
                    scrollBottom()
                }
            }
            runOnUiThread {
                messages.add(ChatMessage("assistant", formatActionResult(action, result), actionResult = result))
                adapter.addMessage(messages.last())
                scrollBottom()
                if (action.action != "execute_task") {
                    NotificationService.showTaskCompleteNotification(
                        this@MainActivity,
                        if (result.success) "Task Completed" else "Task Failed",
                        result.details ?: ""
                    )
                }
                saveSession()
            }
        }
    }

    private fun formatActionResult(action: AgentAction, result: AgentActionResult): String {
        return if (result.success) {
            if (action.response.isNotEmpty()) action.response else (result.details ?: "Done.")
        } else {
            if (action.response.isNotEmpty()) "${action.response}\n\n⚠️ ${result.details}" else "⚠️ ${result.details}"
        }
    }

    private fun toggleVoice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return
            }
        }
        if (voiceService.isListening) { voiceService.stopListening(); return }
        voiceService.startListening(
            onResult = { text -> sendMessage(text) },
            onDone = {}
        )
    }

    private fun scrollBottom() {
        binding.chatList.post {
            if (adapter.itemCount > 0) binding.chatList.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun onResume() {
        super.onResume()
        aiService.init(getPreferences(MODE_PRIVATE))
        updateApiWarning()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        voiceService.dispose()
        telegramService.stop()
    }
}
