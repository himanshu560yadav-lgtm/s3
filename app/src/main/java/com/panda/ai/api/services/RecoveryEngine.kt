package com.panda.ai.api.services

data class RecoveryStep(val action: String, val params: Map<String, String> = emptyMap(), val description: String)

object RecoveryEngine {
    fun diagnose(lastFailedAction: String, screenContent: String): RecoveryStep {
        val lower = screenContent.lowercase()
        if (lower.contains("loading") || lower.contains("progress") || lower.contains("spinner") || lower.contains("wait")) {
            return RecoveryStep("wait", description = "App seems to be loading, waiting...")
        }
        if (lower.contains("gboard") || lower.contains("keyboard")) {
            return RecoveryStep("press_back", description = "Keyboard might be blocking the screen, dismissing it.")
        }
        if (lastFailedAction == "click_text" || lastFailedAction == "click_at") {
            return if (lower.contains("scrollable")) {
                RecoveryStep("scroll", mapOf("direction" to "down"), "Click failed, trying to scroll down to find the target.")
            } else {
                RecoveryStep("press_back", description = "Click failed and not scrollable, pressing back to retry from previous screen.")
            }
        }
        if (lastFailedAction == "open_app") {
            return RecoveryStep("press_home", description = "Failed to open app, going home to try a different approach.")
        }
        return RecoveryStep("press_back", description = "Unknown failure, pressing back to recover.")
    }
}
