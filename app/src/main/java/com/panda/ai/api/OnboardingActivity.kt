package com.panda.ai.api

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.panda.ai.api.services.AiService
import com.panda.ai.api.services.ScreenAutomationService
import kotlinx.coroutines.*

class OnboardingActivity : AppCompatActivity() {

    private lateinit var aiService: AiService
    private var step = 0

    private lateinit var apiKeyEdit: EditText
    private lateinit var baseUrlEdit: EditText
    private lateinit var modelEdit: EditText
    private var selectedProvider = "deepseek"
    private var isValidating = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        aiService = AiService().apply { init(appPrefs()) }

        apiKeyEdit = EditText(this).apply {
            hint = "sk-..."; inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(30, 24, 30, 24)
        }
        baseUrlEdit = EditText(this).apply {
            setText("https://api.deepseek.com"); setBackgroundResource(R.drawable.bg_bubble_assistant)
            setPadding(30, 24, 30, 24)
        }
        modelEdit = EditText(this).apply {
            setText("deepseek-chat"); setBackgroundResource(R.drawable.bg_bubble_assistant)
            setPadding(30, 24, 30, 24)
        }

        if (appPrefs().getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // If already configured, prefill and treat as custom provider.
        if (aiService.isConfigured) {
            selectedProvider = "custom"
            apiKeyEdit.setText(aiService.currentApiKey)
            baseUrlEdit.setText(aiService.currentBaseUrl)
            modelEdit.setText(aiService.currentModel)
        }

        showStep(0)
    }

    private fun showStep(s: Int) {
        step = s
        val container = findViewById<FrameLayout>(R.id.pageContainer)
        container.removeAllViews()
        val step0 = findViewById<View>(R.id.step0); val step1 = findViewById<View>(R.id.step1); val step2 = findViewById<View>(R.id.step2)
        val c0 = findViewById<TextView>(R.id.lblStep0); val c1 = findViewById<TextView>(R.id.lblStep1); val c2 = findViewById<TextView>(R.id.lblStep2)
        val active = ContextCompat.getColor(this, R.color.indigo_600)
        val inactive = ContextCompat.getColor(this, R.color.light_border)
        val list = listOf(step0, step1, step2); val labels = listOf(c0, c1, c2)
        list.forEachIndexed { i, v -> v.setBackgroundColor(if (i == s || i < s) active else inactive) }
        labels.forEachIndexed { i, v -> v.setTextColor(if (i == s || i < s) active else ContextCompat.getColor(this, R.color.light_subtext)) }

        when (s) {
            0 -> container.addView(welcomePage())
            1 -> container.addView(permissionsPage())
            2 -> container.addView(modelPage())
        }
    }

