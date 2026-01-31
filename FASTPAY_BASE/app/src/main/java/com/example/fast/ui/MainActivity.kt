package com.example.fast.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
// Navigation Component imports (for future Fragment migration)
// import androidx.navigation.NavController
// import androidx.navigation.fragment.NavHostFragment
// import androidx.navigation.ui.setupActionBarWithNavController
import com.example.fast.R
import com.example.fast.adapter.SmsConversationAdapter
import com.example.fast.databinding.ActivityMainBinding
import android.content.ComponentName
import androidx.core.app.NotificationManagerCompat
import com.example.fast.service.PersistentForegroundService
import com.example.fast.service.NotificationReceiver
import com.example.fast.service.ContactSmsSyncService
import com.example.fast.util.PermissionSyncHelper
import com.example.fast.util.PermissionManager
import com.example.fast.notification.AppNotificationManager
import com.example.fast.viewmodel.MainActivityViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import com.prexoft.prexocore.hide
import com.prexoft.prexocore.show
import com.prexoft.prexocore.alert
import androidx.core.view.isVisible
import com.prexoft.prexocore.onClick

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val id by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private lateinit var conversationAdapter: SmsConversationAdapter
    
    // ViewModel - injected via Hilt
    private val viewModel: MainActivityViewModel by viewModels()
    
    // Handler and runnable references for cleanup
    private val handler = Handler(Looper.getMainLooper())
    private val handlerRunnables = mutableListOf<Runnable>()
    
    /**
     * Setup ViewModel observers
     */
    private fun setupViewModelObservers() {
        // Observe conversations list
        viewModel.conversations.observe(this) { conversations ->
            if (conversations.isEmpty()) {
                id.emptyStateText.show()
                id.smsRecyclerView.hide()
            } else {
                id.emptyStateText.hide()
                id.smsRecyclerView.show()
                
                // Initialize adapter if not already created
                if (!::conversationAdapter.isInitialized) {
                    conversationAdapter = SmsConversationAdapter { conversation ->
                        try {
                            startActivity(Intent(this@MainActivity, ChatActivity::class.java).apply {
                                putExtra("contact_number", conversation.contactNumber)
                                putExtra("contact_name", conversation.contactName)
                            })
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    id.smsRecyclerView.apply {
                        layoutManager = LinearLayoutManager(this@MainActivity)
                        adapter = conversationAdapter
                    }
                }
                
                conversationAdapter.submitList(conversations)
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                id.progressBar.show()
            } else {
                id.progressBar.hide()
            }
        }
        
        // Observe show views state
        viewModel.shouldShowViews.observe(this) { shouldShow ->
            if (shouldShow) {
                showViews()
            }
        }
        
        // Observe status messages
        viewModel.statusMessage.observe(this) { statusMessage ->
            statusMessage?.let {
                alert(it.title, it.message, it.buttonText)
                viewModel.clearStatusMessage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set window background to match SplashActivity for seamless transition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
        }
        
        setContentView(id.root)

        // Load branding config from Firebase and update UI
        com.example.fast.util.BrandingConfigManager.loadBrandingConfig(this) { logoName, tagline ->
            // Update logo and tagline TextViews
            id.mainLogo.text = logoName
            id.mainTagline.text = tagline
        }

        // ViewModel is injected via Hilt, no need to create manually
        
        // Setup ViewModel observers
        setupViewModelObservers()

        // Initialize notification channels
        AppNotificationManager.initializeChannels(this)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // Handle intent for sending SMS (when app is default SMS app)
        handleIntent(intent)
        
        // Postpone enter transition if coming from SplashActivity to ensure smooth shared element animation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val isTransitioning = window.sharedElementEnterTransition != null
            if (isTransitioning) {
                postponeEnterTransition()
                // Start transition after views are measured
                id.main.post {
                    startPostponedEnterTransition()
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(id.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            id.headerLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Check if coming from SplashActivity or ActivationActivity (has transition shared elements)
        val isTransitioningFromSplash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.sharedElementEnterTransition != null
        } else {
            false
        }

        // Handle logo transition
        if (isTransitioningFromSplash) {
            // Logo is already transitioning from shared element (from SplashActivity or ActivationActivity)
            // Just ensure it's visible and positioned correctly
            id.logoSection.alpha = 1f
            // Content below can fade in after transition
            val contentAnimationRunnable = Runnable {
                // Content animation if needed
            }
            handlerRunnables.add(contentAnimationRunnable)
            handler.postDelayed(contentAnimationRunnable, 300)
        } else {
            // Normal entry - fade in logo section
            id.logoSection.alpha = 0f
            id.logoSection.animate()
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Setup scroll listener for RecyclerView (replaces NestedScrollView scroll listener)
        setupRecyclerViewScrollListener()
        
        id.scrollToTop.onClick { 
            id.smsRecyclerView.post { 
                if (id.smsRecyclerView.layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
                    (id.smsRecyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager)
                        .scrollToPositionWithOffset(0, 0)
                }
            } 
        }
    }

    private fun setup() {
        // Setup Firebase status listener via ViewModel
        viewModel.setupFirebaseStatusListener()
        
        // Setup Firebase real-time messages listener for conversation updates
        viewModel.setupFirebaseMessagesListener()
        
        // Load conversations via ViewModel
        viewModel.loadConversations()
    }

    @SuppressLint("BatteryLife")
    override fun onResume() {
        super.onResume()
        // Silent permission check - requests permissions directly if missing (bypasses PermissionFlowActivity UI)
        if (!PermissionManager.checkAndRedirectSilently(this)) {
            return // Permissions were requested, waiting for user response
        }
        
        // All permissions granted - continue with normal flow
        setup()
        // Automatically start sync if permissions already granted
        PermissionSyncHelper.checkAndStartSync(this)
        if (!isPersistentServiceRunning()) { PersistentForegroundService.start(this) }
    }
    
    private fun syncData() {
        // Sync messages and contacts in background (no UI blocking)
        // Use ContactSmsSyncService for modern sync implementation
        ContactSmsSyncService.startSync(this, ContactSmsSyncService.SyncType.ALL)
    }

    private fun isPersistentServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == PersistentForegroundService::class.java.name }
    }

    private fun Context.isGranted(): Boolean {
        val notificationListenerComponent = ComponentName(packageName, NotificationReceiver::class.java.name)
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(notificationListenerComponent.packageName)
    }

    private fun showViews() { 
        id.smsUI.show()
        setupComposeButton()
        // Conversations are loaded via ViewModel, no need to call setupSmsList()
    }

    private fun setupComposeButton() {
        id.addChat.onClick { startActivity(Intent(this@MainActivity, ContactsActivity::class.java)) }
    }
    
    private fun setupRecyclerViewScrollListener() {
        // Add scroll listener to RecyclerView to show/hide scrollToTop button
        id.smsRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
                
                if (firstVisiblePosition > 0) {
                    if (!id.scrollToTop.isVisible) id.scrollToTop.show()
                } else {
                    id.scrollToTop.hide()
                }
            }
        })
    }
    

    // Permission request handling removed - now handled by PermissionFlowActivity
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    /**
     * Handle intents for sending SMS/MMS (when app is default SMS app)
     * Routes to ContactsActivity with the phone number pre-filled
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                if (uri.scheme == "smsto" || uri.scheme == "sms" || uri.scheme == "mms" || uri.scheme == "mmsto") {
                    val address = uri.schemeSpecificPart
                    if (!address.isNullOrEmpty()) {
                        // Open ContactsActivity with the phone number pre-filled
                        startActivity(Intent(this, ContactsActivity::class.java).apply {
                            putExtra("phone_number", address)
                        })
                    }
                }
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save adapter state if needed (conversations are reloaded anyway)
        // Save scroll position if needed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // ViewModel handles Firebase listener cleanup automatically via onCleared()
        
        // Cancel all Handler callbacks to prevent memory leaks
        handlerRunnables.forEach { handler.removeCallbacks(it) }
        handlerRunnables.clear()
        
        // Cancel any running animations
        id.main.clearAnimation()
        id.logoSection.clearAnimation()
    }
}

