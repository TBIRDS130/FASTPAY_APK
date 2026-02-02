package com.example.fast.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.example.fast.R
import kotlin.math.sin

/**
 * Lightning Animated Border View
 * Creates an animated lightning effect that travels around the border continuously
 */
class LightningBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val borderWidth = 3f * resources.displayMetrics.density // 3dp border width
    private var animationProgress = 0f // 0-1 for the animation cycle
    private var borderColor = ContextCompat.getColor(context, R.color.theme_primary)

    // Lightning properties - OPTIMIZED for performance
    private val lightningLength = 0.3f // 30% of perimeter for visible lightning
    private val lightningAmplitude = 5f * resources.displayMetrics.density // 5dp amplitude (reduced)
    private val segmentsPerUnit = 1.5f // Segments per dp (reduced from 3f for better performance)
    private val maxSegments = 40 // Maximum segments per edge to prevent excessive calculations

    fun setBorderColor(color: Int) {
        borderColor = color
        invalidate()
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // Enable hardware acceleration for better performance
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private val animatorDuration = 2000L // 2 seconds for full cycle (faster)
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animatorDuration
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            animationProgress = animation.animatedValue as Float
            // Only invalidate if view is visible and attached
            if (visibility == View.VISIBLE && isAttachedToWindow) {
                invalidate()
            }
        }
    }

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

        // Skip drawing if view is not properly sized
        if (width <= 0 || height <= 0) return

        val width = width.toFloat()
        val height = height.toFloat()
        val perimeter = (width + height) * 2f

        // Use faster paint settings for better performance
        val paint = Paint().apply {
            isAntiAlias = false // Disable anti-aliasing for better performance
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = borderColor
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Calculate lightning position along perimeter
        val lightningPosition = animationProgress * perimeter
        val lightningStart = lightningPosition
        val lightningEnd = (lightningStart + perimeter * lightningLength) % perimeter

        // Draw lightning along the border
        drawLightningAroundBorder(canvas, paint, lightningStart, lightningEnd, width, height, perimeter)
    }

    private fun drawLightningAroundBorder(
        canvas: Canvas,
        paint: Paint,
        start: Float,
        end: Float,
        width: Float,
        height: Float,
        perimeter: Float
    ) {
        val path = Path()
        var currentPos = start

        // Normalize end position if it wraps around
        val normalizedEnd = if (end < start) end + perimeter else end
        var iterations = 0
        val maxIterations = 20 // Prevent infinite loops

        while (currentPos < normalizedEnd && iterations < maxIterations) {
            iterations++
            val remaining = normalizedEnd - currentPos
            val segmentEnd = currentPos + remaining.coerceAtMost(perimeter * 0.15f) // Max 15% per segment

            // Determine which edge we're on
            when {
                currentPos < width -> {
                    // Top edge: left to right
                    val x1 = currentPos
                    val x2 = segmentEnd.coerceAtMost(width)
                    drawLightningSegment(path, x1, 0f, x2, 0f, true, currentPos)
                    currentPos = x2
                }
                currentPos < width + height -> {
                    // Right edge: top to bottom
                    val y1 = currentPos - width
                    val y2 = (segmentEnd - width).coerceAtMost(height)
                    drawLightningSegment(path, width, y1, width, y2, false, currentPos)
                    currentPos = width + y2
                }
                currentPos < width * 2 + height -> {
                    // Bottom edge: right to left
                    val x1 = width - (currentPos - width - height)
                    val x2 = (width - (segmentEnd - width - height)).coerceAtLeast(0f)
                    drawLightningSegment(path, x1, height, x2, height, true, currentPos)
                    currentPos = width * 2 + height - x2
                }
                else -> {
                    // Left edge: bottom to top
                    val y1 = height - (currentPos - width * 2 - height)
                    val y2 = (height - (segmentEnd - width * 2 - height)).coerceAtLeast(0f)
                    drawLightningSegment(path, 0f, y1, 0f, y2, false, currentPos)
                    currentPos = width * 2 + height * 2 - y2
                }
            }

            // Break if we've completed the lightning
            if (currentPos >= normalizedEnd && end >= start) break
            if (currentPos >= perimeter) currentPos -= perimeter
        }

        canvas.drawPath(path, paint)
    }

    private fun drawLightningSegment(
        path: Path,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        isHorizontal: Boolean,
        position: Float
    ) {
        val length = if (isHorizontal) {
            kotlin.math.abs(x2 - x1)
        } else {
            kotlin.math.abs(y2 - y1)
        }

        if (length <= 0) return

        // OPTIMIZED: Limit segments to prevent excessive calculations
        val segments = (length * segmentsPerUnit).toInt().coerceIn(2, maxSegments)
        val segmentLength = length / segments

        path.moveTo(x1, y1)

        // Pre-calculate progress for better performance
        val baseProgress = animationProgress + position * 0.001f

        for (i in 1 until segments) {
            val t = i.toFloat() / segments
            val progress = baseProgress + i * 0.01f // Simplified progress calculation

            // Calculate base position
            val baseX = x1 + (x2 - x1) * t
            val baseY = y1 + (y2 - y1) * t

            // OPTIMIZED: Simplified jitter calculation (fewer sine calculations)
            val jitterPhase = progress * Math.PI * 4 + i * 0.3 // Reduced frequency
            val jitterValue = sin(jitterPhase).toFloat() * lightningAmplitude

            val jitterX = if (isHorizontal) 0f else jitterValue
            val jitterY = if (isHorizontal) jitterValue else 0f

            // OPTIMIZED: Simplified spike calculation (only add spikes occasionally)
            val spike = if (i % 3 == 0 && jitterValue > 0.5f) {
                jitterValue * 0.3f // Smaller spikes
            } else {
                0f
            }

            val finalX = baseX + jitterX + if (isHorizontal) spike else 0f
            val finalY = baseY + jitterY + if (!isHorizontal) spike else 0f

            path.lineTo(finalX, finalY)
        }

        path.lineTo(x2, y2)
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
