package com.radar.blewifi

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class MiniGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cpuPoints = mutableListOf<Float>()
    private val gpuPoints = mutableListOf<Float>()
    private var maxPoints = 30 // Fewer points for thicker EQ bars

    private val cpuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF41")
        style = Paint.Style.FILL
    }

    private val gpuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FF00FF") // Semi-transparent pink
        style = Paint.Style.FILL
    }

    private var primaryColorStr = "#00FF41"
    private var secondaryColorStr = "#99FF00FF"

    fun setColors(primary: String, secondary: String) {
        primaryColorStr = primary
        secondaryColorStr = secondary
        cpuPaint.color = Color.parseColor(primary)
        gpuPaint.color = Color.parseColor(secondary)
        invalidate()
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF41")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val clipPath = Path()
    private val borderRect = RectF()
    private val cornerRadius = 15f

    var isHighContrastMode: Boolean = false
        set(value) {
            field = value
            cpuPaint.color = if (value) Color.BLACK else Color.parseColor(primaryColorStr)
            gpuPaint.color = if (value) Color.LTGRAY else Color.parseColor(secondaryColorStr)
            borderPaint.color = if (value) Color.BLACK else Color.parseColor("#00FF41")
            invalidate()
        }

    fun addData(cpuLoad: Float, gpuLoad: Float) {
        cpuPoints.add(cpuLoad.coerceIn(0f, 100f))
        gpuPoints.add(gpuLoad.coerceIn(0f, 100f))
        if (cpuPoints.size > maxPoints) cpuPoints.removeAt(0)
        if (gpuPoints.size > maxPoints) gpuPoints.removeAt(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        borderRect.set(2f, 2f, width.toFloat() - 2f, height.toFloat() - 2f)
        
        // Draw rounded border box
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        clipPath.reset()
        clipPath.addRoundRect(borderRect, cornerRadius, cornerRadius, Path.Direction.CW)
        
        canvas.save()
        canvas.clipPath(clipPath)

        if (cpuPoints.isNotEmpty()) {
            drawEqBars(canvas, cpuPoints, cpuPaint)
            drawEqBars(canvas, gpuPoints, gpuPaint)
        }
        
        canvas.restore()
    }

    private fun drawEqBars(canvas: Canvas, points: List<Float>, paint: Paint) {
        val w = width.toFloat()
        val h = height.toFloat()
        val barWidth = w / maxPoints
        val gap = 2f
        
        points.forEachIndexed { i, load ->
            val x = i * barWidth
            val barHeight = (load / 100f) * (h - 4f)
            
            // Draw jumping bar with segmented look
            val segmentHeight = 6f
            val segmentGap = 2f
            var currentY = h - 2f
            
            while (currentY > h - 2f - barHeight) {
                val rectTop = (currentY - segmentHeight).coerceAtLeast(h - 2f - barHeight)
                canvas.drawRect(x + gap, rectTop, x + barWidth - gap, currentY, paint)
                currentY -= (segmentHeight + segmentGap)
            }
        }
    }
}
