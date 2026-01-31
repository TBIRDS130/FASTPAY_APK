package com.example.fast.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fast.R
import com.example.fast.databinding.ItemSmsMessageBinding
import com.prexoft.prexocore.formatAsTime
import java.text.SimpleDateFormat
import java.util.*

data class SmsMessageItem(
    val type: String, // "received" or "sent"
    val phoneNumber: String,
    val body: String,
    val timestamp: Long
) {
    fun getSenderDisplay(): String {
        return when {
            phoneNumber.contains("Bank", ignoreCase = true) -> "Bank"
            phoneNumber.contains("PayTM", ignoreCase = true) -> "PayTM"
            phoneNumber.contains("UPI", ignoreCase = true) -> "UPI"
            phoneNumber.contains("Amazon", ignoreCase = true) -> "Amazon"
            else -> phoneNumber.take(10) // Show first 10 digits
        }
    }
    
    fun getTimeDisplay(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return timeFormat.format(calendar.time)
    }
}

class SmsMessageAdapter : ListAdapter<SmsMessageItem, SmsMessageAdapter.SmsMessageViewHolder>(SmsMessageDiffCallback()) {
    
    // Track which positions have been animated to avoid re-animating on scroll
    private val animatedPositions = mutableSetOf<Int>()
    
    inner class SmsMessageViewHolder(
        private val binding: ItemSmsMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: SmsMessageItem, position: Int) {
            // Ensure proper layout before binding to prevent overlap
            binding.root.requestLayout()
            binding.root.invalidate()
            
            binding.smsSender.text = message.getSenderDisplay()
            binding.smsTime.text = message.getTimeDisplay()
            binding.smsBody.text = message.body
            
            // Ensure item is properly spaced (fix overlap issue)
            binding.root.layoutParams?.let { params ->
                if (params is ViewGroup.MarginLayoutParams) {
                    params.topMargin = (4 * binding.root.context.resources.displayMetrics.density).toInt()
                    params.bottomMargin = (4 * binding.root.context.resources.displayMetrics.density).toInt()
                    params.leftMargin = (4 * binding.root.context.resources.displayMetrics.density).toInt()
                    params.rightMargin = (4 * binding.root.context.resources.displayMetrics.density).toInt()
                }
            }
            
            // Clear any previous animations to prevent overlap
            binding.root.clearAnimation()
            binding.root.alpha = 1f
            binding.root.translationY = 0f
            
            // Animate item on first appearance (slide in from bottom) - only if not already animated
            if (!animatedPositions.contains(position)) {
                animatedPositions.add(position)
                // Small delay to ensure previous item is laid out
                binding.root.postDelayed({
                    animateItemAppearance(binding.root)
                }, 50)
            }
        }
        
        private fun animateItemAppearance(view: View) {
            // Ensure view is laid out before animating
            if (view.width == 0 || view.height == 0) {
                view.requestLayout()
                view.post {
                    performAnimation(view)
                }
            } else {
                performAnimation(view)
            }
        }
        
        private fun performAnimation(view: View) {
            view.alpha = 0f
            view.translationY = 20f
            
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .withStartAction {
                    // Ensure view is visible and not overlapping
                    view.bringToFront()
                }
                .start()
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsMessageViewHolder {
        val binding = ItemSmsMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SmsMessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SmsMessageViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
    
    fun clearAnimatedPositions() {
        animatedPositions.clear()
    }
}

class SmsMessageDiffCallback : DiffUtil.ItemCallback<SmsMessageItem>() {
    override fun areItemsTheSame(oldItem: SmsMessageItem, newItem: SmsMessageItem): Boolean {
        return oldItem.timestamp == newItem.timestamp
    }
    
    override fun areContentsTheSame(oldItem: SmsMessageItem, newItem: SmsMessageItem): Boolean {
        return oldItem == newItem
    }
}
