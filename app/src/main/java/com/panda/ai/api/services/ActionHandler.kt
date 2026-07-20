package com.panda.ai.api.services

import android.content.Context
import com.panda.ai.api.models.AgentAction
import com.panda.ai.api.models.AgentActionResult
import com.panda.ai.api.models.ActionStep
import kotlinx.coroutines.delay

class ActionHandler(private val context: Context) {

    var currentExecutor: TaskExecutor? = null

    suspend fun execute(action: AgentAction, aiService: AiService? = null, onProgress: ((String) -> Unit)? = null): AgentActionResult {
        return try {
            val result: String
            when (action.action) {
                "open_app" -> result = AppLauncherService.openApp(context, action.params["app_name"] as? String ?: "")
                "launch_package" -> result = AppLauncherService.openPackage(context, action.params["package_name"] as? String ?: "")
                "make_call" -> result = CommunicationService.makeCall(
                    context,
                    action.params["contact_name"] as? String,
                    action.params["phone_number"] as? String
                )
                "send_sms" -> result = CommunicationService.sendSms(
                    context,
                    action.params["contact_name"] as? String,
                    action.params["phone_number"] as? String,
                    action.params["message"] as? String ?: ""
                )
                "send_email" -> result = CommunicationService.sendEmail(
                    context,
                    action.params["to"] as? String ?: "",
                    action.params["subject"] as? String,
                    action.params["body"] as? String
                )
                "search_contact" -> result = ContactsService.searchAndFormat(context, action.params["query"] as? String ?: "")
                "set_alarm" -> result = AlarmService.setAlarm(
                    context,
                    (action.params["hour"] as? Number)?.toInt() ?: 0,
                    (action.params["minute"] as? Number)?.toInt() ?: 0,
                    action.params["label"] as? String
                )
                "set_timer" -> result = AlarmService.setTimer(
                    context,
                    (action.params["seconds"] as? Number)?.toInt() ?: 60,
                    action.params["label"] as? String
                )
                "set_volume" -> result = SystemControlService.setVolume(
                    context, (action.params["level"] as? Number)?.toInt() ?: 50
                )
                "set_brightness" -> result = SystemControlService.setBrightness(
                    context, (action.params["level"] as? Number)?.toInt() ?: 50
                )
                "run_adb_command" -> result = ShizukuService.runCommand(action.params["command"] as? String ?: "")
                "open_url" -> result = AppLauncherService.openUrl(context, action.params["url"] as? String ?: "")
                "read_screen" -> result = ScreenAutomationService.getScreenDescription()
                "click_element" -> {
                    val t = action.params["text"] as? String ?: ""
                    result = if (ScreenAutomationService.clickByText(t)) "Clicked \"$t\"" else "Could not find \"$t\" to click"
                }
                "type_on_screen" -> {
                    val t = action.params["text"] as? String ?: ""
                    result = if (ScreenAutomationService.typeText(t, action.params["field_hint"] as? String)) "Typed \"$t\"" else "Could not type into field"
                }
                "scroll_screen" -> {
                    val d = action.params["direction"] as? String ?: "down"
                    result = if (ScreenAutomationService.scroll(d)) "Scrolled $d" else "Could not scroll"
                }
                "press_back" -> result = if (ScreenAutomationService.pressBack()) "Pressed back" else "Could not press back"
                "execute_task" -> {
                    val goal = (action.params["goal"] as? String) ?: action.response
                    if (aiService == null) { result = "AI service not available for task execution." }
                    else {
                        currentExecutor = TaskExecutor(context, aiService, onProgress)
                        result = currentExecutor!!.executeTask(goal)
                        currentExecutor = null
                    }
                }
                else -> result = action.response
            }
            AgentActionResult(action.action, true, result)
        } catch (e: Exception) {
            AgentActionResult(action.action, false, "Error: $e")
        }
    }

    fun cancelTask() { currentExecutor?.cancel() }
}
