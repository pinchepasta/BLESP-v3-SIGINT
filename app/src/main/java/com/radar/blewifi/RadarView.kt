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

    var isHighContrastMode = false
        set(value) {
            field = value
            updateColors()
            cachedBg = null
            invalidate()
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

    private fun updateColors() {
        if (isHighContrastMode) {
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
            
            buildSweepShader(width / 2f, height / 2f)
        } else {
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
            
            buildSweepShader(width / 2f, height / 2f)
        }
    }

    private fun buildSweepShader(cx: Float, cy: Float) {
        val sweepColor = if (isHighContrastMode) Color.BLACK else green
        sweepPaint.shader = SweepGradient(cx, cy, intArrayOf(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            ColorUtils.setAlphaComponent(sweepColor, 0),
            ColorUtils.setAlphaComponent(sweepColor, if (isHighContrastMode) 15 else 25),
            ColorUtils.setAlphaComponent(sweepColor, if (isHighContrastMode) 30 else 50),
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
        sortedDevices.clear()
        sortedDevices.addAll(list.sortedBy { it.id })
        assignPositions()
        invalidate()
    }

    private fun assignPositions() {
        val cx = width / 2f
        val cy = height / 2f
        val r  = min(cx, cy) * 0.97f
        devices.forEach { d ->
            val existing = devicePositions[d.id]
            if (existing == null && d.type != DeviceType.AIRCRAFT) {
                // First time: assign stable angle from id hash
                // Use a different seed for different device types to spread them out
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
                    DeviceType.AIRCRAFT, DeviceType.DRONE -> 0x000
                }
                val hashAngle = (((d.id.hashCode() xor seed) and 0x7FFFFFFF) % 360).toDouble()
                d.angle = hashAngle
            }

            // For aircraft, distance scaling is different (0 to 50km)
            val currentMaxDist = if (d.type == DeviceType.AIRCRAFT) 50000.0 else maxDistance
            
            // Dynamic distance scaling based on maxDistance
            val dist = d.distanceMeters.coerceIn(0.1, currentMaxDist)
            // Use a square root (pow 0.5) to pull things away from the edge more aggressively
            val normDist = (dist / currentMaxDist).pow(0.55).coerceIn(0.05, 0.95)
            
            val rad = Math.toRadians(d.angle - 90.0)
            devicePositions[d.id] = PointF(
                cx + (r * normDist * cos(rad)).toFloat(),
                cy + (r * normDist * sin(rad)).toFloat()
            )
        }
        // Remove stale
        val ids = devices.map { it.id }.toSet()
        devicePositions.keys.retainAll(ids)
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
        super.onDraw(canvas)
        
        val deviceCount = devices.size
        
        // Performance optimization: Skip frames if device count is very high (> 150)
        if (deviceCount > 150) {
            drawCount++
            if (drawCount % DRAW_SKIP_THRESHOLD != 0) return
        }

        if (cachedBg == null || cachedBg?.width != width || cachedBg?.height != height) {
            buildStaticCache()
        }

        val cx = width / 2f
        val cy = height / 2f
        val r  = min(cx, cy) * 0.97f

        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)

        // Only draw full background if we don't have a dirty rect or if device count is low
        // For ultra-high density (> 300), we could potentially use dirty rects, 
        // but since the sweep and rotation are constant, a full redraw is usually cleaner.
        // However, we can optimize by only redrawing blips that moved significantly.
        
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
        val pulseScale = if (cycle < 500) cycle / 500f else 1f // Fast 500ms pulse, then 1s pause
        
        // Expanding ring (the ping)
        if (cycle < 500) {
            val pingRadius = 6f + (pulseScale * 40f)
            val pingAlpha = (255 * (1f - pulseScale)).toInt()
            centerGlowPaint.color = targetPink
            centerGlowPaint.alpha = pingAlpha
            canvas.drawCircle(cx, cy, pingRadius, centerGlowPaint)
        }
        
        // Steady core dot
        val centerColor = if (isHighContrastMode) {
            ColorUtils.blendARGB(Color.parseColor("#FF00FF"), Color.WHITE, centerBlinkAlpha / 255f)
        } else {
            targetPink
        }
        centerPaint.color = centerColor
        canvas.drawCircle(cx, cy, 6f, centerPaint)

        // Blips
        val maxBlipsToDraw = if (deviceCount > 300) 300 else sortedDevices.size
        
        for (i in 0 until sortedDevices.size) {
            if (i >= maxBlipsToDraw) break
            val device = sortedDevices[i]
            
            if (device.type == DeviceType.BLE && !showBle) continue
            if (device.type == DeviceType.PAGER && !showBle) continue
            if (device.type == DeviceType.WIFI && !showWifi) continue
            if (device.type == DeviceType.FIVE_G && !show5g) continue
            if (device.type == DeviceType.LTE && !showLte) continue
            if ((device.type == DeviceType.AIRCRAFT || device.type == DeviceType.DRONE) && !showAero) continue

            val pos = devicePositions[device.id] ?: continue
            
            // Optimization: If > 300 devices, skip those that haven't moved much to reduce draw calls
            if (deviceCount > 300) {
                val lastPos = lastRenderedBlips[device.id]
                if (lastPos != null && hypot(pos.x - lastPos.x, pos.y - lastPos.y) < 2f) {
                   // Still draw it, but maybe we can skip complex effects? 
                   // For now, let's just draw the core dot for non-selected
                   if (device.id != selectedId) {
                       blipPaint.color = ColorUtils.setAlphaComponent(green, 180)
                       canvas.drawCircle(pos.x, pos.y, 5f, blipPaint)
                       continue
                   }
                }
            }
            // Reuse PointF object or avoid allocation if possible
            val savedPos = lastRenderedBlips[device.id]
            if (savedPos != null) {
                savedPos.set(pos.x, pos.y)
            } else {
                lastRenderedBlips[device.id] = PointF(pos.x, pos.y)
            }

            val isSelected = device.id == selectedId
            val blipColor = when (device.type) {
                DeviceType.WIFI -> green
                DeviceType.BLE -> pink
                DeviceType.LTE -> lteColor
                DeviceType.FIVE_G -> fiveGColor
                DeviceType.PAGER -> pagerColor
                DeviceType.AIRCRAFT, DeviceType.DRONE -> aircraftColor
                DeviceType.CAR, DeviceType.ESCOOTER, DeviceType.TV, DeviceType.COMPUTER, DeviceType.SMARTPHONE -> targetPink
            }
            val alpha = if (isSelected) 255 else 210

            blipPaint.color = ColorUtils.setAlphaComponent(blipColor, alpha)
            blipRingPaint.color = ColorUtils.setAlphaComponent(blipColor, alpha)

            val blipR = if (isSelected) 10f else 7f

            // Pulse ring for selected
            if (isSelected) {
                val pr = blipR + 6f + pulseRadius * 20f
                val pa = (255 * (1f - pulseRadius)).toInt()
                val selectionColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF") // Pink instead of Amber
                selectedPaint.color = ColorUtils.setAlphaComponent(selectionColor, pa)
                canvas.drawCircle(pos.x, pos.y, pr, selectedPaint)
                selectedPaint.color = selectionColor
                canvas.drawCircle(pos.x, pos.y, blipR + 4f, selectedPaint)
            }

            // Glow halo
            glowPaint.color = ColorUtils.setAlphaComponent(blipColor, 40)
            glowPaint.style = Paint.Style.FILL
            canvas.drawCircle(pos.x, pos.y, blipR * 3f, glowPaint)

            // Blip dot
            val blinkColor = when {
                device.isCar -> if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF") // Pink for Cars
                device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP") -> if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
                device.isAirTag -> if (isHighContrastMode) Color.BLACK else Color.WHITE
                else -> blipColor
            }
            
            if (blinkColor != blipColor) {
                blipPaint.color = ColorUtils.blendARGB(blipColor, blinkColor, blinkAlpha / 255f)
            } else {
                blipPaint.color = ColorUtils.setAlphaComponent(blipColor, alpha)
            }
            
            canvas.drawCircle(pos.x, pos.y, blipR, blipPaint)
            canvas.drawCircle(pos.x, pos.y, blipR, blipRingPaint)

            // Label - REMOVED (Only large pink overlay in MainActivity is shown)
        }
        
        canvas.restore()

        // Status overlay (top) removed as it contained green/cyan text
        // drawStatusBar(canvas)

        // Subtle overlays: Grain, Scanlines, Glitches
        drawOverlays(canvas)
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
        val bleCount  = devices.count { it.type == DeviceType.BLE }
        val wifiCount = devices.count { it.type == DeviceType.WIFI }
        canvas.drawText("BLE: $bleCount", 20f, 36f, statusPaint)
        canvas.drawText("WiFi: $wifiCount", 130f, 36f, cyanStatusPaint)
        
        if (showMap && userLat != null) {
            canvas.drawText("GPS: %.5f, %.5f".format(userLat, userLon), 260f, 36f, statusPaint)
        }
        
        canvas.drawText("SCANNING", width - 160f, 36f, statusPaint)
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
            val angleRad = Math.toRadians((-rotationDegrees).toDouble())
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
