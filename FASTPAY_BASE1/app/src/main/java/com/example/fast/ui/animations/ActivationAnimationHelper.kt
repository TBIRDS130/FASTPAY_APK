package com.example.fast.ui.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.core.view.ViewCompat
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Activation screen–specific animations.
 * Use this helper to keep ActivationActivity focused on flow; move animation logic here over time.
 *
 * **Flow:**
 * 1. Entry → header + content slide up + fade in (staggered).
 * 2. Keypad → slide up + fade in when user taps input.
 * 3. Activate button → 3D press (scale + elevation + rotationX), then run action.
 * 4. Success → utility card collapse, then sync pulse, then navigate.
 *
 * **How to add or change animations:**
 * - Durations: use [AnimationConstants] (e.g. ACTIVATION_*).
 * - New sequence: add a method here that takes Views and optional callbacks.
 * - Entry/exit: use [runEntryAnimation], or extend for transition-to-Activated.
 */
object ActivationAnimationHelper {

    /**
     * Run the normal entry animation: header and content slide up + fade in with stagger.
     * @param headerSection Header (logo + tagline) view
     * @param contentContainer Center content (form card) view
     * @param skipAnimation If true, views are shown immediately with no animation
     * @param onAllComplete Called when all entry animations finish (e.g. start hint animation)
     */
    @JvmStatic
    fun runEntryAnimation(
        headerSection: View,
        contentContainer: View,
        skipAnimation: Boolean,
        onAllComplete: () -> Unit
    ) {
        if (skipAnimation) {
            headerSection.alpha = 1f
            contentContainer.alpha = 1f
            contentContainer.translationY = 0f
            onAllComplete()
            return
        }
        val views = listOf(headerSection, contentContainer)
        var completed = 0
        val total = views.size
        val staggerMs = 150L
        val durationMs = 600L

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(durationMs)
                .setStartDelay(index * staggerMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    completed++
                    if (completed == total) onAllComplete()
                }
                .start()
        }
    }

    /**
     * Show keypad with slide-up + fade-in.
     * Call when user taps input or Clear restores keypad.
     */
    @JvmStatic
    fun showKeypadWithAnimation(keyboardView: View, slidePx: Float = 24f, durationMs: Long = AnimationConstants.KEYPAD_SHOW_DURATION_MS) {
        keyboardView.visibility = View.VISIBLE
        keyboardView.translationY = slidePx
        keyboardView.alpha = 0f
        keyboardView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * 3D button press: scale down + lower elevation + tilt, run action, then scale back with overshoot.
     * Use for ACTIVATE / CLEAR (and other primary actions on Activation screen).
     */
    @JvmStatic
    fun buttonPress3D(view: View, density: Float, onPressed: () -> Unit) {
        view.animate().cancel()
        val startElevation = ViewCompat.getElevation(view)
        val startTranslationZ = ViewCompat.getTranslationZ(view)
        val pressedZ = -4f * density
        view.cameraDistance = density * 8000f

        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_DOWN_DURATION },
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_DOWN_DURATION },
                ObjectAnimator.ofFloat(view, "elevation", startElevation, 0f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_DOWN_DURATION },
                ObjectAnimator.ofFloat(view, "translationZ", startTranslationZ, pressedZ).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_DOWN_DURATION },
                ObjectAnimator.ofFloat(view, "rotationX", 0f, 12f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_DOWN_DURATION }
            )
        }
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_UP_DURATION; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_UP_DURATION; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "elevation", 0f, startElevation).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_UP_DURATION; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "translationZ", pressedZ, startTranslationZ).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_UP_DURATION; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(view, "rotationX", 12f, 0f).apply { duration = AnimationConstants.BUTTON_PRESS_SCALE_UP_DURATION; interpolator = OvershootInterpolator() }
            )
        }
        scaleDown.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onPressed()
                scaleUp.start()
            }
        })
        scaleDown.start()
    }

    /**
     * Success flow: collapse utility card (scale down), then run sync pulse on input + status card, then call onComplete.
     * Use after activation API success before navigating to ActivatedActivity.
     * @deprecated Use runCollapseAndCenterForFlip for the new center-flip animation flow.
     */
    @JvmStatic
    fun runSuccessCollapseThenSync(
        utilityCard: View,
        inputCard: View,
        statusCard: View,
        collapseDurationMs: Long = AnimationConstants.ACTIVATION_PHONE_DISPLAY_DURATION,
        syncDurationMs: Long = 800L,
        onComplete: () -> Unit
    ) {
        utilityCard.pivotX = utilityCard.width / 2f
        utilityCard.pivotY = 0f
        utilityCard.animate()
            .scaleY(0.25f)
            .scaleX(0.95f)
            .setDuration(collapseDurationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                inputCard.alpha = 0.7f
                statusCard.alpha = 0.7f
                inputCard.animate().alpha(1f).setDuration(300).start()
                statusCard.animate().alpha(1f).setDuration(300).start()
                utilityCard.postDelayed({ onComplete() }, syncDurationMs)
            }
            .start()
    }

    /**
     * New success flow: Collapse utility card to 0, move logo down and input card up to CENTER.
     * After centering, calls onCentered for flip animation.
     *
     * Flow:
     * 1. Utility card collapses to scaleY=0 (invisible)
     * 2. Logo (header section) moves DOWN toward center
     * 3. Input card moves UP toward center (below logo)
     * 4. onCentered callback for flip animation
     *
     * @param rootView The root view to calculate center position
     * @param headerSection The logo/header section to move down
     * @param inputCard The input card to move up
     * @param utilityCard The utility card to collapse
     * @param otherViewsToFade Views to fade out (e.g., status card, grid background)
     * @param collapseDurationMs Duration of collapse/center animation
     * @param onCentered Called when elements are centered and ready for flip
     */
    @JvmStatic
    fun runCollapseAndCenterForFlip(
        rootView: View,
        headerSection: View,
        inputCard: View,
        utilityCard: View,
        otherViewsToFade: List<View> = emptyList(),
        collapseDurationMs: Long = 500L,
        onCentered: () -> Unit
    ) {
        val rootHeight = rootView.height
        val rootCenterY = rootHeight / 2f

        // Calculate target positions for centering
        // Logo should be slightly above center, input card below logo
        val headerHeight = headerSection.height
        val inputCardHeight = inputCard.height
        val totalCenteredHeight = headerHeight + 16 + inputCardHeight // 16dp gap
        val centeredStartY = rootCenterY - (totalCenteredHeight / 2f)

        // Current positions
        val headerCurrentY = headerSection.y
        val inputCardCurrentY = inputCard.y

        // Target Y translations
        val headerTargetY = centeredStartY - headerCurrentY
        val inputCardTargetY = (centeredStartY + headerHeight + 16) - inputCardCurrentY

        // 1. Collapse utility card to 0
        utilityCard.pivotX = utilityCard.width / 2f
        utilityCard.pivotY = 0f
        utilityCard.animate()
            .scaleY(0f)
            .scaleX(0.8f)
            .alpha(0f)
            .setDuration(collapseDurationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                utilityCard.visibility = View.GONE
            }
            .start()

        // 2. Fade out other views
        otherViewsToFade.forEach { view ->
            view.animate()
                .alpha(0f)
                .setDuration(collapseDurationMs)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // 3. Move header section down to center
        headerSection.animate()
            .translationY(headerTargetY)
            .setDuration(collapseDurationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 4. Move input card up to center (below logo)
        inputCard.animate()
            .translationY(inputCardTargetY)
            .setDuration(collapseDurationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // All animations done, ready for flip
                onCentered()
            }
            .start()
    }

    /**
     * Store original positions for restoration if needed.
     */
    data class CenteredPositions(
        val headerOriginalY: Float,
        val inputCardOriginalY: Float,
        val headerTranslationY: Float,
        val inputCardTranslationY: Float
    )

    /**
     * In-place pulse on both cards when ACTIVATE is clicked.
     * Cards stay in their space; only scale 1 -> 1.02 -> 1 (no translation).
     * @param onComplete Called when both card animations have finished (after scale back to 1f).
     */
    @JvmStatic
    fun animateCardsOnActivate(
        cryptoHashCard: View,
        utilityCard: View,
        durationMs: Long = AnimationConstants.CARDS_PULSE_DURATION_MS,
        onComplete: (() -> Unit)? = null
    ) {
        listOf(cryptoHashCard, utilityCard).forEach { card ->
            card.animate().cancel()
            if (card.width > 0 && card.height > 0) {
                card.pivotX = card.width / 2f
                card.pivotY = card.height / 2f
            }
        }
        val half = durationMs / 2
        val peakScale = 1.02f
        var cryptoDone = false
        var utilityDone = false
        fun checkBothDone() {
            if (cryptoDone && utilityDone) onComplete?.invoke()
        }
        cryptoHashCard.animate()
            .scaleX(peakScale)
            .scaleY(peakScale)
            .setDuration(half)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                cryptoHashCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(half)
                    .setInterpolator(OvershootInterpolator(1f))
                    .withEndAction { cryptoDone = true; checkBothDone() }
                    .start()
            }
            .start()
        utilityCard.animate()
            .scaleX(peakScale)
            .scaleY(peakScale)
            .setDuration(half)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                utilityCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(half)
                    .setInterpolator(OvershootInterpolator(1f))
                    .withEndAction { utilityDone = true; checkBothDone() }
                    .start()
            }
            .start()
    }

    /**
     * Keypad key press feedback: quick scale down then back.
     */
    @JvmStatic
    fun keypadKeyPress(keyView: View) {
        keyView.animate().cancel()
        keyView.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(AnimationConstants.KEYPAD_KEY_PRESS_DOWN_MS)
            .withEndAction {
                keyView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(AnimationConstants.KEYPAD_KEY_PRESS_UP_MS)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }
            .start()
    }

    /**
     * New success flow: Wipe up utility card and backgrounds, then flip input card in place.
     * Logo and input card stay in their current positions (no centering movement).
     *
     * Flow:
     * 1. Utility card and backgrounds wipe UP (translationY to negative + alpha to 0)
     * 2. Logo + Input card stay visible in place
     * 3. Input card flips in place (rotationY 0 -> 90)
     * 4. onFlipComplete callback for navigation
     *
     * @param inputCard The input card to flip in place
     * @param utilityCard The utility card to wipe up
     * @param viewsToWipeUp Views to wipe up and fade out (utility card, grid, scanline, status card)
     * @param wipeUpDurationMs Duration of wipe up animation
     * @param flipDurationMs Duration of flip animation
     * @param onFlipComplete Called when flip reaches 90 degrees (ready to navigate)
     */
    @JvmStatic
    fun runWipeUpThenFlip(
        inputCard: View,
        utilityCard: View,
        viewsToWipeUp: List<View> = emptyList(),
        wipeUpDurationMs: Long = AnimationConstants.WIPE_UP_DURATION_MS,
        flipDurationMs: Long = AnimationConstants.CARD_FLIP_DURATION_MS,
        onFlipComplete: () -> Unit
    ) {
        val density = inputCard.context.resources.displayMetrics.density
        val wipeDistance = -200f * density // Wipe up distance in pixels

        // Cancel any existing animations
        inputCard.animate().cancel()
        utilityCard.animate().cancel()
        viewsToWipeUp.forEach { it.animate().cancel() }

        // Phase 1: Wipe up utility card and other background views
        // Utility card wipes up with translation and fade
        utilityCard.animate()
            .translationY(wipeDistance)
            .alpha(0f)
            .setDuration(wipeUpDurationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                utilityCard.visibility = View.GONE
            }
            .start()

        // Other views also wipe up
        viewsToWipeUp.forEach { view ->
            view.animate()
                .translationY(wipeDistance * 0.7f) // Slightly less distance for parallax
                .alpha(0f)
                .setDuration(wipeUpDurationMs)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Phase 2: After wipe up completes, flip input card in place
        inputCard.postDelayed({
            // Safety check - activity may have been destroyed during delay
            val context = inputCard.context
            if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                return@postDelayed
            }

            // Set camera distance for 3D flip
            inputCard.cameraDistance = density * 8000f
            inputCard.pivotX = inputCard.width / 2f
            inputCard.pivotY = inputCard.height / 2f

            // Flip in place (no translation)
            inputCard.animate()
                .rotationY(90f)
                .setDuration(flipDurationMs)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    // Safety check before callback
                    val ctx = inputCard.context
                    if (ctx is android.app.Activity && (ctx.isFinishing || ctx.isDestroyed)) {
                        return@withEndAction
                    }
                    onFlipComplete()
                }
                .start()
        }, wipeUpDurationMs)
    }
}
