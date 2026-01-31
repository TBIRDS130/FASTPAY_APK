package com.example.fast.util

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Firebase Call Tracker
 * 
 * Tracks all Firebase database calls (reads and writes) with:
 * - Total call count
 * - Request data (path, method, data)
 * - Response data (success/error, data)
 * - Timestamp for each call
 * 
 * Data is stored in JSON format in SharedPreferences
 */
object FirebaseCallTracker {
    
    private const val PREFS_NAME = "firebase_call_tracker"
    private const val KEY_TOTAL_COUNT = "total_count"
    private const val KEY_CALLS_JSON = "calls_json"
    private const val MAX_CALLS_STORED = 1000 // Keep last 1000 calls
    
    private val callCount = AtomicInteger(0)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    private var sharedPreferences: SharedPreferences? = null
    
    /**
     * Initialize the tracker with app context
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        callCount.set(sharedPreferences?.getInt(KEY_TOTAL_COUNT, 0) ?: 0)
    }
    
    /**
     * Track a Firebase read operation
     */
    fun trackRead(
        path: String,
        method: String = "read", // "read", "addValueEventListener", "addListenerForSingleValueEvent"
        onSuccess: ((DataSnapshot?) -> Unit)? = null,
        onError: ((DatabaseError?) -> Unit)? = null
    ) {
        val callId = callCount.incrementAndGet()
        val timestamp = System.currentTimeMillis()
        val timestampStr = dateFormat.format(Date(timestamp))
        
        val callData = JsonObject().apply {
            addProperty("id", callId)
            addProperty("type", "read")
            addProperty("method", method)
            addProperty("path", path)
            addProperty("timestamp", timestampStr)
            addProperty("timestampMs", timestamp.toString())
            add("request", JsonObject().apply {
                addProperty("path", path)
                addProperty("method", method)
            })
            add("response", JsonObject().apply {
                addProperty("status", "pending")
            })
        }
        
        // Store call
        storeCall(callData)
        
        // Update response when call completes
        if (onSuccess != null || onError != null) {
            // Note: This is a simplified tracking - actual response tracking
            // would need to be integrated into the Firebase callbacks
        }
    }
    
