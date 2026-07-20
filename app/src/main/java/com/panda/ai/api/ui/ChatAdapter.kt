package com.panda.ai.api.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.panda.ai.api.R
import com.panda.ai.api.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {

    private val messages = mutableListOf<ChatMessage>()

    fun setMessages(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLast(msg: ChatMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.lastIndex] = msg
            notifyItemChanged(messages.lastIndex)
        }
    }

    fun removeLast() {
        if (messages.isNotEmpty()) {
            messages.removeAt(messages.lastIndex)
            notifyItemRemoved(messages.lastIndex)
        }
    }

    override fun getItemViewType(position: Int): Int = if (messages[position].isUser()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_message_user else R.layout.item_message_assistant
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        holder.text.text = msg.content
        holder.time.text = formatTime(msg.timestamp)

        val result = msg.actionResult
        if (result != null) {
            holder.chip.visibility = View.VISIBLE
            val type = result.actionType.uppercase().replace("_", " ")
            holder.chip.text = type
            if (result.success) {
                holder.chip.setTextColor(Color.parseColor("#16A34A"))
                holder.chip.setBackgroundResource(R.drawable.bg_chip_success)
            } else {
                holder.chip.setTextColor(Color.parseColor("#DC2626"))
                holder.chip.setBackgroundResource(R.drawable.bg_chip_failure)
            }
        } else {
            holder.chip.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun formatTime(ts: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.txtMessage)
        val time: TextView = view.findViewById(R.id.txtTime)
        val chip: TextView = view.findViewById(R.id.txtActionChip)
    }
}
