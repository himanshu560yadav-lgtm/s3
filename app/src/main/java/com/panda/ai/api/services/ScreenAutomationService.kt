package com.panda.ai.api.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.bundleOf
import java.util.Locale

class ScreenAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEventTypes
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
            val root = instance?.rootInActiveWindow ?: return "No screen data"
            val sb = StringBuilder()
            traverse(root, sb, 0)
            return sb.toString()
        }

        fun getCompressedScreenDescription(goal: String): String = getScreenDescription()

        private fun traverse(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
            if (node == null || sb.length > 8000) return
            val cls = node.className?.toString() ?: ""
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            val label = text ?: desc
            if (label != null && label.isNotEmpty()) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val center = "(${(bounds.left + bounds.right) / 2}, ${(bounds.top + bounds.bottom) / 2})"
                sb.append("${"-".repeat(depth.coerceAtMost(4))} [$cls] \"$label\" @ $center\n")
            }
            for (i in 0 until node.childCount) traverse(node.getChild(i), sb, depth + 1)
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
            val action = if (direction.lowercase() == "up") AccessibilityNodeInfo.ACTION_SCROLL_UP
            else AccessibilityNodeInfo.ACTION_SCROLL_DOWN
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
