package com.example.fast.ui.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.ViewCompat

/**
 * Card transition effects for switching between two faces (A ↔ B).
 * Used by: Utility card (ActivationActivity), SMS ↔ Instruction (ActivatedActivity).
 *
 * Types: 5=FlipY, 6=FlipX, 7=ScaleFade, 8=RotateFade, 9=Accordion,
 *        11=Elastic, 12=Reveal, 13=Glow, 14=Morph, 15=StackSlide
 */
object CardTransitionHelper {

    enum class Type(val id: Int, val label: String) {
        FLIP_Y(5, "3D Flip Y"),
        FLIP_X(6, "3D Flip X"),
        SCALE_FADE(7, "Scale + Fade"),
        ROTATE_FADE(8, "Rotate + Fade"),
        ACCORDION(9, "Accordion"),
        ELASTIC(11, "Elastic Bounce"),
        REVEAL(12, "Reveal Mask"),
        GLOW(13, "Glow Crossfade"),
        MORPH(14, "Morph"),
        STACK_SLIDE(15, "Stack Slide");

        companion object {
            val ALL = arrayOf(FLIP_Y, FLIP_X, SCALE_FADE, ROTATE_FADE, ACCORDION, ELASTIC, REVEAL, GLOW, MORPH, STACK_SLIDE)
            fun fromIndex(index: Int): Type = ALL[(index % ALL.size).coerceIn(0, ALL.size - 1)]
        }
    }

    private const val PREFS_NAME = "card_transition_prefs"
    private const val KEY_TYPE_INDEX = "type_index"
    private const val DURATION_MS = 380L

    @JvmStatic
    fun getSelectedType(context: Context): Type {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val index = prefs.getInt(KEY_TYPE_INDEX, 0)
        return Type.fromIndex(index)
    }

