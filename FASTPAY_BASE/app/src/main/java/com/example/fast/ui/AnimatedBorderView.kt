package com.example.fast.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.fast.R

/**
 * Corner Border Animation View
 * Implements Example 4: Corner Border Animation
 * Two corners (top-left and bottom-right) expand and contract with animation
 */
class AnimatedBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Corner border dimensions
    private val cornerBorderWidth = 2f * resources.displayMetrics.density // 2px border width
    private val cornerMinSize = 20f * resources.displayMetrics.density // 20dp minimum size
    private val cornerMaxSize = 40f * resources.displayMetrics.density // 40dp maximum size
    private var animationProgress = 0f // 0-1 for the animation cycle
    
    // Dynamic color support
    private var borderColor = ContextCompat.getColor(context, R.color.theme_primary)
    
    fun setBorderColor(color: Int) {
        borderColor = color
        invalidate()
    }

    init {
        // Check for corner radius attribute (for compatibility, but not used for corner animation)
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.AnimatedBorderView)
            typedArray.recycle()
        }
        
        // Set transparent background so it doesn't block content behind it
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private val animatorDuration = 3000L // 3 seconds for full cycle (matching HTML)
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animatorDuration
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator() // Ease-in-out for smooth animation
        addUpdateListener { animation ->
            animationProgress = animation.animatedValue as Float
            invalidate()
        }
    }
    
    /**
     * Set animation duration (speed)
     * @param durationMs Duration in milliseconds (lower = faster)
     */
    fun setAnimationDuration(durationMs: Long) {
        if (animator.isRunning) {
            animator.duration = durationMs
            animator.cancel()
            animator.start()
        } else {
            animator.duration = durationMs
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Calculate current corner size based on animation progress
        // Animation: 0% = minSize, 50% = maxSize, 100% = minSize (sine wave pattern)
        val cornerSize = when {
            animationProgress <= 0.5f -> {
                // First half: expand from min to max
                val progress = animationProgress * 2f // 0 to 1
                cornerMinSize + (cornerMaxSize - cornerMinSize) * progress
            }
            else -> {
                // Second half: contract from max to min
                val progress = (animationProgress - 0.5f) * 2f // 0 to 1
                cornerMaxSize - (cornerMaxSize - cornerMinSize) * progress
            }
        }
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = cornerBorderWidth
            color = borderColor
            strokeCap = Paint.Cap.SQUARE
        }
        
        // Draw top-left corner (only top and left borders)
        // Top border
        canvas.drawLine(0f, 0f, cornerSize, 0f, paint)
        // Left border
        canvas.drawLine(0f, 0f, 0f, cornerSize, paint)
        
        // Draw bottom-right corner (only bottom and right borders)
        val bottomRightX = width.toFloat()
        val bottomRightY = height.toFloat()
        // Right border
        canvas.drawLine(bottomRightX, bottomRightY - cornerSize, bottomRightX, bottomRightY, paint)
        // Bottom border
        canvas.drawLine(bottomRightX - cornerSize, bottomRightY, bottomRightX, bottomRightY, paint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}

