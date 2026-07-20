package com.panda.ai.api.services

import com.panda.ai.api.models.ActionStep

object NavigationShortcuts {

    fun getNavigationShortcut(goal: String): List<ActionStep>? {
        val lower = goal.lowercase()

        if (lower.contains("dark mode") || lower.contains("dark theme")) {
            return listOf(
                ActionStep("open_app", mapOf("app_name" to "Settings")),
                ActionStep("click_text", mapOf("text" to "Display"))
            )
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return listOf(
                ActionStep("open_app", mapOf("app_name" to "Settings")),
                ActionStep("click_text", mapOf("text" to "Network & internet"))
            )
        }
        if (lower.contains("bluetooth")) {
            return listOf(
                ActionStep("open_app", mapOf("app_name" to "Settings")),
                ActionStep("click_text", mapOf("text" to "Connected devices"))
            )
        }

        val appPatterns = mapOf(
            "Settings" to listOf("settings", "brightness", "display", "notification"),
            "Play Store" to listOf("play store", "playstore", "download", "install app", "google play"),
            "YouTube" to listOf("youtube"),
            "WhatsApp" to listOf("whatsapp"),
            "Chrome" to listOf("chrome", "browse", "search google"),
            "Camera" to listOf("camera", "take a photo", "take photo", "take a picture"),
            "Gallery" to listOf("gallery", "photos"),
            "Messages" to listOf("message", "sms", "text to"),
            "Phone" to listOf("call", "dial"),
            "Gmail" to listOf("gmail", "email"),
            "Maps" to listOf("maps", "navigate to", "directions"),
            "Clock" to listOf("alarm", "timer", "stopwatch"),
            "Calculator" to listOf("calculator", "calculate", "calc")
        )

        for ((app, keywords) in appPatterns) {
            for (kw in keywords) {
                if (lower.contains(kw)) {
                    return listOf(ActionStep("open_app", mapOf("app_name" to app)))
                }
            }
        }

        val openMatch = Regex("^open\\s+([a-zA-Z0-9]+)").find(lower)
        if (openMatch != null) {
            var app = openMatch.groupValues[1]
            app = app[0].uppercase() + app.substring(1)
            return listOf(ActionStep("open_app", mapOf("app_name" to app)))
        }
        return null
    }
}
