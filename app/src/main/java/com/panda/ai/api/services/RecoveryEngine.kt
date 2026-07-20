package com.panda.ai.api.services

data class RecoveryStep(val action: String, val params: Map<String, String> = emptyMap(), val description: String)

object RecoveryEngine {
    fun diagnose(action: String, screen: String): RecoveryStep {
        return when (action) {
            "scroll" -> RecoveryStep("scroll", mapOf("direction" to "down"), "Recovering by scrolling down")
            "press_back" -> RecoveryStep("press_back", description = "Recovering by pressing back")
            "press_home" -> RecoveryStep("press_home", description = "Recovering by going home")
            else -> RecoveryStep("wait", description = "Waiting for content to load")
        }
    }
}
