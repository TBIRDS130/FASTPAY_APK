package com.example.fast.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.fast.databinding.ItemChatMessageReceivedBinding
import com.example.fast.databinding.ItemChatMessageSentBinding
import com.example.fast.model.ChatMessage
import com.prexoft.prexocore.formatAsTime

class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun appendMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isReceived) VIEW_TYPE_RECEIVED else VIEW_TYPE_SENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemChatMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentMessageViewHolder(binding)
            }
            VIEW_TYPE_RECEIVED -> {
                val binding = ItemChatMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount() = messages.size

    class SentMessageViewHolder(
        private val binding: ItemChatMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.messageText.text = message.body
            binding.messageTime.text = message.timestamp.formatAsTime()
        }
    }

    class ReceivedMessageViewHolder(
        private val binding: ItemChatMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.messageText.text = message.body
            binding.messageTime.text = message.timestamp.formatAsTime()
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}