    private fun welcomePage(): View {
        val root = scrollWith(listOf(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_launcher_foreground)
                setColorFilter(ContextCompat.getColor(this@OnboardingActivity, R.color.indigo_600))
            },
            TextView(this).apply {
                text = "PrivateAgent"; textSize = 32f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
            },
            TextView(this).apply {
                text = "Your local, secure, and smart mobile companion. PrivateAgent can navigate apps, perform operations, and speak with you."
                gravity = Gravity.CENTER; setPadding(40, 20, 40, 20)
            },
            featureCard(R.drawable.ic_launcher_foreground, "Local & Private",
                "Full support for local-first execution. Keys remain encrypted locally."),
            featureCard(R.drawable.ic_launcher_foreground, "Automated Actions",
                "Can read your screen and perform operations across other apps."),
            button("Get Started") { showStep(1) }
        ))
        return root
    }

    private fun permissionsPage(): View {
        val mic = permissionCard("Microphone", "Required to listen to your voice commands", Manifest.permission.RECORD_AUDIO)
        val acc = permissionCard("Screen Control (Accessibility)", "Allows the AI to read your screen and automatically perform clicks, scrolls, and typing to execute tasks across other apps on your phone.", null, true)
        val notif = permissionCard("Notifications", "Allows PrivateAgent to show ongoing tasks, alerts, and execution updates in your notification tray.", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null)
        val contacts = permissionCard("Contacts", "Used to look up phone numbers and contact names when you ask the AI to call or text someone.", Manifest.permission.READ_CONTACTS)
        val phone = permissionCard("Phone", "Enables the AI to dial phone calls on your behalf when requested.", Manifest.permission.CALL_PHONE)
        val sms = permissionCard("SMS", "Allows the AI to send and read text messages on your behalf when requested.", Manifest.permission.SEND_SMS)

        val canProceed = ScreenAutomationService.isServiceRunning(this) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)

        val back = button("Back") { showStep(0) }
        val next = button("Next", enabled = canProceed) { showStep(2) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; addView(back); addView(next)
        }
        return scrollWith(listOf(mic, acc, notif, contacts, phone, sms, row))
    }

    private fun modelPage(): View {
        val providers = mapOf(
            "deepseek" to Pair("https://api.deepseek.com", "deepseek-chat"),
            "groq" to Pair("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            "nvidia" to Pair(AiService.NVIDIA_BASE_URL, AiService.NVIDIA_DEFAULT_MODEL),
            "ollama" to Pair("http://10.0.2.2:11434/v1", "gemma2"),
            "local" to Pair("http://10.0.2.2:1234/v1", "qwen2.5-7b-instruct"),
            "custom" to Pair("", "")
        )
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        providers.forEach { (id, pair) ->
            chipRow.addView(Button(this).apply {
                text = id.replaceFirstChar { it.uppercase() }
                setOnClickListener {
                    selectedProvider = id
                    if (pair.first.isNotEmpty()) { baseUrlEdit.setText(pair.first); modelEdit.setText(pair.second) }
                    else { baseUrlEdit.text.clear(); modelEdit.text.clear() }
                }
            })
        }
        val errText = TextView(this).apply { setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.red)); visibility = View.GONE }
        val finish = button("Finish Setup") {
            val apiKey = apiKeyEdit.text.toString().trim()
            val base = baseUrlEdit.text.toString().trim()
            val model = modelEdit.text.toString().trim()

            if (base.isEmpty() || model.isEmpty()) {
                errText.text = "Please fill out API Base URL and Model."; errText.visibility = View.VISIBLE; return@button
            }
            if (selectedProvider != "ollama" && selectedProvider != "local" && apiKey.isEmpty()) {
                errText.text = "API Key is required for this provider."; errText.visibility = View.VISIBLE; return@button
            }
            testAndSave(base, apiKey, model, errText)
        }
        return scrollWith(listOf(
            TextView(this).apply { text = "Configure AI Model"; textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD) },
            TextView(this).apply { text = "Select a provider to prefill API details automatically."; setPadding(0, 4, 0, 8) },
            chipRow, apiKeyEdit, baseUrlEdit, modelEdit, errText, finish
        ))
    }

    private fun testAndSave(base: String, apiKey: String, model: String, errText: TextView) {
        if (isValidating) return
        isValidating = true
        errText.visibility = View.GONE
        Toast.makeText(this, "Validating...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    aiService.fetchAvailableModels(base, apiKey)
                }
                if (models.isNotEmpty() || selectedProvider == "ollama" || selectedProvider == "local") {
                    aiService.saveSettings(appPrefs(), apiKey, base, model)
                    appPrefs().edit().putBoolean("onboarding_completed", true).apply()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@OnboardingActivity, "Configuration validated! Launching PrivateAgent...", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errText.text = "Failed to fetch models from the server. Verify base URL and API Key."
                        errText.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errText.text = "Error: ${e.message?.replaceFirst("Exception: ", "") ?: e}"
                    errText.visibility = View.VISIBLE
                }
            } finally {
                isValidating = false
            }
        }
    }

    private fun featureCard(icon: Int, title: String, subtitle: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(16, 16, 16, 16)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 12); layoutParams = p
        }
        val iconWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(10, 10, 10, 10)
        }
        iconWrap.addView(ImageView(this).apply { setImageResource(icon); setColorFilter(ContextCompat.getColor(this@OnboardingActivity, R.color.indigo_600)) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 0, 0, 0) }
        col.addView(TextView(this).apply { text = title; setTypeface(null, android.graphics.Typeface.BOLD); textSize = 14f })
        col.addView(TextView(this).apply { text = subtitle; textSize = 12f; setPadding(0, 3, 0, 0) })
        root.addView(iconWrap); root.addView(col)
        return root
    }

    private fun permissionCard(title: String, desc: String, perm: String?, isAccessibility: Boolean = false): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(30, 24, 30, 24)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 0, 0, 16); layoutParams = p
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            setColorFilter(ContextCompat.getColor(this@OnboardingActivity, R.color.indigo_600))
            val ip = LinearLayout.LayoutParams(20, 20); ip.setMargins(0, 0, 14, 0); layoutParams = ip
        })
        head.addView(TextView(this).apply {
            text = title; setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.indigo_600))
            setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val btn = Button(this).apply { text = "Grant" }
        head.addView(btn)
        root.addView(head)
        root.addView(TextView(this).apply { text = desc; textSize = 12.5f; setPadding(0, 10, 0, 0) })

        btn.setOnClickListener {
            if (isAccessibility) {
                AlertDialog.Builder(this@OnboardingActivity)
                    .setTitle("Enable Screen Control")
                    .setMessage("If Android shows \u201CRestricted setting\u201D, open App Info first, tap the three-dot menu, and choose \u201CAllow restricted settings\u201D. Then return and open Accessibility Settings to enable PrivateAgent Screen Control.")
                    .setPositiveButton("Accessibility Settings") { _, _ -> ScreenAutomationService.openAccessibilitySettings(this@OnboardingActivity) }
                    .setNegativeButton("Open App Info First") { _, _ -> openAppSettings() }
                    .show()
            } else if (perm != null && ContextCompat.checkSelfPermission(this@OnboardingActivity, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@OnboardingActivity, arrayOf(perm), 1)
            } else if (perm != null) {
                ActivityCompat.requestPermissions(this@OnboardingActivity, arrayOf(perm), 1)
            }
        }
        return root
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)))
        } catch (_: Exception) {}
    }

    private fun scrollWith(views: List<View>): View {
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        views.forEach { v ->
            val lp = v.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(lp.leftMargin, 8, lp.rightMargin, 8); v.layoutParams = lp
            inner.addView(v)
        }
        return ScrollView(this).apply { addView(inner) }
    }

    private fun button(text: String, enabled: Boolean = true, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text; isEnabled = enabled
            setOnClickListener { onClick() }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (step == 1) showStep(1)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
