package com.panda.ai.api

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

        aiService = AiService().apply { init(appPrefs()) }
        telegramService = TelegramService(this) { _, _ -> }.apply { init(appPrefs()) }

        binding.topBar.setNavigationOnClickListener { finish() }

        buildUi()
    }

    private fun buildUi() {
        val c = binding.settingsContainer
        c.removeAllViews()

        // 1. Appearance Card
        c.addView(card("Appearance", "Choose your preferred color theme") {
            val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
            val modes = listOf("System" to "system", "Light" to "light", "Dark" to "dark")
            val current = appPrefs().getString("themeMode", "system") ?: "system"
            modes.forEach { (label, value) ->
                val rb = RadioButton(this).apply {
                    text = label; this.tag = value
                    isChecked = value == current
                }
                group.addView(rb)
            }
            group.setOnCheckedChangeListener { _, _ ->
                val sel = group.findViewById<RadioButton>(group.checkedRadioButtonId).tag as String
                appPrefs().edit().putString("themeMode", sel).apply()
            }
            listOf(group)
        })

        // 2. AI Engine Configuration
        c.addView(card("AI Engine Configuration", "Supports any OpenAI-compatible API endpoint") {
            val key = editText("API Key", "sk-...", aiService.currentApiKey, true)
            apiKeyEdit = key
            val url = editText("API Base URL", "https://api.deepseek.com", aiService.currentBaseUrl, false)
            baseUrlEdit = url

            val chips = chipRow(mapOf(
                "DeepSeek" to "https://api.deepseek.com",
                "Groq" to "https://api.groq.com/openai/v1",
                "NVIDIA" to AiService.NVIDIA_BASE_URL,
                "Ollama Cloud" to "https://ollama.com/v1",
                "Local Server" to "http://192.168.1.X:8080/v1",
                "Custom" to ""
            )) { base, model ->
                if (base.isNotEmpty()) {
                    baseUrlEdit.setText(base)
                    if (model.isNotEmpty()) modelEdit.setText(model)
                } else { baseUrlEdit.text.clear(); apiKeyEdit.text.clear(); modelEdit.text.clear() }
            }

            val model = editText("Model", "deepseek-chat", aiService.currentModel, false)
            modelEdit = model
            val fetch = Button(this).apply { text = "Fetch"; setOnClickListener { fetchModels() } }
            val modelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(model, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(fetch)
            }
            listOf(key, url, chips, modelRow)
        })

        // 3. Tuning & Boundaries
        c.addView(card("Tuning & Boundaries", "Configure LLM agent parameters") {
            val disableSwitch = switchRow("Disable Maximum Steps",
                "⚠️ Can cause infinite loops.", aiService.currentDisableMaxSteps) {
                disableMaxSteps = it; autoSave()
            }
            val maxStepsSlider = sliderRow("Maximum Steps Per Task", aiService.rawMaxSteps, 5, 50) {
                aiService.saveMaxSteps(appPrefs(), it); autoSave()
            }
            maxTokensEdit = editText("Context Limit (Max Tokens)", "1024", aiService.currentMaxTokens.toString(), false)
            val tempSlider = sliderRow("Temperature", (aiService.currentTemperature * 100).toInt(), 0, 200) {
                temperature = it / 100.0; autoSave()
            }
            // Gating: hide max-steps slider when disabled (mirrors Dart's conditional).
            if (!aiService.currentDisableMaxSteps) listOf(disableSwitch, maxStepsSlider, maxTokensEdit, tempSlider)
            else listOf(disableSwitch, maxTokensEdit, tempSlider)
        })

        // 4. Behavior & Extensions
        c.addView(card("Behavior & Extensions", "Additional feature flags and overlay options") {
            val sc = switchRow("Use Screen Compression", "Removes duplicate elements to save tokens",
                aiService.currentUseScreenCompression) {
                useScreenCompression = it; autoSave()
            }
            val sp = switchRow("Send System Prompt", "Turn off for custom LoRA fine-tunes",
                aiService.currentUseSystemPrompt) {
                useSystemPrompt = it; autoSave()
            }
            listOf(sc, sp)
        })

        // 5. Telegram Remote Access
        c.addView(card("Telegram Remote Access", "Control your agent remotely from anywhere") {
            telegramTokenEdit = editText("Telegram Bot Token", "123456:ABC-DEF...", telegramService.currentBotToken, true)
            val enSwitch = switchRow("Enable Telegram Bot", "Allows remote control via Telegram chat",
                telegramService.isEnabled) {
                telegramService.saveSettings(appPrefs(), telegramTokenEdit.text.toString(), it)
                if (it) telegramService.start()
            }
            listOf(telegramTokenEdit, enSwitch)
        })

        // 6. Screen Control (Accessibility)
        c.addView(card("Screen Control (Accessibility)", "Required to read screen and perform automated clicks") {
            val running = ScreenAutomationService.isServiceRunning(this@SettingsActivity)
            val status = TextView(this).apply {
                text = if (running) "Screen Control is active" else "Screen Control is disabled"
            }
            val btn = Button(this).apply {
                text = "Open Accessibility Settings"
                setOnClickListener { ScreenAutomationService.openAccessibilitySettings(this@SettingsActivity) }
            }
            listOf(status, btn)
        })

        // 7. App Permissions
        c.addView(card("App Permissions", "Required for automation, microphone, and contacts") {
            listOf(
                permissionRow("Microphone", Manifest.permission.RECORD_AUDIO),
                permissionRow("Contacts", Manifest.permission.READ_CONTACTS),
                permissionRow("Phone", Manifest.permission.CALL_PHONE),
                permissionRow("SMS", Manifest.permission.SEND_SMS),
                permissionRow("Notifications", null)
            )
        })

        // 8. Execution logs
        c.addView(card("Execution logs", "View history of tasks and token analytics") {
            listOf(Button(this).apply {
                text = "View Task History"
                setOnClickListener { startActivity(Intent(this@SettingsActivity, TaskHistoryActivity::class.java)) }
            })
        })

        // 9. About
        c.addView(card("About PrivateAgent", "Resources and repository access") {
            listOf(
                linkRow("Project Repository", "View source code on GitHub") {
                    openUrl("https://github.com/orailnoor/private-agent")
                },
                linkRow("Orailnoor on YouTube", "Subscribe for tutorials and updates") {
                    openUrl("https://www.youtube.com/orailnoor")
                },
                linkRow("Tech Jarves on YouTube", "Subscribe for tutorials and updates") {
                    openUrl("https://www.youtube.com/techjarves")
                }
            )
        })
    }

    private fun autoSave() {
        aiService.saveSettings(appPrefs(), apiKeyEdit.text.toString(), baseUrlEdit.text.toString(), modelEdit.text.toString())
        aiService.saveAdvancedSettings(appPrefs(), temperature, maxTokensEdit.text.toString().toIntOrNull() ?: 1024, useScreenCompression, useSystemPrompt)
        telegramService.saveSettings(appPrefs(), telegramTokenEdit.text.toString(), telegramService.isEnabled)
    }

    private fun fetchModels() {
        val base = baseUrlEdit.text.toString().trim()
        val key = apiKeyEdit.text.toString().trim()
        if (base.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "Please enter Base URL and API Key first.", Toast.LENGTH_SHORT).show(); return
        }
        val dialog = android.app.ProgressDialog.show(this, null, "Fetching models...", true)
        Thread {
            val models = runBlocking { aiService.fetchAvailableModels(base, key) }
            runOnUiThread {
                dialog.dismiss()
                if (models.isEmpty()) {
                    Toast.makeText(this, "No models found or error fetching models.", Toast.LENGTH_SHORT).show(); return@runOnUiThread
                }
                val items = models.toTypedArray()
                android.app.AlertDialog.Builder(this)
                    .setTitle(if (AiService.isNvidiaBaseUrl(base)) "Select a Free NVIDIA Model" else "Select a Model")
                    .setItems(items) { _, i -> modelEdit.setText(items[i]) }
                    .show()
            }
        }.start()
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    // --- UI builders ---
    private fun card(title: String, subtitle: String, content: () -> List<View>): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bubble_assistant)
            setPadding(20, 20, 20, 20)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 40); layoutParams = p
        }
        val t = TextView(this).apply { text = title; textSize = 16f; setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.indigo_600)); setTypeface(null, android.graphics.Typeface.BOLD) }
        val s = TextView(this).apply { text = subtitle; textSize = 12f; setPadding(0, 4, 0, 16) }
        root.addView(t); root.addView(s)
        content().forEach { v ->
            val lp = v.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 8); v.layoutParams = lp
            root.addView(v)
        }
        return root
    }

    private fun editText(label: String, hint: String, value: String, password: Boolean): EditText {
        return EditText(this).apply {
            setText(value); this.hint = hint
            inputType = if (password) android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD else android.text.InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_bubble_assistant)
            setPadding(30, 24, 30, 24)
            tag = label
        }
    }

    private fun chipRow(options: Map<String, String>, onClick: (String, String) -> Unit): View {
        val inner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { (label, base) ->
            val model = when (label) {
                "Ollama Cloud" -> "gemma3:4b"
                "NVIDIA" -> AiService.NVIDIA_DEFAULT_MODEL
                else -> ""
            }
            val chip = Button(this).apply {
                text = label; textSize = 11f
                setOnClickListener { onClick(base, model) }
            }
            inner.addView(chip)
        }
        return inner
    }

    private fun switchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        textCol.addView(TextView(this).apply { text = title })
        textCol.addView(TextView(this).apply { text = subtitle; textSize = 12f })
        val sw = Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, b -> onChange(b) } }
        row.addView(textCol); row.addView(sw)
        return row
    }

    private fun sliderRow(title: String, value: Int, min: Int, maxVal: Int, onChange: (Int) -> Unit): View {
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

    private fun permissionRow(name: String, perm: String?): View {
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

    private fun linkRow(title: String, subtitle: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4, 0, 4) }
        row.addView(TextView(this).apply { text = title; setTypeface(null, android.graphics.Typeface.BOLD) })
        row.addView(TextView(this).apply { text = subtitle; textSize = 12f })
        row.setOnClickListener { onClick() }
        return row
    }

    override fun onResume() {
        super.onResume()
        aiService.init(appPrefs())
        buildUi()
    }
}
