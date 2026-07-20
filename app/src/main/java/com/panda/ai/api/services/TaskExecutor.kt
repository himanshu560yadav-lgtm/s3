package com.panda.ai.api.services

import android.content.Context
import com.panda.ai.api.models.ActionStep
import com.panda.ai.api.models.AgentAction
import com.panda.ai.api.models.AgentActionResult
import com.panda.ai.api.models.SavedSkill
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

class TaskExecutor(
    private val context: Context,
    private val aiService: AiService,
    private val onProgress: ((String) -> Unit)? = null
) {
    private var cancelled = false

    fun cancel() { cancelled = true }

    private val taskSystemPrompt = """
You are a phone automation agent. You are given a TASK and the current SCREEN content.
You must decide what single action to take next to accomplish the task.

Respond with ONLY a JSON object (no markdown, no code fences):
{
  "action": "action_name",
  "params": {"key": "value"},
  "reasoning": "why you chose this action",
  "is_complete": false
}

Available actions:
- click_text: {"text": "exact text to click"} - Click an element by its visible text
- click_at: {"x": 540, "y": 960} - Click at screen coordinates
- type_text: {"text": "hello", "field_hint": "optional hint"} - Type into the focused/first edit field
- press_enter: {} - Press the Enter/Search key
- scroll: {"direction": "down"} - Scroll down/up
- swipe: {"startX": 540, "startY": 2000, "endX": 540, "endY": 500} - Swipe
- press_back: {} - Press the back button
- press_home: {} - Press the home button
- open_app: {"app_name": "WhatsApp"} - Open an app
- wait: {} - Wait a moment
- done: {} - Task is complete

Rules:
- ALWAYS use the text dump to decide your next action.
- Prefer click_text. If no text, use click_at with coordinates.
- After typing a search query, use press_enter once.
- Never scroll more than three times in a row.
- Set is_complete=true ONLY when the task is fully done.
- Keep reasoning very brief (1 sentence).
""".trimIndent()

    private fun extractJson(text: String): String {
        val m = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(text)
        if (m != null) return m.groupValues[1]
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) return text.substring(start, end + 1)
        return text.trim()
    }

    suspend fun executeTask(userGoal: String): String {
        cancelled = false
        ScreenAutomationService.logToNative("[TaskExecutor] executeTask: $userGoal")
        if (!ScreenAutomationService.isServiceRunning(context)) {
            return "Accessibility service is not enabled. Go to Settings → Accessibility → PrivateAgent Screen Control and enable it."
        }

        val results = mutableListOf<String>("Starting task: $userGoal")
        report("Starting task: $userGoal")

        val savedSkill = SkillMemoryService.findSkill(context, userGoal)
        if (savedSkill != null && savedSkill.isReliable) {
            report("Found saved skill! Replaying ${savedSkill.steps.size} steps...")
            val ok = replaySkill(savedSkill, results)
            if (ok) {
                results.add("Task complete via skill memory.")
                NotificationService.showTaskCompleteNotification(context, "Task Completed", "Agent finished its goal using memory.")
                TaskHistoryLogger.logTask(context, userGoal, "Success", 0, savedSkill.steps.size, results)
                ScreenAutomationService.showToast(context, "Task Complete! (Memory)")
                return "Done."
            } else {
                report("Replay failed, falling back to AI...")
                SkillMemoryService.recordFailure(context, savedSkill.id)
            }
        }

        val shortcut = NavigationShortcuts.getNavigationShortcut(userGoal)
        var lastAction = ""
        var sameActionCount = 0
        var consecutiveFailures = 0
        var lastFailedAction = ""
        var totalTokens = 0
        val executedSteps = mutableListOf<ActionStep>()

        if (shortcut != null && shortcut.isNotEmpty()) {
            results.add("Using navigation shortcut: ${shortcut.size} steps")
            report("Using navigation shortcut...")
            for (step in shortcut) {
                if (cancelled) break
                var success = false
                when (step.action) {
                    "open_app" -> {
                        val r = AppLauncherService.openApp(context, step.params["app_name"] as? String ?: "")
                        success = r.startsWith("Opened"); delay(3000)
                    }
                    "click_text" -> {
                        success = ScreenAutomationService.clickByText(step.params["text"] as? String ?: ""); delay(1500)
                    }
                }
                if (success) { executedSteps.add(step); lastAction = step.action } else break
            }
        } else {
            val pkg = ScreenAutomationService.getCurrentPackage()
            if (pkg == context.packageName) {
                report("Moving to background...")
                ScreenAutomationService.pressHome(); delay(1500)
            }
        }

        for (step in 0 until aiService.maxStepsValue) {
            if (cancelled) {
                results.add("Task cancelled by user.")
                NotificationService.showTaskCompleteNotification(context, "Task Cancelled", "Task was stopped by the user.")
                TaskHistoryLogger.logTask(context, userGoal, "Cancelled", totalTokens, step, results)
                ScreenAutomationService.showToast(context, "Task Cancelled")
                return "Task cancelled."
            }

            val delay = when (lastAction) {
                "open_app" -> 3000L
                "type_text" -> 2000L
                "click_text", "click_at" -> 1500L
                "scroll" -> 1000L
                else -> 1200L
            }
            delay(delay)

            val screenContent = if (aiService.currentUseScreenCompression)
                ScreenAutomationService.getCompressedScreenDescription(userGoal)
            else ScreenAutomationService.getScreenDescription()

            val prev = if (results.isNotEmpty()) "\nPREVIOUS ACTION RESULT: ${results.last()}\n" else ""
            val failureHint = if (consecutiveFailures >= 3)
                "\n\nWARNING: You have failed $consecutiveFailures times in a row. Try a completely different action." else ""

            val prompt = """TASK: $userGoal

CURRENT SCREEN TEXT DUMP:
$screenContent$prev$failureHint
Step ${step + 1}/${aiService.maxStepsValue}. What is the next action?"""

            val aiResponse = try {
                aiService.sendTaskMessage(taskSystemPrompt, prompt)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                results.add("AI error: $e")
                NotificationService.showTaskCompleteNotification(context, "Task Error", "AI encountered an error.")
                TaskHistoryLogger.logTask(context, userGoal, "Failed", totalTokens, step, results)
                return "I could not complete the task because the AI service failed."
            }
            totalTokens += aiResponse.totalTokens
            val response = aiResponse.content

            if (cancelled) return "Task cancelled."

            val (actionJson, parsedStr) = try {
                val js = extractJson(response)
                JSONObject(js) to js
            } catch (err: Exception) {
                try {
                    delay(2000)
                    val retry = aiService.sendTaskMessage(taskSystemPrompt, prompt)
                    totalTokens += retry.totalTokens
                    JSONObject(extractJson(retry.content)) to extractJson(retry.content)
                } catch (e: Exception) {
                    results.add("Step ${step + 1}: Error after retry: $e")
                    NotificationService.showTaskCompleteNotification(context, "Task Error", "AI formatting error.")
                    TaskHistoryLogger.logTask(context, userGoal, "Failed", totalTokens, step, results)
                    return "I could not understand the AI response. Please try again."
                }
            }

            val action = actionJson.optString("action", "done")
            val paramsObj = actionJson.optJSONObject("params")
            val params = mutableMapOf<String, Any?>()
            paramsObj?.keys()?.forEach { params[it] = paramsObj.get(it) }
            val reasoning = actionJson.optString("reasoning", "")
            val isComplete = actionJson.optBoolean("is_complete", false)

            report("Step ${step + 1}: $reasoning")

            sameActionCount = if (action == lastAction) sameActionCount + 1 else 1
            val repeatLimit = if (action == "press_enter") 2 else if (action == "scroll" || action == "swipe") 3 else 1000
            if (sameActionCount > repeatLimit) {
                results.add("Blocked repeated $action action.")
                consecutiveFailures = 3; lastFailedAction = action; lastAction = action
                continue
            }
            lastAction = action

            var success = false
            var actionResult = ""
            when (action) {
                "click_text" -> { val t = params["text"] as? String ?: ""; success = ScreenAutomationService.clickByText(t); actionResult = if (success) "Clicked \"$t\"" else "Could not find \"$t\"" }
                "click_at" -> { val x = (params["x"] as? Number)?.toDouble() ?: 0.0; val y = (params["y"] as? Number)?.toDouble() ?: 0.0; success = ScreenAutomationService.clickAt(x.toFloat(), y.toFloat()); actionResult = if (success) "Clicked at ($x, $y)" else "Click failed" }
                "type_text" -> { val t = params["text"] as? String ?: ""; success = ScreenAutomationService.typeText(t, params["field_hint"] as? String); actionResult = if (success) "Typed \"$t\"" else "Could not type text" }
                "press_enter" -> { success = ScreenAutomationService.pressEnter(); actionResult = if (success) "Submitted field" else "Could not submit" }
                "swipe" -> { val sx=(params["startX"] as? Number)?.toFloat() ?:540f; val sy=(params["startY"] as? Number)?.toFloat() ?:2000f; val ex=(params["endX"] as? Number)?.toFloat() ?:540f; val ey=(params["endY"] as? Number)?.toFloat() ?:500f; success = ScreenAutomationService.swipe(sx,sy,ex,ey); actionResult = "Swiped" }
                "scroll" -> { val d = params["direction"] as? String ?: "down"; success = ScreenAutomationService.scroll(d); actionResult = if (success) "Scrolled $d" else "Could not scroll" }
                "press_back" -> { success = ScreenAutomationService.pressBack(); actionResult = "Pressed back" }
                "press_home" -> { success = ScreenAutomationService.pressHome(); actionResult = "Pressed home" }
                "open_app" -> { val an = params["app_name"] as? String ?: ""; actionResult = AppLauncherService.openApp(context, an); success = actionResult.startsWith("Opened") }
                "wait" -> { delay(1000); actionResult = "Waited"; success = true }
                "done" -> {
                    results.add("Task complete: $reasoning")
                    NotificationService.showTaskCompleteNotification(context, "Task Completed", reasoning.ifEmpty { "Agent finished its goal." })
                    TaskHistoryLogger.logTask(context, userGoal, "Success", totalTokens, step, results)
                    SkillMemoryService.saveSkill(context, userGoal, executedSteps)
                    ScreenAutomationService.showToast(context, "Task Complete!")
                    delay(4000)
                    return reasoning.ifEmpty { "Done." }
                }
                else -> actionResult = "Unknown action: $action"
            }

            if (!success) {
                if (action == lastFailedAction) consecutiveFailures++ else { consecutiveFailures = 1; lastFailedAction = action }
                if (consecutiveFailures >= 5) {
                    results.add("Agent is stuck. Stopping task.")
                    NotificationService.showTaskCompleteNotification(context, "Task Stuck", "Agent could not complete the task.")
                    TaskHistoryLogger.logTask(context, userGoal, "Failed", totalTokens, step, results)
                    return "I could not complete the task. Please try again."
                }
                val recovery = RecoveryEngine.diagnose(action, screenContent)
                report("Recovering: ${recovery.description}")
                when (recovery.action) {
                    "wait" -> delay(2000)
                    "press_back" -> ScreenAutomationService.pressBack()
                    "scroll" -> ScreenAutomationService.scroll(recovery.params["direction"] ?: "down")
                    "press_home" -> ScreenAutomationService.pressHome()
                }
                results.add("Recovery step: ${recovery.description}")
                continue
            } else {
                consecutiveFailures = 0; lastFailedAction = ""
                executedSteps.add(ActionStep(action, params))
            }
            results.add("Step ${step + 1}: $actionResult ($reasoning)")
            if (!isComplete && (step + 1) % 3 == 0) ScreenAutomationService.showToast(context, "Working... (Step ${step + 1})")
            if (isComplete) {
                results.add("Task complete.")
                NotificationService.showTaskCompleteNotification(context, "Task Completed", "Agent finished its goal.")
                TaskHistoryLogger.logTask(context, userGoal, "Success", totalTokens, step, results)
                SkillMemoryService.saveSkill(context, userGoal, executedSteps)
                ScreenAutomationService.showToast(context, "Task Complete!")
                delay(4000)
                return reasoning.ifEmpty { "Done." }
            }
        }
        results.add("Reached maximum steps. Task may be incomplete.")
        NotificationService.showTaskCompleteNotification(context, "Task Stopped", "Reached maximum steps.")
        TaskHistoryLogger.logTask(context, userGoal, "Failed", totalTokens, aiService.maxStepsValue, results)
        return "I could not complete the task within the allowed steps."
    }

    private fun report(msg: String) { onProgress?.invoke(msg) }

    private suspend fun replaySkill(skill: SavedSkill, results: MutableList<String>): Boolean {
        for ((i, step) in skill.steps.withIndex()) {
            if (cancelled) return false
            val delay = when (step.action) {
                "open_app" -> 3000L; "type_text" -> 2000L; "click_text", "click_at" -> 1500L; "scroll" -> 1000L
                else -> 1200L
            }
            delay(delay)
            var success = false
            when (step.action) {
                "click_text" -> { val t = step.params["text"] as? String ?: ""; success = ScreenAutomationService.clickByText(t); results.add("Memory Replay ${i+1}: Clicked \"$t\"") }
                "click_at" -> { val x=(step.params["x"] as? Number)?.toFloat()?:0f; val y=(step.params["y"] as? Number)?.toFloat()?:0f; success = ScreenAutomationService.clickAt(x,y); results.add("Memory Replay ${i+1}: Clicked") }
                "type_text" -> { val t = step.params["text"] as? String ?: ""; success = ScreenAutomationService.typeText(t, step.params["field_hint"] as? String); results.add("Memory Replay ${i+1}: Typed") }
                "press_enter" -> { success = ScreenAutomationService.pressEnter(); results.add("Memory Replay ${i+1}: Enter") }
                "swipe" -> { val sx=(step.params["startX"] as? Number)?.toFloat()?:540f; val sy=(step.params["startY"] as? Number)?.toFloat()?:2000f; val ex=(step.params["endX"] as? Number)?.toFloat()?:540f; val ey=(step.params["endY"] as? Number)?.toFloat()?:500f; success = ScreenAutomationService.swipe(sx,sy,ex,ey); results.add("Memory Replay ${i+1}: Swipe") }
                "scroll" -> { val d = step.params["direction"] as? String ?: "down"; success = ScreenAutomationService.scroll(d); results.add("Memory Replay ${i+1}: Scroll") }
                "press_back" -> { success = ScreenAutomationService.pressBack(); results.add("Memory Replay ${i+1}: Back") }
                "press_home" -> { success = ScreenAutomationService.pressHome(); results.add("Memory Replay ${i+1}: Home") }
                "open_app" -> { val an = step.params["app_name"] as? String ?: ""; val r = AppLauncherService.openApp(context, an); success = r.startsWith("Opened"); results.add("Memory Replay ${i+1}: $r") }
                "wait" -> { delay(1000); success = true; results.add("Memory Replay ${i+1}: Wait") }
                "done" -> success = true
                else -> success = false
            }
            if (!success) return false
        }
        return true
    }
}
