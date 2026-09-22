package com.radar.blewifi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.radar.blewifi.databinding.ActivityArchiveMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay

class ArchiveMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveMapBinding
    private lateinit var scanner: ScannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Configure osmdroid user agent before inflating or using the MapView
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        binding = ActivityArchiveMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (settingsPrefs.getBoolean("immersive_mode", true)) {
            enterImmersiveMode()
        }

        scanner = ScannerManager(this)

        setupMap()
        setupListeners()
        loadArchiveMarkers()
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupMap() {
        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = settingsPrefs.getString("theme_name", "DEFAULT") ?: "DEFAULT"
        val theme = try { RadarView.Theme.valueOf(themeName) } catch (e: Exception) { RadarView.Theme.DEFAULT }
        val themeColors = ThemeUtils.getThemeColors(theme)

        // Apply tactical transparency to top bar
        binding.topBar.setBackgroundColor(ColorUtils.setAlphaComponent(themeColors.headerBar, 204)) // 80% alpha
        binding.btnBack.setColorFilter(themeColors.primary)
        binding.tvTitle.setTextColor(themeColors.primary)
        binding.tvStats.setTextColor(themeColors.accent)

        // Theme-specific ProgressBar color
        binding.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(themeColors.primary)

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            
            // Dark/Cyber mode filter for tactical themes
            if (theme != RadarView.Theme.HIGH_CONTRAST) {
                val inverseMatrix = floatArrayOf(
                    -0.9f,  0.0f,  0.0f, 0.0f, 255f, // R
                     0.0f, -0.9f,  0.0f, 0.0f, 255f, // G
                     0.0f,  0.0f, -0.9f, 0.0f, 255f, // B
                     0.0f,  0.0f,  0.0f, 1.0f,   0f  // A
                )
                val filter = android.graphics.ColorMatrixColorFilter(inverseMatrix)
                overlayManager.tilesOverlay.setColorFilter(filter)
            }
        }

        val scaleBarOverlay = ScaleBarOverlay(binding.mapView).apply {
            setAlignBottom(true)
            setLineWidth(2 * resources.displayMetrics.density)
            setTextSize(10 * resources.displayMetrics.density)
        }
        binding.mapView.overlays.add(scaleBarOverlay)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadArchiveMarkers() {
        binding.progressBar.visibility = View.VISIBLE
        
        val archivedDevices = scanner.getArchivedDevices()
        val liveDevices = scanner.getDevices().map { it.id }.toSet()
        
        var wifiCount = 0
        var bleCount = 0
        var cellCount = 0
        var otherCount = 0
        
        var firstValidPoint: GeoPoint? = null

        // If a specific device was passed via intent to center on
        val targetDevice: ScanDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_DEVICE", ScanDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_DEVICE")
        }
        var targetPoint: GeoPoint? = null

        archivedDevices.forEach { device ->
            val lat = device.capturedLat ?: device.lat
            val lon = device.capturedLon ?: device.lon
            
            // Track counts by type
            when (device.type) {
                DeviceType.WIFI -> wifiCount++
                DeviceType.BLE -> bleCount++
                DeviceType.LTE, DeviceType.FIVE_G -> cellCount++
                else -> {
                    if (device.typeLabel == "Cell") cellCount++ else otherCount++
                }
            }

            if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                val point = GeoPoint(lat, lon)
                if (firstValidPoint == null) {
                    firstValidPoint = point
                }

                if (targetDevice != null && device.id == targetDevice.id) {
                    targetPoint = point
                }

                val marker = Marker(binding.mapView).apply {
                    position = point
                    title = device.displayName
                    val addressStr = if (!device.streetName.isNullOrBlank()) {
                        "\nLocation: ${device.streetName} ${device.houseNumber}, ${device.cityName}"
                    } else ""
                    subDescription = "[${device.typeLabel}] RSSI: ${device.rssi} dBm\nMAC: ${device.address}$addressStr"
                    icon = createMarkerIcon(device.type, liveDevices.contains(device.id))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                binding.mapView.overlays.add(marker)
            }
        }

        // Update Stats Overlay String
        val totalNodes = archivedDevices.size
        binding.tvStats.text = "WF: $wifiCount | BL: $bleCount | CL: $cellCount | TOT: $totalNodes"

        // Center / Focus map logic
        val mapController = binding.mapView.controller
        mapController.setZoom(16.0)
        
        if (targetPoint != null) {
            mapController.setCenter(targetPoint)
        } else if (firstValidPoint != null) {
            mapController.setCenter(firstValidPoint)
        } else {
            // Fallback to last known scanner location or standard coordinates if empty
            val lastLoc = scanner.lastLocation
            if (lastLoc != null) {
                mapController.setCenter(GeoPoint(lastLoc.latitude, lastLoc.longitude))
            } else {
                mapController.setCenter(GeoPoint(52.5200, 13.4050)) // Default Berlin
            }
        }

        binding.progressBar.visibility = View.GONE
        binding.mapView.invalidate()
    }

    private fun createMarkerIcon(type: DeviceType, isLive: Boolean = false): BitmapDrawable {
        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = settingsPrefs.getString("theme_name", "DEFAULT") ?: "DEFAULT"
        val theme = try { RadarView.Theme.valueOf(themeName) } catch (e: Exception) { RadarView.Theme.DEFAULT }
        val colors = ThemeUtils.getThemeColors(theme)

        val size = (28 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Style marker color dynamically based on DeviceType and active theme
        when (type) {
            DeviceType.WIFI -> paint.color = colors.primary
            DeviceType.BLE -> paint.color = colors.accent
            DeviceType.LTE, DeviceType.FIVE_G -> paint.color = colors.primary
            DeviceType.AIRCRAFT, DeviceType.DRONE -> paint.color = colors.accent
            DeviceType.CAR, DeviceType.ESCOOTER -> paint.color = colors.accent
            DeviceType.GAME_CONSOLE, DeviceType.TV -> paint.color = colors.primary
            DeviceType.CAMERA -> if (theme == RadarView.Theme.HIGH_CONTRAST) Color.BLACK else Color.RED
            DeviceType.SMARTPHONE, DeviceType.TABLET, DeviceType.COMPUTER -> paint.color = colors.primary
            DeviceType.WEARABLE, DeviceType.HEADPHONES, DeviceType.TRACKER -> paint.color = colors.accent
            else -> paint.color = colors.dim
        }

        // Draw a distinct dot or diamond shape for the WiGLE map marker
        canvas.drawCircle(size / 2f, size / 2f, size / 3.5f, paint)
        
        // Differentiate Live nodes with a pulsing-style outer ring
        if (isLive) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * resources.displayMetrics.density
            paint.color = colors.accent
            canvas.drawCircle(size / 2f, size / 2f, size / 2.8f, paint)
        }
        
        // Draw a small outer ring for styling
        paint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * resources.displayMetrics.density
            color = if (theme == RadarView.Theme.HIGH_CONTRAST) Color.WHITE else ColorUtils.setAlphaComponent(Color.WHITE, 180)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, paint)

        return BitmapDrawable(resources, bitmap)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
