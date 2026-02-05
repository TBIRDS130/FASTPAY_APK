package com.example.fast.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import com.example.fast.util.VersionChecker
import com.example.fast.util.DebugLogger
import com.google.firebase.Firebase
import com.example.fast.util.DjangoApiHelper
import com.example.fast.ui.MultipurposeCardActivity
import com.example.fast.ui.card.RemoteCardHandler
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * SplashActivity - "Redmi" Version
 *
 * Flow:
 * - After animation, we navigate once to next activity: ActivationActivity if not activated, ActivatedActivity if activated. Permission entry point is the status card in ActivationActivity.
 *
 * Features:
 * - First letter ("F") always comes from right edge of screen
 * - Other letters come randomly from: above, below, or left (right not available after first letter)
 * - All letters start from actual screen edges (completely off-screen)
 * - Tagline animation after all letters complete
 * - Logo transitions smoothly to next activity (ActivationActivity or ActivatedActivity)
 * - Smart navigation based on Firebase/local (activated vs not activated)
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val letterViews = mutableListOf<TextView>()
    private var completedLetterAnimations = 0
    private var totalLetters = 0
    private var taglineAnimated = false

    // Store animation references for cleanup
    private val continuousAnimations = mutableListOf<AnimatorSet>()

    // Store Handler references for cleanup
    private val handler = Handler(Looper.getMainLooper())
    private val handlerRunnables = mutableListOf<Runnable>()

    private var isNavigating = false
    private var hasStartedNavigation = false // Prevent double navigation during animation
    private var navigateRetryCount = 0
    private val MAX_NAVIGATE_RETRIES = 10

    // SharedPreferences for tracking activation status
    private val prefsName = "activation_prefs"
    private val KEY_FIRST_LAUNCH = "first_launch"
    private val KEY_LOCALLY_ACTIVATED = "locally_activated"
    private val KEY_ACTIVATION_CODE = "activation_code"

    private val sharedPreferences: SharedPreferences
        get() = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private var currentGridAnimationType: GridBackgroundView.GridAnimationType = GridBackgroundView.GridAnimationType.RADIAL

    // Minimum splash screen display time (6 seconds)
    private val MIN_SPLASH_DISPLAY_TIME_MS = 6000L
    private var splashStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
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
            window.statusBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
            // Disable default exit transition to prevent black screen
            window.allowEnterTransitionOverlap = true
            window.allowReturnTransitionOverlap = true
            postponeEnterTransition()
        }

        setContentView(R.layout.activity_splash)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // CRITICAL: Start postponed transition when first frame is ready (fixes stuck/black screen on 2nd launch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            var transitionStarted = false
            fun startTransition() {
                if (!transitionStarted) {
                    transitionStarted = true
                    startPostponedEnterTransition()
                }
            }
            window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    startTransition()
                    return true
                }
            })
            window.decorView.requestLayout()
            // Fallback: on some devices (Xiaomi, "Don't keep activities") PreDrawListener never fires
            val postponeFallback = Runnable { startTransition() }
            handlerRunnables.add(postponeFallback)
            handler.postDelayed(postponeFallback, 500L)
        }

        val letterContainer = findViewById<LinearLayout>(R.id.letterContainer)
        val taglineTextView = findViewById<TextView>(R.id.taglineTextView)
        val logoTextView = findViewById<TextView>(R.id.logoTextView)
        val scanlineView = findViewById<ScanlineView>(R.id.scanlineView)
        val waveView = findViewById<WaveView>(R.id.waveView)
        val gridBackground = findViewById<GridBackgroundView>(R.id.gridBackground)

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

        // Use default branding values from resources
        val defaultLogoName = getString(R.string.app_name_title)
        val defaultTagline = getString(R.string.app_tagline)
        taglineTextView?.text = defaultTagline
        logoTextView?.text = defaultLogoName

        // Grid animation already set in onCreate - continue with logo animation (require views)
        if (logoTextView != null && taglineTextView != null && letterContainer != null) {
            setupNeonGlowAnimation(logoTextView, taglineTextView, letterContainer)
        } else {
            android.util.Log.e("SplashActivity", "Missing splash views - skipping animation")
            handler.postDelayed({ navigateToNextWithFade() }, MIN_SPLASH_DISPLAY_TIME_MS)
        }

        // Primary: guarantee navigation at 6.5s (fixes stuck on devices where animation onAnimationEnd never fires)
        val primaryRunnable = Runnable {
            if (!isFinishing && !isDestroyed && !hasStartedNavigation) {
                android.util.Log.d("SplashActivity", "Primary timeout: forcing navigation at 6.5s")
                navigateToNextWithFade()
            }
        }
        handlerRunnables.add(primaryRunnable)
        handler.postDelayed(primaryRunnable, 6500L)

        // Safety: guarantee navigation after max time (backup if primary also fails)
        val safetyRunnable = Runnable {
            if (!isFinishing && !isDestroyed && !hasStartedNavigation) {
                android.util.Log.w("SplashActivity", "Safety timeout: forcing navigation after 9s")
                navigateToNextWithFade()
            }
        }
        handlerRunnables.add(safetyRunnable)
        handler.postDelayed(safetyRunnable, 9000L)

        // Register device with Django and Firebase as soon as possible (non-blocking)
        registerDeviceEarly()
    }

    /**
     * Register device with Django and Firebase immediately (background).
     * Permission entry point is the status card in ActivationActivity; before that only device registration runs here.
     * Strategy: Try Firebase first (with timeout), then register with best available data.
     */
    @SuppressLint("HardwareIds")
    private fun registerDeviceEarly() {
        try {
            val deviceIdRaw = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            if (deviceIdRaw.isNullOrBlank()) {
                android.util.Log.w("SplashActivity", "Device ID is null or blank, skipping early registration")
                return
            }
            val deviceId = deviceIdRaw

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

                    val isActiveRaw = snapshot.child("isActive").value
                    val isActive = when (isActiveRaw) {
                        is Boolean -> isActiveRaw
                        is String -> isActiveRaw == "Opened" || isActiveRaw.equals("true", ignoreCase = true)
                        else -> false
                    }
                    val code = (snapshot.child("code").value as? String) ?: ""

                    // Update device data with Firebase info
                    val deviceData = baseDeviceData.toMutableMap()
                    if (code.isNotBlank()) {
                        deviceData["code"] = code
                    }
                    deviceData["isActive"] = isActive

                    // Register with Django with complete info (isActive parsed safely from String or Boolean)
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
     * Setup Neon Glow style animation (original)
     * Logo appears with neon glow effect, then tagline fades in
     */
    private fun setupNeonGlowAnimation(
        logoTextView: TextView,
        taglineTextView: TextView,
        letterContainer: LinearLayout
    ) {
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
    }

    /**
     * Animate logo with neon glow effect
     */
    private fun animateNeonLogo(logoTextView: TextView, taglineTextView: TextView) {
        if (isFinishing) return

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
                    if (!isFinishing && !isDestroyed) {
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
                    if (!isFinishing && !isDestroyed) {
                        // Update neon glow at last: pulse tagline to match logo, then navigate after brief hold
                        startNeonGlowPulse(taglineTextView)
                        val navRunnable = Runnable {
                            if (!isFinishing && !isDestroyed) navigateToNextWithFade()
                        }
                        handlerRunnables.add(navRunnable)
                        handler.postDelayed(navRunnable, 1500L)
                    }
                }
            })
        }

        taglineAnimator.start()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Permission entry point is status card in ActivationActivity; Splash does not handle permission results.
    }

    override fun onResume() {
        super.onResume()
    }

    private fun navigateToNextWithFade() {
        if (this@SplashActivity.isFinishing || this@SplashActivity.isDestroyed || isNavigating) {
            android.util.Log.d("SplashActivity", "navigateToNextWithFade: Skipping - finishing=$isFinishing, destroyed=$isDestroyed, navigating=$isNavigating")
            return
        }

        android.util.Log.d("SplashActivity", "navigateToNextWithFade: Starting navigation")

        // Prevent multiple navigation attempts
        isNavigating = true
        hasStartedNavigation = false // Reset flag for new navigation

        android.util.Log.d("SplashActivity", "Navigating - permission entry point is status card in ActivationActivity")

        val letterContainer = this@SplashActivity.findViewById<LinearLayout>(R.id.letterContainer)
        val taglineTextView = this@SplashActivity.findViewById<TextView>(R.id.taglineTextView)
        val splashContent = this@SplashActivity.findViewById<LinearLayout>(R.id.splashContent)

        // Ensure views are available; retry with limit (fixes 2nd launch when views not yet ready; prevents infinite loop on stuck devices)
        if (letterContainer == null || taglineTextView == null || splashContent == null) {
            navigateRetryCount++
            if (navigateRetryCount <= MAX_NAVIGATE_RETRIES) {
                android.util.Log.e("SplashActivity", "Views not available for navigation, retry $navigateRetryCount/$MAX_NAVIGATE_RETRIES in 300ms")
                isNavigating = false
                handler.postDelayed({ navigateToNextWithFade() }, 300L)
                return
            }
            android.util.Log.e("SplashActivity", "Views still null after $MAX_NAVIGATE_RETRIES retries, forcing direct navigation")
            navigateToActivity(false)
            return
        }
        navigateRetryCount = 0

        // Add timeout fallback to ensure navigation always happens
        val timeoutRunnable = Runnable {
            if (!isFinishing && !isDestroyed && isNavigating) {
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
                    if (!isFinishing && !isDestroyed && isNavigating) {
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
                if (!isFinishing && !isDestroyed && isNavigating) {
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
                    if (!isFinishing && !isDestroyed && isNavigating) {
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
                if (!isFinishing && !isDestroyed && isNavigating) {
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

    /**
     * Get locally saved activation code (used as fallback when Firebase/Django unavailable)
     */
    private fun getLocalActivationCode(): String? {
        return sharedPreferences.getString(KEY_ACTIVATION_CODE, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Check if local activation code is valid format (XXXXX11111 = 5 letters + 5 digits)
     */
    private fun isValidLocalActivationCode(code: String?): Boolean {
        if (code == null) return false
        return isValidUniqueCode(code)
    }

    /**
     * Try Django validation first, then use local code only if Django also fails.
     * Called when Firebase fails (timeout/error) to attempt Django validation before local fallback.
     *
     * @param localCode The locally saved activation code
     * @param deviceId The device ID
     * @param hasValidLocalCode Whether the local code is valid format
     * @param callback Called with true if activated (Django success OR both failed but local code valid)
     */
    @SuppressLint("HardwareIds")
    private fun tryDjangoThenLocalFallback(
        localCode: String?,
        deviceId: String,
        hasValidLocalCode: Boolean,
        callback: (Boolean) -> Unit
    ) {
        if (localCode.isNullOrBlank()) {
            // No local code to validate - not activated
            android.util.Log.d("SplashActivity", "No local code available - navigating to ActivationActivity")
            callback(false)
            return
        }

        // Try Django validation
        android.util.Log.d("SplashActivity", "Firebase failed - trying Django validation for code: ${localCode.take(4)}...")
        
        lifecycleScope.launch {
            try {
                val result = DjangoApiHelper.isValidCodeLogin(localCode, deviceId)
                
                when (result) {
                    is com.example.fast.util.Result.Success -> {
                        if (result.data) {
                            // Django says code is valid - activated
                            android.util.Log.d("SplashActivity", "Django validation success - code is valid")
                            DebugLogger.logActivationCheck("Django", "Code valid - activated")
                            handler.post { callback(true) }
                        } else {
                            // Django says code is NOT valid - not activated (don't use local fallback)
                            android.util.Log.d("SplashActivity", "Django validation: code is invalid - navigating to ActivationActivity")
                            DebugLogger.logActivationCheck("Django", "Code invalid - NOT activated")
                            handler.post { callback(false) }
                        }
                    }
                    is com.example.fast.util.Result.Error -> {
                        // Django also failed (network error) - NOW use local fallback
                        android.util.Log.w("SplashActivity", "Django validation failed (${result.exception.message}) - BOTH Firebase and Django failed")
                        if (hasValidLocalCode) {
                            android.util.Log.d("SplashActivity", "Using local code as offline fallback (activated=true)")
                            DebugLogger.logActivationCheck("Offline", "Both Firebase and Django failed - using local fallback")
                            handler.post { callback(true) }
                        } else {
                            android.util.Log.d("SplashActivity", "No valid local code - navigating to ActivationActivity")
                            DebugLogger.logActivationCheck("Offline", "Both Firebase and Django failed, no local code - NOT activated")
                            handler.post { callback(false) }
                        }
                    }
                }
            } catch (e: Exception) {
                // Exception during Django call - use local fallback
                android.util.Log.e("SplashActivity", "Django validation exception: ${e.message}")
                if (hasValidLocalCode) {
                    android.util.Log.d("SplashActivity", "Using local code as offline fallback (activated=true)")
                    handler.post { callback(true) }
                } else {
                    handler.post { callback(false) }
                }
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun checkActivationStatus(callback: (Boolean) -> Unit) {
        var callbackInvoked = false
        val callbackLock = Any()

        // CRITICAL: Always check local status first
        // If this is the first launch, always go to ActivationActivity (new installation)
        if (isFirstLaunch()) {
            android.util.Log.d("SplashActivity", "First launch detected - navigating to ActivationActivity")
            DebugLogger.logActivationCheck("Local", "First launch - not activated")
            callbackInvoked = true
            callback(false)
            return
        }

        // If not locally activated, always go to ActivationActivity
        // This ensures fresh installations always go through activation flow
        if (!isLocallyActivated()) {
            android.util.Log.d("SplashActivity", "Not locally activated - navigating to ActivationActivity")
            DebugLogger.logActivationCheck("Local", "Not locally activated")
            callbackInvoked = true
            callback(false)
            return
        }

        // Get local activation code for fallback (used if Firebase/Django unavailable)
        val localCode = getLocalActivationCode()
        val hasValidLocalCode = isValidLocalActivationCode(localCode)
        android.util.Log.d("SplashActivity", "Local activation code available: $hasValidLocalCode (code: ${localCode?.take(4) ?: "null"}...)")

        // Get deviceId early so it's available for timeout handler
        val deviceIdRaw = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        if (deviceIdRaw.isNullOrBlank()) {
            callbackInvoked = true
            callback(hasValidLocalCode)
            return
        }
        val deviceId = deviceIdRaw

        // Only check Firebase if locally activated (previous successful activation in this install)
        // Timeout fallback - if Firebase doesn't respond in 2 seconds, try Django then local fallback
        DebugLogger.logActivationCheck("Firebase", "Starting Firebase check...")
        val timeoutRunnable = Runnable {
            synchronized(callbackLock) {
                if (!callbackInvoked) {
                    callbackInvoked = true
                    // Firebase timeout - try Django validation before local fallback
                    android.util.Log.w("SplashActivity", "Firebase check timeout - trying Django validation")
                    DebugLogger.logActivationCheck("Firebase", "Timeout - falling back to Django")
                    tryDjangoThenLocalFallback(localCode, deviceId, hasValidLocalCode, callback)
                }
            }
        }
        handler.postDelayed(timeoutRunnable, 2000) // 2 second timeout

        try {
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

                        // Check 1: isActive must be true ("Opened" string or Boolean true)
                        val isActiveRaw = snapshot.child("isActive").value
                        val isActiveOk = when (isActiveRaw) {
                            is Boolean -> isActiveRaw
                            is String -> isActiveRaw == "Opened" || isActiveRaw.equals("true", ignoreCase = true)
                            else -> false
                        }

                        // Check 2: code must not be empty
                        val code = (snapshot.child("code").value as? String) ?: ""

                        if (!isActiveOk || code.isEmpty()) {
                            // Firebase explicitly shows not active or no code - do NOT use local fallback
                            // Server said "not activated" - respect that decision
                            android.util.Log.d("SplashActivity", "Firebase shows not active (isActive=$isActiveOk, code=${code.isNotEmpty()}) - navigating to ActivationActivity")
                            DebugLogger.logActivationCheck("Firebase", "isActive=$isActiveOk, hasCode=${code.isNotEmpty()} - NOT activated")
                            callbackInvoked = true
                            callback(false)
                            return
                        }

                        // Check 3: device-list entry must exist and match deviceId
                        // Path: fastpay/device-list/{code}/deviceId
                        val deviceListPath = AppConfig.getFirebaseDeviceListPath(code)

                        // Add timeout for second Firebase call - try Django then local fallback if timeout
                        val secondTimeoutRunnable = Runnable {
                            synchronized(callbackLock) {
                                if (!callbackInvoked) {
                                    callbackInvoked = true
                                    // Device-list timeout - try Django validation before local fallback
                                    android.util.Log.w("SplashActivity", "Device-list check timeout - trying Django validation")
                                    tryDjangoThenLocalFallback(localCode, deviceId, hasValidLocalCode, callback)
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
                                        val deviceListDeviceId = deviceListSnapshot.value as? String
                                        val isFullyActivated = deviceListDeviceId == deviceId

                                        if (isFullyActivated) {
                                            // Ensure device is registered at Django backend
                                            DebugLogger.logActivationCheck("Firebase", "Fully activated - device verified")
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
                                        } else {
                                            // Device-list mismatch - server says not activated, do NOT use local fallback
                                            android.util.Log.d("SplashActivity", "Device-list mismatch (deviceId not found) - navigating to ActivationActivity")
                                            DebugLogger.logActivationCheck("Firebase", "Device-list mismatch - NOT activated")
                                        }

                                        callbackInvoked = true
                                        callback(isFullyActivated)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    handler.removeCallbacks(secondTimeoutRunnable)

                                    synchronized(callbackLock) {
                                        if (callbackInvoked) return
                                        callbackInvoked = true

                                        // On error checking device-list, try Django then local fallback
                                        android.util.Log.w("SplashActivity", "Device-list check error - trying Django validation")
                                        tryDjangoThenLocalFallback(localCode, deviceId, hasValidLocalCode, callback)
                                    }
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    handler.removeCallbacks(timeoutRunnable)

                    synchronized(callbackLock) {
                        if (callbackInvoked) return
                        callbackInvoked = true

                        // On Firebase error, try Django then local fallback
                        android.util.Log.w("SplashActivity", "Firebase check error - trying Django validation")
                        tryDjangoThenLocalFallback(localCode, deviceId, hasValidLocalCode, callback)
                    }
                }
            })
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable)

            synchronized(callbackLock) {
                if (callbackInvoked) return
                callbackInvoked = true

                android.util.Log.e("SplashActivity", "Error checking activation status", e)
                // On exception, try Django then local fallback
                android.util.Log.w("SplashActivity", "Exception during Firebase check - trying Django validation")
                tryDjangoThenLocalFallback(localCode, deviceId, hasValidLocalCode, callback)
            }
        }
    }

    private fun navigateWithFadeAnimation(
        isActivated: Boolean,
        letterContainer: LinearLayout,
        taglineTextView: TextView,
        splashContent: LinearLayout
    ) {
        if (isFinishing || isDestroyed || !isNavigating) return

        android.util.Log.d("SplashActivity", "navigateWithFadeAnimation: Starting smooth shared element transition")

        // Get logoTextView for shared element transition
        val logoTextView = findViewById<TextView>(R.id.logoTextView)

        // Ensure views are available for shared element transition
        if (logoTextView == null || taglineTextView == null) {
            android.util.Log.e("SplashActivity", "Views are null, navigating with fallback")
            if (!isFinishing && !isDestroyed && isNavigating && !hasStartedNavigation) {
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
        if (!isFinishing && !isDestroyed && isNavigating && !hasStartedNavigation) {
            hasStartedNavigation = true
            navigateToActivity(isActivated)
        }
    }

    private fun navigateToActivity(isActivated: Boolean) {
        if (isFinishing || isDestroyed) {
            android.util.Log.w("SplashActivity", "navigateToActivity: Activity finishing or destroyed, cannot navigate")
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
                    // Update is available and will be handled by MultipurposeCardActivity
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
            finish()
        }
    }

    /**
     * Navigate to ActivatedActivity with transition
     */
    private fun navigateToActivatedActivity() {
        if (isFinishing || isDestroyed) return

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

                    startActivity(intent, options)
                        android.util.Log.d("SplashActivity", "Navigation started with smooth shared element transition")
                        // Finish immediately to allow destination activity to show immediately
                        if (!isFinishing) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAfterTransition()
                                // Fallback: on some devices finishAfterTransition hangs; force finish after 1.5s
                                val finishFallback = Runnable {
                                    if (!isFinishing) {
                                        android.util.Log.w("SplashActivity", "finishAfterTransition fallback: forcing finish")
                                        finish()
                                    }
                                }
                                handlerRunnables.add(finishFallback)
                                handler.postDelayed(finishFallback, 1500L)
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

        // Cancel all Handler callbacks to prevent memory leaks
        handlerRunnables.forEach { handler.removeCallbacks(it) }
        handlerRunnables.clear()

        // Cancel all infinite animations to prevent memory leaks and battery drain
        continuousAnimations.forEach { it.cancel() }
        continuousAnimations.clear()

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
     * If an update is available, launches MultipurposeCardActivity
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

                    // Launch MultipurposeCardActivity to handle the update
                    val intent = Intent(this, MultipurposeCardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(RemoteCardHandler.KEY_CARD_TYPE, RemoteCardHandler.CARD_TYPE_UPDATE)
                        putExtra(RemoteCardHandler.KEY_DOWNLOAD_URL, "$requiredVersionCode|$downloadUrl")
                        putExtra(RemoteCardHandler.KEY_DISPLAY_MODE, RemoteCardHandler.DISPLAY_MODE_FULLSCREEN)
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
