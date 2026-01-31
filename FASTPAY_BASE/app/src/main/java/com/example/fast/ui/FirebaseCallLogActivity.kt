package com.example.fast.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.fast.R
import com.example.fast.databinding.ActivityFirebaseCallLogBinding
import com.example.fast.util.FirebaseCallTracker
import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * Activity to display Firebase call logs
 * Shows total call count and allows viewing/exporting JSON data
 */
class FirebaseCallLogActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFirebaseCallLogBinding
    private val gson = Gson()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        binding = ActivityFirebaseCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set status bar colors
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
        }
        
        setupUI()
        loadData()
    }
    
    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }
        
        // Refresh button
        binding.refreshButton.setOnClickListener {
            loadData()
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }
        
        // Copy JSON button
        binding.copyJsonButton.setOnClickListener {
            copyJsonToClipboard()
        }
        
        // Clear logs button
        binding.clearLogsButton.setOnClickListener {
            FirebaseCallTracker.clearAll()
            loadData()
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadData() {
        // Get summary
        val summary = FirebaseCallTracker.getSummary()
        
        // Update summary text
        binding.totalCallsText.text = "Total Calls: ${summary.get("totalCalls")?.asInt ?: 0}"
        binding.storedCallsText.text = "Stored: ${summary.get("totalStored")?.asInt ?: 0}"
        binding.readsText.text = "Reads: ${summary.get("reads")?.asInt ?: 0}"
        binding.writesText.text = "Writes: ${summary.get("writes")?.asInt ?: 0}"
        binding.successesText.text = "Successes: ${summary.get("successes")?.asInt ?: 0}"
        binding.errorsText.text = "Errors: ${summary.get("errors")?.asInt ?: 0}"
        binding.pendingText.text = "Pending: ${summary.get("pending")?.asInt ?: 0}"
        
        // Get all calls JSON
        val allCallsJson = FirebaseCallTracker.getAllCallsJson()
        
        // Format JSON for display
        val formattedJson = try {
            val jsonElement = JsonParser.parseString(allCallsJson)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            allCallsJson
        }
        
        // Update JSON text view
        binding.jsonTextView.text = formattedJson
    }
    
    private fun copyJsonToClipboard() {
        val json = FirebaseCallTracker.getAllCallsJson()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Firebase Call Logs", json)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
