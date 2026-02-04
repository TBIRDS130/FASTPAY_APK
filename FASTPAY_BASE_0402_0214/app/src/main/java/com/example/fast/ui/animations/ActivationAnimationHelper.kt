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
    fun showKeypadWithAnimation(keyboardView: View, slidePx: Float = 24f, durationMs: Long = 220L) {
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
     * In-place pulse on both cards when ACTIVATE is clicked.
     * Cards stay in their space; only scale 1 -> 1.02 -> 1 (no translation).
     * @param onComplete Called when both card animations have finished (after scale back to 1f).
     */
    @JvmStatic
    fun animateCardsOnActivate(
        cryptoHashCard: View,
        utilityCard: View,
        durationMs: Long = 350L,
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
            .setDuration(80)
            .withEndAction {
                keyView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }
            .start()
    }
}
