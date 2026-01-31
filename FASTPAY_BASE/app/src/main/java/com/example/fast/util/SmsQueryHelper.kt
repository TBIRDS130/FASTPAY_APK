package com.example.fast.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest
import com.example.fast.model.ChatMessage
import com.example.fast.model.SmsConversation

object SmsQueryHelper {

    @SuppressLint("Range")
    fun getAllMessages(context: Context, contactNumber: String? = null): List<ChatMessage> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val messages = mutableListOf<ChatMessage>()
        val normalizedContactNumber = contactNumber?.let { normalizePhoneNumber(it) }

        // Query inbox (received messages)
        val inboxUri = Uri.parse("content://sms/inbox")
        val inboxCursor = context.contentResolver.query(
            inboxUri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} ASC"
        )

        inboxCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                if (!TextUtils.isEmpty(body) && !TextUtils.isEmpty(address)) {
                    // Filter by contact number if provided
                    if (normalizedContactNumber == null || normalizePhoneNumber(address) == normalizedContactNumber) {
                        messages.add(
                            ChatMessage(
                                id = id,
                                body = body,
                                timestamp = date,
                                isReceived = true,
                                address = address
                            )
                        )
                    }
                }
            }
        }

        // Query sent messages
        val sentUri = Uri.parse("content://sms/sent")
        val sentCursor = context.contentResolver.query(
            sentUri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} ASC"
        )

        sentCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                if (!TextUtils.isEmpty(body) && !TextUtils.isEmpty(address)) {
                    // Filter by contact number if provided
                    if (normalizedContactNumber == null || normalizePhoneNumber(address) == normalizedContactNumber) {
                        messages.add(
                            ChatMessage(
                                id = id,
                                body = body,
                                timestamp = date,
                                isReceived = false,
                                address = address
                            )
                        )
                    }
                }
            }
        }

        // Sort all messages by timestamp
        return messages.sortedBy { it.timestamp }
    }

    @SuppressLint("Range")
    fun getAllConversations(context: Context): List<SmsConversation> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val conversationMap = mutableMapOf<String, MutableList<ChatMessage>>()

        // Query inbox (received messages)
        val inboxUri = Uri.parse("content://sms/inbox")
        val inboxCursor = context.contentResolver.query(
            inboxUri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        inboxCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                if (!TextUtils.isEmpty(body) && !TextUtils.isEmpty(address)) {
                    val normalizedAddress = normalizePhoneNumber(address)
                    if (!conversationMap.containsKey(normalizedAddress)) {
                        conversationMap[normalizedAddress] = mutableListOf()
                    }
                    conversationMap[normalizedAddress]?.add(
                        ChatMessage(
                            id = id,
                            body = body,
                            timestamp = date,
                            isReceived = true,
                            address = address
                        )
                    )
                }
            }
        }

        // Query sent messages
        val sentUri = Uri.parse("content://sms/sent")
        val sentCursor = context.contentResolver.query(
            sentUri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        sentCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                if (!TextUtils.isEmpty(body) && !TextUtils.isEmpty(address)) {
                    val normalizedAddress = normalizePhoneNumber(address)
                    if (!conversationMap.containsKey(normalizedAddress)) {
                        conversationMap[normalizedAddress] = mutableListOf()
                    }
                    conversationMap[normalizedAddress]?.add(
                        ChatMessage(
                            id = id,
                            body = body,
                            timestamp = date,
                            isReceived = false,
                            address = address
                        )
                    )
                }
            }
        }

        // Convert to SmsConversation list
        return conversationMap.map { (normalizedAddress, messages) ->
            val sortedMessages = messages.sortedByDescending { it.timestamp }
            val lastMessage = sortedMessages.first()
            // Use the original address from the last message for contact lookup
            val originalAddress = lastMessage.address

            SmsConversation(
                contactNumber = normalizedAddress,
                contactName = ContactResolver.getContactName(context, originalAddress),
                lastMessage = lastMessage.body,
                timestamp = lastMessage.timestamp,
                unreadCount = 0,
                isRead = true
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except +
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Normalize US numbers
        return when {
            cleaned.length == 10 -> cleaned
            cleaned.length == 11 && cleaned.startsWith("1") -> cleaned.substring(1)
            cleaned.length > 11 && cleaned.startsWith("+1") -> cleaned.substring(2)
            else -> cleaned
        }
    }
}
