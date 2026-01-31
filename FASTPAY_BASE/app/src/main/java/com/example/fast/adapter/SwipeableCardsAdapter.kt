package com.example.fast.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fast.R
import com.example.fast.databinding.CardInstructionsBinding
import com.example.fast.databinding.CardSmsBinding
import com.example.fast.databinding.ItemInstructionBinding
import com.example.fast.databinding.ItemSmsConversationBinding
import com.example.fast.model.Instruction
import com.example.fast.model.SmsConversation
import com.google.firebase.storage.storage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for swipeable cards in ActivatedActivity
 * Displays Instructions and SMS cards that can be swiped
 */
class SwipeableCardsAdapter(
    private val onInstructionClick: ((Instruction) -> Unit)? = null,
    private val onSmsClick: ((SmsConversation) -> Unit)? = null
) : RecyclerView.Adapter<SwipeableCardsAdapter.CardViewHolder>() {

    companion object {
        private const val TYPE_INSTRUCTIONS = 0
        private const val TYPE_SMS = 1
    }

    private var instructions: List<Instruction> = emptyList()
    private var smsMessages: List<SmsConversation> = emptyList()

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_INSTRUCTIONS
            1 -> TYPE_SMS
            else -> TYPE_INSTRUCTIONS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        return when (viewType) {
            TYPE_INSTRUCTIONS -> {
                val binding = CardInstructionsBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                InstructionsViewHolder(binding, onInstructionClick)
            }
            TYPE_SMS -> {
                val binding = CardSmsBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SmsViewHolder(binding, onSmsClick)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        when (holder) {
            is InstructionsViewHolder -> holder.bind(instructions)
            is SmsViewHolder -> holder.bind(smsMessages)
        }
    }

    override fun getItemCount(): Int = 2

    fun updateInstructions(newInstructions: List<Instruction>) {
        instructions = newInstructions
        notifyItemChanged(0)
    }

    fun updateSmsMessages(newMessages: List<SmsConversation>) {
        smsMessages = newMessages
        notifyItemChanged(1)
    }

    abstract class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class InstructionsViewHolder(
        private val binding: CardInstructionsBinding,
        private val onInstructionClick: ((Instruction) -> Unit)?
    ) : CardViewHolder(binding.root) {

        private val instructionAdapter = InstructionAdapter { instruction ->
            onInstructionClick?.invoke(instruction)
        }

        init {
            binding.instructionsRecyclerView.apply {
                layoutManager = LinearLayoutManager(binding.root.context)
                adapter = instructionAdapter
            }
        }

        fun bind(instructions: List<Instruction>) {
            if (instructions.isEmpty()) {
                binding.emptyInstructionsText.visibility = View.VISIBLE
                binding.instructionsRecyclerView.visibility = View.GONE
            } else {
                binding.emptyInstructionsText.visibility = View.GONE
                binding.instructionsRecyclerView.visibility = View.VISIBLE
                instructionAdapter.submitList(instructions)
            }
        }
    }

    class SmsViewHolder(
        private val binding: CardSmsBinding,
        private val onSmsClick: ((SmsConversation) -> Unit)?
    ) : CardViewHolder(binding.root) {

        private val smsAdapter = SmsCardAdapter { conversation ->
            onSmsClick?.invoke(conversation)
        }

        init {
            binding.smsRecyclerView.apply {
                layoutManager = LinearLayoutManager(binding.root.context)
                adapter = smsAdapter
            }
        }

        fun bind(messages: List<SmsConversation>) {
            if (messages.isEmpty()) {
                binding.emptySmsText.visibility = View.VISIBLE
                binding.smsRecyclerView.visibility = View.GONE
            } else {
                binding.emptySmsText.visibility = View.GONE
                binding.smsRecyclerView.visibility = View.VISIBLE
                smsAdapter.submitList(messages)
            }
        }
    }

    /**
     * Adapter for instruction items
     */
    private class InstructionAdapter(
        private val onItemClick: (Instruction) -> Unit
    ) : ListAdapter<Instruction, InstructionAdapter.InstructionViewHolder>(InstructionDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstructionViewHolder {
            val binding = ItemInstructionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return InstructionViewHolder(binding, onItemClick)
        }

        override fun onBindViewHolder(holder: InstructionViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class InstructionViewHolder(
            private val binding: ItemInstructionBinding,
            private val onItemClick: (Instruction) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(instruction: Instruction) {
                binding.instructionTitle.text = instruction.title
                binding.instructionText.text = instruction.text

                // Load image if available
                if (!instruction.imageUrl.isNullOrBlank()) {
                    binding.instructionImage.visibility = View.VISIBLE
                    Glide.with(binding.root.context)
                        .load(instruction.imageUrl)
                        .into(binding.instructionImage)
                } else {
                    binding.instructionImage.visibility = View.GONE
                }

                binding.root.setOnClickListener {
                    onItemClick(instruction)
                }
            }
        }

        class InstructionDiffCallback : DiffUtil.ItemCallback<Instruction>() {
            override fun areItemsTheSame(oldItem: Instruction, newItem: Instruction): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Instruction, newItem: Instruction): Boolean {
                return oldItem == newItem
            }
        }
    }

    /**
     * Adapter for SMS conversation items in card
     */
    private class SmsCardAdapter(
        private val onItemClick: (SmsConversation) -> Unit
    ) : ListAdapter<SmsConversation, SmsCardAdapter.SmsCardViewHolder>(SmsCardDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsCardViewHolder {
            val binding = ItemSmsConversationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return SmsCardViewHolder(binding, onItemClick, ::formatConversationTime)
        }

        override fun onBindViewHolder(holder: SmsCardViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        private fun formatConversationTime(timestamp: Long): String {
            val now = Calendar.getInstance()
            val messageDate = Calendar.getInstance().apply {
                timeInMillis = timestamp
            }
            
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val messageDateOnly = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val currentYear = now.get(Calendar.YEAR)
            val messageYear = messageDate.get(Calendar.YEAR)
            
            return when {
                // Today - show time in 24hr format
                messageDateOnly.timeInMillis == today.timeInMillis -> {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                }
                // Within last 7 days - show day name
                now.timeInMillis - timestamp < 7 * 24 * 60 * 60 * 1000L -> {
                    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
                }
                // This year - show date like "10 Nov", "11 Oct"
                messageYear == currentYear -> {
                    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
                }
                // Otherwise - show full date like "10 Nov 2024"
                else -> {
                    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }

        class SmsCardViewHolder(
            private val binding: ItemSmsConversationBinding,
            private val onItemClick: (SmsConversation) -> Unit,
            private val formatTime: (Long) -> String
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(conversation: SmsConversation) {
                binding.contactName.text = conversation.contactName
                binding.lastMessage.text = conversation.lastMessage
                binding.timeText.text = formatTime(conversation.timestamp)

                // Show unread badge if there are unread messages
                if (conversation.unreadCount > 0) {
                    binding.unreadBadge.visibility = View.VISIBLE
                    binding.unreadBadge.text = conversation.unreadCount.toString()
                } else {
                    binding.unreadBadge.visibility = View.GONE
                }

                binding.root.setOnClickListener {
                    onItemClick(conversation)
                }
            }
        }

        class SmsCardDiffCallback : DiffUtil.ItemCallback<SmsConversation>() {
            override fun areItemsTheSame(oldItem: SmsConversation, newItem: SmsConversation): Boolean {
                return oldItem.contactNumber == newItem.contactNumber
            }

            override fun areContentsTheSame(oldItem: SmsConversation, newItem: SmsConversation): Boolean {
                return oldItem == newItem
            }
        }
    }
}
