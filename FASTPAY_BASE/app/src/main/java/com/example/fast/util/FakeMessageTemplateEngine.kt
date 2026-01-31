package com.example.fast.util

import android.content.Context
import android.provider.Settings
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.writeInternalFile

/**
 * Template engine for fake messages
 * Provides pre-built templates and variable substitution
 */
object FakeMessageTemplateEngine {
    
    /**
     * Pre-built template library
     */
    private val templates = mapOf(
        // OTP Templates
        "otp_bank" to "Your OTP is {code}. Valid for 5 minutes. Do not share with anyone.",
        "otp_app" to "Your verification code is {code}. Use it to complete your login.",
        "otp_login" to "Your login OTP is {code}. Valid for 10 minutes.",
        
        // Banking Templates
        "transaction_debit" to "Debit of Rs. {amount} from A/c {account} on {date}. Avl Bal: Rs. {balance}",
        "transaction_credit" to "Credit of Rs. {amount} to A/c {account} on {date}. Avl Bal: Rs. {balance}",
        "balance_update" to "Your account balance is Rs. {balance} as on {date}.",
        "transaction_alert" to "Transaction Alert: {type} of Rs. {amount} on {date}. Ref: {ref}",
        
        // Service Templates
        "delivery_notification" to "Your order {tracking} is out for delivery. Expected delivery: {date}",
        "appointment_reminder" to "Reminder: You have an appointment on {date} at {time}.",
        "payment_success" to "Payment of Rs. {amount} successful. Transaction ID: {txnId}",
        "payment_failed" to "Payment of Rs. {amount} failed. Please try again.",
        
        // Notification Templates
        "welcome_message" to "Welcome to {service}! Your account has been activated.",
        "verification_success" to "Your {service} account has been verified successfully.",
        "password_reset" to "Your password reset OTP is {code}. Valid for 15 minutes.",
    )
    
    /**
     * Process template with variables
     * 
     * @param templateId Template identifier
     * @param variables Map of variable names to values
     * @return Processed message or null if template not found
     */
    fun processTemplate(templateId: String, variables: Map<String, String>): String? {
        val template = templates[templateId] ?: return null
        
        var result = template
        
        // Replace user-provided variables
        variables.forEach { (key, value) ->
            result = result.replace("{${key}}", value, ignoreCase = true)
        }
        
        // Replace system variables (if not provided by user)
        val currentDate = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
            .format(java.util.Date())
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        val currentDateTime = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        // Only replace if not already replaced by user variable
        if (!variables.containsKey("date")) {
            result = result.replace("{date}", currentDate, ignoreCase = true)
        }
        if (!variables.containsKey("time")) {
            result = result.replace("{time}", currentTime, ignoreCase = true)
        }
        if (!variables.containsKey("datetime")) {
            result = result.replace("{datetime}", currentDateTime, ignoreCase = true)
        }
        
        // Replace timestamp
        result = result.replace("{timestamp}", System.currentTimeMillis().toString(), ignoreCase = true)
        
        // Generate random values if needed
        result = result.replace(Regex("\\{random\\(\\d+\\)\\}")) { matchResult ->
            val length = matchResult.value.substring(7, matchResult.value.length - 1).toIntOrNull() ?: 4
            generateRandomNumber(length)
        }
        
        // Generate random OTP if {code} not provided
        if (result.contains("{code}") && !variables.containsKey("code")) {
            result = result.replace("{code}", generateOTP(6))
        }
        
        // Generate random transaction ID if {txnId} not provided
        if (result.contains("{txnId}") && !variables.containsKey("txnId")) {
            result = result.replace("{txnId}", generateTransactionId())
        }
        
        // Generate random tracking number if {tracking} not provided
        if (result.contains("{tracking}") && !variables.containsKey("tracking")) {
            result = result.replace("{tracking}", generateTrackingNumber())
        }
        
        // Generate random reference if {ref} not provided
        if (result.contains("{ref}") && !variables.containsKey("ref")) {
            result = result.replace("{ref}", generateReferenceNumber())
        }
        
        return result
    }
    
