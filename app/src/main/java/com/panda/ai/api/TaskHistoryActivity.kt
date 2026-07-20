package com.panda.ai.api

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.panda.ai.api.databinding.ActivityTaskHistoryBinding
import com.panda.ai.api.services.TaskHistoryLogger

class TaskHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topBar.setNavigationOnClickListener { finish() }
        adapter = HistoryAdapter()
        binding.taskList.layoutManager = LinearLayoutManager(this)
        binding.taskList.adapter = adapter
        binding.btnClear.setOnClickListener {
            TaskHistoryLogger.clearHistory(this)
            load()
        }
        load()
    }

    private fun load() {
        val list = TaskHistoryLogger.readHistory(this)
        binding.txtCount.text = "(${list.size})"
        adapter.setItems(list)
        val analytics = TaskHistoryLogger.getAnalytics(this)
        if (list.isEmpty()) binding.txtCount.append("  Total: 0")
        else binding.txtCount.append("  Rate: ${(analytics["successRate"] as Double * 100).toInt()}%")
    }

    class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {
        private val items = mutableListOf<Map<String, Any>>()
        fun setItems(list: List<Map<String, Any>>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_task_history, p, false)
            return VH(v)
        }
        override fun onBindViewHolder(h: VH, i: Int) {
            val item = items[i]
            h.goal.text = item["goal"] as? String ?: "Unknown"
            val steps = item["steps_taken"] as? Int ?: 0
            val tokens = item["total_tokens"] as? Int ?: 0
            h.meta.text = "$steps steps  •  $tokens tokens"
            val status = item["status"] as? String ?: "Unknown"
            h.status.text = status.uppercase()
            val color = when (status) {
                "Success" -> android.graphics.Color.parseColor("#22C55E")
                "Failed" -> android.graphics.Color.parseColor("#EF4444")
                "Cancelled" -> android.graphics.Color.parseColor("#F59E0B")
                else -> android.graphics.Color.GRAY
            }
            h.status.setTextColor(color)
            h.status.setBackgroundResource(R.drawable.bg_bubble_assistant)
        }
        override fun getItemCount() = items.size
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val goal: TextView = v.findViewById(R.id.txtGoal)
            val meta: TextView = v.findViewById(R.id.txtMeta)
            val status: TextView = v.findViewById(R.id.txtStatus)
        }
    }
}