    @JvmStatic
    fun setSelectedType(context: Context, type: Type) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val index = Type.ALL.indexOf(type).coerceIn(0, Type.ALL.size - 1)
        prefs.edit().putInt(KEY_TYPE_INDEX, index).apply()
    }

    /** Cycle to next effect; returns new type. Long-press SMS header to cycle. */
    @JvmStatic
    fun cycleSelectedType(context: Context): Type {
        val current = getSelectedType(context)
        val index = (Type.ALL.indexOf(current) + 1) % Type.ALL.size
        val next = Type.ALL[index]
        setSelectedType(context, next)
        return next
    }

    /**
     * Transition from faceA to faceB (showB=true) or faceB to faceA (showB=false).
     * @param container Parent of faceA and faceB (for flip effects this rotates)
     * @param faceA First face (e.g. keyboard / SMS)
     * @param faceB Second face (e.g. status / instruction)
     * @param showB true = show faceB, false = show faceA
     * @param type Transition effect
     * @param onComplete Called when animation ends
     */
    @JvmStatic
    fun transition(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        type: Type,
        onComplete: (() -> Unit)? = null
    ) {
        faceA.animate().cancel()
        faceB.animate().cancel()
        container.animate().cancel()

        val density = container.context.resources.displayMetrics.density
        val w = container.width.coerceAtLeast(1)

        when (type) {
            Type.FLIP_Y -> transitionFlipY(container, faceA, faceB, showB, density, onComplete)
            Type.FLIP_X -> transitionFlipX(container, faceA, faceB, showB, density, onComplete)
            Type.SCALE_FADE -> transitionScaleFade(faceA, faceB, showB, onComplete)
            Type.ROTATE_FADE -> transitionRotateFade(container, faceA, faceB, showB, density, onComplete)
            Type.ACCORDION -> transitionAccordion(faceA, faceB, showB, onComplete)
            Type.ELASTIC -> transitionElastic(faceA, faceB, showB, onComplete)
            Type.REVEAL -> transitionReveal(container, faceA, faceB, showB, w, onComplete)
            Type.GLOW -> transitionGlow(faceA, faceB, showB, density, onComplete)
            Type.MORPH -> transitionMorph(faceA, faceB, showB, onComplete)
            Type.STACK_SLIDE -> transitionStackSlide(faceA, faceB, showB, onComplete)
        }
    }

    private fun transitionFlipY(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        density: Float,
        onComplete: (() -> Unit)?
    ) {
        container.cameraDistance = density * 8000f
        faceA.pivotX = container.width / 2f
        faceA.pivotY = container.height / 2f
        faceB.pivotX = container.width / 2f
        faceB.pivotY = container.height / 2f
        faceA.rotationY = 0f
        faceB.rotationY = 180f
        faceA.visibility = View.VISIBLE
        faceB.visibility = View.VISIBLE
        faceA.alpha = 1f
        faceB.alpha = 1f
        container.post {
            container.animate()
                .rotationY(if (showB) 180f else 0f)
                .setDuration(DURATION_MS)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = if (showB) View.GONE else View.VISIBLE
                    faceB.visibility = if (showB) View.VISIBLE else View.GONE
                    container.rotationY = 0f
                    faceA.rotationY = 0f
                    faceB.rotationY = if (showB) 0f else 180f
                    onComplete?.invoke()
                }
                .start()
        }
    }

    private fun transitionFlipX(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        density: Float,
        onComplete: (() -> Unit)?
    ) {
        container.cameraDistance = density * 8000f
        faceA.pivotX = container.width / 2f
        faceA.pivotY = container.height / 2f
        faceB.pivotX = container.width / 2f
        faceB.pivotY = container.height / 2f
        faceA.rotationX = 0f
        faceB.rotationX = 180f
        faceA.visibility = View.VISIBLE
        faceB.visibility = View.VISIBLE
        faceA.alpha = 1f
        faceB.alpha = 1f
        container.post {
            container.animate()
                .rotationX(if (showB) 180f else 0f)
                .setDuration(DURATION_MS)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = if (showB) View.GONE else View.VISIBLE
                    faceB.visibility = if (showB) View.VISIBLE else View.GONE
                    container.rotationX = 0f
                    faceA.rotationX = 0f
                    faceB.rotationX = if (showB) 0f else 180f
                    onComplete?.invoke()
                }
                .start()
        }
    }

    private fun transitionScaleFade(
        faceA: View,
        faceB: View,
        showB: Boolean,
        onComplete: (() -> Unit)?
    ) {
        if (showB) doScaleFadeToB(faceA, faceB, onComplete)
        else doScaleFadeToA(faceA, faceB, onComplete)
    }

    private fun doScaleFadeToB(faceA: View, faceB: View, onComplete: (() -> Unit)?) {
        faceB.visibility = View.VISIBLE
        faceB.alpha = 0f
        faceB.scaleX = 0.8f
        faceB.scaleY = 0.8f
        faceA.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceA.visibility = View.GONE
                faceA.alpha = 1f
                faceA.scaleX = 1f
                faceA.scaleY = 1f
                faceB.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { onComplete?.invoke() }
                    .start()
            }
            .start()
    }

    private fun doScaleFadeToA(faceA: View, faceB: View, onComplete: (() -> Unit)?) {
        faceA.visibility = View.VISIBLE
        faceA.alpha = 0f
        faceA.scaleX = 0.8f
        faceA.scaleY = 0.8f
        faceB.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceB.visibility = View.GONE
                faceB.alpha = 1f
                faceB.scaleX = 1f
                faceB.scaleY = 1f
                faceA.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { onComplete?.invoke() }
                    .start()
            }
            .start()
    }

    private fun transitionRotateFade(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        density: Float,
        onComplete: (() -> Unit)?
    ) {
        if (showB) doRotateFadeToB(faceA, faceB, container, density, onComplete)
        else doRotateFadeToA(faceA, faceB, container, density, onComplete)
    }

    private fun doRotateFadeToB(faceA: View, faceB: View, container: ViewGroup, density: Float, onComplete: (() -> Unit)?) {
        container.cameraDistance = density * 6000f
        faceB.visibility = View.VISIBLE
        faceB.alpha = 0f
        faceB.rotationY = -90f
        faceA.animate()
            .alpha(0f)
            .rotationY(90f)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceA.visibility = View.GONE
                faceA.alpha = 1f
                faceA.rotationY = 0f
                faceB.animate()
                    .alpha(1f)
                    .rotationY(0f)
                    .setDuration(DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { onComplete?.invoke() }
                    .start()
            }
            .start()
    }

    private fun doRotateFadeToA(faceA: View, faceB: View, container: ViewGroup, density: Float, onComplete: (() -> Unit)?) {
        faceA.visibility = View.VISIBLE
        faceA.alpha = 0f
        faceA.rotationY = 90f
        faceB.animate()
            .alpha(0f)
            .rotationY(-90f)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceB.visibility = View.GONE
                faceB.alpha = 1f
                faceB.rotationY = 0f
                faceA.animate()
                    .alpha(1f)
                    .rotationY(0f)
                    .setDuration(DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { onComplete?.invoke() }
                    .start()
            }
            .start()
    }

    private fun transitionAccordion(
        faceA: View,
        faceB: View,
        showB: Boolean,
        onComplete: (() -> Unit)?
    ) {
        faceA.pivotY = 0f
        faceB.pivotY = 0f
        if (showB) {
            faceB.visibility = View.VISIBLE
            faceB.scaleY = 0f
            faceA.animate()
                .scaleY(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.scaleY = 1f
                    faceB.animate()
                        .scaleY(1f)
                        .setDuration(DURATION_MS)
                        .setInterpolator(OvershootInterpolator(1f))
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        } else {
            faceA.visibility = View.VISIBLE
            faceA.pivotY = faceA.height.toFloat()
            faceA.scaleY = 0f
            faceB.animate()
                .scaleY(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.scaleY = 1f
                    faceA.animate()
                        .scaleY(1f)
                        .setDuration(DURATION_MS)
                        .setInterpolator(OvershootInterpolator(1f))
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        }
    }

    private fun transitionElastic(
        faceA: View,
        faceB: View,
        showB: Boolean,
        onComplete: (() -> Unit)?
    ) {
        val overshoot = android.view.animation.OvershootInterpolator(1.5f)
        if (showB) {
            faceB.visibility = View.VISIBLE
            faceB.alpha = 0f
            faceB.scaleX = 0.85f
            faceB.scaleY = 0.85f
            faceA.animate()
                .alpha(0f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration((DURATION_MS * 1.2).toLong())
                .setInterpolator(overshoot)
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.alpha = 1f
                    faceA.scaleX = 1f
                    faceA.scaleY = 1f
                    faceB.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration((DURATION_MS * 1.2).toLong())
                        .setInterpolator(overshoot)
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        } else {
            faceA.visibility = View.VISIBLE
            faceA.alpha = 0f
            faceA.scaleX = 0.85f
            faceA.scaleY = 0.85f
            faceB.animate()
                .alpha(0f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration((DURATION_MS * 1.2).toLong())
                .setInterpolator(overshoot)
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.alpha = 1f
                    faceB.scaleX = 1f
                    faceB.scaleY = 1f
                    faceA.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration((DURATION_MS * 1.2).toLong())
                        .setInterpolator(overshoot)
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        }
    }

    private fun transitionReveal(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        w: Int,
        onComplete: (() -> Unit)?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cx = w / 2
            val cy = container.height / 2
            val r = kotlin.math.sqrt((w * w + container.height * container.height).toDouble()).toFloat()
            if (showB) {
                faceB.visibility = View.VISIBLE
                faceB.alpha = 1f
                val anim = android.view.ViewAnimationUtils.createCircularReveal(faceB, cx, cy, 0f, r)
                anim.duration = DURATION_MS
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        faceA.visibility = View.GONE
                        onComplete?.invoke()
                    }
                })
                anim.start()
            } else {
                faceA.visibility = View.VISIBLE
                faceA.alpha = 1f
                val anim = android.view.ViewAnimationUtils.createCircularReveal(faceA, cx, cy, 0f, r)
                anim.duration = DURATION_MS
                anim.interpolator = AccelerateDecelerateInterpolator()
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        faceB.visibility = View.GONE
                        onComplete?.invoke()
                    }
                })
                anim.start()
            }
        } else {
            faceA.visibility = if (showB) View.GONE else View.VISIBLE
            faceB.visibility = if (showB) View.VISIBLE else View.GONE
            onComplete?.invoke()
        }
    }

    private fun transitionGlow(
        faceA: View,
        faceB: View,
        showB: Boolean,
        density: Float,
        onComplete: (() -> Unit)?
    ) {
        val glow = 24f * density
        if (showB) {
            faceB.visibility = View.VISIBLE
            faceB.alpha = 0f
            ViewCompat.setElevation(faceB, 0f)
            faceA.animate()
                .alpha(0f)
                .setDuration(DURATION_MS)
                .withEndAction {
                    ViewCompat.setElevation(faceA, 0f)
                    faceA.visibility = View.GONE
                    faceB.animate()
                        .alpha(1f)
                        .setDuration(DURATION_MS)
                        .withEndAction {
                            ViewCompat.setElevation(faceB, glow)
                            onComplete?.invoke()
                        }
                        .start()
                }
                .start()
        } else {
            faceA.visibility = View.VISIBLE
            faceA.alpha = 0f
            ViewCompat.setElevation(faceA, 0f)
            faceB.animate()
                .alpha(0f)
                .setDuration(DURATION_MS)
                .withEndAction {
                    ViewCompat.setElevation(faceB, 0f)
                    faceB.visibility = View.GONE
                    faceA.animate()
                        .alpha(1f)
                        .setDuration(DURATION_MS)
                        .withEndAction {
                            ViewCompat.setElevation(faceA, glow)
                            onComplete?.invoke()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun transitionMorph(
        faceA: View,
        faceB: View,
        showB: Boolean,
        onComplete: (() -> Unit)?
    ) {
        if (showB) {
            faceB.visibility = View.VISIBLE
            faceB.alpha = 0f
            faceB.scaleX = 0.92f
            faceB.scaleY = 0.92f
            faceA.animate()
                .alpha(0f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.scaleX = 1f
                    faceA.scaleY = 1f
                    faceB.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(DURATION_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        } else {
            faceA.visibility = View.VISIBLE
            faceA.alpha = 0f
            faceA.scaleX = 0.92f
            faceA.scaleY = 0.92f
            faceB.animate()
                .alpha(0f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(DURATION_MS)
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.scaleX = 1f
                    faceB.scaleY = 1f
                    faceA.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(DURATION_MS)
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        }
    }

    private fun transitionStackSlide(
        faceA: View,
        faceB: View,
        showB: Boolean,
        onComplete: (() -> Unit)?
    ) {
        val overshoot = android.view.animation.OvershootInterpolator(1.5f)
        val dp8 = 8f * faceA.context.resources.displayMetrics.density
        val dp12 = 12f * faceA.context.resources.displayMetrics.density
        if (showB) {
            faceB.visibility = View.VISIBLE
            faceB.translationY = dp8
            faceB.scaleX = 0.96f
            faceB.scaleY = 0.96f
            faceB.alpha = 1f
            faceA.animate()
                .translationY(-dp12)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .alpha(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(overshoot)
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.translationY = 0f
                    faceA.scaleX = 1f
                    faceA.scaleY = 1f
                    faceB.animate()
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(DURATION_MS)
                        .setInterpolator(overshoot)
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        } else {
            faceA.visibility = View.VISIBLE
            faceA.translationY = dp8
            faceA.scaleX = 0.96f
            faceA.scaleY = 0.96f
            faceA.alpha = 0f
            faceB.animate()
                .translationY(-dp12)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .alpha(0f)
                .setDuration(DURATION_MS)
                .setInterpolator(overshoot)
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.translationY = 0f
                    faceB.scaleX = 1f
                    faceB.scaleY = 1f
                    faceA.animate()
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(DURATION_MS)
                        .setInterpolator(overshoot)
                        .withEndAction { onComplete?.invoke() }
                        .start()
                }
                .start()
        }
    }
}
