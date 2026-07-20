package com.panda.ai.api

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.panda.ai.api.databinding.ActivitySettingsBinding
import com.panda.ai.api.services.*
import kotlinx.coroutines.runBlocking

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var aiService: AiService
    private lateinit var telegramService: TelegramService

    private lateinit var apiKeyEdit: EditText
    private lateinit var baseUrlEdit: EditText
    private lateinit var modelEdit: EditText
    private lateinit var telegramTokenEdit: EditText
    private lateinit var maxTokensEdit: EditText
    private var disableMaxSteps = false
    private var useScreenCompression = true
    private var useSystemPrompt = true
    private var temperature = 1.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        aiService = AiService().apply { init(getPreferences(MODE_PRIVATE)) }
        telegramService = TelegramService(this) { _, _ -> }.apply { init(getPreferences(MODE_PRIVATE)) }

        binding.topBar.setNavigationOnClickListener { finish() }

        buildUi()
    }

    private fun buildUi() {
        val c = binding.settingsContainer
        c.removeAllViews()

        // AI Engine Configuration
        c.addView(card("AI Engine Configuration", "Supports any OpenAI-compatible API endpoint") {
            val key = editText("API Key", "sk-...", aiService.currentApiKey, true)
            apiKeyEdit = key
            val url = editText("API Base URL", "https://api.deepseek.com", aiService.currentBaseUrl, false)
            baseUrlEdit = url
            val chips = chipRow(mapOf(
                "Local" to "http://192.168.1.X:8080/v1",
                "DeepSeek" to "https://api.deepseek.com",
                "Groq" to "https://api.groq.com/openai/v1",
                "NVIDIA" to AiService.NVIDIA_BASE_URL,
                "Custom" to ""
            )) { base -> if (base.isNotEmpty()) { baseUrlEdit.setText(base); if (base == AiService.NVIDIA_BASE_URL) modelEdit.setText(AiService.NVIDIA_DEFAULT_MODEL) } else { baseUrlEdit.text.clear(); apiKeyEdit.text.clear(); modelEdit.text.clear() } }

            val model = editText("Model", "deepseek-chat", aiService.currentModel, false)
            modelEdit = model
            val fetch = Button(this).apply {
                text = "Fetch"
                setOnClickListener { fetchModels() }
            }
            val modelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(model, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(fetch)
            }
            listOf(key, url, chips, modelRow)
        })

        // Tuning & Boundaries
        c.addView(card("Tuning & Boundaries", "Configure LLM agent parameters") {
            val disableSwitch = switchRow("Disable Maximum Steps", "⚠️ Can cause infinite loops.", aiService.currentDisableMaxSteps) {
                disableMaxSteps = it; autoSave()
            }
            val maxStepsSlider = sliderRow("Maximum Steps Per Task", aiService.rawMaxSteps, 5, 50) {
                aiService.saveMaxSteps(getPreferences(MODE_PRIVATE), it); autoSave()
            }
            maxTokensEdit = editText("Context Limit (Max Tokens)", "1024", aiService.currentMaxTokens.toString(), false)
            val tempSlider = sliderRow("Temperature", (aiService.currentTemperature * 100).toInt(), 0, 200) {
                temperature = it / 100.0; autoSave()
            }
            listOf(disableSwitch, maxStepsSlider, maxTokensEdit, tempSlider)
        })

        // Behavior & Extensions
        c.addView(card("Behavior & Extensions", "Additional feature flags") {
            val sc = switchRow("Use Screen Compression", "Removes duplicate elements to save tokens", aiService.currentUseScreenCompression) {
                useScreenCompression = it; autoSave()
            }
            val sp = switchRow("Send System Prompt", "Turn off for custom LoRA fine-tunes", aiService.currentUseSystemPrompt) {
                useSystemPrompt = it; autoSave()
            }
            listOf(sc, sp)
        })

        // Telegram
        c.addView(card("Telegram Remote Access", "Control your agent remotely") {
            telegramTokenEdit = editText("Telegram Bot Token", "123456:ABC-DEF...", telegramService.currentBotToken, true)
            val enSwitch = switchRow("Enable Telegram Bot", "Allows remote control via Telegram chat", telegramService.isEnabled) {
                telegramService.saveSettings(getPreferences(MODE_PRIVATE), telegramTokenEdit.text.toString(), it)
                if (it) telegramService.start()
            }
            listOf(telegramTokenEdit, enSwitch)
        })

        // Screen Control
        c.addView(card("Screen Control (Accessibility)", "Required to read screen and perform automated clicks") {
            val status = TextView(this).apply {
                text = if (ScreenAutomationService.isServiceRunning(this@SettingsActivity)) "Screen Control is active" else "Screen Control is disabled"
            }
            val btn = Button(this).apply {
                text = "Open Accessibility Settings"
                setOnClickListener { ScreenAutomationService.openAccessibilitySettings(this@SettingsActivity) }
            }
            listOf(status, btn)
        })

        // Permissions
        c.addView(card("App Permissions", "Required for automation, microphone, contacts") {
            listOf(
                permissionRow("Microphone", Manifest.permission.RECORD_AUDIO),
                permissionRow("Contacts", Manifest.permission.READ_CONTACTS),
                permissionRow("Phone", Manifest.permission.CALL_PHONE),
                permissionRow("SMS", Manifest.permission.SEND_SMS),
                permissionRow("Notifications", null)
            )
        })

        // Task History
        c.addView(card("Execution logs", "View history of tasks") {
            listOf(Button(this).apply {
                text = "View Task History"
                setOnClickListener { startActivity(Intent(this@SettingsActivity, TaskHistoryActivity::class.java)) }
            })
        })

        // About
        c.addView(card("About PrivateAgent", "Resources") {
            listOf(TextView(this).apply { text = "PrivateAgent — local, secure, smart mobile companion." })
        })
    }

    private fun autoSave() {
        aiService.saveSettings(getPreferences(MODE_PRIVATE), apiKeyEdit.text.toString(), baseUrlEdit.text.toString(), modelEdit.text.toString())
        aiService.saveAdvancedSettings(getPreferences(MODE_PRIVATE), temperature, maxTokensEdit.text.toString().toIntOrNull() ?: 1024, useScreenCompression, useSystemPrompt)
        telegramService.saveSettings(getPreferences(MODE_PRIVATE), telegramTokenEdit.text.toString(), telegramService.isEnabled)
    }

    private fun fetchModels() {
        val base = baseUrlEdit.text.toString().trim()
        val key = apiKeyEdit.text.toString().trim()
        if (base.isEmpty() || key.isEmpty()) { Toast.makeText(this, "Enter Base URL and API Key first", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Fetching models...", Toast.LENGTH_SHORT).show()
        Thread {
            val models = runBlocking { aiService.fetchAvailableModels(base, key) }
            runOnUiThread {
                if (models.isEmpty()) { Toast.makeText(this, "No models found", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                val items = models.toTypedArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle(if (AiService.isNvidiaBaseUrl(base)) "Select a Free NVIDIA Model" else "Select a Model")
                    .setItems(items) { _, i -> modelEdit.setText(items[i]) }
                    .show()
            }
        }.start()
    }

    // --- UI builders ---
    private fun card(title: String, subtitle: String, content: () -> List<android.view.View>): android.view.View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        val root = android.widget.LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bubble_assistant)
            setPadding(20, 20, 20, 20)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 40); layoutParams = p
        }
        val t = TextView(this).apply { text = title; textSize = 16f; setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.indigo_600)); setTypeface(null, android.graphics.Typeface.BOLD) }
        val s = TextView(this).apply { text = subtitle; textSize = 12f; setPadding(0, 4, 0, 16) }
        root.addView(t); root.addView(s)
        content().forEach { root.addView(it.apply { val lp = it.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 8, 0, 8); it.layoutParams = lp }) }
        return root
    }

    private fun editText(label: String, hint: String, value: String, password: Boolean): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            inputType = if (password) android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD else android.text.InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_bubble_assistant)
            setPadding(30, 24, 30, 24)
            tag = label
        }
    }

    private fun chipRow(options: Map<String, String>, onClick: (String) -> Unit): android.view.View {
        val row = android.widget.TableLayout(this)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { (label, base) ->
            val chip = Button(this).apply { text = label; setOnClickListener { onClick(base) } }
            inner.addView(chip)
        }
        return inner
    }

    private fun switchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): android.view.View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        textCol.addView(TextView(this).apply { text = title })
        textCol.addView(TextView(this).apply { text = subtitle; textSize = 12f })
        val sw = Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, b -> onChange(b) } }
        row.addView(textCol); row.addView(sw)
        return row
    }

    private fun sliderRow(title: String, value: Int, min: Int, maxVal: Int, onChange: (Int) -> Unit): android.view.View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(this).apply { text = "$title: $value"; setPadding(0, 8, 0, 0) }
        val slider = SeekBar(this).apply { progress = value - min; max = maxVal - min; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { label.text = "$title: ${p + min}"; onChange(p + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }) }
        col.addView(label); col.addView(slider)
        return col
    }

    private fun permissionRow(name: String, perm: String?): android.view.View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val label = TextView(this).apply { text = name; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val btn = Button(this).apply {
            text = "Grant"
            setOnClickListener {
                if (perm != null && ContextCompat.checkSelfPermission(this@SettingsActivity, perm) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@SettingsActivity, arrayOf(perm), 1)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && name == "Notifications") {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                } else Toast.makeText(this@SettingsActivity, "$name already granted", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(label); row.addView(btn)
        return row
    }

    override fun onResume() {
        super.onResume()
        aiService.init(getPreferences(MODE_PRIVATE))
        buildUi()
    }
}
