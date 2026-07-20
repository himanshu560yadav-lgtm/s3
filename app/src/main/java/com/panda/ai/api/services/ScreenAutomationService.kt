package com.panda.ai.api.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.bundleOf
import java.util.Locale

class ScreenAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = -1
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (flags
                or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS)
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        private var instance: ScreenAutomationService? = null

        fun setInstance(svc: ScreenAutomationService?) { instance = svc }

        fun isServiceRunning(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(":").any { it.equals("${context.packageName}/.services.ScreenAutomationService", true) }
        }

        fun openAccessibilitySettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        fun getScreenDescription(): String {
            val root = instance?.rootInActiveWindow ?: return "Could not read screen. Make sure accessibility service is enabled."
            val nodes = mutableListOf<NodeInfo>()
            collect(root, nodes, 0)
            if (nodes.isEmpty()) return "Could not read screen. Make sure accessibility service is enabled."
            val sb = StringBuilder()
            val pkg = getCurrentPackage()
            if (pkg.isNotEmpty()) sb.appendLine("Current app: $pkg")
            sb.appendLine("Screen elements:")
            var count = 0
            for (n in nodes) {
                val display = n.text.ifEmpty { n.desc }
                if (display.isEmpty() && !n.clickable && !n.editable && !n.scrollable) continue
                var d = display
                if (d.length > 200) d = d.substring(0, 200) + "..."
                val tags = mutableListOf<String>()
                if (n.clickable) tags.add("clickable")
                if (n.editable) tags.add("editable")
                if (n.scrollable) tags.add("scrollable")
                val label = if (d.isNotEmpty()) "\"$d\"" else "(no text)"
                val type = if (n.cls.isNotEmpty()) "[${n.cls}]" else ""
                val tagStr = if (tags.isNotEmpty()) "{${tags.joinToString(", ")}}" else ""
                val b = n.bounds
                val cx = (b.left + b.right) / 2
                val cy = (b.top + b.bottom) / 2
                val boundsStr = " bounds:[${b.left},${b.top},${b.right},${b.bottom}] center:($cx,$cy)"
                sb.appendLine("  [${n.index}] $type $label $tagStr$boundsStr")
                count++
            }
            return sb.toString()
        }

        fun getCompressedScreenDescription(goal: String): String {
            val root = instance?.rootInActiveWindow ?: return "Could not read screen. Make sure accessibility service is enabled."
            val nodes = mutableListOf<NodeInfo>()
            collect(root, nodes, 0)
            if (nodes.isEmpty()) return "Could not read screen. Make sure accessibility service is enabled."
            val sb = StringBuilder()
            val pkg = getCurrentPackage()
            if (pkg.isNotEmpty()) sb.appendLine("APP: $pkg")

            val stopWords = setOf("to", "and", "the", "a", "in", "of", "for", "on", "with", "at", "by", "from", "go", "turn", "open")
            val keywords = goal.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+")).filter { it.isNotEmpty() && it !in stopWords }

            for (n in nodes) {
                val display = n.text.ifEmpty { n.desc }
                val lower = display.lowercase()
                if (lower.contains("battery") || lower.contains("percent") ||
                    lower.contains("do not disturb") || lower.contains("three bars") ||
                    lower == "stop macro" || Regex("^\\d{1,2}:\\d{2}$").matches(lower)) continue
                if (display.isEmpty() && !n.clickable && !n.editable && !n.scrollable) continue

                var d = display
                if (d.length > 50) d = d.substring(0, 50) + "..."
                val tags = mutableListOf<String>()
                if (n.clickable) tags.add("tap")
                if (n.editable) tags.add("edit")
                if (n.scrollable) tags.add("scroll")
                var type = n.cls.substringAfterLast('.').lowercase()
                when (type) {
                    "textview" -> type = "text"
                    "button" -> type = "btn"
                    "switch" -> type = "toggle"
                    "imageview" -> type = "img"
                    "edittext" -> type = "input"
                    "framelayout", "linearlayout" -> type = "view"
                }
                val label = if (d.isNotEmpty()) "\"$d\"" else ""
                val tagStr = if (tags.isNotEmpty()) "[${tags.joinToString(",")}]" else ""
                val isTarget = d.isNotEmpty() && keywords.any { lower.contains(it) }
                val mark = if (isTarget) "*" else ""
                val b = n.bounds
                val cx = (b.left + b.right) / 2
                val cy = (b.top + b.bottom) / 2
                val boundsStr = " center:($cx,$cy)"
                val line = "[${n.index}]$mark $type $label $tagStr$boundsStr"
                sb.appendLine(line.replace(Regex("\\s+"), " "))
            }
            return sb.toString()
        }

        private data class NodeInfo(
            val index: Int, val cls: String, val text: String, val desc: String,
            val clickable: Boolean, val editable: Boolean, val scrollable: Boolean,
            val bounds: android.graphics.Rect
        )

        private fun collect(node: AccessibilityNodeInfo?, out: MutableList<NodeInfo>, index: Int) {
            if (node == null) return
            val b = android.graphics.Rect()
            node.getBoundsInScreen(b)
            out.add(NodeInfo(
                index = index,
                cls = node.className?.toString() ?: "",
                text = node.text?.toString() ?: "",
                desc = node.contentDescription?.toString() ?: "",
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                bounds = b
            ))
            var i = 0
            while (i < node.childCount) { collect(node.getChild(i), out, i); i++ }
        }

        fun clickByText(text: String): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            return clickRecursive(root, text.lowercase())
        }

        private fun clickRecursive(node: AccessibilityNodeInfo?, text: String): Boolean {
            if (node == null) return false
            val label = (node.text?.toString() ?: node.contentDescription?.toString())?.lowercase()
            if (label != null && label.contains(text)) {
                if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                var p = node.parent
                while (p != null) {
                    if (p.isClickable) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                    p = p.parent
                }
            }
            for (i in 0 until node.childCount) if (clickRecursive(node.getChild(i), text)) return true
            return false
        }

        fun typeText(text: String, fieldHint: String? = null): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            val target = findEditable(root, fieldHint)
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = bundleOf(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE to text)
                return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            return false
        }

        private fun findEditable(node: AccessibilityNodeInfo?, hint: String?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isEditable) {
                if (hint == null) return node
                val h = node.hintText?.toString()?.lowercase() ?: ""
                if (h.contains(hint.lowercase())) return node
            }
            for (i in 0 until node.childCount) {
                val r = findEditable(node.getChild(i), hint)
                if (r != null) return r
            }
            return null
        }

        fun pressBack(): Boolean {
            instance ?: return false
            instance?.performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        fun pressHome(): Boolean {
            instance?.performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }

        fun pressEnter(): Boolean {
            val root = instance?.rootInActiveWindow ?: return false
            return typeKey(root, "\n") || typeKey(root, "\r")
        }

        private fun typeKey(node: AccessibilityNodeInfo?, key: String): Boolean {
            if (node == null) return false
            if (node.isEditable) {
                val args = bundleOf(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE to key)
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            for (i in 0 until node.childCount) if (typeKey(node.getChild(i), key)) return true
            return false
        }

        fun scroll(direction: String): Boolean {
            instance ?: return false
            val action = if (direction.lowercase() == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            instance?.rootInActiveWindow?.performAction(action)
            return true
        }

        fun clickAt(x: Float, y: Float): Boolean {
            instance?.dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder().apply {
                    addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                        android.graphics.Path().apply { moveTo(x, y) }, 0, 1
                    ))
                }.build(), null, null
            )
            return true
        }

        fun swipe(sx: Float, sy: Float, ex: Float, ey: Float): Boolean {
            instance?.dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder().apply {
                    addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                        android.graphics.Path().apply { moveTo(sx, sy); lineTo(ex, ey) }, 0, 300
                    ))
                }.build(), null, null
            )
            return true
        }

        fun getCurrentPackage(): String {
            return instance?.rootInActiveWindow?.packageName?.toString() ?: ""
        }

        fun showToast(context: Context, msg: String) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        fun logToNative(msg: String) {
            android.util.Log.d("PrivateAgent", msg)
        }
    }
}
