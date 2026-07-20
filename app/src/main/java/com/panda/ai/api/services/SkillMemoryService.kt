package com.panda.ai.api.services

import android.content.Context
import com.panda.ai.api.models.ActionStep
import com.panda.ai.api.models.SavedSkill
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SkillMemoryService {
    private var skills = mutableListOf<SavedSkill>()
    private var loaded = false

    private fun file(context: Context): File = File(context.filesDir, "skills_memory.jsonl")

    private fun load(context: Context) {
        if (loaded) return
        try {
            val f = file(context)
            if (!f.exists()) { loaded = true; return }
            f.readLines().filter { it.trim().isNotEmpty() }.forEach { line ->
                skills.add(SavedSkill.fromJson(JSONObject(line)))
            }
            loaded = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveAll(context: Context) {
        val lines = skills.joinToString("\n") { it.toJson().toString() }
        file(context).writeText(if (lines.isNotEmpty()) "$lines\n" else "")
    }

    private fun extractKeywords(text: String): List<String> {
        val stop = setOf("to","and","the","a","in","of","for","on","with","at","by","from","go","turn","open")
        return text.lowercase().replace(Regex("[^a-z0-9\\s]"), "").split(Regex("\\s+"))
            .filter { it.isNotEmpty() && !stop.contains(it) }
    }

    private fun jaccard(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val sa = a.toSet(); val sb = b.toSet()
        val inter = sa.intersect(sb).size
        val union = sa.union(sb).size
        return inter.toDouble() / union
    }

    fun findSkill(context: Context, taskGoal: String): SavedSkill? {
        load(context)
        if (skills.isEmpty()) return null
        val q = extractKeywords(taskGoal)
        var best: SavedSkill? = null
        var bestSim = 0.0
        for (s in skills) {
            val sim = jaccard(q, s.taskKeywords)
            if (sim > bestSim) { bestSim = sim; best = s }
        }
        return if (bestSim > 0.6) best else null
    }

    fun saveSkill(context: Context, taskGoal: String, steps: List<ActionStep>) {
        load(context)
        val q = extractKeywords(taskGoal)
        for (s in skills) {
            if (jaccard(q, s.taskKeywords) > 0.8) {
                s.successCount++
                s.lastUsed = System.currentTimeMillis()
                if (steps.size < s.steps.size) {
                    (s.steps as? MutableList)?.clear()
                    (s.steps as? MutableList)?.addAll(steps)
                }
                saveAll(context)
                return
            }
        }
        val newSkill = SavedSkill(
            id = System.currentTimeMillis().toString(),
            task = taskGoal,
            taskKeywords = q,
            successCount = 1,
            failCount = 0,
            lastUsed = System.currentTimeMillis(),
            steps = steps
        )
        skills.add(newSkill)
        saveAll(context)
    }

    fun recordFailure(context: Context, skillId: String) {
        load(context)
        val idx = skills.indexOfFirst { it.id == skillId }
        if (idx != -1) { skills[idx].failCount++; saveAll(context) }
    }
}
