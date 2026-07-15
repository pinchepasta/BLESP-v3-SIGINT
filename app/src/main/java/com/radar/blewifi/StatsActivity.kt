package com.radar.blewifi

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.TextView
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.radar.blewifi.databinding.ActivityStatsBinding

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    private var isHighContrastMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        isHighContrastMode = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (isHighContrastMode) {
            binding.root.setBackgroundColor(Color.WHITE)
            binding.btnCloseStats.setTextColor(Color.BLACK)
            binding.btnCloseStats.setBackgroundResource(R.drawable.status_box_bg_white_pink)
            
            // Collect all children of statsList to apply black text color if they were green
            for (i in 0 until binding.statsList.childCount) {
                val child = binding.statsList.getChildAt(i)
                if (child is TextView) {
                    if (child.id == R.id.tvVulnCounter) {
                         child.setTextColor(Color.BLACK)
                         child.setBackgroundColor(Color.parseColor("#11000000"))
                    } else if (child.currentTextColor == Color.parseColor("#00FF41") || child.currentTextColor == -16711871) { // #00FF41
                        child.setTextColor(Color.BLACK)
                    } else if (child.id != R.id.tvAeroCount && child.currentTextColor != Color.RED && child.currentTextColor != Color.parseColor("#FF00FF")) {
                        // Catch any other green-ish text that might be default from XML
                        child.setTextColor(Color.BLACK)
                    }
                }
            }
            
            binding.tvAeroCount.setTextColor(Color.BLACK)
            binding.tvWepCount.setTextColor(Color.BLACK)
            binding.tvOpenWifi.setTextColor(Color.BLACK)
            binding.tvWhisperCount.setTextColor(Color.BLACK)

            binding.tvStatsTitle.setTextColor(Color.BLACK)
            binding.tvChartLabel.setTextColor(Color.BLACK)
        }

        binding.btnCloseStats.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        setupChart()
        loadStats()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun setupChart() {
        // High-density multi-stream graph tracking BLE, WiFi, Aircraft and Security vulnerabilities
        val bleData = floatArrayOf(40f, 55f, 45f, 70f, 60f, 85f, 75f, 80f, 65f, 55f, 70f, 90f, 85f, 95f, 80f, 85f)
        val wifiData = floatArrayOf(20f, 30f, 25f, 40f, 35f, 50f, 45f, 55f, 40f, 35f, 45f, 60f, 55f, 65f, 50f, 60f)
        val aeroData = floatArrayOf(10f, 15f, 12f, 20f, 18f, 25f, 22f, 30f, 28f, 25f, 35f, 45f, 40f, 50f, 45f, 55f)
        val vulnData = floatArrayOf(5f, 10f, 8f, 15f, 12f, 25f, 20f, 18f, 10f, 5f, 12f, 30f, 25f, 40f, 35f, 45f)
        
        binding.chartView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (binding.chartView.width <= 0 || binding.chartView.height <= 0) return@addOnLayoutChangeListener
            
            val bitmap = android.graphics.Bitmap.createBitmap(binding.chartView.width, binding.chartView.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            
            val w = binding.chartView.width.toFloat()
            val h = binding.chartView.height.toFloat()
            val padding = 35f
            val chartW = w - (padding * 2)
            val chartH = h - (padding * 2)
            val step = chartW / (bleData.size - 1)

            // 1. Background Grid (Cyberpunk scanlines)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = if (isHighContrastMode) Color.parseColor("#1A000000") else Color.parseColor("#0A00FF41")
            for (i in 0 until (h / 8).toInt()) {
                val y = i * 8f
                canvas.drawLine(0f, y, w, y, paint)
            }

            paint.color = if (isHighContrastMode) Color.parseColor("#33000000") else Color.parseColor("#1A00FF41")
            for (i in 0..4) {
                val y = padding + (chartH * i / 4f)
                canvas.drawLine(padding, y, w - padding, y, paint)
            }

            // Helper to draw a curve
            fun drawCurve(data: FloatArray, color: Int, strokeWidth: Float, alpha: Int = 255, fill: Boolean = false) {
                val path = Path()
                path.moveTo(padding, h - padding - (data[0] / 100f * chartH))
                for (i in 1 until data.size) {
                    val x = padding + (i * step)
                    val y = h - padding - (data[i] / 100f * chartH)
                    path.lineTo(x, y)
                }
                
                val drawColor = if (isHighContrastMode) Color.BLACK else color
                paint.color = drawColor
                paint.alpha = alpha
                paint.strokeWidth = strokeWidth
                paint.style = Paint.Style.STROKE
                
                if (!isHighContrastMode && (color == Color.parseColor("#FF00FF") || color == Color.parseColor("#00FFFF"))) {
                    paint.setShadowLayer(10f, 0f, 0f, color)
                }
                
                canvas.drawPath(path, paint)
                paint.clearShadowLayer()

                if (fill) {
                    val fillPath = Path(path)
                    fillPath.lineTo(padding + (data.size - 1) * step, h - padding)
                    fillPath.lineTo(padding, h - padding)
                    fillPath.close()
                    paint.style = Paint.Style.FILL
                    paint.alpha = if (isHighContrastMode) 10 else 20
                    canvas.drawPath(fillPath, paint)
                }
            }

            // 2. Draw BLE Stream (Green)
            drawCurve(bleData, Color.parseColor("#00FF41"), 3f, alpha = 200, fill = true)

            // 3. Draw WiFi Stream (Yellow)
            drawCurve(wifiData, Color.parseColor("#FFFF00"), 3f, alpha = 200)

            // 4. Draw Aircraft Stream (Cyan)
            drawCurve(aeroData, Color.parseColor("#00FFFF"), 3f, alpha = 255)

            // 5. Draw Vulnerability Stream (Pink Neon)
            drawCurve(vulnData, Color.parseColor("#FF00FF"), 4f, alpha = 255)

            // 6. Data Markers
            paint.style = Paint.Style.FILL
            for (i in bleData.indices) {
                val x = padding + (i * step)
                // Markers for Vulns (Critical)
                paint.color = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
                canvas.drawCircle(x, h - padding - (vulnData[i] / 100f * chartH), 4f, paint)
            }

            // 7. HUD labels
            paint.textSize = 20f
            paint.typeface = android.graphics.Typeface.MONOSPACE
            
            val hudColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41")
            paint.color = hudColor
            canvas.drawText("BLE_FLUX: ACTIVE", padding, padding - 10f, paint)
            
            val aeroHudColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FFFF")
            paint.color = aeroHudColor
            canvas.drawText("AERO_INTEL: RECEIVING", padding + 220f, padding - 10f, paint)
            
            val threatHudColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
            paint.color = threatHudColor
            canvas.drawText("THREAT_LOG: MONITORING", padding, h - 5f, paint)

            binding.chartView.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
        }
    }

    private fun loadStats() {
        val prefs = getSharedPreferences("scan_stats", MODE_PRIVATE)
        val totalMs = prefs.getLong("total_scan_time_ms", 0L)
        
        val seconds = (totalMs / 1000) % 60
        val minutes = (totalMs / (1000 * 60)) % 60
        val hours = (totalMs / (1000 * 60 * 60)) % 24
        val days = totalMs / (1000 * 60 * 60 * 24)
        
        val uptimeStr = if (days > 0) {
            String.format(java.util.Locale.US, "%dD %02dH %02dM %02dS", days, hours, minutes, seconds)
        } else if (hours > 0) {
            String.format(java.util.Locale.US, "%02dH %02dM %02dS", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02dM %02dS", minutes, seconds)
        }
        
        binding.tvChartLabel.text = "TOTAL SCAN TIME: $uptimeStr"
        
        // Load persistent stats
        val discoveredIds = prefs.getStringSet("discovered_device_ids", emptySet()) ?: emptySet()
        val totalUnique = discoveredIds.size
        val bleCount = prefs.getInt("total_ble", 0)
        val wifiCount = prefs.getInt("total_wifi", 0)
        val aeroCount = prefs.getInt("total_aircraft", 0)
        val carCount = prefs.getInt("total_car", 0)
        val tvCount = prefs.getInt("total_tv", 0)
        val pcCount = prefs.getInt("total_computer", 0)
        val phnCount = prefs.getInt("total_smartphone", 0)
        val airtagCount = prefs.getInt("total_airtag", 0)
        val whisperCount = prefs.getInt("total_whisper", 0)
        val wepCount = prefs.getInt("total_wep", 0)
        val openCount = prefs.getInt("total_open", 0)

        binding.tvTotalDevices.text = "TOTAL UNIQUE MAPPED: %,d".format(totalUnique)
        binding.tvAppleDevices.text = "TOTAL BLE NODES: %,d".format(bleCount)
        binding.tvAirTagCount.text = "AIRTAG BEACONS: %,d".format(airtagCount)
        binding.tvAndroidDevices.text = "SMARTPHONE TARGETS: %,d".format(phnCount)
        binding.tvCarCount.text = "VEHICLE SYSTEMS: %,d".format(carCount)
        binding.tvOtherDevices.text = "PC/TV/OTHER: %,d".format(pcCount + tvCount)
        
        binding.tvWifi24.text = "WIFI TOTAL: %,d".format(wifiCount)
        binding.tvWifi5.text = "COMPUTING NODES: %,d".format(pcCount)
        binding.tvBleCount.text = "BLE BEACONS: %,d".format(bleCount)
        binding.tvAeroCount.text = "AIRCRAFT TRACKED: %,d".format(aeroCount)
        
        binding.tvWepCount.text = "WEP VULNERABLE: %,d".format(wepCount)
        binding.tvOpenWifi.text = "OPEN HOTSPOTS: %,d".format(openCount)
        binding.tvWhisperCount.text = "WHISPER PAIRS: %,d".format(whisperCount)
        
        binding.tvUptime.text = "UPTIME: $uptimeStr"
        binding.tvDataUsage.text = "METADATA LOGGED: %.1f MB".format(totalUnique * 0.05) // Simulated 50KB per unique device
    }
}
