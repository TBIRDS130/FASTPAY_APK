package com.example.fast.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.fast.R

/**
 * Animated horizontal line that moves across the view
 * Supports reverse direction for opposite movement
 */
class AnimatedLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lineColor = ContextCompat.getColor(context, R.color.theme_primary)
    private var lineWidth = 2f * resources.displayMetrics.density // 2dp
    private var reverseDirection = false
    private var animationProgress = 0f // 0-1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = lineWidth
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000L // 3 seconds for full cycle (matching HTML)
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            animationProgress = animation.animatedValue as Float
            invalidate()
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun setLineColor(color: Int) {
        lineColor = color
        paint.color = color
        invalidate()
    }

    fun setLineWidth(widthDp: Float) {
        lineWidth = widthDp * resources.displayMetrics.density
        paint.strokeWidth = lineWidth
        invalidate()
    }

    fun setReverseDirection(reverse: Boolean) {
        reverseDirection = reverse
    }

    fun setAnimationDuration(durationMs: Long) {
        val wasRunning = animator.isRunning
        animator.duration = durationMs
        if (wasRunning) {
            animator.cancel()
            animator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerY = height / 2f
        val width = width.toFloat()
        
        // Calculate position based on animation progress
        // Line moves from left to right (or right to left if reversed)
        val progress = if (reverseDirection) 1f - animationProgress else animationProgress
        
        // Start position: -width (off-screen left) to width (off-screen right)
        val startX = -width + (progress * width * 2f)
        val endX = startX + width
        
        // Draw the line
        canvas.drawLine(startX, centerY, endX, centerY, paint)
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