    /**
     * Track a Firebase write operation
     */
    fun trackWrite(
        path: String,
        method: String = "write", // "setValue", "updateChildren", "push", "removeValue"
        data: Any? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception?) -> Unit)? = null
    ) {
        val callId = callCount.incrementAndGet()
        val timestamp = System.currentTimeMillis()
        val timestampStr = dateFormat.format(Date(timestamp))
        
        // Convert data to JSON string
        val dataJson = try {
            if (data != null) {
                gson.toJson(data)
            } else {
                "null"
            }
        } catch (e: Exception) {
            "{\"error\": \"Failed to serialize: ${e.message}\"}"
        }
        
        val callData = JsonObject().apply {
            addProperty("id", callId)
            addProperty("type", "write")
            addProperty("method", method)
            addProperty("path", path)
            addProperty("timestamp", timestampStr)
            addProperty("timestampMs", timestamp.toString())
            add("request", JsonObject().apply {
                addProperty("path", path)
                addProperty("method", method)
                addProperty("data", dataJson)
            })
            add("response", JsonObject().apply {
                addProperty("status", "pending")
            })
        }
        
        // Store call
        storeCall(callData)
    }
    
    /**
     * Update call response (success)
     */
    fun updateCallResponse(
        callId: Int? = null,
        path: String? = null,
        success: Boolean = true,
        data: Any? = null,
        error: String? = null
    ) {
        val calls = getCallsList()
        val callToUpdate = if (callId != null) {
            calls.find { it.get("id")?.asInt == callId }
        } else if (path != null) {
            // Update the most recent call for this path
            calls.filter { it.get("path")?.asString == path }.maxByOrNull { 
                try {
                    it.get("timestampMs")?.asString?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        } else {
            // Update the most recent call
            calls.maxByOrNull { 
                try {
                    it.get("timestampMs")?.asString?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }
        }
        
        callToUpdate?.let { call ->
            val response = call.getAsJsonObject("response")
            response.addProperty("status", if (success) "success" else "error")
            response.addProperty("timestamp", dateFormat.format(Date()))
            
            if (success && data != null) {
                try {
                    val dataJson = gson.toJson(data)
                    response.addProperty("data", dataJson)
                } catch (e: Exception) {
                    response.addProperty("data", "{\"error\": \"Failed to serialize response\"}")
                }
            }
            
            if (error != null) {
                response.addProperty("error", error)
            }
            
            // Update stored calls
            updateCallsList(calls)
        }
    }
    
    /**
     * Store a call in SharedPreferences
     */
    private fun storeCall(callData: JsonObject) {
        val calls = getCallsList().toMutableList()
        calls.add(callData)
        
        // Keep only last MAX_CALLS_STORED calls
        if (calls.size > MAX_CALLS_STORED) {
            calls.removeAt(0)
        }
        
        updateCallsList(calls)
        saveTotalCount()
    }
    
    /**
     * Get all calls as a list
     */
    private fun getCallsList(): MutableList<JsonObject> {
        val prefs = sharedPreferences ?: return mutableListOf()
        val callsJson = prefs.getString(KEY_CALLS_JSON, "[]") ?: "[]"
        
        return try {
            val jsonArray = JsonParser.parseString(callsJson).asJsonArray
            jsonArray.map { it.asJsonObject }.toMutableList()
        } catch (e: Exception) {
            LogHelper.e("FirebaseCallTracker", "Error parsing calls JSON", e)
            mutableListOf()
        }
    }
    
    /**
     * Update calls list in SharedPreferences
     */
    private fun updateCallsList(calls: List<JsonObject>) {
        val prefs = sharedPreferences ?: return
        
        try {
            val jsonArray = com.google.gson.JsonArray().apply {
                calls.forEach { add(it) }
            }
            val jsonString = gson.toJson(jsonArray)
            
            prefs.edit()
                .putString(KEY_CALLS_JSON, jsonString)
                .apply()
        } catch (e: Exception) {
            LogHelper.e("FirebaseCallTracker", "Error saving calls JSON", e)
        }
    }
    
    /**
     * Save total count
     */
    private fun saveTotalCount() {
        sharedPreferences?.edit()
            ?.putInt(KEY_TOTAL_COUNT, callCount.get())
            ?.apply()
    }
    
    /**
     * Get total call count
     */
    fun getTotalCount(): Int {
        return callCount.get()
    }
    
    /**
     * Get all calls as JSON string
     */
    fun getAllCallsJson(): String {
        val calls = getCallsList()
        val summary = JsonObject().apply {
            addProperty("totalCalls", callCount.get())
            addProperty("totalStored", calls.size)
            addProperty("generatedAt", dateFormat.format(Date()))
            add("calls", com.google.gson.JsonArray().apply {
                calls.forEach { add(it) }
            })
        }
        return gson.toJson(summary)
    }
    
    /**
     * Get calls summary statistics
     */
    fun getSummary(): JsonObject {
        val calls = getCallsList()
        val reads = calls.count { it.get("type")?.asString == "read" }
        val writes = calls.count { it.get("type")?.asString == "write" }
        val successes = calls.count { 
            it.getAsJsonObject("response").get("status")?.asString == "success" 
        }
        val errors = calls.count { 
            it.getAsJsonObject("response").get("status")?.asString == "error" 
        }
        val pending = calls.count { 
            it.getAsJsonObject("response").get("status")?.asString == "pending" 
        }
        
        return JsonObject().apply {
            addProperty("totalCalls", callCount.get())
            addProperty("totalStored", calls.size)
            addProperty("reads", reads)
            addProperty("writes", writes)
            addProperty("successes", successes)
            addProperty("errors", errors)
            addProperty("pending", pending)
            addProperty("generatedAt", dateFormat.format(Date()))
        }
    }
    
    /**
     * Clear all tracked calls
     */
    fun clearAll() {
        callCount.set(0)
        sharedPreferences?.edit()
            ?.putInt(KEY_TOTAL_COUNT, 0)
            ?.putString(KEY_CALLS_JSON, "[]")
            ?.apply()
    }
    
    /**
     * Get calls filtered by path
     */
    fun getCallsByPath(path: String): List<JsonObject> {
        return getCallsList().filter { 
            it.get("path")?.asString?.contains(path) == true 
        }
    }
    
    /**
     * Get calls filtered by type (read/write)
     */
    fun getCallsByType(type: String): List<JsonObject> {
        return getCallsList().filter { 
            it.get("type")?.asString == type 
        }
    }
}
