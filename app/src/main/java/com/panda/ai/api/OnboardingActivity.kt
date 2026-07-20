package com.panda.ai.api

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.panda.ai.api.services.AiService
import com.panda.ai.api.services.ScreenAutomationService

class OnboardingActivity : AppCompatActivity() {

    private lateinit var aiService: AiService
    private var step = 0

    private lateinit var apiKeyEdit: EditText
    private lateinit var baseUrlEdit: EditText
    private lateinit var modelEdit: EditText
    private var selectedProvider = "deepseek"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        aiService = AiService().apply { init(getPreferences(MODE_PRIVATE)) }

        apiKeyEdit = EditText(this).apply { hint = "sk-..."; setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(30, 24, 30, 24) }
        baseUrlEdit = EditText(this).apply { setText("https://api.deepseek.com"); setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(30, 24, 30, 24) }
        modelEdit = EditText(this).apply { setText("deepseek-chat"); setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(30, 24, 30, 24) }

        if (getPreferences(MODE_PRIVATE).getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        showStep(0)
    }

    private fun showStep(s: Int) {
        step = s
        val container = findViewById<android.widget.FrameLayout>(R.id.pageContainer)
        container.removeAllViews()
        val step0 = findViewById<View>(R.id.step0); val step1 = findViewById<View>(R.id.step1); val step2 = findViewById<View>(R.id.step2)
        val c0 = findViewById<TextView>(R.id.lblStep0); val c1 = findViewById<TextView>(R.id.lblStep1); val c2 = findViewById<TextView>(R.id.lblStep2)
        val active = ContextCompat.getColor(this, R.color.indigo_600)
        val inactive = ContextCompat.getColor(this, R.color.light_border)
        val list = listOf(step0, step1, step2); val labels = listOf(c0, c1, c2)
        list.forEachIndexed { i, v -> v.setBackgroundColor(if (i == s) active else if (i < s) active else inactive) }
        labels.forEachIndexed { i, v -> v.setTextColor(if (i == s) active else if (i < s) active else ContextCompat.getColor(this, R.color.light_subtext)) }

        when (s) {
            0 -> container.addView(welcomePage())
            1 -> container.addView(permissionsPage())
            2 -> container.addView(modelPage())
        }
    }

    private fun welcomePage(): android.view.View {
        val root = scrollWith(listOf(
            ImageView(this).apply { setImageResource(R.drawable.ic_launcher_foreground); setColorFilter(ContextCompat.getColor(this@OnboardingActivity, R.color.indigo_600)) },
            TextView(this).apply { text = "PrivateAgent"; textSize = 32f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = android.view.Gravity.CENTER },
            TextView(this).apply { text = "Your local, secure, smart mobile companion. Can navigate apps, perform operations, and speak with you."; gravity = android.view.Gravity.CENTER; setPadding(40, 20, 40, 20) },
            button("Get Started") { showStep(1) }
        ))
        return root
    }

    private fun permissionsPage(): android.view.View {
        val mic = permissionCard("Microphone", "Required to listen to your voice commands", Manifest.permission.RECORD_AUDIO)
        val acc = permissionCard("Screen Control (Accessibility)", "Allows AI to read screen and perform clicks", null, true)
        val notif = permissionCard("Notifications", "Shows ongoing tasks and alerts", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null)
        val contacts = permissionCard("Contacts", "Look up phone numbers", Manifest.permission.READ_CONTACTS)
        val phone = permissionCard("Phone", "Dial calls", Manifest.permission.CALL_PHONE)
        val sms = permissionCard("SMS", "Send text messages", Manifest.permission.SEND_SMS)
        val back = button("Back") { showStep(0) }
        val next = button("Next") { showStep(2) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(back); addView(next) }
        return scrollWith(listOf(mic, acc, notif, contacts, phone, sms, row))
    }

    private fun modelPage(): android.view.View {
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
        val finish = button("Finish Setup") {
            val base = baseUrlEdit.text.toString().trim(); val model = modelEdit.text.toString().trim()
            if (base.isEmpty() || model.isEmpty()) { Toast.makeText(this, "Fill Base URL and Model", Toast.LENGTH_SHORT).show(); return@button }
            if (selectedProvider != "ollama" && selectedProvider != "local" && apiKeyEdit.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "API Key required", Toast.LENGTH_SHORT).show(); return@button
            }
            aiService.saveSettings(getPreferences(MODE_PRIVATE), apiKeyEdit.text.toString(), base, model)
            getPreferences(MODE_PRIVATE).edit().putBoolean("onboarding_completed", true).apply()
            Toast.makeText(this, "Launching PrivateAgent...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        return scrollWith(listOf(
            TextView(this).apply { text = "Select a provider:" },
            chipRow,
            apiKeyEdit, baseUrlEdit, modelEdit, finish
        ))
    }

    private fun permissionCard(title: String, desc: String, perm: String?, isAccessibility: Boolean = false): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_bubble_assistant); setPadding(30, 24, 30, 24)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, 0, 0, 16); layoutParams = p
        }
        root.addView(TextView(this).apply { text = title; setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.indigo_600)); setTypeface(null, android.graphics.Typeface.BOLD) })
        root.addView(TextView(this).apply { text = desc; textSize = 12f; setPadding(0, 4, 0, 8) })
        val btn = Button(this).apply {
            text = "Grant"
            setOnClickListener {
                if (isAccessibility) ScreenAutomationService.openAccessibilitySettings(this@OnboardingActivity)
                else if (perm != null && ContextCompat.checkSelfPermission(this@OnboardingActivity, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    ActivityCompat.requestPermissions(this@OnboardingActivity, arrayOf(perm), 1)
                else if (perm != null) ActivityCompat.requestPermissions(this@OnboardingActivity, arrayOf(perm), 1)
            }
        }
        root.addView(btn)
        return root
    }

    private fun scrollWith(views: List<android.view.View>): android.view.View {
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        views.forEach { v ->
            val lp = v.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(lp.leftMargin, 8, lp.rightMargin, 8); v.layoutParams = lp
            inner.addView(v)
        }
        return android.widget.ScrollView(this).apply { addView(inner) }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply { this.text = text; setOnClickListener { onClick() } }
    }
}
