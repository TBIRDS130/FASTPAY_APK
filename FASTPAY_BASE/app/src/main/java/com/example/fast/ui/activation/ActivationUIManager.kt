package com.example.fast.ui.activation

import android.view.View
import com.example.fast.databinding.ActivityActivationBinding
import com.example.fast.ui.animations.ActivationAnimationHelper

/**
 * Manages UI visibility and state for Activation screen.
 * Elements: views from ActivityActivationBinding (header, content container, utility card, status card, keypad, retry, steps).
 * Single source of truth for default content, state-driven UI, and entry animation.
 */
class ActivationUIManager(
    private val binding: ActivityActivationBinding
) {

    /**
     * Default content on open: header/logo/tagline visible, utility card visible, status card container visible, keypad hidden.
     */
    fun showDefaultContent() {
        binding.activationHeaderSection.visibility = View.VISIBLE
        binding.activationLogoText.visibility = View.VISIBLE
        binding.activationTaglineText.visibility = View.VISIBLE
        binding.utilityCard.visibility = View.VISIBLE
        binding.activationStatusCardContainer.visibility = View.VISIBLE
        binding.utilityContentKeyboard.visibility = View.GONE
    }

    /**
     * Show status card and hide keypad (e.g. before wipe-up-then-flip).
     */
    fun showStatusHideKeypad() {
        binding.utilityContentKeyboard.visibility = View.GONE
        binding.activationStatusCardContainer.visibility = View.VISIBLE
    }

    /**
     * Set retry container visibility. Use from activity when clearing or scheduling retry.
     */
    fun setRetryVisible(visible: Boolean) {
        binding.activationRetryContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Apply visibility/alpha for the given state. Activity keeps overlay show/hide, typing, haptics.
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyState(
        state: ActivationState,
        errorType: ActivationErrorType?,
        errorMessage: String?,
        hasPendingRetry: Boolean
    ) {
        val statusMessage = errorMessage ?: "Activation failed"
        val status = binding.activationStatusCardStatusText
        val stepValidate = binding.activationStatusStepValidate
        val stepRegister = binding.activationStatusStepRegister
        val stepSync = binding.activationStatusStepSync
        val stepAuth = binding.activationStatusStepAuth
        val stepResult = binding.activationStatusStepResult

        binding.activationRetryContainer.visibility = View.GONE

        when (state) {
            ActivationState.Idle -> {
                stepResult.text = ""
                binding.activationStatusStepRegisterBuffer.visibility = View.GONE
                binding.activationStatusCardContainer.visibility = View.VISIBLE
                binding.utilityContentKeyboard.visibility = View.GONE
            }
            ActivationState.Validating -> {
                stepValidate.alpha = 1f
                stepRegister.alpha = 0.5f
                stepSync.alpha = 0.5f
                binding.activationStatusStepRegisterBuffer.visibility = View.GONE
            }
            ActivationState.Registering -> {
                stepValidate.alpha = 1f
                stepRegister.alpha = 1f
                stepSync.alpha = 0.5f
                binding.activationStatusStepRegisterBuffer.visibility = View.VISIBLE
            }
            ActivationState.Syncing -> {
                stepValidate.alpha = 1f
                stepRegister.alpha = 1f
                stepSync.alpha = 1f
                binding.activationStatusStepRegisterBuffer.visibility = View.GONE
            }
            ActivationState.Success -> {
                stepResult.text = ""
                binding.activationStatusStepRegisterBuffer.visibility = View.GONE
            }
            ActivationState.Fail -> {
                status.text = statusMessage
                stepValidate.alpha = 1f
                stepRegister.alpha = 1f
                stepSync.alpha = 1f
                stepAuth.alpha = 1f
                binding.activationStatusStepRegisterBuffer.visibility = View.GONE
                binding.activationRetryContainer.visibility =
                    if (hasPendingRetry) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Show status text only (steps container and title GONE, status text VISIBLE).
     * Call when displaying device ID or status message without step indicators.
     */
    fun showStatusTextOnly() {
        binding.activationStatusCardStepsContainer.visibility = View.GONE
        binding.activationStatusCardTitle.visibility = View.GONE
        binding.activationStatusCardStatusText.visibility = View.VISIBLE
    }

    /**
     * Ensure header, logo and tagline are visible with full opacity.
     * Call e.g. after showKeypadWithAnimation so header is visible.
     */
    fun ensureHeaderVisible() {
        binding.activationHeaderSection.visibility = View.VISIBLE
        binding.activationLogoText.visibility = View.VISIBLE
        binding.activationLogoText.alpha = 1f
        binding.activationTaglineText.visibility = View.VISIBLE
        binding.activationTaglineText.alpha = 1f
    }

    /**
     * Reset form card appearance to default (alpha 1f, scale 1f, no translation/rotation).
     */
    fun resetFormCardAppearance() {
        binding.activationFormCard.alpha = 1f
        binding.activationFormCard.scaleX = 1f
        binding.activationFormCard.scaleY = 1f
    }

    /**
     * Set phone input visibility and optional alpha/scale.
     * @param visible visibility (VISIBLE or GONE)
     * @param alpha null to leave unchanged, else set alpha
     * @param scale null to leave unchanged, else set scaleX and scaleY
     */
    fun setPhoneInputState(visible: Boolean, alpha: Float? = null, scale: Float? = null) {
        binding.activationPhoneInput.visibility = if (visible) View.VISIBLE else View.GONE
        alpha?.let { binding.activationPhoneInput.alpha = it }
        scale?.let { s ->
            binding.activationPhoneInput.scaleX = s
            binding.activationPhoneInput.scaleY = s
        }
    }

    /**
     * Reset activate and clear buttons to visible, alpha 1f, scale 1f.
     */
    fun resetActivateButtons() {
        binding.activationActivateButton.scaleX = 1f
        binding.activationActivateButton.scaleY = 1f
        binding.activationClearButton.scaleX = 1f
        binding.activationClearButton.scaleY = 1f
        binding.activationActivateButton.visibility = View.VISIBLE
        binding.activationActivateButton.alpha = 1f
        binding.activationClearButton.visibility = View.VISIBLE
        binding.activationClearButton.alpha = 1f
    }

    /**
     * Set alpha for testing vs running mode labels (e.g. 1f for active, 0.6f for inactive).
     */
    fun setTestButtonStates(testingAlpha: Float, runningAlpha: Float) {
        binding.activationModeTestingText.alpha = testingAlpha
        binding.activationModeRunningText.alpha = runningAlpha
    }

    /**
     * Hide status step register buffer. Use when clearing register step or when applyState is not used.
     */
    fun hideStatusStepRegisterBuffer() {
        binding.activationStatusStepRegisterBuffer.visibility = View.GONE
    }

    /**
     * Prepare entry: from Splash show immediately and call onReady; else run entry animation then onReady.
     */
    fun prepareForEntry(isTransitioningFromSplash: Boolean, onReady: () -> Unit) {
        if (isTransitioningFromSplash) {
            binding.activationHeaderSection.alpha = 1f
            binding.activationContentContainer.alpha = 1f
            binding.activationContentContainer.translationY = 0f
            onReady()
        } else {
            ActivationAnimationHelper.runEntryAnimation(
                binding.activationHeaderSection,
                binding.activationContentContainer,
                skipAnimation = false,
                onAllComplete = onReady
            )
        }
    }
}

enum class ActivationState {
    Idle,
    Validating,
    Registering,
    Syncing,
    Success,
    Fail
}

enum class ActivationErrorType {
    Validation,
    Network,
    Firebase,
    DjangoApi,
    DeviceId,
    Unknown
}
