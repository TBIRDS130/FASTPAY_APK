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
     * @param durationMs Animation duration in milliseconds. Default 380ms for status-to-keyboard, SMS/instruction.
     * @param onComplete Called when animation ends
     */
    @JvmStatic
    fun transition(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        type: Type,
        durationMs: Long = AnimationConstants.ACTIVATION_STATUS_TO_KEYBOARD_MS,
        onComplete: (() -> Unit)? = null
    ) {
        faceA.animate().cancel()
        faceB.animate().cancel()
        container.animate().cancel()

        val density = container.context.resources.displayMetrics.density
        val w = container.width.coerceAtLeast(1)

        when (type) {
            Type.FLIP_Y -> transitionFlipY(container, faceA, faceB, showB, density, durationMs, onComplete)
            Type.FLIP_X -> transitionFlipX(container, faceA, faceB, showB, density, durationMs, onComplete)
            Type.SCALE_FADE -> transitionScaleFade(faceA, faceB, showB, durationMs, onComplete)
            Type.ROTATE_FADE -> transitionRotateFade(container, faceA, faceB, showB, density, durationMs, onComplete)
            Type.ACCORDION -> transitionAccordion(faceA, faceB, showB, durationMs, onComplete)
            Type.ELASTIC -> transitionElastic(faceA, faceB, showB, durationMs, onComplete)
            Type.REVEAL -> transitionReveal(container, faceA, faceB, showB, w, durationMs, onComplete)
            Type.GLOW -> transitionGlow(faceA, faceB, showB, density, durationMs, onComplete)
            Type.MORPH -> transitionMorph(faceA, faceB, showB, durationMs, onComplete)
            Type.STACK_SLIDE -> transitionStackSlide(faceA, faceB, showB, durationMs, onComplete)
        }
    }

    private fun transitionFlipY(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        density: Float,
        durationMs: Long,
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
                .setDuration(durationMs)
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
        durationMs: Long,
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
                .setDuration(durationMs)
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
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        if (showB) doScaleFadeToB(faceA, faceB, durationMs, onComplete)
        else doScaleFadeToA(faceA, faceB, durationMs, onComplete)
    }

    private fun doScaleFadeToB(faceA: View, faceB: View, durationMs: Long, onComplete: (() -> Unit)?) {
        // Bring faceB to front and start crossfade simultaneously
        faceB.bringToFront()
        faceB.visibility = View.VISIBLE
        faceB.alpha = 0f
        faceB.scaleX = 0.95f
        faceB.scaleY = 0.95f
        
        // Crossfade: both animations run simultaneously
        faceA.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceA.visibility = View.GONE
                faceA.alpha = 1f
                faceA.scaleX = 1f
                faceA.scaleY = 1f
            }
            .start()
        
        faceB.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onComplete?.invoke() }
            .start()
    }

    private fun doScaleFadeToA(faceA: View, faceB: View, durationMs: Long, onComplete: (() -> Unit)?) {
        // Bring faceA to front and start crossfade simultaneously
        faceA.bringToFront()
        faceA.visibility = View.VISIBLE
        faceA.alpha = 0f
        faceA.scaleX = 0.95f
        faceA.scaleY = 0.95f
        
        // Crossfade: both animations run simultaneously
        faceB.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceB.visibility = View.GONE
                faceB.alpha = 1f
                faceB.scaleX = 1f
                faceB.scaleY = 1f
            }
            .start()
        
        faceA.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onComplete?.invoke() }
            .start()
    }

    private fun transitionRotateFade(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        density: Float,
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        if (showB) doRotateFadeToB(faceA, faceB, container, density, durationMs, onComplete)
        else doRotateFadeToA(faceA, faceB, container, density, durationMs, onComplete)
    }

    private fun doRotateFadeToB(faceA: View, faceB: View, container: ViewGroup, density: Float, durationMs: Long, onComplete: (() -> Unit)?) {
        container.cameraDistance = density * 6000f
        faceB.bringToFront()
        faceB.visibility = View.VISIBLE
        faceB.alpha = 0f
        faceB.rotationY = -45f
        
        // Crossfade with rotation - both run simultaneously
        faceA.animate()
            .alpha(0f)
            .rotationY(45f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceA.visibility = View.GONE
                faceA.alpha = 1f
                faceA.rotationY = 0f
            }
            .start()
        
        faceB.animate()
            .alpha(1f)
            .rotationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onComplete?.invoke() }
            .start()
    }

    private fun doRotateFadeToA(faceA: View, faceB: View, container: ViewGroup, density: Float, durationMs: Long, onComplete: (() -> Unit)?) {
        container.cameraDistance = density * 6000f
        faceA.bringToFront()
        faceA.visibility = View.VISIBLE
        faceA.alpha = 0f
        faceA.rotationY = 45f
        
        // Crossfade with rotation - both run simultaneously
        faceB.animate()
            .alpha(0f)
            .rotationY(-45f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                faceB.visibility = View.GONE
                faceB.alpha = 1f
                faceB.rotationY = 0f
            }
            .start()
        
        faceA.animate()
            .alpha(1f)
            .rotationY(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onComplete?.invoke() }
            .start()
    }

    private fun transitionAccordion(
        faceA: View,
        faceB: View,
        showB: Boolean,
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        faceA.pivotY = 0f
        faceB.pivotY = 0f
        
        if (showB) {
            faceB.bringToFront()
            faceB.visibility = View.VISIBLE
            faceB.scaleY = 0.3f
            faceB.alpha = 0f
            
            // Crossfade with accordion - both run simultaneously
            faceA.animate()
                .scaleY(0.3f)
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.scaleY = 1f
                    faceA.alpha = 1f
                }
                .start()
            
            faceB.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(OvershootInterpolator(1f))
                .withEndAction { onComplete?.invoke() }
                .start()
        } else {
            faceA.bringToFront()
            faceA.visibility = View.VISIBLE
            faceA.pivotY = 0f
            faceA.scaleY = 0.3f
            faceA.alpha = 0f
            
            // Crossfade with accordion - both run simultaneously
            faceB.animate()
                .scaleY(0.3f)
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.scaleY = 1f
                    faceB.alpha = 1f
                }
                .start()
            
            faceA.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(OvershootInterpolator(1f))
                .withEndAction { onComplete?.invoke() }
                .start()
        }
    }

    private fun transitionElastic(
        faceA: View,
        faceB: View,
        showB: Boolean,
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        val overshoot = android.view.animation.OvershootInterpolator(1.2f)
        val duration = (durationMs * 0.9).toLong()
        
        if (showB) {
            faceB.bringToFront()
            faceB.visibility = View.VISIBLE
            faceB.alpha = 0f
            faceB.scaleX = 0.9f
            faceB.scaleY = 0.9f
            
            // Crossfade with elastic - both run simultaneously
            faceA.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(duration)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.alpha = 1f
                    faceA.scaleX = 1f
                    faceA.scaleY = 1f
                }
                .start()
            
            faceB.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(overshoot)
                .withEndAction { onComplete?.invoke() }
                .start()
        } else {
            faceA.bringToFront()
            faceA.visibility = View.VISIBLE
            faceA.alpha = 0f
            faceA.scaleX = 0.9f
            faceA.scaleY = 0.9f
            
            // Crossfade with elastic - both run simultaneously
            faceB.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(duration)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.alpha = 1f
                    faceB.scaleX = 1f
                    faceB.scaleY = 1f
                }
                .start()
            
            faceA.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(overshoot)
                .withEndAction { onComplete?.invoke() }
                .start()
        }
    }

    private fun transitionReveal(
        container: ViewGroup,
        faceA: View,
        faceB: View,
        showB: Boolean,
        w: Int,
        durationMs: Long,
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
                anim.duration = durationMs
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
                anim.duration = durationMs
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
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        val glow = 24f * density
        if (showB) {
            faceB.bringToFront()
            faceB.visibility = View.VISIBLE
            faceB.alpha = 0f
            ViewCompat.setElevation(faceB, glow)
            
            // Crossfade - both run simultaneously
            faceA.animate()
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    ViewCompat.setElevation(faceA, 0f)
                    faceA.visibility = View.GONE
                    faceA.alpha = 1f
                }
                .start()
            
            faceB.animate()
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { onComplete?.invoke() }
                .start()
        } else {
            faceA.bringToFront()
            faceA.visibility = View.VISIBLE
            faceA.alpha = 0f
            ViewCompat.setElevation(faceA, glow)
            
            // Crossfade - both run simultaneously
            faceB.animate()
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    ViewCompat.setElevation(faceB, 0f)
                    faceB.visibility = View.GONE
                    faceB.alpha = 1f
                }
                .start()
            
            faceA.animate()
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { onComplete?.invoke() }
                .start()
        }
    }

    private fun transitionMorph(
        faceA: View,
        faceB: View,
        showB: Boolean,
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        if (showB) {
            faceB.bringToFront()
            faceB.visibility = View.VISIBLE
            faceB.alpha = 0f
            faceB.scaleX = 0.95f
            faceB.scaleY = 0.95f
            
            // Crossfade with morph - both run simultaneously
            faceA.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.alpha = 1f
                    faceA.scaleX = 1f
                    faceA.scaleY = 1f
                }
                .start()
            
            faceB.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { onComplete?.invoke() }
                .start()
        } else {
            faceA.bringToFront()
            faceA.visibility = View.VISIBLE
            faceA.alpha = 0f
            faceA.scaleX = 0.95f
            faceA.scaleY = 0.95f
            
            // Crossfade with morph - both run simultaneously
            faceB.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.alpha = 1f
                    faceB.scaleX = 1f
                    faceB.scaleY = 1f
                }
                .start()
            
            faceA.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { onComplete?.invoke() }
                .start()
        }
    }

    private fun transitionStackSlide(
        faceA: View,
        faceB: View,
        showB: Boolean,
        durationMs: Long,
        onComplete: (() -> Unit)?
    ) {
        val overshoot = android.view.animation.OvershootInterpolator(1.2f)
        val dp6 = 6f * faceA.context.resources.displayMetrics.density
        
        if (showB) {
            faceB.bringToFront()
            faceB.visibility = View.VISIBLE
            faceB.translationY = dp6
            faceB.scaleX = 0.98f
            faceB.scaleY = 0.98f
            faceB.alpha = 0f
            
            // Crossfade with stack slide - both run simultaneously
            faceA.animate()
                .translationY(-dp6)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceA.visibility = View.GONE
                    faceA.translationY = 0f
                    faceA.scaleX = 1f
                    faceA.scaleY = 1f
                    faceA.alpha = 1f
                }
                .start()
            
            faceB.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(overshoot)
                .withEndAction { onComplete?.invoke() }
                .start()
        } else {
            faceA.bringToFront()
            faceA.visibility = View.VISIBLE
            faceA.translationY = dp6
            faceA.scaleX = 0.98f
            faceA.scaleY = 0.98f
            faceA.alpha = 0f
            
            // Crossfade with stack slide - both run simultaneously
            faceB.animate()
                .translationY(-dp6)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .alpha(0f)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    faceB.visibility = View.GONE
                    faceB.translationY = 0f
                    faceB.scaleX = 1f
                    faceB.scaleY = 1f
                    faceB.alpha = 1f
                }
                .start()
            
            faceA.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(overshoot)
                .withEndAction { onComplete?.invoke() }
                .start()
        }
    }
}
