package com.example.fast.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.transition.Fade
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.util.Pair
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.service.NotificationReceiver
import com.example.fast.util.VersionChecker
import com.google.firebase.Firebase
import com.example.fast.util.DjangoApiHelper
import com.example.fast.util.ProcessLog
import com.example.fast.ui.RemoteUpdateActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.prexoft.prexocore.after
import com.prexoft.prexocore.goTo
import com.prexoft.prexocore.readInternalFile
import android.provider.Settings
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import android.content.SharedPreferences
import androidx.cardview.widget.CardView

/**
 * SplashActivity - "Redmi" Version
 *
 * Features:
 * - First letter ("F") always comes from right edge of screen
 * - Other letters come randomly from: above, below, or left (right not available after first letter)
 * - All letters start from actual screen edges (completely off-screen)
 * - Tagline animation after all letters complete
 * - Logo transitions smoothly to next activity (ActivationActivity or ActivatedActivity)
 * - Smart navigation based on setup.txt (activated vs not activated)
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val letterViews = mutableListOf<TextView>()
    private var completedLetterAnimations = 0
    private var totalLetters = 0
    private var taglineAnimated = false

    // Store animation references for cleanup
    private val continuousAnimations = mutableListOf<AnimatorSet>()
    private var taglinePulseAnimation: ObjectAnimator? = null

    // Store Handler references for cleanup
    private val handler = Handler(Looper.getMainLooper())
    private val handlerRunnables = mutableListOf<Runnable>()

    private var isNavigating = false
    private var hasStartedNavigation = false // Prevent double navigation during animation

    // SharedPreferences for tracking activation status
    private val prefsName = "activation_prefs"
    private val KEY_FIRST_LAUNCH = "first_launch"
    private val KEY_LOCALLY_ACTIVATED = "locally_activated"
    private val KEY_ACTIVATION_CODE = "activation_code"
    private val KEY_SPLASH_ANIMATION_INDEX = "splash_animation_index"

    private val sharedPreferences: SharedPreferences
        get() = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    // Grid animation types for testing
    private val gridAnimationTypes = listOf(
        GridBackgroundView.GridAnimationType.HORIZONTAL_SCROLL,
        GridBackgroundView.GridAnimationType.DIAGONAL_SCROLL,
        GridBackgroundView.GridAnimationType.PULSE,
        GridBackgroundView.GridAnimationType.WAVE,
        GridBackgroundView.GridAnimationType.SPIRAL,
        GridBackgroundView.GridAnimationType.RADIAL,
        GridBackgroundView.GridAnimationType.RANDOM,
        GridBackgroundView.GridAnimationType.STATIC_PULSE
    )

    private var currentAnimationIndex = 0
    private var currentGridAnimationType: GridBackgroundView.GridAnimationType = GridBackgroundView.GridAnimationType.RADIAL
    private var animationCycleRunnable: Runnable? = null
    private val ANIMATION_CYCLE_INTERVAL_MS = 2 * 60 * 1000L // 2 minutes

    // Minimum splash screen display time (6 seconds)
    private val MIN_SPLASH_DISPLAY_TIME_MS = 6000L
    private var splashStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        ProcessLog.start("SplashActivity_onCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Use RADIAL grid animation (fixed)
        currentGridAnimationType = GridBackgroundView.GridAnimationType.RADIAL

        android.util.Log.d("SplashActivity", "=== Grid Animation: ${currentGridAnimationType.name} ===")

        // Track splash screen start time for minimum display duration
        splashStartTime = System.currentTimeMillis()

        // Set window background IMMEDIATELY before setContentView to prevent black screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setBackgroundDrawableResource(R.drawable.gradient)
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            // Disable default exit transition to prevent black screen
            window.allowEnterTransitionOverlap = true
            window.allowReturnTransitionOverlap = true
            postponeEnterTransition()
        }

        setContentView(R.layout.activity_splash)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        val letterContainer = findViewById<LinearLayout>(R.id.letterContainer)
        val rootView = findViewById<View>(R.id.main)
        val taglineTextView = findViewById<TextView>(R.id.taglineTextView)
        val logoTextView = findViewById<TextView>(R.id.logoTextView)
        val scanlineView = findViewById<ScanlineView>(R.id.scanlineView)
        val waveView = findViewById<WaveView>(R.id.waveView)
        val gridBackground = findViewById<GridBackgroundView>(R.id.gridBackground)

        // Animation name display and cycle button removed
        // Automatic animation cycling disabled - using RADIAL animation

        // Start scanline animation
        scanlineView?.startScanlineAnimation()

        // Set grid animation type (already determined in onCreate)
        gridBackground?.setGridAnimationType(currentGridAnimationType)

        // Connect wave brightness to grid background
        waveView?.setBrightnessUpdateListener(object : WaveView.BrightnessUpdateListener {
            override fun onBrightnessUpdate(multiplier: Float) {
                gridBackground?.setBrightnessMultiplier(multiplier)
            }
        })

        // Schedule wave animation to start 2 seconds before navigation
        // This will be triggered when we know navigation is about to happen
        // We'll start it in navigateToNextWithFade() to ensure proper timing

        // Use default branding values from resources
        val defaultLogoName = getString(R.string.app_name_title)
        val defaultTagline = getString(R.string.app_tagline)
        taglineTextView.text = defaultTagline
        logoTextView.text = defaultLogoName

        // Grid animation already set in onCreate - continue with logo animation
        setupNeonGlowAnimation(logoTextView, taglineTextView, letterContainer)

        // Register device with Django and Firebase as soon as possible (non-blocking)
        registerDeviceEarly()
        ProcessLog.stop("SplashActivity_onCreate")
    }

    /**
     * Register device with Django and Firebase immediately (background).
     * Permission entry point is the status card in ActivationActivity; before that only device registration runs here.
     * Strategy: Try Firebase first (with timeout), then register with best available data.
     */
    @SuppressLint("HardwareIds")
    private fun registerDeviceEarly() {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            if (deviceId.isNullOrBlank()) {
                android.util.Log.w("SplashActivity", "Device ID is null or blank, skipping early registration")
                return
            }

            android.util.Log.d("SplashActivity", "Starting early device registration (deviceId: $deviceId)")

            // Try to get activation code from SharedPreferences (if available)
            val savedCode = sharedPreferences.getString(KEY_ACTIVATION_CODE, "") ?: ""

            // Base device data (always available)
            val baseDeviceData = mutableMapOf<String, Any?>(
                "model" to (Build.BRAND + " " + Build.MODEL),
                "time" to System.currentTimeMillis(),
                "app_version_code" to VersionChecker.getCurrentVersionCode(this@SplashActivity),
                "app_version_name" to VersionChecker.getCurrentVersionName(this@SplashActivity)
            )

            // Add code if available from SharedPreferences
            if (savedCode.isNotBlank()) {
                baseDeviceData["code"] = savedCode
            }

            // Try to get isActive and code from Firebase (with timeout)
            val deviceRef = Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId))

            // Set timeout - if Firebase doesn't respond in 1.5 seconds, register with basic data
            var firebaseTimeout = false
            val timeoutLock = Any()
            val timeoutRunnable = Runnable {
                synchronized(timeoutLock) {
                    if (!firebaseTimeout) {
                        firebaseTimeout = true
                        android.util.Log.d("SplashActivity", "Early registration: Firebase timeout, registering with basic data")
                        lifecycleScope.launch {
                            baseDeviceData["isActive"] = false // Default to false if Firebase unavailable
                            DjangoApiHelper.registerDevice(deviceId, baseDeviceData)
                        }
                    }
                }
            }
            handler.postDelayed(timeoutRunnable, 1500) // 1.5 second timeout

            deviceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    handler.removeCallbacks(timeoutRunnable)

                    synchronized(timeoutLock) {
                        if (firebaseTimeout) {
                            // Already registered with basic data due to timeout
                            android.util.Log.d("SplashActivity", "Early registration: Firebase responded after timeout, skipping duplicate registration")
                            return
                        }
                        firebaseTimeout = true // Prevent timeout from firing
                    }

                    val isActiveString = snapshot.child("isActive").getValue(String::class.java)
                    val isActiveBool = snapshot.child("isActive").getValue(Boolean::class.java)
                    val code = snapshot.child("code").getValue(String::class.java) ?: ""

                    // Update device data with Firebase info
                    val deviceData = baseDeviceData.toMutableMap()
                    if (code.isNotBlank()) {
                        deviceData["code"] = code
                    }
                    val isActive = (isActiveString == "Opened") ||
                        (isActiveString == "true") ||
                        (isActiveBool == true)
                    deviceData["isActive"] = isActive

                    // Register with Django with complete info
                    lifecycleScope.launch {
                        DjangoApiHelper.registerDevice(deviceId, deviceData)
                        android.util.Log.d("SplashActivity", "Early registration: Registered with Django using Firebase data")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    handler.removeCallbacks(timeoutRunnable)

                    synchronized(timeoutLock) {
                        if (firebaseTimeout) {
                            // Already registered with basic data due to timeout
                            return
                        }
                        firebaseTimeout = true // Prevent timeout from firing
                    }

                    // If Firebase fails, register with Django using available data
                    android.util.Log.w("SplashActivity", "Early registration: Firebase read failed, registering with basic data")
                    lifecycleScope.launch {
                        baseDeviceData["isActive"] = false // Default to false if Firebase unavailable
                        DjangoApiHelper.registerDevice(deviceId, baseDeviceData)
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Error in early device registration", e)
        }
    }

    /**
     * Update animation name display
     */
    private fun updateAnimationNameDisplay(animationNameTextView: TextView?) {
        animationNameTextView?.text = "Animation: ${currentGridAnimationType.name}"
        // Fade in animation name
        animationNameTextView?.alpha = 0f
        animationNameTextView?.animate()
            ?.alpha(0.7f)
            ?.setDuration(500)
            ?.start()
    }

    /**
     * Setup animation cycle button click listener
     */
    private fun setupAnimationCycleButton(
        button: CardView?,
        gridBackground: GridBackgroundView?,
        animationNameTextView: TextView?
    ) {
        button?.setOnClickListener {
            if (!isFinishing) {
                cycleToNextAnimation(gridBackground, animationNameTextView)
            }
        }

        // Add light pulse animation to button
        button?.let { btn ->
            val pulseAnimator = ObjectAnimator.ofFloat(btn, "alpha", 0.5f, 1f, 0.5f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
            pulseAnimator.start()
            continuousAnimations.add(AnimatorSet().apply { play(pulseAnimator) })
        }
    }

    /**
     * Start automatic animation cycling every 2 minutes
     */
    private fun startAutomaticAnimationCycle(
        gridBackground: GridBackgroundView?,
        animationNameTextView: TextView?
    ) {
        animationCycleRunnable = Runnable {
            if (!isFinishing && !isNavigating) {
                cycleToNextAnimation(gridBackground, animationNameTextView)
                // Schedule next cycle
                handler.postDelayed(animationCycleRunnable!!, ANIMATION_CYCLE_INTERVAL_MS)
            }
        }
        handlerRunnables.add(animationCycleRunnable!!)
        handler.postDelayed(animationCycleRunnable!!, ANIMATION_CYCLE_INTERVAL_MS)
    }

    /**
     * Cycle to next animation type
     */
    private fun cycleToNextAnimation(
        gridBackground: GridBackgroundView?,
        animationNameTextView: TextView?
    ) {
        // Increment animation index
        currentAnimationIndex = (currentAnimationIndex + 1) % gridAnimationTypes.size
        currentGridAnimationType = gridAnimationTypes[currentAnimationIndex]

        // Update grid animation
        gridBackground?.setGridAnimationType(currentGridAnimationType)

        // Update name display with fade effect
        animationNameTextView?.let { textView ->
            textView.animate()
                ?.alpha(0f)
                ?.setDuration(200)
                ?.withEndAction {
                    textView.text = "Animation: ${currentGridAnimationType.name}"
                    textView.animate()
                        ?.alpha(0.7f)
                        ?.setDuration(200)
                        ?.start()
                }
                ?.start()
        }

        android.util.Log.d("SplashActivity", "Cycled to animation: ${currentGridAnimationType.name} (index: $currentAnimationIndex)")
    }

    /**
     * Setup Neon Glow style animation (original)
     * Logo appears with neon glow effect, then tagline fades in
     */
    private fun setupNeonGlowAnimation(
        logoTextView: TextView,
        taglineTextView: TextView,
        letterContainer: LinearLayout
    ) {
        ProcessLog.start("Splash_setupNeonGlowAnimation")
        // Apply neon glow effect to logo text
        applyNeonGlowEffect(logoTextView)

        // Start logo animation with neon glow
        val logoAnimationRunnable = Runnable {
            if (!isFinishing) {
                animateNeonLogo(logoTextView, taglineTextView)
            }
        }
        handlerRunnables.add(logoAnimationRunnable)
        handler.postDelayed(logoAnimationRunnable, 300) // Small delay for grid to appear
        ProcessLog.stop("Splash_setupNeonGlowAnimation")
    }

    /**
     * Animate logo with neon glow effect
     */
    private fun animateNeonLogo(logoTextView: TextView, taglineTextView: TextView) {
        if (isFinishing) return
        ProcessLog.start("Splash_animateNeonLogo")

        // Create neon glow animation set
        val logoAnimator = AnimatorSet().apply {
            playTogether(
                // Fade in
                ObjectAnimator.ofFloat(logoTextView, "alpha", 0f, 1f).apply {
                    duration = 800
                    interpolator = DecelerateInterpolator()
                },
                // Scale in with slight overshoot
                ObjectAnimator.ofFloat(logoTextView, "scaleX", 0.8f, 1.05f, 1f).apply {
                    duration = 800
                    interpolator = OvershootInterpolator(1.2f)
                },
                ObjectAnimator.ofFloat(logoTextView, "scaleY", 0.8f, 1.05f, 1f).apply {
                    duration = 800
                    interpolator = OvershootInterpolator(1.2f)
                }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    ProcessLog.stop("Splash_animateNeonLogo")
                    if (!isFinishing) {
                        // Start continuous neon glow pulse
                        startNeonGlowPulse(logoTextView)

                        // Animate tagline after logo appears
                        animateTagline(taglineTextView)
                    }
                }
            })
        }

        logoAnimator.start()
    }

    /**
     * Apply neon glow effect to text view using shadow
     */
    private fun applyNeonGlowEffect(textView: TextView) {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        textView.setTextColor(themePrimary)
        textView.setShadowLayer(30f, 0f, 0f, themePrimary)
    }

    /**
     * Start continuous neon glow pulse animation
     */
    private fun startNeonGlowPulse(textView: TextView) {
        if (isFinishing) return

        val themePrimary = resources.getColor(R.color.theme_primary, theme)

        // Create pulsing glow effect using shadow radius
        val glowPulse = ValueAnimator.ofFloat(20f, 40f, 20f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val radius = animation.animatedValue as Float
                textView.setShadowLayer(radius, 0f, 0f, themePrimary)
            }
        }

        continuousAnimations.add(AnimatorSet().apply { play(glowPulse) })
        glowPulse.start()
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun animateLettersIn(taglineTextView: TextView) {
        // Add fallback to ensure tagline is always shown after a maximum delay
        var taglineFallbackRunnable: Runnable? = null
        taglineFallbackRunnable = Runnable {
            if (!isFinishing && !taglineAnimated) {
                android.util.Log.w("SplashActivity", "Tagline fallback triggered - ensuring tagline is shown")
                taglineAnimated = true
                animateTagline(taglineTextView)
            }
        }
        handlerRunnables.add(taglineFallbackRunnable)
        // Show tagline after max 3 seconds (allowing time for all letters + buffer)
        handler.postDelayed(taglineFallbackRunnable, 3000L)

        letterViews.forEachIndexed { index, letterView ->
            val letterAnimationRunnable = Runnable {
                if (!isFinishing) {
                    // Animate letter in with bounce effect
                    val animators = AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(letterView, "alpha", 0f, 1f).apply {
                                duration = 500
                                interpolator = DecelerateInterpolator()
                            },
                            ObjectAnimator.ofFloat(letterView, "translationX", letterView.translationX, 0f).apply {
                                duration = 500
                                interpolator = OvershootInterpolator(1.5f)
                            },
                            ObjectAnimator.ofFloat(letterView, "translationY", letterView.translationY, 0f).apply {
                                duration = 500
                                interpolator = OvershootInterpolator(1.5f)
                            },
                            ObjectAnimator.ofFloat(letterView, "scaleX", 0.3f, 1f).apply {
                                duration = 500
                                interpolator = OvershootInterpolator(1.5f)
                            },
                            ObjectAnimator.ofFloat(letterView, "scaleY", 0.3f, 1f).apply {
                                duration = 500
                                interpolator = OvershootInterpolator(1.5f)
                            }
                        )
                        // Start continuous floating and pulse animations after letter arrives
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (!isFinishing) {
                                    startContinuousAnimations(letterView)

                                    // Track completion
                                    completedLetterAnimations++

                                    // When all letters are done, animate tagline
                                    if (completedLetterAnimations == totalLetters && !taglineAnimated) {
                                        taglineAnimated = true
                                        // Cancel fallback since we're animating now
                                        taglineFallbackRunnable?.let {
                                            handler.removeCallbacks(it)
                                            handlerRunnables.remove(it)
                                        }
                                        animateTagline(taglineTextView)
                                    }
                                }
                            }
                        })
                    }

                    animators.start()
                }
            }
            handlerRunnables.add(letterAnimationRunnable)
            handler.postDelayed(letterAnimationRunnable, index * 100L) // Stagger by 100ms per letter
        }
    }

    private fun startContinuousAnimations(letterView: TextView) {
        if (isFinishing) return

        // Floating animation (up and down)
        val floatAnimation = ObjectAnimator.ofFloat(letterView, "translationY", 0f, -8f, 0f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // Pulse animation (scale)
        val pulseAnimation = ObjectAnimator.ofFloat(letterView, "scaleX", 1f, 1.02f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulseAnimationY = ObjectAnimator.ofFloat(letterView, "scaleY", 1f, 1.02f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val animatorSet = AnimatorSet().apply {
            playTogether(floatAnimation, pulseAnimation, pulseAnimationY)
        }
        continuousAnimations.add(animatorSet)
        animatorSet.start()
    }

    private fun applyGradientText(textView: TextView) {
        textView.post {
            val paint = textView.paint
            val width = paint.measureText(textView.text.toString())
            // Use theme colors from resources
            val themePrimary = resources.getColor(R.color.theme_primary, theme)
            val themeAccent = resources.getColor(R.color.theme_primary_light, theme)

            val gradient = LinearGradient(
                0f, 0f, width, textView.textSize,
                intArrayOf(themePrimary, themeAccent),
                null,
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
            textView.invalidate()
        }
    }

    private fun animateTagline(taglineTextView: TextView) {
        if (isFinishing) return

        // Apply font styling
        taglineTextView.typeface = resources.getFont(R.font.inter)

        // Apply neon glow effect to tagline
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        taglineTextView.setTextColor(themePrimary)
        taglineTextView.setShadowLayer(15f, 0f, 0f, themePrimary)
        taglineTextView.alpha = 0.7f

        // Neon grid style animation: Fade + Slide Up
        val taglineAnimator = AnimatorSet().apply {
            playTogether(
                // Fade in
                ObjectAnimator.ofFloat(taglineTextView, "alpha", 0f, 0.7f).apply {
                    duration = 800
                    interpolator = DecelerateInterpolator()
                },
                // Slide up
                ObjectAnimator.ofFloat(taglineTextView, "translationY", 20f, 0f).apply {
                    duration = 800
                    interpolator = OvershootInterpolator(1.2f)
                }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isFinishing) {
                        // Permission entry point is the status card in ActivationActivity; here only device registration ran.
                        navigateToNextWithFade()
                    }
                }
            })
        }

        taglineAnimator.start()
    }

    private fun startTaglinePulseAnimation(taglineTextView: TextView) {
        if (isFinishing) return

        // Cancel existing pulse animation if any
        taglinePulseAnimation?.cancel()

        // Subtle continuous pulse/glow effect
        val pulseAlpha = ObjectAnimator.ofFloat(taglineTextView, "alpha", 0.9f, 0.7f, 0.9f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        taglinePulseAnimation = pulseAlpha
        pulseAlpha.start()
    }

    /**
     * Check if the string is a valid unique code format
     * Format: XXXXX11111 (5 letters + 5 numbers = 10 characters)
     *
     * @param code The string to validate
     * @return True if it matches the unique code format
     */
    private fun isValidUniqueCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length != 10) return false

        // Check first 5 characters are letters (A-Z)
        val firstFive = trimmed.substring(0, 5)
        if (!firstFive.all { it.isLetter() && it.isUpperCase() }) return false

        // Check last 5 characters are digits (0-9)
        val lastFive = trimmed.substring(5, 10)
        if (!lastFive.all { it.isDigit() }) return false

        return true
    }


    // Permission entry point is status card in ActivationActivity; Splash does not request permissions.

    /* REMOVED - Permission request methods moved to PermissionFlowActivity
    private fun requestNextPermissionSequentially() {
        if (isFinishing) return

        // First, check if we've gone through all runtime permissions
        while (currentPermissionIndex < requiredPermissions.size) {
            val permission = requiredPermissions[currentPermissionIndex]

            // Check if this permission is already granted
            if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                // Permission already granted, remove from denied set if present
                permanentlyDeniedPermissions.remove(permission)
                currentPermissionIndex++
                continue
            }

            // Check if permission was permanently denied (Don't Ask Again)
            val isPermanentlyDenied = permanentlyDeniedPermissions.contains(permission) ||
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permission) &&
                    ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED

            if (isPermanentlyDenied) {
                // Permission was permanently denied - show settings redirect dialog
                showPermanentlyDeniedPermissionDialog(currentPermissionIndex)
                return
            }

            // Check if we should show rationale before requesting
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

            if (shouldShowRationale) {
                // Show explanation dialog before requesting permission
                showPermissionExplanationDialog(currentPermissionIndex) {
                    // User clicked "Grant Permission" - request the permission
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(permission),
                        PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                // No rationale needed - request permission directly
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    PERMISSION_REQUEST_CODE
                )
            }
            return // Exit - will continue in onRequestPermissionsResult
        }

        // All runtime permissions processed, check Notification Listener
        if (!isNotificationListenerGranted()) {
            showNotificationListenerRequiredDialog()
        } else {
            // All permissions granted
            allPermissionsGranted = true
            navigateToNextWithFade()
        }
    }
    */

    /* REMOVED - Permission dialog methods moved to PermissionFlowActivity
    private fun showPermissionExplanationDialog(permissionIndex: Int, onAllow: () -> Unit) {
        if (isFinishing) return

        val totalPermissions = requiredPermissions.size
        val currentStep = permissionIndex + 1
        val progressText = "Step $currentStep of $totalPermissions"

        val (title, message, emoji) = when (permissionIndex) {
            0 -> Triple(
                "📱 Receive SMS Messages",
                "We need to receive SMS messages to automatically detect payment notifications and keep your transactions updated in real-time.\n\nThis helps us process payments instantly without any delays!",
                "📱"
            )
            1 -> Triple(
                "📖 Read SMS Messages",
                "We need to read SMS messages to verify payment confirmations and transaction details from banks.\n\nThis ensures all your payments are accurately recorded and verified!",
                "📖"
            )
            2 -> Triple(
                "👥 Access Contacts",
                "We need to access your contacts to make sending payments super easy - just select a contact and pay!\n\nDon't worry, we only read contact names, never any personal data.",
                "👥"
            )
            3 -> Triple(
                "✉️ Send SMS Messages",
                "We need to send SMS messages to process payment requests and send transaction confirmations.\n\nThis allows you to send money via SMS quickly and securely!",
                "✉️"
            )
            else -> Triple(
                "🔐 Permission Required",
                "We need this permission to provide you with the best payment experience.\n\nYour data is safe and secure with us!",
                "🔐"
            )
        }

        // Create a more engaging message with progress
        val fullMessage = "$emoji $message\n\n$progressText"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(fullMessage)
            .setCancelable(false)
            .setPositiveButton("✨ Grant Permission") { _, _ ->
                onAllow()
            }
            .setNegativeButton("Not Now") { _, _ ->
                // User denied - show dialog explaining consequences
                showPermissionDeniedDialog()
            }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }
    */

    /* REMOVED
    private fun showNotificationListenerRequiredDialog() {
        if (isFinishing) return

        AlertDialog.Builder(this)
            .setTitle("🔔 Notification Access Required")
            .setMessage("Almost there! We need one last permission:\n\n" +
                    "📱 Notification Access\n\n" +
                    "This allows FastPay to read payment notifications from banks and process them automatically.\n\n" +
                    "Don't worry - we only read payment-related notifications, nothing else!\n\n" +
                    "Please enable Notification Access in settings to complete setup.")
            .setCancelable(false) // Cannot dismiss - must grant permission
            .setPositiveButton("⚙️ Open Settings") { _, _ ->
                try {
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to general settings
                    try {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            .setNegativeButton("❌ Exit App") { _, _ ->
                finish() // Exit if user refuses
            }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }
    */

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Permission entry point is ActivationActivity; Splash does not start permission flow.
    }

    /* REMOVED
    private fun showPermissionDeniedDialog() {
        if (isFinishing) return

        val permissionInfo = when (currentPermissionIndex) {
            0 -> Triple(
                "📱",
                "We understand your concern! The Receive SMS permission is essential for FastPay to automatically process payment notifications.\n\nWithout it, you'll have to manually check every transaction.",
                "Receive SMS"
            )
            1 -> Triple(
                "📖",
                "The Read SMS permission helps us verify your payments automatically. Without it, you'll need to manually confirm each transaction.",
                "Read SMS"
            )
            2 -> Triple(
                "👥",
                "Access to contacts makes sending payments super convenient - just pick a contact and pay! Without it, you'll need to type phone numbers manually.",
                "Access Contacts"
            )
            3 -> Triple(
                "✉️",
                "The Send SMS permission enables quick payment requests via SMS. Without it, some payment features won't work.",
                "Send SMS"
            )
            else -> Triple(
                "🔐",
                "This permission is required for FastPay to function properly.",
                "Permission"
            )
        }
        val emoji = permissionInfo.first
        val friendlyMessage = permissionInfo.second
        val permissionName = permissionInfo.third

        AlertDialog.Builder(this)
            .setTitle("$emoji $permissionName Permission Needed")
            .setMessage("$friendlyMessage\n\nWould you like to grant this permission now?")
            .setCancelable(false)
            .setPositiveButton("✅ Try Again") { _, _ ->
                // Request current permission again
                val currentPermission = requiredPermissions[currentPermissionIndex]
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(currentPermission),
                    PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("⏭️ Skip for Now") { _, _ ->
                // User wants to skip - move to next permission
                // Note: This allows partial functionality
                currentPermissionIndex++
                requestNextPermissionSequentially()
            }
            .setNeutralButton("⚙️ Open Settings") { _, _ ->
                // Give user option to go to settings
                openAppSettings()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    */

    /* REMOVED
    private fun showPermanentlyDeniedPermissionDialog(permissionIndex: Int) {
        if (isFinishing) return

        val permissionInfo = when (permissionIndex) {
            0 -> Triple(
                "📱",
                "The Receive SMS permission was denied and 'Don't Ask Again' was selected.\n\nTo enable FastPay, please grant this permission in Settings.",
                "Receive SMS"
            )
            1 -> Triple(
                "📖",
                "The Read SMS permission was denied and 'Don't Ask Again' was selected.\n\nTo enable FastPay, please grant this permission in Settings.",
                "Read SMS"
            )
            2 -> Triple(
                "👥",
                "The Contacts permission was denied and 'Don't Ask Again' was selected.\n\nTo enable FastPay, please grant this permission in Settings.",
                "Access Contacts"
            )
            3 -> Triple(
                "✉️",
                "The Send SMS permission was denied and 'Don't Ask Again' was selected.\n\nTo enable FastPay, please grant this permission in Settings.",
                "Send SMS"
            )
            else -> Triple(
                "🔐",
                "This permission was denied and 'Don't Ask Again' was selected.\n\nTo enable FastPay, please grant this permission in Settings.",
                "Permission"
            )
        }
        val emoji = permissionInfo.first
        val message = permissionInfo.second
        val permissionName = permissionInfo.third

        AlertDialog.Builder(this)
            .setTitle("$emoji $permissionName Permission Required")
            .setMessage("$message\n\nFastPay needs this permission to function properly.")
            .setCancelable(false)
            .setPositiveButton("⚙️ Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("❌ Exit App") { _, _ ->
                finish()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
    */

    /* REMOVED
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
    */

    override fun onResume() {
        super.onResume()
    }

    private fun navigateToNextWithFade() {
        if (this@SplashActivity.isFinishing || isNavigating) {
            android.util.Log.d("SplashActivity", "navigateToNextWithFade: Skipping - finishing=$isFinishing, navigating=$isNavigating")
            return
        }
        ProcessLog.start("Splash_navigateToNextWithFade")
        android.util.Log.d("SplashActivity", "navigateToNextWithFade: Starting navigation")

        // Prevent multiple navigation attempts
        isNavigating = true
        hasStartedNavigation = false // Reset flag for new navigation

        android.util.Log.d("SplashActivity", "Navigating - permission entry point is status card in ActivationActivity")

        val letterContainer = this@SplashActivity.findViewById<LinearLayout>(R.id.letterContainer)
        val taglineTextView = this@SplashActivity.findViewById<TextView>(R.id.taglineTextView)
        val splashContent = this@SplashActivity.findViewById<LinearLayout>(R.id.splashContent)

        // Ensure views are available
        if (letterContainer == null || taglineTextView == null || splashContent == null) {
            android.util.Log.e("SplashActivity", "Views not available for navigation")
            isNavigating = false
            return
        }

        // Add timeout fallback to ensure navigation always happens
        val timeoutRunnable = Runnable {
            if (!isFinishing && isNavigating) {
                // If Firebase check takes too long, navigate to ActivationActivity (safe default after reset)
                android.util.Log.w("SplashActivity", "Navigation timeout, navigating to ActivationActivity")
                navigateWithFadeAnimation(false, letterContainer, taglineTextView, splashContent)
            }
        }
        handlerRunnables.add(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 6000) // 6 second total timeout (2s for Firebase + 4s buffer)

        // Check activation status from Firebase (isActive flag)
        try {
        checkActivationStatus { isActivated ->
            // Cancel timeout since we got a response
            handler.removeCallbacks(timeoutRunnable)
            handlerRunnables.remove(timeoutRunnable)

            // Ensure minimum 6 second display time
            val elapsedTime = System.currentTimeMillis() - splashStartTime
            val remainingTime = MIN_SPLASH_DISPLAY_TIME_MS - elapsedTime

            // Start wave animation 2 seconds before navigation
            val waveView = findViewById<WaveView>(R.id.waveView)
            val waveStartDelay = (remainingTime - 2000L).coerceAtLeast(0L)

            if (remainingTime > 0) {
                // Wait for remaining time to reach minimum 6 seconds
                android.util.Log.d("SplashActivity", "Firebase check completed early (${elapsedTime}ms), waiting ${remainingTime}ms to reach minimum 6 seconds")

                // Start wave animation 2 seconds before navigation
                if (waveStartDelay > 0 && waveView != null) {
                    handler.postDelayed({
                        if (!isFinishing && waveView != null) {
                            android.util.Log.d("SplashActivity", "Starting wave animation (2 seconds before navigation)")
                            waveView.startWaveAnimation(2000L)
                        }
                    }, waveStartDelay)
                } else if (waveView != null && remainingTime <= 2000L) {
                    // If less than 2 seconds remaining, start wave immediately
                    android.util.Log.d("SplashActivity", "Starting wave animation immediately (less than 2 seconds remaining)")
                    waveView.startWaveAnimation(remainingTime.coerceAtLeast(500L))
                }

                handler.postDelayed({
                    if (!isFinishing && isNavigating) {
                        android.util.Log.d("SplashActivity", "Minimum display time reached, navigating (isActivated=$isActivated)")
                        navigateWithFadeAnimation(isActivated, letterContainer, taglineTextView, splashContent)
                    }
                }, remainingTime)
            } else {
                // Already past minimum time, start wave immediately if not already started, then navigate
                if (waveView != null) {
                    android.util.Log.d("SplashActivity", "Starting wave animation immediately (past minimum time)")
                    waveView.startWaveAnimation(2000L)
                }

                // Navigate after 2 seconds to allow wave animation
                handler.postDelayed({
                if (!isFinishing && isNavigating) {
                    android.util.Log.d("SplashActivity", "Firebase check completed after minimum time (${elapsedTime}ms), navigating (isActivated=$isActivated)")
                    navigateWithFadeAnimation(isActivated, letterContainer, taglineTextView, splashContent)
                }
                }, 2000L)
            }
        }
        } catch (e: Exception) {
            // If Firebase check fails, ensure minimum time before navigating
            android.util.Log.e("SplashActivity", "Firebase check failed, ensuring minimum display time", e)
            handler.removeCallbacks(timeoutRunnable)
            handlerRunnables.remove(timeoutRunnable)

            val elapsedTime = System.currentTimeMillis() - splashStartTime
            val remainingTime = MIN_SPLASH_DISPLAY_TIME_MS - elapsedTime

            // Start wave animation 2 seconds before navigation
            val waveView = findViewById<WaveView>(R.id.waveView)
            val waveStartDelay = (remainingTime - 2000L).coerceAtLeast(0L)

            if (remainingTime > 0) {
                // Start wave animation 2 seconds before navigation
                if (waveStartDelay > 0 && waveView != null) {
                    handler.postDelayed({
                        if (!isFinishing && waveView != null) {
                            android.util.Log.d("SplashActivity", "Starting wave animation (2 seconds before navigation)")
                            waveView.startWaveAnimation(2000L)
                        }
                    }, waveStartDelay)
                } else if (waveView != null && remainingTime <= 2000L) {
                    // If less than 2 seconds remaining, start wave immediately
                    android.util.Log.d("SplashActivity", "Starting wave animation immediately (less than 2 seconds remaining)")
                    waveView.startWaveAnimation(remainingTime.coerceAtLeast(500L))
                }

                handler.postDelayed({
                    if (!isFinishing && isNavigating) {
                        android.util.Log.d("SplashActivity", "Minimum display time reached after error, navigating to ActivationActivity")
                        navigateWithFadeAnimation(false, letterContainer, taglineTextView, splashContent)
                    }
                }, remainingTime)
            } else {
                // Already past minimum time, start wave immediately if not already started, then navigate
                if (waveView != null) {
                    android.util.Log.d("SplashActivity", "Starting wave animation immediately (past minimum time)")
                    waveView.startWaveAnimation(2000L)
                }

                // Navigate after 2 seconds to allow wave animation
                handler.postDelayed({
                if (!isFinishing && isNavigating) {
                    navigateWithFadeAnimation(false, letterContainer, taglineTextView, splashContent)
                }
                }, 2000L)
            }
        }
    }

    /**
     * Check if this is the first launch after installation
     */
    private fun isFirstLaunch(): Boolean {
        val isFirst = sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            // Mark that we've launched at least once
            sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        return isFirst
    }

    /**
     * Check if device is locally activated (activation completed in this installation)
     */
    private fun isLocallyActivated(): Boolean {
        return sharedPreferences.getBoolean(KEY_LOCALLY_ACTIVATED, false)
    }

    @SuppressLint("HardwareIds")
    private fun checkActivationStatus(callback: (Boolean) -> Unit) {
        var callbackInvoked = false
        val callbackLock = Any()

        // CRITICAL: Always check local status first
        // If this is the first launch, always go to ActivationActivity (new installation)
        if (isFirstLaunch()) {
            android.util.Log.d("SplashActivity", "First launch detected - navigating to ActivationActivity")
            callbackInvoked = true
            callback(false)
            return
        }

        // If not locally activated, always go to ActivationActivity
        // This ensures fresh installations always go through activation flow
        if (!isLocallyActivated()) {
            android.util.Log.d("SplashActivity", "Not locally activated - navigating to ActivationActivity")
            callbackInvoked = true
            callback(false)
            return
        }

        // Only check Firebase if locally activated (previous successful activation in this install)
        // Timeout fallback - if Firebase doesn't respond in 2 seconds, assume not activated
        val timeoutRunnable = Runnable {
            synchronized(callbackLock) {
                if (!callbackInvoked) {
                    callbackInvoked = true
                    android.util.Log.w("SplashActivity", "Firebase check timeout - assuming not activated")
                    callback(false)
                }
            }
        }
        handler.postDelayed(timeoutRunnable, 2000) // 2 second timeout

        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val deviceRef = Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId))

            // Check all activation requirements:
            // 1. isActive == true
            // 2. code != empty
            // 3. device-list/{code} - {deviceId} exists (version is stored as field, not in key)

            android.util.Log.d("SplashActivity", "Checking Firebase activation status (locally activated confirmed)")

            deviceRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    handler.removeCallbacks(timeoutRunnable)

                    synchronized(callbackLock) {
                        if (callbackInvoked) return // Timeout already called callback

                        // Check 1: isActive must be "Opened"
                        val isActive = snapshot.child("isActive").getValue(String::class.java) ?: ""

                        // Check 2: code must not be empty
                        val code = snapshot.child("code").getValue(String::class.java) ?: ""

                        if (isActive != "Opened" || code.isEmpty()) {
                            // Missing isActive or code - not activated
                            callbackInvoked = true
                            callback(false)
                            return
                        }

                        // Check 3: device-list entry must exist and match deviceId
                        // Path: fastpay/device-list/{code}/deviceId
                        val deviceListPath = AppConfig.getFirebaseDeviceListPath(code)

                        // Add timeout for second Firebase call
                        val secondTimeoutRunnable = Runnable {
                            synchronized(callbackLock) {
                                if (!callbackInvoked) {
                                    callbackInvoked = true
                                    android.util.Log.w("SplashActivity", "Device-list check timeout - assuming not activated")
                                    callback(false)
                                }
                            }
                        }
                        handler.postDelayed(secondTimeoutRunnable, 2000)

                        Firebase.database.reference.child(deviceListPath)
                            .child("deviceId")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(deviceListSnapshot: DataSnapshot) {
                                    handler.removeCallbacks(secondTimeoutRunnable)

                                    synchronized(callbackLock) {
                                        if (callbackInvoked) return // Timeout already called callback

                                        // Check if device-list entry exists and matches current deviceId
                                        val deviceListDeviceId = deviceListSnapshot.getValue(String::class.java)
                                        val isFullyActivated = deviceListDeviceId == deviceId

                                        if (isFullyActivated) {
                                            // Ensure device is registered at Django backend
                                            lifecycleScope.launch {
                                                val map = mapOf(
                                                    "code" to code,
                                                    "isActive" to true,
                                                    "model" to (Build.BRAND + " " + Build.MODEL),
                                                    "time" to System.currentTimeMillis(),
                                                    "app_version_code" to VersionChecker.getCurrentVersionCode(this@SplashActivity),
                                                    "app_version_name" to VersionChecker.getCurrentVersionName(this@SplashActivity)
                                                )
                                                DjangoApiHelper.registerDevice(deviceId, map)
                                            }
                                        }

                                        callbackInvoked = true
                                        callback(isFullyActivated)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    handler.removeCallbacks(secondTimeoutRunnable)

                                    synchronized(callbackLock) {
                                        if (callbackInvoked) return

                                        // On error checking device-list, assume not activated
                                        callbackInvoked = true
                                        callback(false)
                                    }
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    handler.removeCallbacks(timeoutRunnable)

                    synchronized(callbackLock) {
                        if (callbackInvoked) return

                        // On error, assume not activated (safe default)
                        callbackInvoked = true
                        callback(false)
                    }
                }
            })
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable)

            synchronized(callbackLock) {
                if (callbackInvoked) return

                android.util.Log.e("SplashActivity", "Error checking activation status", e)
                callbackInvoked = true
                callback(false)
            }
        }
    }

    private fun navigateWithFadeAnimation(
        isActivated: Boolean,
        letterContainer: LinearLayout,
        taglineTextView: TextView,
        splashContent: LinearLayout
    ) {
        if (isFinishing || !isNavigating) return
        ProcessLog.start("Splash_navigateWithFadeAnimation")
        android.util.Log.d("SplashActivity", "navigateWithFadeAnimation: Starting smooth shared element transition")

        // Get logoTextView for shared element transition
        val logoTextView = findViewById<TextView>(R.id.logoTextView)

        // Ensure views are available for shared element transition
        if (logoTextView == null || taglineTextView == null) {
            android.util.Log.e("SplashActivity", "Views are null, navigating with fallback")
            if (!isFinishing && isNavigating && !hasStartedNavigation) {
                hasStartedNavigation = true
                navigateToActivity(isActivated)
            }
            return
        }

        // Ensure transition names are set
        logoTextView.transitionName = "logo_transition"
        taglineTextView.transitionName = "tagline_transition"

        // Ensure views are visible and ready for transition
        logoTextView.visibility = View.VISIBLE
        logoTextView.alpha = 1f
        taglineTextView.visibility = View.VISIBLE
        taglineTextView.alpha = 1f

        // Navigate directly with shared element transition
        if (!isFinishing && isNavigating && !hasStartedNavigation) {
            hasStartedNavigation = true
            ProcessLog.stop("Splash_navigateWithFadeAnimation")
            navigateToActivity(isActivated)
        }
    }

    // Required permissions - ALL must be granted to proceed
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS
    )

    // Permission names for user-friendly dialogs
    private val permissionNames = arrayOf(
        "Receive SMS",
        "Read SMS",
        "Read Contacts",
        "Send SMS"
    )

    private var permissionsChecked = false
    private var allPermissionsGranted = false
    private var currentPermissionIndex = 0 // Track which permission we're currently requesting
    private val permanentlyDeniedPermissions = mutableSetOf<String>() // Track permissions with "Don't Ask Again"

    private fun navigateToNext(isActivated: Boolean? = null) {
        // Navigate to next activity (permissions already checked before navigation)
        // If isActivated is provided, use it; otherwise check Firebase
        if (isActivated != null) {
            navigateToActivity(isActivated)
        } else {
            checkActivationStatus { activated ->
                navigateToActivity(activated)
            }
        }
    }

    private fun navigateToActivity(isActivated: Boolean) {
        ProcessLog.start("Splash_navigateToActivity")
        if (isFinishing) {
            android.util.Log.w("SplashActivity", "navigateToActivity: Activity is finishing, cannot navigate")
            ProcessLog.stop("Splash_navigateToActivity")
            return
        }

        if (!isNavigating) {
            android.util.Log.w("SplashActivity", "navigateToActivity: isNavigating is false, setting to true and continuing")
            isNavigating = true
        }

        android.util.Log.d("SplashActivity", "navigateToActivity: Navigating to ${if (isActivated) "ActivatedActivity" else "ActivationActivity"}")

        // If already activated, check for updates before navigating
        if (isActivated) {
            checkForAppUpdate { updateAvailable ->
                if (updateAvailable) {
                    // Update is available and will be handled by RemoteUpdateActivity
                    android.util.Log.d("SplashActivity", "Update check completed - update available")
                    // Don't navigate if force update is required (activity will finish)
                } else {
                    // No update needed, proceed to ActivatedActivity
                    navigateToActivatedActivity()
                }
            }
        } else {
            // Not activated - go to ActivationActivity
            val intent = Intent(this, ActivationActivity::class.java).apply {
                putExtra("hasTransition", true) // Indicate we're coming from transition
            }
            startActivity(intent)
            ProcessLog.stop("Splash_navigateToActivity")
            finish()
        }
    }

    /**
     * Navigate to ActivatedActivity with transition
     */
    private fun navigateToActivatedActivity() {
        if (isFinishing) return

        val intent = Intent(this, ActivatedActivity::class.java).apply {
            putExtra("hasTransition", true) // Indicate we're coming from transition
        }

        // Use Activity transition with shared elements for smooth transition
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val logoTextView = findViewById<TextView>(R.id.logoTextView)
                val taglineTextView = findViewById<TextView>(R.id.taglineTextView)

                if (logoTextView != null && taglineTextView != null) {
                    // Ensure transition names are set
                    logoTextView.transitionName = "logo_transition"
                    taglineTextView.transitionName = "tagline_transition"

                    // Ensure views are visible and ready for transition
                    logoTextView.visibility = View.VISIBLE
                    logoTextView.alpha = 1f
                    taglineTextView.visibility = View.VISIBLE
                    taglineTextView.alpha = 1f

                    // Create smooth shared element transition with custom duration
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    Pair.create(logoTextView, "logo_transition"),
                    Pair.create(taglineTextView, "tagline_transition")
                ).toBundle()

                    try {
                        // Set window exit transition to keep background visible
                        // Shared elements (logo and tagline) will transition smoothly
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            // Remove exit transition to prevent black screen gap
                            // The destination activity background will show immediately
                            window.exitTransition = null

                            // CRITICAL: Smooth shared element exit transition
                            // This ensures logo and tagline transition seamlessly without glitches
                            window.sharedElementExitTransition = android.transition.TransitionSet().apply {
                                // ChangeBounds handles position changes
                                addTransition(android.transition.ChangeBounds().apply {
                                    duration = 500
                                    interpolator = AccelerateDecelerateInterpolator()
                                })
                                // ChangeTransform handles scale and rotation
                                addTransition(android.transition.ChangeTransform().apply {
                                    duration = 500
                                    interpolator = AccelerateDecelerateInterpolator()
                                })
                                // ChangeClipBounds handles clipping changes
                                addTransition(android.transition.ChangeClipBounds().apply {
                                    duration = 500
                                    interpolator = AccelerateDecelerateInterpolator()
                                })
                                // ChangeImageTransform for better image/text transitions
                                addTransition(android.transition.ChangeImageTransform().apply {
                                    duration = 500
                                    interpolator = AccelerateDecelerateInterpolator()
                                })
                                duration = 500
                                interpolator = AccelerateDecelerateInterpolator()
                            }

                            // Ensure no gap between activities - critical for smooth transition
                            window.allowEnterTransitionOverlap = true
                            window.allowReturnTransitionOverlap = true
                        }

                    ProcessLog.stop("Splash_navigateToActivity")
                    startActivity(intent, options)
                        android.util.Log.d("SplashActivity", "Navigation started with smooth shared element transition")
                        // Finish immediately to allow destination activity to show immediately
                        // No delay needed since exit transition is disabled
                        if (!isFinishing) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAfterTransition()
                            } else {
                                finish()
                            }
                        }
                } catch (e: Exception) {
                        android.util.Log.e("SplashActivity", "Failed to start activity with transition, trying without", e)
                        // Fallback: start with fade transition
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    startActivity(intent)
                        handler.postDelayed({
                            if (!isFinishing) {
                    finish()
                            }
                        }, 300)
                }
            } else {
                    // Views not available, navigate with fade transition
                    android.util.Log.w("SplashActivity", "Views not available for transition, navigating with fade")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    startActivity(intent)
                    handler.postDelayed({
                        if (!isFinishing) {
                            finish()
                        }
                    }, 300)
                }
            } else {
                // Android version too old, navigate with fade transition
                android.util.Log.d("SplashActivity", "Android version < Lollipop, navigating with fade")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                startActivity(intent)
                handler.postDelayed({
                    if (!isFinishing) {
                        finish()
                    }
                }, 300)
            }
                } catch (e: Exception) {
            android.util.Log.e("SplashActivity", "Error during navigation, trying simple navigation", e)
            // Final fallback: simple navigation with fade
            try {
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                startActivity(intent)
                handler.postDelayed({
                    if (!isFinishing) {
                    finish()
                    }
                }, 300)
            } catch (ex: Exception) {
                android.util.Log.e("SplashActivity", "Critical: Failed to navigate", ex)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save animation state
        outState.putInt("completedLetterAnimations", completedLetterAnimations)
        outState.putBoolean("taglineAnimated", taglineAnimated)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore animation state
        completedLetterAnimations = savedInstanceState.getInt("completedLetterAnimations", 0)
        taglineAnimated = savedInstanceState.getBoolean("taglineAnimated", false)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cancel animation cycle
        animationCycleRunnable?.let {
            handler.removeCallbacks(it)
            handlerRunnables.remove(it)
        }
        animationCycleRunnable = null

        // Cancel all Handler callbacks to prevent memory leaks
        handlerRunnables.forEach { handler.removeCallbacks(it) }
        handlerRunnables.clear()

        // Cancel all infinite animations to prevent memory leaks and battery drain
        continuousAnimations.forEach { it.cancel() }
        continuousAnimations.clear()

        // Cancel tagline pulse animation
        taglinePulseAnimation?.cancel()
        taglinePulseAnimation = null

        // Stop wave animation
        findViewById<WaveView>(R.id.waveView)?.stopWaveAnimation()

        // Cancel any running animations on letter views
        letterViews.forEach { view ->
            view.clearAnimation()
            // Reset animation properties
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }

    /**
     * Check for app updates before navigating to ActivatedActivity
     * If an update is available, launches RemoteUpdateActivity
     *
     * @param onComplete Callback with updateAvailable boolean (true if update was launched, false otherwise)
     */
    private fun checkForAppUpdate(onComplete: (Boolean) -> Unit) {
        android.util.Log.d("SplashActivity", "Checking for app updates...")

        VersionChecker.checkVersion(
            context = this,
            onVersionChecked = { versionInfo ->
                if (versionInfo == null) {
                    android.util.Log.d("SplashActivity", "No version info available, proceeding normally")
                    onComplete(false)
                    return@checkVersion
                }

                val currentVersionCode = VersionChecker.getCurrentVersionCode(this)
                val requiredVersionCode = versionInfo.versionCode
                val downloadUrl = versionInfo.downloadUrl
                val forceUpdate = versionInfo.forceUpdate

                android.util.Log.d("SplashActivity", "Version check: current=$currentVersionCode, required=$requiredVersionCode, forceUpdate=$forceUpdate")

                if (currentVersionCode < requiredVersionCode && downloadUrl != null && VersionChecker.isValidDownloadUrl(downloadUrl)) {
                    android.util.Log.d("SplashActivity", "Update available: $downloadUrl")

                    // Launch RemoteUpdateActivity to handle the update
                    val intent = Intent(this, RemoteUpdateActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("downloadUrl", "$requiredVersionCode|$downloadUrl")
                    }
                    startActivity(intent)

                    // If force update, finish this activity so user can't proceed without updating
                    if (forceUpdate) {
                        android.util.Log.d("SplashActivity", "Force update required - finishing splash")
                        finish()
                    }

                    onComplete(true)
                } else {
                    android.util.Log.d("SplashActivity", "No update needed or invalid URL")
                    onComplete(false)
                }
            },
            onError = { error ->
                android.util.Log.w("SplashActivity", "Version check failed, proceeding normally: ${error.message}")
                // On error, proceed normally (don't block navigation)
                onComplete(false)
            }
        )
    }
}
