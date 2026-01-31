package com.example.fast.adapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fast.R
import com.example.fast.databinding.ItemSmsConversationBinding
import com.example.fast.model.SmsConversation
import java.text.SimpleDateFormat
import java.util.*

class SmsConversationAdapter(
    private val onConversationClick: (SmsConversation) -> Unit
) : ListAdapter<SmsConversation, SmsConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {
    
    // Track which positions have been animated to avoid re-animating on scroll
    private val animatedPositions = mutableSetOf<Int>()

    inner class ConversationViewHolder(
        private val binding: ItemSmsConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(conversation: SmsConversation, onClick: (SmsConversation) -> Unit) {
            binding.contactName.text = conversation.contactName
            binding.lastMessage.text = conversation.lastMessage
            binding.timeText.text = formatConversationTime(conversation.timestamp)
            
            // Show unread badge if there are unread messages
            if (conversation.unreadCount > 0) {
                binding.unreadBadge.visibility = View.VISIBLE
                binding.unreadBadge.text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
            } else {
                binding.unreadBadge.visibility = View.GONE
            }
            
            // Set contact name style based on read status - matching HTML design exactly
            binding.contactName.setTypeface(
                binding.contactName.typeface,
                if (conversation.isRead) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD
            )
            // Update text color based on read status (HTML: #1a1a1a normal, #2d2d2d unread)
            if (conversation.isRead) {
                binding.contactName.setTextColor(0xFF1a1a1a.toInt()) // #1a1a1a
            } else {
                binding.contactName.setTextColor(0xFF2d2d2d.toInt()) // #2d2d2d unread
            }
            
            // Apply unread card background gradient - matching HTML exactly
            val cardContent = binding.smsCardContent
            if (!conversation.isRead && conversation.unreadCount > 0) {
                cardContent.setBackgroundResource(R.drawable.sms_card_background_unread)
                // Add unread class styling
                cardContent.alpha = 1f
            } else {
                cardContent.setBackgroundResource(R.drawable.sms_card_background)
            }
            
            // Set click listener
            binding.root.setOnClickListener {
                onClick(conversation)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemSmsConversationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position), onConversationClick)
        
        // Animate new message card only if it hasn't been animated before
        if (!animatedPositions.contains(position)) {
            animatedPositions.add(position)
            animateNewMessageCard(holder.itemView)
        }
    }
    
    /**
     * Animate SMS card when new message arrives
     * Effects: Matching HTML animation - slide in from right + fade + scale pulse with blur
     * HTML: elegantSlideIn animation - translateX(120px) scale(0.9) rotateY(-10deg) with blur
     */
    private fun animateNewMessageCard(view: View) {
        // Reset any previous animations
        view.clearAnimation()
        view.alpha = 0f
        view.translationX = 120f * view.context.resources.displayMetrics.density // 120px
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.rotationY = -10f
        
        // Create animation set matching HTML exactly
        val animatorSet = AnimatorSet().apply {
            playTogether(
                // Slide in from right (120px -> 0)
                ObjectAnimator.ofFloat(view, "translationX", 120f * view.context.resources.displayMetrics.density, 0f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                },
                // Fade in
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                },
                // Scale pulse: 0.9 -> 1.02 -> 1.0 (matching HTML)
                ObjectAnimator.ofFloat(view, "scaleX", 0.9f, 1.02f, 1f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(view, "scaleY", 0.9f, 1.02f, 1f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                },
                // 3D rotation: -10deg -> 2deg -> 0deg (matching HTML)
                ObjectAnimator.ofFloat(view, "rotationY", -10f, 2f, 0f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
            )
        }
        
        animatorSet.start()
    }
    
    override fun onViewRecycled(holder: ConversationViewHolder) {
        super.onViewRecycled(holder)
        // Clear animation state when view is recycled
        holder.itemView.clearAnimation()
        holder.itemView.alpha = 1f
        holder.itemView.translationX = 0f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
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
    
    private class ConversationDiffCallback : DiffUtil.ItemCallback<SmsConversation>() {
        override fun areItemsTheSame(oldItem: SmsConversation, newItem: SmsConversation): Boolean {
            return oldItem.contactNumber == newItem.contactNumber
        }
        
        override fun areContentsTheSame(oldItem: SmsConversation, newItem: SmsConversation): Boolean {
            return oldItem == newItem
        }
    }
}

