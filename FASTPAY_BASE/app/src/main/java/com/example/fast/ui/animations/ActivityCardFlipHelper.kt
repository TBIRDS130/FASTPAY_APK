package com.example.fast.ui.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils

/**
 * ActivityCardFlipHelper
 *
 * Handles **cross-activity sequential card flip transitions** with maximum polish:
 * - Sequential card flips (card1 flips, then card2 flips)
 * - Content fade (old content fades out, new content fades in)
 * - Background dim (focuses attention on flipping cards)
 * - Card glow effect (neon pulse at edge-on moment)
 * - Clean hide/reveal (no "see through" effect)
 *
 * Usage:
 * ```kotlin
 * // In ActivationActivity (exit):
 * ActivityCardFlipHelper.flipOutSequential(
 *     activity = this,
 *     cards = listOf(inputCard, utilityCard),
 *     dimOverlay = dimView,
 *     onComplete = { launchActivatedActivity() }
 * )
 *
 * // In ActivatedActivity (entry):
 * ActivityCardFlipHelper.flipInSequential(
 *     activity = this,
 *     cards = listOf(phoneCard, smsCard),
 *     dimOverlay = dimView,
 *     onComplete = { /* animation done */ }
 * )
 * ```
 */
object ActivityCardFlipHelper {

    private const val TAG = "ActivityCardFlipHelper"

    // Animation timing
    const val FLIP_DURATION_MS = 300L
    const val CONTENT_FADE_DURATION_MS = 150L
    const val DIM_DURATION_MS = 200L
    const val GLOW_PULSE_DURATION_MS = 150L

    // Visual settings
    const val DIM_ALPHA = 0.6f
    const val RECEDE_SCALE = 0.95f
    const val RECEDE_ALPHA = 0.5f
    const val CAMERA_DISTANCE_FACTOR = 8000f
    val GLOW_COLOR = Color.parseColor("#00FF88") // Theme primary neon green

    /**
     * Data class for card flip configuration
     */
    data class CardConfig(
        val card: View,           // The card view to flip
        val content: View? = null // Optional content inside card (for fade effect)
    )

    /**
     * Sequential flip-out animation for exiting activity.
     *
     * @param activity The current activity
     * @param cards List of cards to flip out sequentially
     * @param dimOverlay Optional overlay view to dim background (will be made visible and animated)
     * @param recedeViews Optional views to scale down and fade during flip
     * @param onComplete Called when all flips complete
     */
    fun flipOutSequential(
        activity: Activity,
        cards: List<CardConfig>,
        dimOverlay: View? = null,
        recedeViews: List<View> = emptyList(),
        onComplete: () -> Unit
    ) {
        if (cards.isEmpty()) {
            onComplete()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val density = activity.resources.displayMetrics.density

        // Step 1: Dim background
        dimOverlay?.let { overlay ->
            overlay.visibility = View.VISIBLE
            overlay.alpha = 0f
            overlay.animate()
                .alpha(DIM_ALPHA)
                .setDuration(DIM_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Step 2: Recede non-flipping views
        recedeViews.forEach { view ->
            view.animate()
                .scaleX(RECEDE_SCALE)
                .scaleY(RECEDE_SCALE)
                .alpha(RECEDE_ALPHA)
                .setDuration(DIM_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Step 3: Sequential flip-out
        flipOutCard(cards, 0, density, handler, onComplete)
    }

    /**
     * Recursively flip out cards one by one.
     */
    private fun flipOutCard(
        cards: List<CardConfig>,
        index: Int,
        density: Float,
        handler: Handler,
        onComplete: () -> Unit
    ) {
        if (index >= cards.size) {
            onComplete()
            return
        }

        val config = cards[index]
        val card = config.card
        val content = config.content

        // Setup camera distance for 3D effect
        card.cameraDistance = density * CAMERA_DISTANCE_FACTOR

        // Fade out content first (quick)
        content?.animate()
            ?.alpha(0f)
            ?.setDuration(CONTENT_FADE_DURATION_MS)
            ?.setInterpolator(AccelerateInterpolator())
            ?.start()

        // Wait for content fade, then flip
        handler.postDelayed({
            // Add glow effect at start
            animateGlow(card, true, density)

            // Flip card to 90 degrees (edge-on)
            card.animate()
                .rotationY(90f)
                .setDuration(FLIP_DURATION_MS)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    // Hide card at 90 degrees
                    card.visibility = View.INVISIBLE

                    // Remove glow
                    animateGlow(card, false, density)

                    // Flip next card
                    flipOutCard(cards, index + 1, density, handler, onComplete)
                }
                .start()
        }, CONTENT_FADE_DURATION_MS)
    }

    /**
     * Sequential flip-in animation for entering activity.
     *
     * @param activity The current activity
     * @param cards List of cards to flip in sequentially (should start at rotationY=-90 and INVISIBLE)
     * @param dimOverlay Optional overlay view to fade out
     * @param recedeViews Optional views to restore from receded state
     * @param onComplete Called when all flips complete
     */
    fun flipInSequential(
        activity: Activity,
        cards: List<CardConfig>,
        dimOverlay: View? = null,
        recedeViews: List<View> = emptyList(),
        onComplete: () -> Unit
    ) {
        if (cards.isEmpty()) {
            dimOverlay?.animate()?.alpha(0f)?.setDuration(DIM_DURATION_MS)?.withEndAction {
                dimOverlay.visibility = View.GONE
            }?.start()
            onComplete()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val density = activity.resources.displayMetrics.density

        // Setup initial state for all cards
        cards.forEach { config ->
            config.card.rotationY = -90f
            config.card.visibility = View.INVISIBLE
            config.content?.alpha = 0f
            config.card.cameraDistance = density * CAMERA_DISTANCE_FACTOR
        }

        // Start sequential flip-in
        flipInCard(cards, 0, density, handler) {
            // After all flips, restore background
            dimOverlay?.animate()
                ?.alpha(0f)
                ?.setDuration(DIM_DURATION_MS)
                ?.setInterpolator(DecelerateInterpolator())
                ?.withEndAction {
                    dimOverlay.visibility = View.GONE
                }
                ?.start()

            recedeViews.forEach { view ->
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(DIM_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            onComplete()
        }
    }

    /**
     * Recursively flip in cards one by one.
     */
    private fun flipInCard(
        cards: List<CardConfig>,
        index: Int,
        density: Float,
        handler: Handler,
        onComplete: () -> Unit
    ) {
        if (index >= cards.size) {
            onComplete()
            return
        }

        val config = cards[index]
        val card = config.card
        val content = config.content

        // Make card visible (at -90 degrees, edge-on)
        card.visibility = View.VISIBLE

        // Add glow effect
        animateGlow(card, true, density)

        // Flip card from -90 to 0 degrees
        card.animate()
            .rotationY(0f)
            .setDuration(FLIP_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // Remove glow
                animateGlow(card, false, density)

                // Fade in content
                content?.animate()
                    ?.alpha(1f)
                    ?.setDuration(CONTENT_FADE_DURATION_MS)
                    ?.setInterpolator(DecelerateInterpolator())
                    ?.withEndAction {
                        // Flip next card
                        flipInCard(cards, index + 1, density, handler, onComplete)
                    }
                    ?.start()
                    ?: run {
                        // No content, flip next card immediately
                        flipInCard(cards, index + 1, density, handler, onComplete)
                    }
            }
            .start()
    }

    /**
     * Animate glow effect on card during flip.
     */
    private fun animateGlow(card: View, glowOn: Boolean, density: Float) {
        // Use elevation for glow effect (creates shadow/glow on supported API levels)
        val targetElevation = if (glowOn) 24f * density else 4f * density

        card.animate()
            .translationZ(targetElevation)
            .setDuration(GLOW_PULSE_DURATION_MS)
            .start()
    }

    /**
     * Prepare cards for flip-in animation.
     * Call this in onCreate before layout is complete.
     */
    fun prepareForFlipIn(cards: List<CardConfig>, density: Float) {
        cards.forEach { config ->
            config.card.rotationY = -90f
            config.card.visibility = View.INVISIBLE
            config.content?.alpha = 0f
            config.card.cameraDistance = density * CAMERA_DISTANCE_FACTOR
        }
    }

    /**
     * Create a dim overlay view programmatically.
     * Useful if layout doesn't have a dedicated overlay.
     */
    fun createDimOverlay(parent: ViewGroup): View {
        return View(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
            parent.addView(this, 0) // Add at bottom
        }
    }
}
