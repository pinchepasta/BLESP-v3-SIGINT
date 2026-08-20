package com.radar.blewifi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import kotlin.math.*

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnDeviceClickListener {
        fun onDeviceClicked(device: ScanDevice)
        fun onNothingSelected()
    }

    var onDeviceClickListener: OnDeviceClickListener? = null

    enum class Theme {
        DEFAULT, HIGH_CONTRAST, RED_NIGHT, PINK, NEON, NARANJA, BUBBLEGUM, SUMMERTIME, MORIO
    }

    var theme = Theme.DEFAULT
        set(value) {
            field = value
            updateColors()
            cachedBg = null
            invalidate()
        }

    @Deprecated("Use theme property instead", ReplaceWith("theme = if (value) Theme.HIGH_CONTRAST else Theme.DEFAULT"))
    var isHighContrastMode: Boolean
        get() = theme == Theme.HIGH_CONTRAST
        set(value) {
            theme = if (value) Theme.HIGH_CONTRAST else Theme.DEFAULT
        }

    private var green       = "#00FF41".toColorInt()
    private var greenDim    = "#003B0F".toColorInt()
    private var greenMid    = "#00AA2A".toColorInt()
    private var radarBg     = Color.BLACK
    private var amber       = "#FFB300".toColorInt()
    private var cyan        = "#00FFFF".toColorInt()
    private var aircraftColor = "#00FFFF".toColorInt()
    private var lteColor    = "#FFB300".toColorInt()
    private var fiveGColor  = Color.parseColor("#4B0082") // Indigo/Dark Purple
    private var pagerColor  = Color.parseColor("#00FF41") // Green for Pager
    private var cameraColor = Color.RED
    private var pink        = Color.parseColor("#0066FF") // Blue
    private var targetPink  = Color.parseColor("#FF00FF") // Added for consistency

    // Paints
    private val bgPaint        = Paint().apply { color = radarBg; style = Paint.Style.FILL }
    private val mapPaint       = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        alpha = 60
    }
    private val ringPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = greenDim; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val sweepPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val crossPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = greenDim; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val blipPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = green; style = Paint.Style.FILL
    }
    private val blipRingPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = green; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val labelSmallPaint= Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = greenMid; textSize = 42f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val selectedPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = amber; style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val centerPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00FF") // Pink
        style = Paint.Style.FILL
    }
    private val centerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00FF")
        style = Paint.Style.FILL
    }
    private val tickPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = greenMid; textSize = 20f; typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val glowPaint      = Paint(Paint.ANTI_ALIAS_FLAG)

    // Preallocated paints for onDraw
    private val compassPaint = Paint(tickPaint).apply { textSize = 24f; color = greenMid }
    private val sweepLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(green, 200)
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val shadowPaint = Paint(labelSmallPaint).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val shadowPaintSmall = Paint(shadowPaint).apply { textSize = 26f }
    private val distPaint = Paint(labelSmallPaint).apply { textSize = 26f }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = greenMid; textSize = 23f; typeface = Typeface.MONOSPACE
    }
    private val cyanStatusPaint = Paint(statusPaint).apply { color = cyan }

    private val alarmPaint = Paint().apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 42f
        color = Color.RED
    }

    private val whisperPaint = Paint().apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 42f
        color = Color.parseColor("#FF00FF")
    }

    private val vulnPaint = Paint().apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 42f
        color = green
    }

    private val airTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 38f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val glitchPaint = Paint().apply {
        color = green
        alpha = 30
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val staticPaint = Paint().apply {
        color = Color.parseColor("#FF00FF") // Pink
        style = Paint.Style.FILL
    }


    init {
        updateColors()
    }

    private fun updateColors() {
        when (theme) {
            Theme.HIGH_CONTRAST -> {
                green = Color.BLACK
                greenDim = Color.LTGRAY
                greenMid = Color.BLACK
                radarBg = Color.WHITE
                amber = Color.BLACK
                cyan = Color.BLACK
                aircraftColor = Color.BLACK
                lteColor = Color.BLACK
                fiveGColor = Color.BLACK
                pagerColor = Color.BLACK
                pink = Color.BLACK
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.BLACK
                centerPaint.color = Color.BLACK
                centerGlowPaint.color = Color.LTGRAY
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.BLACK
                distPaint.color = Color.BLACK
                statusPaint.color = Color.BLACK
                cyanStatusPaint.color = Color.BLACK
                airTagPaint.color = Color.BLACK
                staticPaint.color = Color.LTGRAY
                glitchPaint.color = Color.LTGRAY
                alarmPaint.color = Color.BLACK
                whisperPaint.color = Color.BLACK
                vulnPaint.color = Color.BLACK
                targetPink = Color.BLACK
            }
            Theme.RED_NIGHT -> {
                green = Color.RED
                greenDim = Color.parseColor("#330000")
                greenMid = Color.parseColor("#990000")
                radarBg = Color.BLACK
                amber = Color.RED
                cyan = Color.RED
                aircraftColor = Color.RED
                lteColor = Color.RED
                fiveGColor = Color.RED
                pagerColor = Color.RED
                pink = Color.RED
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.RED
                centerPaint.color = Color.RED
                centerGlowPaint.color = Color.RED
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.RED
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.RED
                airTagPaint.color = Color.RED
                staticPaint.color = Color.RED
                glitchPaint.color = Color.RED
                alarmPaint.color = Color.RED
                whisperPaint.color = Color.RED
                vulnPaint.color = Color.RED
                targetPink = Color.RED
            }
            Theme.DEFAULT -> {
                green = "#00FF41".toColorInt()
                greenDim = "#003B0F".toColorInt()
                greenMid = "#00AA2A".toColorInt()
                radarBg = Color.BLACK
                amber = "#FFB300".toColorInt()
                cyan = "#00FFFF".toColorInt()
                aircraftColor = "#00FFFF".toColorInt()
                lteColor = "#FFB300".toColorInt()
                fiveGColor = Color.parseColor("#4B0082")
                pagerColor = Color.parseColor("#00FF41")
                pink = Color.parseColor("#0066FF")

                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = amber
                centerPaint.color = Color.parseColor("#FF00FF")
                centerGlowPaint.color = Color.parseColor("#FF00FF")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = ColorUtils.setAlphaComponent(green, 200)
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = cyan
                airTagPaint.color = Color.WHITE
                staticPaint.color = Color.parseColor("#FF00FF")
                glitchPaint.color = green
                alarmPaint.color = Color.RED
                whisperPaint.color = Color.parseColor("#FF00FF")
                vulnPaint.color = green
                targetPink = Color.parseColor("#FF00FF")
            }
            Theme.PINK -> {
                green = Color.parseColor("#FF00FF")
                greenDim = Color.parseColor("#330033")
                greenMid = Color.parseColor("#990099")
                radarBg = Color.BLACK
                amber = Color.parseColor("#FF69B4")
                cyan = Color.parseColor("#FF1493")
                aircraftColor = Color.parseColor("#FF00FF")
                lteColor = Color.parseColor("#FF00FF")
                fiveGColor = Color.parseColor("#FF00FF")
                pagerColor = Color.parseColor("#FF00FF")
                pink = Color.parseColor("#FF00FF")
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.WHITE
                centerPaint.color = Color.parseColor("#FF00FF")
                centerGlowPaint.color = Color.parseColor("#FF00FF")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.parseColor("#FF00FF")
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.parseColor("#FF00FF")
                airTagPaint.color = Color.parseColor("#FF00FF")
                staticPaint.color = Color.parseColor("#FF00FF")
                glitchPaint.color = Color.parseColor("#FF00FF")
                alarmPaint.color = Color.parseColor("#FF00FF")
                whisperPaint.color = Color.parseColor("#FF00FF")
                vulnPaint.color = Color.parseColor("#FF00FF")
                targetPink = Color.parseColor("#FF00FF")
            }
            Theme.NEON -> {
                green = Color.parseColor("#E6FB04")
                greenDim = Color.parseColor("#333801")
                greenMid = Color.parseColor("#B3C403")
                radarBg = Color.BLACK
                amber = Color.parseColor("#FFFF00")
                cyan = Color.parseColor("#E6FB04")
                aircraftColor = Color.parseColor("#E6FB04")
                lteColor = Color.parseColor("#E6FB04")
                fiveGColor = Color.parseColor("#E6FB04")
                pagerColor = Color.parseColor("#E6FB04")
                pink = Color.parseColor("#E6FB04")
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.WHITE
                centerPaint.color = Color.parseColor("#E6FB04")
                centerGlowPaint.color = Color.parseColor("#E6FB04")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.parseColor("#E6FB04")
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.parseColor("#E6FB04")
                airTagPaint.color = Color.parseColor("#E6FB04")
                staticPaint.color = Color.parseColor("#E6FB04")
                glitchPaint.color = Color.parseColor("#E6FB04")
                alarmPaint.color = Color.parseColor("#E6FB04")
                whisperPaint.color = Color.parseColor("#E6FB04")
                vulnPaint.color = Color.parseColor("#E6FB04")
                targetPink = Color.parseColor("#E6FB04")
            }
            Theme.NARANJA -> {
                green = Color.parseColor("#FF8C00")
                greenDim = Color.parseColor("#331C00")
                greenMid = Color.parseColor("#995400")
                radarBg = Color.BLACK
                amber = Color.parseColor("#FFA500")
                cyan = Color.parseColor("#FF8C00")
                aircraftColor = Color.parseColor("#FF8C00")
                lteColor = Color.parseColor("#FF8C00")
                fiveGColor = Color.parseColor("#FF8C00")
                pagerColor = Color.parseColor("#FF8C00")
                pink = Color.parseColor("#FF8C00")
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.WHITE
                centerPaint.color = Color.parseColor("#FF8C00")
                centerGlowPaint.color = Color.parseColor("#FF8C00")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.parseColor("#FF8C00")
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.parseColor("#FF8C00")
                airTagPaint.color = Color.parseColor("#FF8C00")
                staticPaint.color = Color.parseColor("#FF8C00")
                glitchPaint.color = Color.parseColor("#FF8C00")
                alarmPaint.color = Color.parseColor("#FF8C00")
                whisperPaint.color = Color.parseColor("#FF8C00")
                vulnPaint.color = Color.parseColor("#FF8C00")
                targetPink = Color.parseColor("#FF8C00")
            }
            Theme.BUBBLEGUM -> {
                green = Color.parseColor("#FF00FF") // Neon Magenta
                greenDim = Color.parseColor("#330033")
                greenMid = Color.parseColor("#00FDFF") // Turquoise
                radarBg = Color.BLACK
                amber = Color.parseColor("#00FDFF") // Turquoise accent
                cyan = Color.parseColor("#00FDFF")
                aircraftColor = Color.parseColor("#00FDFF")
                lteColor = Color.parseColor("#FF00FF")
                fiveGColor = Color.parseColor("#FF00FF")
                pagerColor = Color.parseColor("#FF00FF")
                pink = Color.parseColor("#FF00FF")
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.WHITE
                centerPaint.color = Color.parseColor("#FF00FF")
                centerGlowPaint.color = Color.parseColor("#FF00FF")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.parseColor("#FF00FF")
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.parseColor("#00FDFF")
                airTagPaint.color = Color.WHITE
                staticPaint.color = Color.parseColor("#FF00FF")
                glitchPaint.color = Color.parseColor("#00FDFF")
                alarmPaint.color = Color.parseColor("#FF00FF")
                whisperPaint.color = Color.parseColor("#FF00FF")
                vulnPaint.color = Color.parseColor("#00FDFF")
                targetPink = Color.parseColor("#FF00FF")
            }
            Theme.SUMMERTIME -> {
                green = Color.parseColor("#ff9f6b")
                greenDim = Color.parseColor("#331f15")
                greenMid = Color.parseColor("#cc7f56")
                radarBg = Color.BLACK
                amber = Color.parseColor("#6befff")
                cyan = Color.parseColor("#6befff")
                aircraftColor = Color.parseColor("#6befff")
                lteColor = Color.parseColor("#ff9f6b")
                fiveGColor = Color.parseColor("#ff9f6b")
                pagerColor = Color.parseColor("#ff9f6b")
                pink = Color.parseColor("#ff9f6b")
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.WHITE
                centerPaint.color = Color.parseColor("#ff9f6b")
                centerGlowPaint.color = Color.parseColor("#ff9f6b")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.parseColor("#ff9f6b")
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.parseColor("#6befff")
                airTagPaint.color = Color.WHITE
                staticPaint.color = Color.parseColor("#ff9f6b")
                glitchPaint.color = Color.parseColor("#6befff")
                alarmPaint.color = Color.parseColor("#ff9f6b")
                whisperPaint.color = Color.parseColor("#ff9f6b")
                vulnPaint.color = Color.parseColor("#6befff")
                targetPink = Color.parseColor("#ff9f6b")
            }
            Theme.MORIO -> {
                green = Color.parseColor("#c3ac3a")
                greenDim = Color.parseColor("#244f48")
                greenMid = Color.parseColor("#c8f29e")
                radarBg = Color.BLACK
                amber = Color.parseColor("#c8f29e")
                cyan = Color.parseColor("#c8f29e")
                aircraftColor = Color.parseColor("#c8f29e")
                lteColor = Color.parseColor("#c3ac3a")
                fiveGColor = Color.parseColor("#c3ac3a")
                pagerColor = Color.parseColor("#c3ac3a")
                pink = Color.parseColor("#c8f29e")
                
                bgPaint.color = radarBg
                ringPaint.color = greenDim
                crossPaint.color = greenDim
                blipPaint.color = green
                blipRingPaint.color = green
                labelSmallPaint.color = greenMid
                selectedPaint.color = Color.WHITE
                centerPaint.color = Color.parseColor("#c3ac3a")
                centerGlowPaint.color = Color.parseColor("#c3ac3a")
                tickPaint.color = greenMid
                compassPaint.color = greenMid
                sweepLinePaint.color = Color.parseColor("#c3ac3a")
                distPaint.color = greenMid
                statusPaint.color = greenMid
                cyanStatusPaint.color = Color.parseColor("#c8f29e")
                airTagPaint.color = Color.WHITE
                staticPaint.color = Color.parseColor("#c3ac3a")
                glitchPaint.color = Color.parseColor("#c8f29e")
                alarmPaint.color = Color.parseColor("#c8f29e")
                whisperPaint.color = Color.parseColor("#c3ac3a")
                vulnPaint.color = Color.parseColor("#c8f29e")
                targetPink = Color.parseColor("#c8f29e")
            }
        }
        buildSweepShader(width / 2f, height / 2f)
    }

    private fun buildSweepShader(cx: Float, cy: Float) {
        val sweepColor = when (theme) {
            Theme.HIGH_CONTRAST -> Color.BLACK
            Theme.RED_NIGHT -> Color.RED
            Theme.PINK -> Color.parseColor("#FF00FF")
            Theme.NEON -> Color.parseColor("#E6FB04")
            Theme.NARANJA -> Color.parseColor("#FF8C00")
            Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            Theme.MORIO -> Color.parseColor("#c3ac3a")
            Theme.DEFAULT -> green
        }
        val maxAlpha = when (theme) {
            Theme.HIGH_CONTRAST -> 30
            Theme.RED_NIGHT -> 40
            Theme.PINK -> 60
            Theme.NEON -> 50
            Theme.NARANJA -> 50
            Theme.BUBBLEGUM -> 60
            Theme.SUMMERTIME -> 50
            Theme.MORIO -> 50
            Theme.DEFAULT -> 50
        }
        
        sweepPaint.shader = SweepGradient(cx, cy, intArrayOf(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            ColorUtils.setAlphaComponent(sweepColor, 0),
            ColorUtils.setAlphaComponent(sweepColor, maxAlpha / 2),
            ColorUtils.setAlphaComponent(sweepColor, maxAlpha),
            ColorUtils.setAlphaComponent(sweepColor, 0)
        ), floatArrayOf(0f, 0.5f, 0.7f, 0.85f, 1.0f, 1.0f))
    }

    // Overlay effects
    private val grainPaint = Paint().apply {
        color = Color.WHITE
        alpha = 12
    }
    private val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 25
        style = Paint.Style.FILL
    }
    private var scanlineShader: BitmapShader? = null
    
    private val random = java.util.Random()
    private var lastGlitchTime = 0L
    private var isGlitching = false
    private var glitchShift = 0f

    // Sweep animation
    private var sweepAngle = 0f
    private val sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 5000 // Slower (from 3000)
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            sweepAngle = it.animatedValue as Float
            invalidate()
        }
    }

    // Blip pulse animation
    private var pulseRadius = 0f
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { pulseRadius = it.animatedValue as Float; invalidate() }
    }

    private var blinkAlpha = 255
    private val blinkAnimator = ValueAnimator.ofInt(0, 255).apply {
        duration = 500
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { blinkAlpha = it.animatedValue as Int; invalidate() }
    }

    private var centerBlinkAlpha = 255
    private val centerBlinkAnimator = ValueAnimator.ofInt(0, 255).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { centerBlinkAlpha = it.animatedValue as Int; invalidate() }
    }

    // Device list and positions
    private val devices = mutableListOf<ScanDevice>()
    private val sortedDevices = mutableListOf<ScanDevice>()
    private val devicePositions = mutableMapOf<String, PointF>()
    var selectedId: String? = null

    private var bleCountCache = 0
    private var wifiCountCache = 0
    
    private val currentIds = HashSet<String>()
    private var blipPointsBuffer = FloatArray(2000) // Support up to 1000 points per batch

    var showBle = true
        set(value) { field = value; invalidate() }
    var showWifi = true
        set(value) { field = value; invalidate() }
    var show5g = true
        set(value) { field = value; invalidate() }
    var showLte = true
        set(value) { field = value; invalidate() }
    var showAero = true
        set(value) { field = value; invalidate() }
    var showCams = true
        set(value) { field = value; invalidate() }
    var showMap = false
        set(value) { 
            field = value
            cachedBg = null // Invalidate cache
            invalidate() 
        }

    private var userLat: Double? = null
    private var userLon: Double? = null
    var rotationDegrees = 0f
        set(value) { field = value; invalidate() }

    fun setUserLocation(lat: Double, lon: Double) {
        userLat = lat
        userLon = lon
        invalidate()
    }

    override fun setRotation(degrees: Float) {
        rotationDegrees = degrees
        invalidate()
    }

    // Pinch-to-zoom support
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            // Zooming in (fingers apart, scale > 1) decreases maxDistance
            maxDistance /= scaleFactor
            maxDistance = maxDistance.coerceIn(5.0, 500.0)
            assignPositions()
            invalidate()
            return true
        }
    })

    // Max distance shown on radar (metres)
    var maxDistance = 30.0

    fun setDevices(list: List<ScanDevice>) {
        devices.clear()
        devices.addAll(list)
        
        var bc = 0
        var wc = 0
        list.forEach { d ->
            if (d.type == DeviceType.BLE || d.type == DeviceType.PAGER) bc++
            else if (d.type == DeviceType.WIFI) wc++
        }
        bleCountCache = bc
        wifiCountCache = wc

        sortedDevices.clear()
        sortedDevices.addAll(list.sortedBy { it.id })
        assignPositions()
        invalidate()
    }

    private fun assignPositions() {
        if (width <= 0 || height <= 0) return
        
        val cx = width / 2f
        val cy = height / 2f
        val r  = min(cx, cy) * 0.97f
        
        val deviceCount = devices.size
        
        // Throttling position updates for non-selected devices in high density
        val skipModulo = when {
            deviceCount > 500 -> 10
            deviceCount > 200 -> 5
            else -> 1
        }
        
        // Optimize: Use a pre-sized HashSet to avoid repeated allocations if possible
        val currentIds = HashSet<String>(devices.size)
        devices.forEach { currentIds.add(it.id) }
        
        devices.forEachIndexed { index, d ->
            val isSelected = d.id == selectedId
            if (!isSelected && skipModulo > 1 && (index + drawCount) % skipModulo != 0) {
                return@forEachIndexed
            }
            
            var pos = devicePositions[d.id]
            
            if (pos == null && d.type != DeviceType.AIRCRAFT && d.type != DeviceType.CAMERA) {
                // First time: assign stable angle from id hash
                val seed = when(d.type) {
                    DeviceType.WIFI -> 0xABC
                    DeviceType.BLE -> 0xDEF
                    DeviceType.LTE -> 0x123
                    DeviceType.FIVE_G -> 0x456
                    DeviceType.CAR -> 0x789
                    DeviceType.ESCOOTER -> 0x987
                    DeviceType.TV -> 0x246
                    DeviceType.COMPUTER -> 0x135
                    DeviceType.SMARTPHONE -> 0x579
                    DeviceType.PAGER -> 0xAAA
                    DeviceType.CAMERA -> 0xBBB
                    DeviceType.AIRCRAFT, DeviceType.DRONE -> 0x000
                }
                d.angle = (((d.id.hashCode() xor seed) and 0x7FFFFFFF) % 360).toDouble()
            }

            val currentMaxDist = if (d.type == DeviceType.AIRCRAFT) 50000.0 else maxDistance
            val dist = d.distanceMeters.coerceIn(0.1, currentMaxDist)
            val normDist = (dist / currentMaxDist).pow(0.55).coerceIn(0.05, 0.95)
            val rad = Math.toRadians(d.angle - 90.0)
            
            val newX = cx + (r * normDist * cos(rad)).toFloat()
            val newY = cy + (r * normDist * sin(rad)).toFloat()
            
            if (pos == null) {
                pos = PointF(newX, newY)
                devicePositions[d.id] = pos
            } else {
                pos.set(newX, newY)
            }
        }
        
        // Remove stale using the set
        if (devicePositions.size > currentIds.size) {
            val iterator = devicePositions.entries.iterator()
            while (iterator.hasNext()) {
                if (!currentIds.contains(iterator.next().key)) {
                    iterator.remove()
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildSweepShader(w / 2f, h / 2f)
        buildScanlineShader()
        assignPositions()
    }

    private fun buildScanlineShader() {
        val bitmap = Bitmap.createBitmap(1, 8, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint().apply { color = Color.BLACK; alpha = 25 }
        canvas.drawPoint(0f, 0f, p)
        scanlineShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        scanlinePaint.shader = scanlineShader
    }

    private var cachedBg: Bitmap? = null
    private fun buildStaticCache() {
        if (width <= 0 || height <= 0) return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = height / 2f
        val r  = min(cx, cy) * 0.97f

        // Background
        if (!showMap) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // Range rings
        for (i in 1..4) {
            canvas.drawCircle(cx, cy, r * i / 4f, ringPaint)
        }

        // Crosshairs
        canvas.drawLine(cx - r, cy, cx + r, cy, crossPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, crossPaint)
        val d45 = (r * cos(Math.toRadians(45.0))).toFloat()
        canvas.drawLine(cx - d45, cy - d45, cx + d45, cy + d45, crossPaint)
        canvas.drawLine(cx + d45, cy - d45, cx - d45, cy + d45, crossPaint)
        
        cachedBg = bitmap
    }

    private var drawCount = 0
    private val DRAW_SKIP_THRESHOLD = 2 // Skip 1 in 2 frames if device count is high
    
    private var dirtyRect: RectF? = null
    private var lastRenderedBlips: MutableMap<String, PointF> = mutableMapOf()
    private val pointPool = PointF()

    override fun onDraw(canvas: Canvas) {
        // Ensure black background for Summertime and Morio themes
        if (theme == Theme.SUMMERTIME || theme == Theme.MORIO) {
            canvas.drawColor(Color.BLACK)
        }
        super.onDraw(canvas)
        
        val deviceCount = devices.size
        
        // Dynamic frame skipping based on device count
        val skipThreshold = when {
            deviceCount > 500 -> 4 // Draw every 4th frame
            deviceCount > 300 -> 3 // Draw every 3rd frame
            deviceCount > 150 -> 2 // Draw every 2nd frame
            else -> 1
        }
        
        if (skipThreshold > 1) {
            drawCount++
            if (drawCount % skipThreshold != 0) return
        }

        if (cachedBg == null || cachedBg?.width != width || cachedBg?.height != height) {
            buildStaticCache()
        }

        val cx = width / 2f
        val cy = height / 2f
        val r  = min(cx, cy) * 0.97f

        canvas.save()
        canvas.rotate(rotationDegrees + 180f, cx, cy)

        cachedBg?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // Sweep
        canvas.withRotation(sweepAngle, cx, cy) {
            drawCircle(cx, cy, r, sweepPaint)
        }

        // Sweep line
        val sweepRad = Math.toRadians(sweepAngle.toDouble())
        canvas.drawLine(cx, cy, (cx + r * cos(sweepRad)).toFloat(), (cy + r * sin(sweepRad)).toFloat(), sweepLinePaint)

        // Center dot with submarine radar pulsate effect
        val time = System.currentTimeMillis()
        val cycle = time % 1500
        val pulseScale = if (cycle < 500) cycle / 500f else 1f 
        
        if (cycle < 500 && deviceCount < 300) { // Only show ping ripple if density is low
            val pingRadius = 6f + (pulseScale * 40f)
            val pingAlpha = (180 * (1f - pulseScale)).toInt()
            centerGlowPaint.color = targetPink
            centerGlowPaint.alpha = pingAlpha
            canvas.drawCircle(cx, cy, pingRadius, centerGlowPaint)
        }
        
        val isHighContrast = theme == Theme.HIGH_CONTRAST
        centerPaint.color = if (isHighContrast) {
            ColorUtils.blendARGB(Color.parseColor("#FF00FF"), Color.WHITE, centerBlinkAlpha / 255f)
        } else {
            targetPink
        }
        canvas.drawCircle(cx, cy, 6f, centerPaint)

        // Blips
        val isHighDensity = deviceCount > 250

        if (deviceCount > 500) {
            renderHighDensityBlips(canvas)
        } else {
            renderStandardBlips(canvas, isHighDensity, isHighContrast)
        }
        
        canvas.restore()
        drawOverlays(canvas)
        drawStatusBar(canvas)
    }

    private fun renderHighDensityBlips(canvas: Canvas) {
        // Group by type to minimize paint changes
        val grouped = sortedDevices.groupBy { it.type }
        
        grouped.forEach { (type, deviceList) ->
            val color = when (type) {
                DeviceType.WIFI -> green
                DeviceType.BLE -> pink
                DeviceType.LTE -> lteColor
                DeviceType.FIVE_G -> fiveGColor
                DeviceType.PAGER -> pagerColor
                DeviceType.AIRCRAFT, DeviceType.DRONE -> aircraftColor
                else -> targetPink
            }
            
            blipPaint.color = ColorUtils.setAlphaComponent(color, 180)
            blipPaint.strokeWidth = 4f
            
            var pointIdx = 0
            if (blipPointsBuffer.size < deviceList.size * 2) {
                blipPointsBuffer = FloatArray(deviceList.size * 2)
            }
            
            deviceList.forEach { device ->
                if (!shouldShow(device.type)) return@forEach
                val pos = devicePositions[device.id] ?: return@forEach
                blipPointsBuffer[pointIdx++] = pos.x
                blipPointsBuffer[pointIdx++] = pos.y
            }
            
            if (pointIdx > 0) {
                canvas.drawPoints(blipPointsBuffer, 0, pointIdx, blipPaint)
            }
        }
        
        // Always render selected device on top with full detail
        selectedId?.let { id ->
            devices.find { it.id == id }?.let { renderSingleBlip(canvas, it, true, theme == Theme.HIGH_CONTRAST) }
        }
    }

    private fun renderStandardBlips(canvas: Canvas, isHighDensity: Boolean, isHighContrast: Boolean) {
        for (i in 0 until sortedDevices.size) {
            val device = sortedDevices[i]
            if (!shouldShow(device.type)) continue
            val isSelected = device.id == selectedId
            renderSingleBlip(canvas, device, isSelected, isHighContrast, isHighDensity)
        }
    }

    private fun shouldShow(type: DeviceType): Boolean {
        return when (type) {
            DeviceType.BLE, DeviceType.PAGER -> showBle
            DeviceType.WIFI -> showWifi
            DeviceType.FIVE_G -> show5g
            DeviceType.LTE -> showLte
            DeviceType.AIRCRAFT, DeviceType.DRONE -> showAero
            DeviceType.CAMERA -> showCams
            else -> true
        }
    }

    private fun renderSingleBlip(canvas: Canvas, device: ScanDevice, isSelected: Boolean, isHighContrast: Boolean, isHighDensity: Boolean = false) {
        val pos = devicePositions[device.id] ?: return
        
        val blipColor = when (device.type) {
            DeviceType.WIFI -> green
            DeviceType.BLE -> pink
            DeviceType.LTE -> lteColor
            DeviceType.FIVE_G -> fiveGColor
            DeviceType.PAGER -> pagerColor
            DeviceType.AIRCRAFT, DeviceType.DRONE -> aircraftColor
            DeviceType.CAMERA -> cameraColor
            else -> targetPink
        }

        if (isHighDensity && !isSelected) {
            val lastPos = lastRenderedBlips[device.id]
            if (lastPos != null && abs(pos.x - lastPos.x) < 0.5f && abs(pos.y - lastPos.y) < 0.5f) {
                blipPaint.color = ColorUtils.setAlphaComponent(blipColor, 120)
                canvas.drawPoint(pos.x, pos.y, blipPaint)
                return
            }
        }
        
        val savedPos = lastRenderedBlips[device.id]
        if (savedPos != null) {
            savedPos.set(pos.x, pos.y)
        } else {
            lastRenderedBlips[device.id] = PointF(pos.x, pos.y)
        }

        val alpha = if (isSelected) 255 else 210
        blipPaint.color = ColorUtils.setAlphaComponent(blipColor, alpha)
        blipRingPaint.color = ColorUtils.setAlphaComponent(blipColor, alpha)
        val blipR = if (isSelected) 10f else 7f

        if (isSelected) {
            val pr = blipR + 6f + pulseRadius * 20f
            val pa = (255 * (1f - pulseRadius)).toInt()
            val selectionColor = when (theme) {
                Theme.RED_NIGHT -> Color.RED
                Theme.HIGH_CONTRAST -> Color.BLACK
                Theme.PINK -> Color.parseColor("#FF00FF")
                Theme.NEON -> Color.parseColor("#E6FB04")
                Theme.NARANJA -> Color.parseColor("#FF8C00")
                Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
                Theme.SUMMERTIME -> cyan
                else -> targetPink
            }
            selectedPaint.color = ColorUtils.setAlphaComponent(selectionColor, pa)
            canvas.drawCircle(pos.x, pos.y, pr, selectedPaint)
            selectedPaint.color = selectionColor
            canvas.drawCircle(pos.x, pos.y, blipR + 4f, selectedPaint)

            // Draw SSID/MAC label for selected device in theme color
            val label = device.displayName
            val oldColor = distPaint.color
            distPaint.color = selectionColor
            canvas.drawText(label, pos.x + blipR + 10f, pos.y + 8f, shadowPaintSmall)
            canvas.drawText(label, pos.x + blipR + 10f, pos.y + 8f, distPaint)
            distPaint.color = oldColor
        }

        if (!isHighDensity || isSelected) {
            glowPaint.color = ColorUtils.setAlphaComponent(blipColor, 40)
            glowPaint.style = Paint.Style.FILL
            canvas.drawCircle(pos.x, pos.y, blipR * 3f, glowPaint)
        }

        val blinkColor = when {
            device.isCar -> when(theme) {
                Theme.RED_NIGHT -> Color.RED
                Theme.HIGH_CONTRAST -> Color.BLACK
                Theme.SUMMERTIME -> cyan
                else -> targetPink
            }
            device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP") -> when(theme) {
                Theme.RED_NIGHT -> Color.RED
                Theme.HIGH_CONTRAST -> Color.BLACK
                Theme.SUMMERTIME -> cyan
                else -> targetPink
            }
            device.isAirTag -> when(theme) {
                Theme.RED_NIGHT -> Color.BLACK
                Theme.HIGH_CONTRAST -> Color.BLACK
                else -> Color.WHITE
            }
            else -> blipColor
        }
        
        if (blinkColor != blipColor) {
            blipPaint.color = ColorUtils.blendARGB(blipColor, blinkColor, blinkAlpha / 255f)
        }
        
        canvas.drawCircle(pos.x, pos.y, blipR, blipPaint)
        if (!isHighDensity || isSelected) {
            canvas.drawCircle(pos.x, pos.y, blipR, blipRingPaint)
        }
    }

    private fun drawOverlays(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Static Scanlines - Optimized with BitmapShader
        canvas.drawRect(0f, 0f, w, h, scanlinePaint)

        // 2. Glitches (Occasional) - Optimized to only draw when active
        val now = System.currentTimeMillis()
        if (now - lastGlitchTime > 3000 + random.nextInt(5000)) {
            isGlitching = true
            lastGlitchTime = now
            glitchShift = (random.nextFloat() - 0.5f) * 40f
        }
        
        if (isGlitching && now - lastGlitchTime < 100) {
            // Draw a horizontal slice shifted
            val sliceH = 20f + random.nextFloat() * 100f
            val sliceY = random.nextFloat() * (h - sliceH)
            
            glitchPaint.alpha = 30 + random.nextInt(30)
            canvas.drawRect(0f, sliceY, w, sliceY + sliceH, glitchPaint)
            
            // Random horizontal line
            canvas.drawLine(0f, sliceY + sliceH/2, w, sliceY + sliceH/2, glitchPaint)

            // 3. Pink Static (Occasional Overlay) - Reduced density
            if (random.nextInt(5) == 0) {
                for (i in 0 until 15) {
                    val sx = random.nextFloat() * w
                    val sy_ = random.nextFloat() * h
                    val sw = 2f + random.nextFloat() * 6f
                    val sh = 1f + random.nextFloat() * 3f
                    staticPaint.alpha = 40 + random.nextInt(80)
                    canvas.drawRect(sx, sy_, sx + sw, sy_ + sh, staticPaint)
                }
            }
        } else {
            isGlitching = false
        }
    }

    private fun drawStatusBar(canvas: Canvas) {
        // BLE Wifi text removed as per user request
        
        if (showMap && userLat != null) {
            val color1 = if (theme == Theme.RED_NIGHT) Color.RED else statusPaint.color
            statusPaint.color = color1
            canvas.drawText("GPS: %.5f, %.5f".format(userLat, userLon), 260f, 36f, statusPaint)
        }
        
        val color1 = if (theme == Theme.RED_NIGHT) Color.RED else statusPaint.color
        statusPaint.color = color1
    }

    private fun drawMapOverlay(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Since we don't have a real map tiling system here, we'll draw a schematic grid
        // representing the coordinates to simulate a map overlay.
        // In a real app, you'd use Google Maps Static API or a tile provider.
        
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            alpha = 40
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        
        // Draw some "streets" based on GPS coordinates
        val step = r / 5
        for (i in -10..10) {
            val offset = i * step
            canvas.drawLine(cx - r, cy + offset, cx + r, cy + offset, gridPaint)
            canvas.drawLine(cx + offset, cy - r, cx + offset, cy + r, gridPaint)
        }
        
        // Small "MAP ACTIVE" indicator
        val mapLabelPaint = Paint(statusPaint).apply { color = Color.GRAY; textSize = 18f }
        canvas.drawText("SCHEMATIC MAP OVERLAY", cx - 100f, cy + r - 20f, mapLabelPaint)
    }

    // Touch handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        if (event.action == MotionEvent.ACTION_UP) {
            val cx = width / 2f
            val cy = height / 2f

            // Account for canvas rotation: rotate touch point backwards to match blip coordinate space
            // Rotation is rotationDegrees + 180, so we rotate back by -(rotationDegrees + 180)
            val angleRad = Math.toRadians(-(rotationDegrees + 180f).toDouble())
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()

            val relX = event.x - cx
            val relY = event.y - cy

            val tx = cx + (relX * cosA - relY * sinA)
            val ty = cy + (relX * sinA + relY * cosA)

            var best: ScanDevice? = null
            var bestDist = Float.MAX_VALUE

            // Only consider devices currently being shown (respect filters)
            for (d in devices) {
                if (d.type == DeviceType.BLE && !showBle) continue
                if (d.type == DeviceType.PAGER && !showBle) continue
                if (d.type == DeviceType.WIFI && !showWifi) continue
                if (d.type == DeviceType.FIVE_G && !show5g) continue
                if (d.type == DeviceType.LTE && !showLte) continue
                if (d.type == DeviceType.AIRCRAFT && !showAero) continue

                val p = devicePositions[d.id] ?: continue
                val dist = hypot(tx - p.x, ty - p.y)

                // 80f radius for easier clicking on all devices
                if (dist < 80f && dist < bestDist) {
                    best = d
                    bestDist = dist
                }
            }

            if (best != null) {
                performClick()
                selectedId = best.id
                pulseAnimator.start()
                onDeviceClickListener?.onDeviceClicked(best)
                invalidate()
                return true
            } else {
                selectedId = null
                pulseAnimator.cancel()
                onDeviceClickListener?.onNothingSelected()
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun startAnimation() { sweepAnimator.start(); pulseAnimator.start(); blinkAnimator.start(); centerBlinkAnimator.start() }
    fun stopAnimation()  { sweepAnimator.cancel(); pulseAnimator.cancel(); blinkAnimator.cancel(); centerBlinkAnimator.cancel() }
}
