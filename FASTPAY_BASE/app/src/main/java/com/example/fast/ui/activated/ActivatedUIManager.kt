package com.example.fast.ui.activated

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fast.R
import com.example.fast.databinding.ActivityActivatedBinding
import com.example.fast.ui.animations.AnimationConstants
import com.example.fast.util.DebugLogger
import com.example.fast.util.LogHelper

/**
 * Entry animation style for Activated screen (from Activation).
 */
enum class ActivatedEntryVariant {
    /** Staggered fade-in: phone, status, device, SMS, buttons with delays */
    STAGGERED,
    /** All elements fade in together (no stagger) */
    FADE_AT_ONCE
}

/**
 * Manages UI setup and visibility for ActivatedActivity
 */
class ActivatedUIManager(
    private val binding: ActivityActivatedBinding,
    private val isTransitioningFromSplash: Boolean,
    private val entryVariant: ActivatedEntryVariant = ActivatedEntryVariant.STAGGERED
) {

    /**
     * Setup UI after branding is loaded
     * Always shows SMS by default (instruction is always available on back side)
     */
    fun setupUIAfterBranding(hasInstructionCard: Boolean) {
        LogHelper.d("ActivatedUIManager", "setupUIAfterBranding() called (always showing SMS by default)")

        if (isTransitioningFromSplash) {
            // Coming from SplashActivity - show all elements immediately
            showAllElementsImmediately()
        } else {
            // Coming from ActivationActivity - hide for animation
            hideElementsForAnimation()
        }

        setupEdgeToEdgeInsets()
    }

    /**
     * Ensure header (logo, tagline, header section) is visible and opaque.
     * Call after branding load; activity sets logo/tagline text.
     */
    fun ensureHeaderVisible() {
        binding.headerSection.visibility = View.VISIBLE
        binding.headerSection.alpha = 1f
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE
    }

    /**
     * Ensure phone card is visible and opaque for shared-element transition start.
     * Call from transition listener onTransitionStart; activity keeps setBackgroundResource.
     */
    fun ensurePhoneCardVisibleForTransition() {
        binding.phoneCard.visibility = View.VISIBLE
        binding.phoneCard.alpha = 1f
    }

    /**
     * Single source of truth: show all 6 main content elements (header, phone card, status card,
     * device info, SMS card, test/reset buttons) with visibility VISIBLE, alpha 1f, scale 1f.
     * Call this whenever the main screen should be fully visible (no receded or hidden state).
     */
    fun showMainContent() {
        // 1. Header
        binding.headerSection.visibility = View.VISIBLE
        binding.headerSection.alpha = 1f
        binding.headerSection.scaleX = 1f
        binding.headerSection.scaleY = 1f
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE

        // 2. Phone card
        binding.phoneCardWrapper.visibility = View.VISIBLE
        binding.phoneCardWrapper.alpha = 1f
        binding.phoneCardWrapper.scaleX = 1f
        binding.phoneCardWrapper.scaleY = 1f
        binding.phoneCard.visibility = View.VISIBLE
        binding.phoneCard.alpha = 1f

        // 3. Status card
        binding.statusCard.visibility = View.VISIBLE
        binding.statusCard.alpha = 1f
        binding.statusCard.scaleX = 1f
        binding.statusCard.scaleY = 1f

        // 4. Device info
        binding.deviceInfoColumn.visibility = View.VISIBLE
        binding.deviceInfoColumn.alpha = 1f
        binding.deviceInfoColumn.scaleX = 1f
        binding.deviceInfoColumn.scaleY = 1f

        // 5. SMS card (SMS side visible, instruction on back)
        binding.smsCard.visibility = View.VISIBLE
        binding.smsCard.alpha = 1f
        binding.smsCard.scaleX = 1f
        binding.smsCard.scaleY = 1f
        binding.smsContentFront.visibility = View.VISIBLE
        binding.smsContentFront.alpha = 1f
        binding.instructionContentBack.visibility = View.GONE
        binding.instructionContentBack.alpha = 0f

        // 6. Buttons
        binding.testButtonsContainer.visibility = View.VISIBLE
        binding.testButtonsContainer.alpha = 1f
        binding.testButtonsContainer.scaleX = 1f
        binding.testButtonsContainer.scaleY = 1f
        binding.testButtonCard.alpha = 1f
        binding.testButtonCard.visibility = View.VISIBLE
        binding.resetButtonCard.alpha = 1f
        binding.resetButtonCard.visibility = View.VISIBLE

        DebugLogger.logVisibility("mainContent", "visible", "alpha=1.0")
    }

    /**
     * Show all UI elements immediately (no animation). Delegates to showMainContent().
     */
    private fun showAllElementsImmediately() {
        LogHelper.d("ActivatedUIManager", "Path: SplashActivity - showing all elements immediately")
        showMainContent()
    }

    /**
     * Hide elements for animation (coming from ActivationActivity)
     * Always shows SMS by default
     */
    private fun hideElementsForAnimation() {
        LogHelper.d("ActivatedUIManager", "Path: ActivationActivity - hiding elements for animation")

        // Cancel any running animators so we don't leave views in half-way state
        cancelEntryAndArrivalAnimators()

        // Ensure headerSection (logo) is always visible
        binding.headerSection.alpha = 1f
        binding.headerSection.visibility = View.VISIBLE
        binding.textView11.visibility = View.VISIBLE
        binding.textView12.visibility = View.VISIBLE

        // Hide cards and buttons for animation (all six areas driven by manager)
        binding.phoneCard.alpha = 0f
        binding.phoneCard.visibility = View.VISIBLE
        binding.statusCard.alpha = 0f
        binding.statusCard.visibility = View.VISIBLE

        binding.deviceInfoColumn.alpha = 0f
        binding.deviceInfoColumn.visibility = View.VISIBLE

        // SMS card is always visible (instruction is always available on back side)
        binding.smsCard.alpha = 0f
        binding.smsCard.visibility = View.VISIBLE

        // Always show SMS by default
        binding.smsContentFront.alpha = 0f
        binding.smsContentFront.visibility = View.VISIBLE
        binding.instructionContentBack.alpha = 0f
        binding.instructionContentBack.visibility = View.GONE

        binding.testButtonsContainer.alpha = 0f
        binding.testButtonsContainer.visibility = View.VISIBLE

        // Animate cards and buttons in (durations/delays from AnimationConstants; variant may remove stagger)
        val entryFade = AnimationConstants.ACTIVATED_ENTRY_FADE_MS
        val delayPhone = if (entryVariant == ActivatedEntryVariant.FADE_AT_ONCE) 0L else AnimationConstants.ACTIVATED_ENTRY_DELAY_PHONE_MS
        val delayStatus = if (entryVariant == ActivatedEntryVariant.FADE_AT_ONCE) 0L else AnimationConstants.ACTIVATED_ENTRY_DELAY_STATUS_MS
        val delayDevice = if (entryVariant == ActivatedEntryVariant.FADE_AT_ONCE) 0L else AnimationConstants.ACTIVATED_ENTRY_DELAY_DEVICE_MS
        val delaySms = if (entryVariant == ActivatedEntryVariant.FADE_AT_ONCE) 0L else AnimationConstants.ACTIVATED_ENTRY_DELAY_SMS_MS
        val delayButtons = if (entryVariant == ActivatedEntryVariant.FADE_AT_ONCE) 0L else AnimationConstants.ACTIVATED_ENTRY_DELAY_BUTTONS_MS

        binding.phoneCard.animate()
            .alpha(1f)
            .setDuration(entryFade)
            .setStartDelay(delayPhone)
            .start()

        binding.statusCard.animate()
            .alpha(1f)
            .setDuration(entryFade)
            .setStartDelay(delayStatus)
            .start()

        binding.deviceInfoColumn.animate()
            .alpha(1f)
            .setDuration(entryFade)
            .setStartDelay(delayDevice)
            .start()

        binding.smsCard.animate()
            .alpha(1f)
            .setDuration(entryFade)
            .setStartDelay(delaySms)
            .withEndAction {
                binding.smsContentFront.animate()
                    .alpha(1f)
                    .setDuration(AnimationConstants.ACTIVATED_ENTRY_SMS_INNER_FADE_MS)
                    .start()
            }
            .start()

        binding.testButtonsContainer.animate()
            .alpha(1f)
            .setDuration(entryFade)
            .setStartDelay(delayButtons)
            .withEndAction { showMainContent() }
            .start()
    }

    /**
     * Cancel any running ViewPropertyAnimator on views used in entry and arrival animations.
     * Call before starting hideElementsForAnimation or runArrivalAnimation to avoid half-way state.
     */
    private fun cancelEntryAndArrivalAnimators() {
        binding.headerSection.animate().cancel()
        binding.phoneCardWrapper.animate().cancel()
        binding.phoneCard.animate().cancel()
        binding.statusCard.animate().cancel()
        binding.deviceInfoColumn.animate().cancel()
        binding.smsCard.animate().cancel()
        binding.smsContentFront.animate().cancel()
        binding.testButtonsContainer.animate().cancel()
    }

    /**
     * Setup edge-to-edge insets handling.
     * Apply insets to the root (main) so the entire content sits below the status bar
     * and above the nav bar, avoiding an invisible "element on top" overlapping content.
     */
    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.main.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }

    /**
     * Show elements immediately (no animation path). Delegates to showMainContent().
     */
    fun showElementsImmediately(hasInstructionCard: Boolean) {
        showMainContent()
    }

    /**
     * Ensure all elements are visible (safety check). Delegates to showMainContent().
     */
    fun ensureElementsVisible(hasInstructionCard: Boolean) {
        showMainContent()
    }

    /**
     * Hide all main content (header, cards, buttons) so only overlay is visible.
     * Call before showing MultipurposeCard overlay. Restore with showMainContent() in card onComplete.
     */
    fun hideMainContentForOverlay() {
        binding.headerSection.visibility = View.GONE
        binding.phoneCardWrapper.visibility = View.GONE
        binding.statusCard.visibility = View.GONE
        binding.deviceInfoColumn.visibility = View.GONE
        binding.smsCard.visibility = View.GONE
        binding.testButtonsContainer.visibility = View.GONE
    }

    /**
     * Run wipe-down entry animation (elements slide up from off-screen then fade in).
     * Call onComplete when the last animation ends.
     */
    fun runWipeDownEntryAnimation(onComplete: () -> Unit) {
        val rootView = binding.main
        val headerSection = binding.headerSection
        val phoneCardWrapper = binding.phoneCardWrapper
        val smsCard = binding.smsCard
        rootView.post {
            headerSection.animate().cancel()
            phoneCardWrapper.animate().cancel()
            smsCard.animate().cancel()

            val screenHeight = rootView.height.toFloat()
            val wipeDistance = -screenHeight * 0.4f
            headerSection.translationY = wipeDistance
            headerSection.alpha = 0f
            phoneCardWrapper.translationY = wipeDistance
            phoneCardWrapper.alpha = 0f
            smsCard.translationY = wipeDistance
            smsCard.alpha = 0f
            smsCard.visibility = View.VISIBLE
            val wipeDuration = AnimationConstants.ACTIVATION_WIPE_DOWN_ENTRY_MS
            val staggerDelay = AnimationConstants.ACTIVATION_WIPE_DOWN_STAGGER_MS
            headerSection.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(wipeDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()
            phoneCardWrapper.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(wipeDuration)
                .setStartDelay(staggerDelay)
                .setInterpolator(DecelerateInterpolator())
                .start()
            smsCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(wipeDuration)
                .setStartDelay(staggerDelay * 2)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(onComplete)
                .start()
        }
    }

    /**
     * Run one-by-one arrival animation (header, phone card, status, SMS, buttons fade in).
     * On completion calls showMainContent() then onComplete.
     */
    fun runArrivalAnimation(onComplete: () -> Unit) {
        cancelEntryAndArrivalAnimators()

        val elements = listOf(
            binding.headerSection,
            binding.phoneCardWrapper,
            binding.statusCard,
            binding.smsCard,
            binding.testButtonsContainer
        )
        elements.forEach { it.alpha = 0f }
        val handler = Handler(Looper.getMainLooper())
        var index = 0
        fun animateNext() {
            if (index >= elements.size) {
                showMainContent()
                onComplete()
                return
            }
            val view = elements[index]
            view.animate()
                .alpha(1f)
                .setDuration(AnimationConstants.ACTIVATED_ARRIVAL_FADE_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    index++
                    if (index >= elements.size) {
                        showMainContent()
                        onComplete()
                    } else {
                        handler.postDelayed({ animateNext() }, AnimationConstants.ACTIVATED_ARRIVAL_STAGGER_MS)
                    }
                }
                .start()
        }
        handler.postDelayed({ animateNext() }, AnimationConstants.ACTIVATED_ARRIVAL_INITIAL_DELAY_MS)
    }

    /**
     * Show SMS side of the card. Optionally show empty state or list.
     */
    fun showSmsSide(showEmptyState: Boolean) {
        binding.smsCard.visibility = View.VISIBLE
        binding.smsCard.alpha = 1f
        binding.smsContentFront.visibility = View.VISIBLE
        binding.smsContentFront.alpha = 1f
        binding.instructionContentBack.visibility = View.GONE
        binding.instructionContentBack.alpha = 0f
        binding.smsEmptyState.visibility = if (showEmptyState) View.VISIBLE else View.GONE
        binding.smsRecyclerView.visibility = if (showEmptyState) View.GONE else View.VISIBLE
    }

    /**
     * Show instruction side of the card.
     */
    fun showInstructionSide() {
        binding.smsCard.visibility = View.VISIBLE
        binding.smsCard.alpha = 1f
        binding.smsContentFront.visibility = View.GONE
        binding.smsContentFront.alpha = 0f
        binding.instructionContentBack.visibility = View.VISIBLE
        binding.instructionContentBack.alpha = 1f
    }

    /**
     * Set permission status text visibility (e.g. for "Permissions: granted" line).
     */
    fun setPermissionStatusVisible(visible: Boolean) {
        binding.permissionStatusText.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
