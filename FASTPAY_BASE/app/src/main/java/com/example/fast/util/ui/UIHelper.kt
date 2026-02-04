package com.example.fast.util.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.CycleInterpolator
import android.view.animation.OvershootInterpolator
import com.google.android.material.snackbar.Snackbar

/**
 * UIHelper - Centralized UI utilities for better UX
 * 
 * Features:
 * - Button press animations (scale effect)
 * - Shake animation for errors
 * - Haptic feedback
 * - Clipboard operations
 * - Snackbar helpers
 */
object UIHelper {

    // Animation durations
    private const val PRESS_DURATION_MS = 100L
    private const val SHAKE_DURATION_MS = 500L
    private const val SCALE_DOWN = 0.95f
    private const val SCALE_NORMAL = 1.0f

    /**
     * Add press animation to a view (scale down on press, scale up on release)
     * Makes buttons feel more interactive and responsive
     */
    fun addPressAnimation(view: View, scaleFactor: Float = SCALE_DOWN) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleFactor)
                        .scaleY(scaleFactor)
                        .setDuration(PRESS_DURATION_MS)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(SCALE_NORMAL)
                        .scaleY(SCALE_NORMAL)
                        .setDuration(PRESS_DURATION_MS)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                }
            }
            false // Don't consume the event, let onClick work
        }
    }

    /**
     * Apply press animation to multiple views
     */
    fun addPressAnimationToViews(vararg views: View, scaleFactor: Float = SCALE_DOWN) {
        views.forEach { addPressAnimation(it, scaleFactor) }
    }

    /**
     * Shake animation for error indication
     * Shakes the view horizontally to indicate invalid input
     */
    fun shakeView(view: View, intensity: Float = 10f, duration: Long = SHAKE_DURATION_MS) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, intensity, -intensity, intensity, -intensity, intensity, -intensity, 0f)
        shake.duration = duration
        shake.interpolator = CycleInterpolator(1f)
        shake.start()
    }

    /**
     * Pulse animation - scale up briefly then back to normal
     * Good for success feedback or drawing attention
     */
    fun pulseView(view: View, scaleFactor: Float = 1.1f, duration: Long = 200L) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, scaleFactor, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, scaleFactor, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = duration
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    /**
     * Pop-in animation - view appears with scale + fade
     */
    fun popIn(view: View, duration: Long = 300L, onComplete: (() -> Unit)? = null) {
        view.alpha = 0f
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.visibility = View.VISIBLE
        
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setInterpolator(OvershootInterpolator(1.5f))
            .withEndAction { onComplete?.invoke() }
            .start()
    }

    /**
     * Pop-out animation - view disappears with scale + fade
     */
    fun popOut(view: View, duration: Long = 200L, onComplete: (() -> Unit)? = null) {
        view.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                // Reset for next show
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                onComplete?.invoke()
            }
            .start()
    }

    // ========== HAPTIC FEEDBACK ==========

    /**
     * Provide haptic feedback - light tap
     */
    fun hapticTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Provide haptic feedback - strong click
     */
    fun hapticClick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Provide haptic feedback - error/reject
     */
    fun hapticError(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Vibrate the device (for error feedback when no view is available)
     */
    fun vibrate(context: Context, durationMs: Long = 100L) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /**
     * Vibrate pattern for error (two short pulses)
     */
    fun vibrateError(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
        }
    }

    // ========== CLIPBOARD ==========

    /**
     * Get text from clipboard
     */
    fun getClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    /**
     * Check if clipboard has text
     */
    fun hasClipboardText(context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.hasPrimaryClip() && 
               clipboard.primaryClip?.getItemAt(0)?.text?.isNotEmpty() == true
    }

    /**
     * Copy text to clipboard
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Copied text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    // ========== SNACKBAR ==========

    /**
     * Show a simple snackbar message
     */
    fun showSnackbar(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(view, message, duration).show()
    }

    /**
     * Show an error snackbar (longer duration, red background)
     */
    fun showErrorSnackbar(view: View, message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
        }
        snackbar.show()
    }

    /**
     * Show a snackbar with retry action
     */
    fun showRetrySnackbar(view: View, message: String, onRetry: () -> Unit) {
        Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
            .setAction("Retry") { onRetry() }
            .show()
    }

    /**
     * Show success snackbar (short duration)
     */
    fun showSuccessSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
    }

    // ========== INPUT HELPERS ==========

    /**
     * Format phone number for display (adds dashes)
     * e.g., "1234567890" -> "123-456-7890"
     */
    fun formatPhoneDisplay(input: String): String {
        val digits = input.filter { it.isDigit() }
        return when {
            digits.length <= 3 -> digits
            digits.length <= 6 -> "${digits.take(3)}-${digits.drop(3)}"
            digits.length <= 10 -> "${digits.take(3)}-${digits.substring(3, 6)}-${digits.drop(6)}"
            else -> "${digits.take(3)}-${digits.substring(3, 6)}-${digits.drop(6).take(4)}"
        }
    }

    /**
     * Format activation code for display
     * e.g., "ABCDE12345" -> "ABCDE-12345" (5 letters + 5 digits)
     */
    fun formatActivationCode(input: String): String {
        val clean = input.uppercase().filter { it.isLetterOrDigit() }
        return when {
            clean.length <= 5 -> clean
            else -> "${clean.take(5)}-${clean.drop(5).take(5)}"
        }
    }

    /**
     * Extract digits only from formatted string
     */
    fun extractDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    /**
     * Extract alphanumeric only from formatted string
     */
    fun extractAlphanumeric(input: String): String {
        return input.filter { it.isLetterOrDigit() }
    }
}
