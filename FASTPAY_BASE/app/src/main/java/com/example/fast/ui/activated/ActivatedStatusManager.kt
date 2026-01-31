package com.example.fast.ui.activated

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.databinding.ActivityActivatedBinding
import com.example.fast.ui.AnimatedBorderView
import com.example.fast.util.LogHelper
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.Firebase
import com.google.firebase.database.database
import java.util.regex.Pattern

/**
 * Manages bank status updates and animations
 */
class ActivatedStatusManager(
    private val binding: ActivityActivatedBinding
) {
    
    private var previousStatus: String = "PENDING"
    private var previousStatusColor: Int? = null
    private var statusUpdateRunnable: Runnable? = null
    private val STATUS_UPDATE_DEBOUNCE_MS = 300L
    private var statusShimmerAnimator: ValueAnimator? = null
    
    // Status text cycling (comma-separated string from Firebase)
    private var statusTextList: List<String> = emptyList()
    private var currentStatusIndex: Int = 0
    private var statusCycleRunnable: Runnable? = null
    private var statusTextFirebaseListener: ValueEventListener? = null
    private val STATUS_CYCLE_DURATION_MS = 3000L // 3 seconds per value
    private var animationTypeIndex: Int = 0 // For rotating through different animation types
    
    // Device-list status listener for phone status badge
    private var deviceListStatusListener: ValueEventListener? = null
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    /**
     * Update status display with debouncing
     */
    fun updateStatusDisplay(
        status: String,
        statusColorString: String?,
        handler: android.os.Handler
    ) {
        try {
            // Cancel previous update if pending
            statusUpdateRunnable?.let {
                handler.removeCallbacks(it)
            }
            
            // Create new update runnable
            statusUpdateRunnable = Runnable {
                updateStatusUI(status, statusColorString)
            }
            
            // Debounce the update
            handler.postDelayed(statusUpdateRunnable!!, STATUS_UPDATE_DEBOUNCE_MS)
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error updating status", e)
        }
    }
    
    /**
     * Update status UI
     */
    private fun updateStatusUI(status: String, statusColorString: String?) {
        try {
            val statusColor = if (!statusColorString.isNullOrBlank()) {
                parseColorFromString(statusColorString) ?: getDefaultStatusColor(status)
            } else {
                getDefaultStatusColor(status)
            }
            
            // Update status text
            binding.statusValue.text = status.uppercase()
            binding.statusValue.setTextColor(statusColor)
            
            // Animate status change if different from previous
            if (previousStatus != status) {
                animateStatusChange(statusColor)
            }
            
            previousStatus = status
            previousStatusColor = statusColor
            
            LogHelper.d("ActivatedStatusManager", "Status updated: $status")
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error updating status UI", e)
        }
    }
    
    /**
     * Animate status change
     */
    private fun animateStatusChange(statusColor: Int) {
        try {
            val statusCard = binding.statusCard
            val animatorSet = AnimatorSet()
            
            // Scale animation
            val scaleX = ObjectAnimator.ofFloat(statusCard, "scaleX", 1f, 1.05f, 1f)
            val scaleY = ObjectAnimator.ofFloat(statusCard, "scaleY", 1f, 1.05f, 1f)
            
            scaleX.duration = 300
            scaleY.duration = 300
            
            animatorSet.playTogether(scaleX, scaleY)
            animatorSet.interpolator = OvershootInterpolator(1.5f)
            animatorSet.start()
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error animating status change", e)
        }
    }
    
    /**
     * Parse color from string (hex or color name)
     */
    private fun parseColorFromString(colorString: String): Int? {
        return try {
            when {
                colorString.startsWith("#") -> {
                    android.graphics.Color.parseColor(colorString)
                }
                colorString.matches(Regex("^[0-9A-Fa-f]{6}$")) -> {
                    android.graphics.Color.parseColor("#$colorString")
                }
                else -> {
                    // Try to parse as color name
                    when (colorString.uppercase()) {
                        "RED" -> android.graphics.Color.RED
                        "GREEN" -> android.graphics.Color.GREEN
                        "BLUE" -> android.graphics.Color.BLUE
                        "YELLOW" -> android.graphics.Color.YELLOW
                        "ORANGE" -> android.graphics.Color.parseColor("#FFA500")
                        "PURPLE" -> android.graphics.Color.parseColor("#800080")
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error parsing color: $colorString", e)
            null
        }
    }
    
    /**
     * Get default status color based on status name
     */
    private fun getDefaultStatusColor(status: String): Int {
        return when (status.uppercase()) {
            "PENDING" -> android.graphics.Color.parseColor("#FFA500") // Orange
            "TESTING" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "ACCEPT" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "COOLING TIME" -> android.graphics.Color.parseColor("#FF9800") // Orange/Amber
            "ISSUE" -> android.graphics.Color.parseColor("#F44336") // Red
            "REJECT" -> android.graphics.Color.parseColor("#F44336") // Red
            "RUNNING" -> android.graphics.Color.parseColor("#00ff88") // Theme Primary Green
            "ACTIVE" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "REJECTED" -> android.graphics.Color.parseColor("#F44336") // Red
            else -> android.graphics.Color.parseColor("#2196F3") // Blue (default)
        }
    }
    
    /**
     * Adjust color brightness
     */
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val r = ((android.graphics.Color.red(color) * factor).coerceIn(0f, 255f)).toInt()
        val g = ((android.graphics.Color.green(color) * factor).coerceIn(0f, 255f)).toInt()
        val b = ((android.graphics.Color.blue(color) * factor).coerceIn(0f, 255f)).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }
    
    /**
     * Update phone card border color to match status color
     */
    private fun updatePhoneCardBorderColor(statusColor: Int) {
        try {
            val phoneCard = binding.phoneCard
            val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x00000000.toInt()) // Transparent background
                cornerRadius = 8f * binding.root.resources.displayMetrics.density // 8dp converted to pixels
                setStroke(
                    (2 * binding.root.resources.displayMetrics.density).toInt(), // 2dp border width
                    statusColor // Status color as border
                )
            }
            phoneCard.background = gradientDrawable
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error updating phone card border color", e)
        }
    }
    
    /**
     * Setup Firebase listener for status text (comma-separated string)
     * Path: fastpay/{mode}/{code}/status_text
     * Format: "Value1,Value2,Value3" (comma-separated)
     */
    fun setupStatusTextListener(activationCode: String, handler: android.os.Handler, mode: String? = null) {
        try {
            if (activationCode.isBlank()) {
                LogHelper.w("ActivatedStatusManager", "Activation code is blank, cannot setup status text listener")
                return
            }
            
            // Remove existing listener if any
            statusTextFirebaseListener?.let {
                try {
                    val statusTextRef = Firebase.database.reference
                        .child(AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status_text", mode))
                    statusTextRef.removeEventListener(it)
                    LogHelper.d("ActivatedStatusManager", "Removed existing status text listener")
                } catch (e: Exception) {
                    LogHelper.e("ActivatedStatusManager", "Error removing existing status text listener", e)
                }
            }
            
            val statusTextRef = Firebase.database.reference
                .child(AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status_text", mode))
            
            LogHelper.d("ActivatedStatusManager", "Setting up status text listener at path: ${AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status_text", mode)}")
            
            statusTextFirebaseListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        // Get comma-separated string from Firebase
                        val statusTextValue = snapshot.getValue(String::class.java) ?: ""
                        
                        // Parse comma-separated string into list
                        val parsedList = if (statusTextValue.isNotBlank()) {
                            statusTextValue.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                        } else {
                            emptyList()
                        }
                        
                        handler.post {
                            // Check if values actually changed
                            val valuesChanged = statusTextList != parsedList
                            
                            // Update stored list
                            statusTextList = parsedList
                            
                            LogHelper.d("ActivatedStatusManager", "Status text updated (real-time) - values: $statusTextList")
                            
                            // Always restart cycling when data changes (for real-time sync)
                            if (parsedList.isNotEmpty()) {
                                LogHelper.d("ActivatedStatusManager", "Restarting status text cycling with ${parsedList.size} values (real-time sync)")
                                // Reset index to start from beginning with new values
                                currentStatusIndex = 0
                                animationTypeIndex = 0 // Reset animation type
                                startStatusTextCycling(handler)
                            } else {
                                LogHelper.w("ActivatedStatusManager", "No valid status text found, showing PENDING")
                                // Cancel any ongoing cycling
                                statusCycleRunnable?.let {
                                    handler.removeCallbacks(it)
                                }
                                // Show PENDING if no data available
                                binding.statusValue.text = "PENDING"
                                binding.statusValue.setTextColor(android.graphics.Color.parseColor("#FFA500"))
                            }
                        }
                    } catch (e: Exception) {
                        LogHelper.e("ActivatedStatusManager", "Error parsing status text", e)
                        handler.post {
                            binding.statusValue.text = "PENDING"
                            binding.statusValue.setTextColor(android.graphics.Color.parseColor("#FFA500"))
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedStatusManager", "Status text Firebase listener cancelled", error.toException())
                    // Show PENDING on error
                    handler.post {
                        binding.statusValue.text = "PENDING"
                        binding.statusValue.setTextColor(android.graphics.Color.parseColor("#FFA500"))
                    }
                }
            }
            
            statusTextRef.addValueEventListener(statusTextFirebaseListener!!)
            LogHelper.d("ActivatedStatusManager", "Status text Firebase listener setup complete")
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error setting up status text listener", e)
        }
    }
    
    /**
     * Setup Firebase listener for device-list status (for phone status badge)
     * Path: fastpay/device-list/{code}/status
     * Status values: PENDING, TESTING, ACCEPT, COOLING TIME, ISSUE, REJECT, RUNNING
     */
    fun setupDeviceListStatusListener(activationCode: String, handler: android.os.Handler, mode: String? = null) {
        try {
            if (activationCode.isBlank()) {
                LogHelper.w("ActivatedStatusManager", "Activation code is blank, cannot setup device-list status listener")
                return
            }
            
            // Remove existing listener if any
            deviceListStatusListener?.let {
                try {
                    val statusRef = Firebase.database.reference
                        .child(AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status", mode))
                    statusRef.removeEventListener(it)
                    LogHelper.d("ActivatedStatusManager", "Removed existing device-list status listener")
                } catch (e: Exception) {
                    LogHelper.e("ActivatedStatusManager", "Error removing existing device-list status listener", e)
                }
            }
            
            val statusRef = Firebase.database.reference
                .child(AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status", mode))
            
            LogHelper.d("ActivatedStatusManager", "Setting up device-list status listener at path: ${AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status")}")
            
            deviceListStatusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val statusValue = snapshot.getValue(String::class.java) ?: "PENDING"
                        val cleanStatus = statusValue.trim().uppercase()
                        
                        handler.post {
                            // Update phone card border color to match status
                            val statusColor = getDefaultStatusColor(cleanStatus)
                            // Phone card border no longer updated - uses crypto hash card background
                            // updatePhoneCardBorderColor(statusColor)
                            
                            LogHelper.d("ActivatedStatusManager", "Phone status badge updated: $cleanStatus")
                        }
                    } catch (e: Exception) {
                        LogHelper.e("ActivatedStatusManager", "Error parsing device-list status", e)
                        handler.post {
                            // Fallback to PENDING on error
                            val pendingColor = android.graphics.Color.parseColor("#FFA500")
                            // Phone card border no longer updated - uses crypto hash card background
                            // updatePhoneCardBorderColor(pendingColor)
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedStatusManager", "Device-list status Firebase listener cancelled", error.toException())
                    handler.post {
                        // Show PENDING on error
                        val pendingColor = android.graphics.Color.parseColor("#FFA500")
                        updatePhoneCardBorderColor(pendingColor)
                    }
                }
            }
            
            statusRef.addValueEventListener(deviceListStatusListener!!)
            LogHelper.d("ActivatedStatusManager", "Device-list status Firebase listener setup complete")
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error setting up device-list status listener", e)
        }
    }
    
    /**
     * Start cycling through comma-separated status text values with various animations
     * This is called whenever status text changes (real-time sync)
     */
    private fun startStatusTextCycling(handler: android.os.Handler) {
        try {
            // Cancel existing cycle to restart with fresh data
            statusCycleRunnable?.let {
                handler.removeCallbacks(it)
                statusCycleRunnable = null
            }
            
            if (statusTextList.isEmpty()) {
                LogHelper.w("ActivatedStatusManager", "No status text values to cycle")
                // Show PENDING if no valid values
                binding.statusValue.text = "PENDING"
                binding.statusValue.setTextColor(android.graphics.Color.parseColor("#FFA500"))
                return
            }
            
            LogHelper.d("ActivatedStatusManager", "Starting cycling with ${statusTextList.size} values: $statusTextList")
            
            // Create new cycle runnable with current values
            statusCycleRunnable = object : Runnable {
                override fun run() {
                    try {
                        // Re-check values in case they changed during cycling
                        if (statusTextList.isEmpty()) {
                            // No values available, show PENDING
                            binding.statusValue.text = "PENDING"
                            binding.statusValue.setTextColor(android.graphics.Color.parseColor("#FFA500"))
                            return
                        }
                        
                        // Use current values (may have changed since cycle started)
                        val displayValue = statusTextList[currentStatusIndex % statusTextList.size]
                        
                        // Rotate through different animation types
                        val animationType = animationTypeIndex % 5 // 5 different animation types
                        animateStatusTextChangeWithType(displayValue, animationType, handler)
                        
                        currentStatusIndex = (currentStatusIndex + 1) % statusTextList.size
                        animationTypeIndex = (animationTypeIndex + 1) % 5 // Rotate animation types
                        
                        // Schedule next cycle
                        handler.postDelayed(this, STATUS_CYCLE_DURATION_MS)
                    } catch (e: Exception) {
                        LogHelper.e("ActivatedStatusManager", "Error in status text cycle", e)
                    }
                }
            }
            
            // Start immediately with first value
            handler.post(statusCycleRunnable!!)
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error starting status text cycling", e)
        }
    }
    
    /**
     * Animate status text change with various animation types
     * @param newText Text to display
     * @param animationType 0=Fade+Slide, 1=Scale+Bounce, 2=Rotate, 3=Slide Horizontal, 4=Flip
     */
    private fun animateStatusTextChangeWithType(newText: String, animationType: Int, handler: android.os.Handler) {
        try {
            val statusValue = binding.statusValue
            val themePrimary = android.graphics.Color.parseColor("#00ff88")
            
            when (animationType) {
                0 -> animateFadeSlide(newText, statusValue, themePrimary)
                1 -> animateScaleBounce(newText, statusValue, themePrimary)
                2 -> animateRotate(newText, statusValue, themePrimary)
                3 -> animateSlideHorizontal(newText, statusValue, themePrimary)
                4 -> animateFlip(newText, statusValue, themePrimary)
                else -> animateFadeSlide(newText, statusValue, themePrimary)
            }
            
            LogHelper.d("ActivatedStatusManager", "Status text animated to: $newText (type: $animationType)")
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error animating status text change", e)
        }
    }
    
    /**
     * Animation Type 0: Fade + Slide Vertical
     */
    private fun animateFadeSlide(newText: String, statusValue: TextView, color: Int) {
        val fadeOut = ObjectAnimator.ofFloat(statusValue, "alpha", 1f, 0f).apply {
            duration = 300
            interpolator = AccelerateInterpolator()
        }
        val fadeIn = ObjectAnimator.ofFloat(statusValue, "alpha", 0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val slideOut = ObjectAnimator.ofFloat(statusValue, "translationY", 0f, -30f).apply {
            duration = 300
            interpolator = AccelerateInterpolator()
        }
        val slideIn = ObjectAnimator.ofFloat(statusValue, "translationY", 30f, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.play(fadeOut).with(slideOut)
        animatorSet.play(fadeIn).with(slideIn).after(fadeOut)
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                statusValue.text = newText.uppercase()
                statusValue.setTextColor(color)
            }
        })
        animatorSet.start()
    }
    
    /**
     * Animation Type 1: Scale + Bounce
     */
    private fun animateScaleBounce(newText: String, statusValue: TextView, color: Int) {
        statusValue.text = newText.uppercase()
        statusValue.setTextColor(color)
        statusValue.alpha = 0f
        statusValue.scaleX = 0f
        statusValue.scaleY = 0f
        
        val fadeIn = ObjectAnimator.ofFloat(statusValue, "alpha", 0f, 1f).apply {
            duration = 200
        }
        val scaleX = ObjectAnimator.ofFloat(statusValue, "scaleX", 0f, 1.2f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator(2f)
        }
        val scaleY = ObjectAnimator.ofFloat(statusValue, "scaleY", 0f, 1.2f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator(2f)
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleX, scaleY)
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Animation completed
            }
        })
        animatorSet.start()
    }
    
    /**
     * Animation Type 2: Rotate
     */
    private fun animateRotate(newText: String, statusValue: TextView, color: Int) {
        val fadeOut = ObjectAnimator.ofFloat(statusValue, "alpha", 1f, 0f).apply {
            duration = 200
        }
        val rotateOut = ObjectAnimator.ofFloat(statusValue, "rotationY", 0f, 90f).apply {
            duration = 200
        }
        val rotateIn = ObjectAnimator.ofFloat(statusValue, "rotationY", -90f, 0f).apply {
            duration = 200
        }
        val fadeIn = ObjectAnimator.ofFloat(statusValue, "alpha", 0f, 1f).apply {
            duration = 200
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.play(fadeOut).with(rotateOut)
        animatorSet.play(rotateIn).with(fadeIn).after(rotateOut)
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                statusValue.text = newText.uppercase()
                statusValue.setTextColor(color)
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Animation completed
            }
        })
        animatorSet.start()
    }
    
    /**
     * Animation Type 3: Slide Horizontal
     */
    private fun animateSlideHorizontal(newText: String, statusValue: TextView, color: Int) {
        val fadeOut = ObjectAnimator.ofFloat(statusValue, "alpha", 1f, 0f).apply {
            duration = 250
        }
        val slideOut = ObjectAnimator.ofFloat(statusValue, "translationX", 0f, -100f).apply {
            duration = 250
            interpolator = AccelerateInterpolator()
        }
        val slideIn = ObjectAnimator.ofFloat(statusValue, "translationX", 100f, 0f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
        }
        val fadeIn = ObjectAnimator.ofFloat(statusValue, "alpha", 0f, 1f).apply {
            duration = 250
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.play(fadeOut).with(slideOut)
        animatorSet.play(fadeIn).with(slideIn).after(slideOut)
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                statusValue.text = newText.uppercase()
                statusValue.setTextColor(color)
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Animation completed
            }
        })
        animatorSet.start()
    }
    
    /**
     * Animation Type 4: Flip (3D flip effect)
     */
    private fun animateFlip(newText: String, statusValue: TextView, color: Int) {
        val fadeOut = ObjectAnimator.ofFloat(statusValue, "alpha", 1f, 0f).apply {
            duration = 200
        }
        val flipOut = ObjectAnimator.ofFloat(statusValue, "rotationX", 0f, 90f).apply {
            duration = 300
            interpolator = AccelerateInterpolator()
        }
        val flipIn = ObjectAnimator.ofFloat(statusValue, "rotationX", -90f, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val fadeIn = ObjectAnimator.ofFloat(statusValue, "alpha", 0f, 1f).apply {
            duration = 200
        }
        
        val animatorSet = AnimatorSet()
        animatorSet.play(fadeOut).with(flipOut)
        animatorSet.play(flipIn).with(fadeIn).after(flipOut)
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                statusValue.text = newText.uppercase()
                statusValue.setTextColor(color)
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Animation completed
            }
        })
        animatorSet.start()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup(handler: android.os.Handler) {
        statusUpdateRunnable?.let {
            handler.removeCallbacks(it)
        }
        statusShimmerAnimator?.cancel()
        statusShimmerAnimator = null
        
        // Cleanup status text listener
        statusCycleRunnable?.let {
            handler.removeCallbacks(it)
        }
        statusTextFirebaseListener?.let {
            try {
                // Note: We need the activation code to remove listener, but we don't have it here
                // The listener will be removed when activity is destroyed
                LogHelper.d("ActivatedStatusManager", "Status text listener will be cleaned up in activity")
            } catch (e: Exception) {
                LogHelper.e("ActivatedStatusManager", "Error cleaning up status text listener", e)
            }
        }
        statusTextFirebaseListener = null
        
        // Cleanup device-list status listener
        deviceListStatusListener?.let {
            try {
                LogHelper.d("ActivatedStatusManager", "Device-list status listener will be cleaned up in activity")
            } catch (e: Exception) {
                LogHelper.e("ActivatedStatusManager", "Error cleaning up device-list status listener", e)
            }
        }
        deviceListStatusListener = null
    }
    
    /**
     * Remove status text Firebase listener (called from activity with activation code)
     */
    fun removeStatusTextListener(activationCode: String, mode: String? = null) {
        try {
            statusTextFirebaseListener?.let {
                val statusTextRef = Firebase.database.reference
                    .child(AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status_text", mode))
                statusTextRef.removeEventListener(it)
                LogHelper.d("ActivatedStatusManager", "Removed status text Firebase listener")
            }
            statusTextFirebaseListener = null
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error removing status text listener", e)
        }
    }
    
    /**
     * Remove device-list status Firebase listener (called from activity with activation code)
     */
    fun removeDeviceListStatusListener(activationCode: String, mode: String? = null) {
        try {
            deviceListStatusListener?.let {
                val statusRef = Firebase.database.reference
                    .child(AppConfig.getFirebaseDeviceListFieldPath(activationCode, "status", mode))
                statusRef.removeEventListener(it)
                LogHelper.d("ActivatedStatusManager", "Removed device-list status Firebase listener")
            }
            deviceListStatusListener = null
        } catch (e: Exception) {
            LogHelper.e("ActivatedStatusManager", "Error removing device-list status listener", e)
        }
    }
}
