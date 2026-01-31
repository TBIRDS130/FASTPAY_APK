package com.example.fast.model

import android.content.Context
import com.example.fast.util.SmsQueryHelper

data class SmsConversation(
    val contactNumber: String,
    val contactName: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val isRead: Boolean = true
) {
    companion object {
        fun fromSmsQueryHelper(context: Context): List<SmsConversation> {
            return SmsQueryHelper.getAllConversations(context)
        }
    }
}