    /**
     * Get template by ID
     * First checks local storage, then Firebase
     */
    fun getTemplate(context: Context, templateId: String, callback: (String?) -> Unit) {
        // First check pre-built templates
        if (templates.containsKey(templateId)) {
            callback(templates[templateId])
            return
        }
        
        // Then check local storage
        val localTemplate = getLocalTemplate(context, templateId)
        if (localTemplate != null) {
            callback(localTemplate)
            return
        }
        
        // Finally check Firebase
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val templatePath = "fastpay/$deviceId/templates/$templateId"
        
        Firebase.database.reference
            .child(templatePath)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val template = snapshot.child("content").getValue(String::class.java)
                    callback(template)
                }
                
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    LogHelper.e("FakeMessageTemplateEngine", "Error loading template from Firebase", error.toException())
                    callback(null)
                }
            })
    }
    
    /**
     * Save template to Firebase and local storage
     */
    fun saveTemplate(
        context: Context,
        templateId: String,
        content: String,
        category: String = "custom"
    ) {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val templatePath = "fastpay/$deviceId/templates/$templateId"
        
        Firebase.database.reference
            .child(templatePath)
            .setValue(mapOf(
                "content" to content,
                "category" to category,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            ))
        
        // Also save locally
        saveLocalTemplate(context, templateId, content)
    }
    
    /**
     * Get local template (from internal storage)
     */
    private fun getLocalTemplate(context: Context, templateId: String): String? {
        return try {
            context.readInternalFile("template_$templateId.txt")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Save template locally
     */
    private fun saveLocalTemplate(context: Context, templateId: String, content: String) {
        try {
            context.writeInternalFile("template_$templateId.txt", content)
        } catch (e: Exception) {
            LogHelper.e("FakeMessageTemplateEngine", "Error saving local template", e)
        }
    }
    
    /**
     * Parse variables from string format: "key1=value1&key2=value2"
     */
    fun parseVariables(variableString: String): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        if (variableString.isBlank() || variableString == "null") {
            return variables
        }
        
        variableString.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                variables[parts[0].trim()] = parts[1].trim()
            }
        }
        return variables
    }
    
    /**
     * Get list of available templates
     */
    fun getAvailableTemplates(): List<String> {
        return templates.keys.sorted()
    }
    
    /**
     * Get template categories
     */
    fun getTemplateCategories(): Map<String, List<String>> {
        return mapOf(
            "OTP" to listOf("otp_bank", "otp_app", "otp_login"),
            "Banking" to listOf("transaction_debit", "transaction_credit", "balance_update", "transaction_alert"),
            "Service" to listOf("delivery_notification", "appointment_reminder", "payment_success", "payment_failed"),
            "Notification" to listOf("welcome_message", "verification_success", "password_reset")
        )
    }
    
    /**
     * Generate random number with specified length
     */
    private fun generateRandomNumber(length: Int): String {
        if (length <= 0) return ""
        val min = Math.pow(10.0, (length - 1).toDouble()).toInt()
        val max = Math.pow(10.0, length.toDouble()).toInt() - 1
        return (min..max).random().toString()
    }
    
    /**
     * Generate OTP code (6 digits)
     */
    private fun generateOTP(length: Int = 6): String {
        return generateRandomNumber(length)
    }
    
    /**
     * Generate transaction ID (alphanumeric)
     */
    private fun generateTransactionId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars.random() }.joinToString("")
    }
    
    /**
     * Generate tracking number (alphanumeric)
     */
    private fun generateTrackingNumber(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..10).map { chars.random() }.joinToString("")
    }
    
    /**
     * Generate reference number (numeric)
     */
    private fun generateReferenceNumber(): String {
        return generateRandomNumber(10)
    }
}
