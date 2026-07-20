package com.panda.ai.api.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.panda.ai.api.R
import com.panda.ai.api.models.ChatMessage

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
        holder.text.text = messages[position].content
    }

    override fun getItemCount(): Int = messages.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.txtMessage)
    }
}
