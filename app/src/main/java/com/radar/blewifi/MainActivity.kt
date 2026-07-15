package com.radar.blewifi

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsDisplay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.radar.blewifi.databinding.ActivityMainBinding
import kotlin.math.sin
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size

class MainActivity : AppCompatActivity(), ScannerManager.ScanListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: ScannerManager
    private lateinit var deviceAdapter: DeviceAdapter
    private var isScanning = false
    private var isListView = false
    private var activeAnimator: android.animation.ObjectAnimator? = null
    private var headerAnimator: android.animation.ObjectAnimator? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var overlayHideRunnable: Runnable? = null

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var compassOverlay: CompassOverlay? = null
    private var lastAzimuth = 0f
    private var isHighContrastMode = false

    private var audioTrack: AudioTrack? = null
    private var isBeeping = false
    private var noiseBlinkRunnable: Runnable? = null
    private var noisePulsator: android.animation.ObjectAnimator? = null
    private var glitchRunnable: Runnable? = null
    private var pagerNotificationReceiver: android.content.BroadcastReceiver? = null
    private var pagerBreatheAnim: android.animation.ValueAnimator? = null
    private var statusPulseAnim: android.animation.ObjectAnimator? = null


    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val newAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                
                // Low-pass filter to reduce sensitivity and jitter
                // We handle the 360-degree wrap-around to ensure smooth transitions
                var diff = newAzimuth - lastAzimuth
                while (diff < -180) diff += 360
                while (diff > 180) diff -= 360
                
                // 0.08f is the smoothing factor; lower values are slower/smoother
                val smoothedAzimuth = lastAzimuth + (diff * 0.08f)
                lastAzimuth = smoothedAzimuth
                
                // Update RadarView
                binding.radarView.setRotation(-smoothedAzimuth)
                // Update MapView (osmdroid)
                binding.mapView.mapOrientation = -smoothedAzimuth
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val requiredPermissions: Array<String> get() {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_VIDEO
            list += Manifest.permission.READ_MEDIA_AUDIO
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                list += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            list += Manifest.permission.BLUETOOTH
            list += Manifest.permission.BLUETOOTH_ADMIN
        }
        return list.toTypedArray()
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startScanning()
        } else {
            showError("Permissions denied — BLE/WiFi scanning requires location & Bluetooth.")
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showEslOverlay()
        } else {
            showEslOverlay()
            Toast.makeText(this, "Camera permission denied. Barcode scanner will not work.", Toast.LENGTH_LONG).show()
        }
    }

    private val btEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> checkAndStartScanning() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // OSMDroid configuration
        Configuration.getInstance().userAgentValue = packageName
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        isHighContrastMode = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        
        scanner = ScannerManager(this)
        scanner.addListener(this)
        binding.netGraph.setColors("#00FF41", "#99FF00FF")

        checkAndStartScanning()

        setupImmersiveMode()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Start Timecode Runner
        startTimecodeRunner()
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        binding.radarView.onDeviceClickListener = object : RadarView.OnDeviceClickListener {
            override fun onDeviceClicked(device: ScanDevice) {
                markers[device.id]?.let { marker ->
                    marker.showInfoWindow()
                    binding.mapView.controller.animateTo(marker.position)
                }
                
                // Select in list adapter
                deviceAdapter.setSelectedId(device.id)

                // Show overlay
                binding.tvOverlayNetworkName.text = device.displayName.uppercase()
                binding.tvOverlayNetworkName.animate().alpha(1f).setDuration(200).start()
                binding.tvOverlayNetworkName.visibility = View.VISIBLE

                // Cancel existing hide timer
                overlayHideRunnable?.let { handler.removeCallbacks(it) }
                
                // Create new hide timer
                overlayHideRunnable = Runnable {
                    binding.tvOverlayNetworkName.animate()
                        .alpha(0f)
                        .setDuration(1000)
                        .withEndAction { 
                            binding.tvOverlayNetworkName.visibility = View.GONE
                            // Also deselect in radar view to hide the blip label
                            binding.radarView.selectedId = null
                            binding.radarView.invalidate()
                        }
                        .start()
                }
                handler.postDelayed(overlayHideRunnable!!, 3000)
            }

            override fun onNothingSelected() {
                overlayHideRunnable?.let { handler.removeCallbacks(it) }
                binding.tvOverlayNetworkName.visibility = View.GONE
            }
        }

        binding.btnKillswitch.setOnClickListener {
            val intent = Intent(this, CalculatorActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.btnScan.setOnClickListener {
            setActiveButton(binding.btnScan)
            if (isScanning) {
                stopScanning()
            } else {
                checkAndStartScanning()
            }
        }

        // Neon pulsate animation for the Logo
        val logoPvhX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f)
        val logoPvhY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
        android.animation.ObjectAnimator.ofPropertyValuesHolder(binding.ivLogo, logoPvhX, logoPvhY).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }

        binding.btnClear.setOnClickListener {
            setActiveButton(binding.btnClear)
            binding.radarView.setDevices(emptyList())
        }

        binding.cbBle.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.showBle = isChecked
        }

        binding.cb24.setOnCheckedChangeListener { _, _ ->
            // The filtering is handled in onDevicesUpdated
        }

        binding.cb5.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.show5g = isChecked
        }

        binding.cbLte.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.showLte = isChecked
        }

        binding.cbAero.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.showAero = isChecked
        }

        binding.cbMap.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.showMap = isChecked
            binding.mapView.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        setupMapView()
        setupListView()

        binding.btnGlobeListTop.setOnClickListener {
            isListView = !isListView
            updateViewVisibility()
        }

        // Hide lock icon initially
        binding.btnKillswitch.visibility = View.VISIBLE

        // Apply pulsate animation for the toggle button
        updateViewVisibility()
        setActiveHeaderButton(binding.btnGlobeListTop)

        binding.ivLogo.setOnClickListener {
            showInfoDialog()
        }
        
        binding.ivLogo.setOnLongClickListener {
            showChangePinDialog()
            true
        }
        
        binding.btnExt.setOnClickListener {
            setActiveButton(binding.btnExt)
            showExtOverlay()
        }
        binding.btnTerminal.setOnClickListener {
            setActiveButton(binding.btnTerminal)
            showTerminalOverlay()
        }

        binding.btnStats.setOnClickListener {
            val intent = Intent(this, StatsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnArchive.setOnClickListener {
            val intent = Intent(this, ArchiveActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnPager.setOnClickListener {
            stopPagerBreathing()
            getSharedPreferences("pager_history", MODE_PRIVATE).edit().putBoolean("has_unread", false).apply()
            val intent = Intent(this, PagerActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnEsl.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                showEslOverlay()
            }
        }

        binding.btnThemeToggle.setOnClickListener {
            toggleTheme()
        }

        binding.btnBeep.setOnClickListener {
            if (isBeeping) {
                stopBeep()
            } else {
                startBeep()
            }
        }

        binding.btnStats.setColorFilter(if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnPager.setColorFilter(if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnGlobeListTop.setColorFilter(if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnKillswitch.setColorFilter(if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF"))
        binding.btnEsl.setColorFilter(if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41"))

        setActiveButton(binding.btnScan)
        startLogoAnimation()
        startGraphUpdates()

        if (isHighContrastMode) {
            updateThemeUI()
        }
    }

    private fun triggerGlobalGlitchEffect() {
        // Implementation remains if needed, currently unused as per plan
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isSshWindowOpen) {
            setupImmersiveMode()
        }
    }

    private fun toggleTheme() {
        isHighContrastMode = !isHighContrastMode
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("high_contrast", isHighContrastMode).apply()

        binding.radarView.isHighContrastMode = isHighContrastMode
        deviceAdapter.isHighContrastMode = isHighContrastMode
        
        updateThemeUI()
    }

    private fun updateThemeUI() {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val bgColor = if (isHighContrast) Color.WHITE else Color.BLACK
        val headerColor = if (isHighContrast) Color.parseColor("#F0F0F0") else Color.parseColor("#0A0A0A")
        val textColor = if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41")
        val accentColor = if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF")

        binding.root.setBackgroundColor(bgColor)
        binding.headerContainer.setBackgroundColor(headerColor)
        binding.headerBar.setBackgroundColor(if (isHighContrast) Color.LTGRAY else Color.parseColor("#0F0F0F"))
        
        binding.statusText.setTextColor(textColor)
        binding.statusText.setBackgroundResource(if (isHighContrast) R.drawable.status_box_bg_white else R.drawable.status_box_bg)
        
        binding.tvCoordinates.setTextColor(textColor)
        binding.tvCoordinates.setBackgroundColor(if (isHighContrast) Color.parseColor("#1A000000") else Color.parseColor("#44000000"))
        binding.tvTimecode.setTextColor(accentColor)
        binding.tvTimecode.setBackgroundColor(if (isHighContrast) Color.parseColor("#1A000000") else Color.parseColor("#44000000"))
        
        binding.listView.setBackgroundColor(bgColor)
        binding.bottomBar.setBackgroundColor(headerColor)
        
        val filterRow = (binding.cbBle.parent as View)
        filterRow.setBackgroundResource(if (isHighContrast) R.drawable.status_box_bg_white else R.drawable.status_box_bg)

        val checkBoxes = listOf(binding.cbBle, binding.cb24, binding.cb5, binding.cbLte, binding.cbAero, binding.cbMap)
        checkBoxes.forEach {
            it.setTextColor(if (isHighContrast) Color.BLACK else Color.parseColor("#00AA2A"))
            it.buttonTintList = android.content.res.ColorStateList.valueOf(if (isHighContrast) Color.BLACK else Color.parseColor("#00AA2A"))
        }

        binding.btnThemeToggle.setImageResource(if (isHighContrast) R.drawable.ic_theme_toggle_light else R.drawable.ic_theme_toggle)
        binding.btnStats.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnPager.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnGlobeListTop.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnKillswitch.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF"))
        binding.btnEsl.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnArchive.setImageResource(if (isHighContrast) R.drawable.ic_archive_light else R.drawable.ic_archive)
        binding.btnArchive.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
        binding.btnThemeToggle.setColorFilter(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))

        if (isHighContrast) {
            binding.btnBeep.setTextColor(Color.BLACK)
            binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.BLACK))
            binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_white)
        } else {
            binding.btnBeep.setTextColor(Color.parseColor("#00FF41"))
            binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF41")))
            binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_green)
        }
        
        // Update button states
        setActiveButton(if (binding.btnScan.scaleX > 1.05f) binding.btnScan else if (binding.btnExt.scaleX > 1.05f) binding.btnExt else if (binding.btnTerminal.scaleX > 1.05f) binding.btnTerminal else binding.btnClear)
        updateViewVisibility()
        
        // Ensure RadarView is updated
        binding.radarView.isHighContrastMode = isHighContrast
        binding.miniGraph.isHighContrastMode = isHighContrast
        binding.netGraph.isHighContrastMode = isHighContrast
        if (::deviceAdapter.isInitialized) {
            deviceAdapter.isHighContrastMode = isHighContrast
            deviceAdapter.notifyDataSetChanged()
        }
    }

    private fun setupImmersiveMode() {
        if (isSshWindowOpen) return
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun setActiveButton(activeButton: Button) {
        activeAnimator?.cancel()
        
        val green = if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41")
        val pink = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
        val dimBg = if (isHighContrastMode) R.drawable.btn_bg_white_dim else R.drawable.btn_bg_dim
        val activeBg = if (isHighContrastMode) R.drawable.btn_bg_white else R.drawable.btn_bg
        val redBg = if (isHighContrastMode) R.drawable.btn_bg_white_red else R.drawable.btn_bg_red

        // Reset all buttons
        listOf(binding.btnScan, binding.btnExt, binding.btnTerminal, binding.btnClear).forEach {
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.setBackgroundResource(dimBg)
            setCyberText(it, it.text.toString(), green, forceAllGreen = true)
        }
        
        if (activeButton == binding.btnScan && isScanning) {
            activeButton.setBackgroundResource(redBg)
        } else {
            activeButton.setBackgroundResource(activeBg)
        }
        
        setCyberText(activeButton, activeButton.text.toString(), pink)
        
        val pvhX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.1f)
        val pvhY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.1f)
        activeAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(activeButton, pvhX, pvhY).apply {
            duration = 1000
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }
    }

    private fun setActiveHeaderButton(activeButton: View) {
        headerAnimator?.cancel()
        
        binding.btnGlobeListTop.scaleX = 1.0f
        binding.btnGlobeListTop.scaleY = 1.0f
        
        val pvhX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.05f)
        val pvhY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.05f)
        headerAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(activeButton, pvhX, pvhY).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }
    }

    private fun setCyberText(button: Button, text: String, contentColor: Int, forceAllGreen: Boolean = false) {
        val spannable = android.text.SpannableString(text)
        val green = if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41")
        
        // Find brackets
        val openIdx = text.indexOf("[")
        val closeIdx = text.lastIndexOf("]")
        
        if (openIdx != -1 && closeIdx != -1) {
            // Color brackets as green
            spannable.setSpan(android.text.style.ForegroundColorSpan(green), openIdx, openIdx + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.ForegroundColorSpan(green), closeIdx, closeIdx + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            // Color text inside
            val insideColor = if (forceAllGreen) green else contentColor
            spannable.setSpan(android.text.style.ForegroundColorSpan(insideColor), openIdx + 1, closeIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val pinkColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
            if (contentColor == pinkColor) {
                spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), openIdx + 1, closeIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            // Any other text is green
            if (openIdx > 0) spannable.setSpan(android.text.style.ForegroundColorSpan(green), 0, openIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (closeIdx < text.length - 1) spannable.setSpan(android.text.style.ForegroundColorSpan(green), closeIdx + 1, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            val pinkColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
            button.setTextColor(if (forceAllGreen) green else contentColor)
            if (contentColor == pinkColor) button.setTypeface(null, android.graphics.Typeface.BOLD)
            button.text = text
            return
        }
        button.text = spannable
    }

    private fun blinkPink(button: Button) {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        button.setBackgroundResource(R.drawable.status_box_bg_pink)
        button.setTextColor(Color.BLACK)
        button.postDelayed({
            if (isHighContrast) {
                button.setBackgroundResource(R.drawable.btn_bg_white)
                button.setTextColor(Color.BLACK)
            } else {
                button.setBackgroundResource(R.drawable.btn_bg_dim)
                button.setTextColor(Color.parseColor("#00FF41"))
            }
        }, 100)
    }

    private fun showExtOverlay() {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setOnDismissListener { setupImmersiveMode() }
        dialog.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(if (isHighContrast) Color.WHITE else Color.BLACK)
            setPadding(40, 40, 40, 40)
            gravity = android.view.Gravity.CENTER
        }

        // Title
        val title = android.widget.TextView(this).apply {
            text = " EXTERNAL NODE // MULTIPASS "
            setTextColor(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }
        root.addView(title)

        // IP Input Field
        val ipLabel = android.widget.TextView(this).apply {
            text = "TARGET IP ADDRESS:"
            setTextColor(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        root.addView(ipLabel)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        val lastIp = prefs.getString("last_ext_ip", "192.168.1.1")

        val ipInput = android.widget.EditText(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
            setText(lastIp)
            setTextColor(if (isHighContrast) Color.BLACK else Color.WHITE)
            setBackgroundColor(if (isHighContrast) Color.WHITE else Color.parseColor("#111111"))
            if (isHighContrast) setBackgroundResource(R.drawable.status_box_bg_white)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 18f
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_PHONE or android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        root.addView(ipInput)

        // Connect Button
        val goBtn = android.widget.Button(this).apply {
            text = "INITIATE UPLINK"
            setTextColor(if (isHighContrast) Color.WHITE else Color.BLACK)
            setBackgroundColor(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 18f
            setPadding(60, 40, 60, 40)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(40, 20, 40, 20) }
        }
        root.addView(goBtn)

        // Cancel Button
        val cancelBtn = android.widget.Button(this).apply {
            text = "ABORT"
            setTextColor(if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF"))
            background = null
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 16f
            setPadding(0, 60, 0, 0)
        }
        root.addView(cancelBtn)

        goBtn.setOnClickListener {
            val rawIp = ipInput.text.toString().trim()
            if (rawIp.isNotEmpty()) {
                prefs.edit().putString("last_ext_ip", rawIp).apply()
                var targetIp = rawIp
                if (!targetIp.startsWith("http")) targetIp = "http://$targetIp"
                showWebViewOverlay(targetIp)
                dialog.dismiss()
            }
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun showEslOverlay() {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar else android.R.style.Theme_Black_NoTitleBar)
        dialog.setOnDismissListener {
            androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this).get().unbindAll()
            setupImmersiveMode()
        }
        val overlayBinding = com.radar.blewifi.databinding.EslOverlayBinding.inflate(layoutInflater)
        dialog.setContentView(overlayBinding.root)

        if (isHighContrast) {
            overlayBinding.eslRoot.setBackgroundColor(Color.WHITE)
            overlayBinding.headerEsl.setBackgroundColor(Color.WHITE)
            overlayBinding.ivLogoEsl.setImageResource(R.drawable.logo_blesp)
            overlayBinding.btnScanBarcode.setTextColor(Color.BLACK)
            overlayBinding.btnScanBarcode.setBackgroundResource(R.drawable.status_box_bg_white)
            overlayBinding.btnEslBack.setTextColor(Color.BLACK)
            overlayBinding.btnEslBack.setBackgroundResource(R.drawable.status_box_bg_white)
        }

        dialog.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        overlayBinding.webViewEsl.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun openScanner() {
                    runOnUiThread { overlayBinding.btnScanBarcode.performClick() }
                }
            }, "AndroidHost")
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                    runOnUiThread { request.grant(request.resources) }
                }
            }
            webViewClient = android.webkit.WebViewClient()
            loadUrl("file:///android_asset/esltools.html")
        }

        overlayBinding.btnEslBack.setOnClickListener { dialog.dismiss() }

        var isScanningBarcode = false
        overlayBinding.btnScanBarcode.setOnClickListener {
            if (!isScanningBarcode) {
                isScanningBarcode = true
                overlayBinding.cameraPreview.visibility = View.VISIBLE
                overlayBinding.scannerOverlay.visibility = View.VISIBLE
                overlayBinding.tvScannerHint.visibility = View.VISIBLE
                val pink = if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF")
                setCyberText(overlayBinding.btnScanBarcode, "[ ABORT ]", pink)
                startBarcodeScanner(overlayBinding) { barcode ->
                    runOnUiThread {
                        overlayBinding.webViewEsl.evaluateJavascript("if(window.onBarcodeScanned) { window.onBarcodeScanned('$barcode'); } else { barcodeToPlid('$barcode'); }", null)
                        isScanningBarcode = false
                        overlayBinding.cameraPreview.visibility = View.GONE
                        overlayBinding.scannerOverlay.visibility = View.GONE
                        overlayBinding.tvScannerHint.visibility = View.GONE
                        val green = if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41")
                        setCyberText(overlayBinding.btnScanBarcode, "[ SCANNER ]", green)
                        // Stop camera
                        androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this@MainActivity).get().unbindAll()
                    }
                }
            } else {
                isScanningBarcode = false
                overlayBinding.cameraPreview.visibility = View.GONE
                overlayBinding.scannerOverlay.visibility = View.GONE
                overlayBinding.tvScannerHint.visibility = View.GONE
                val green = if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41")
                setCyberText(overlayBinding.btnScanBarcode, "[ SCANNER ]", green)
                androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this@MainActivity).get().unbindAll()
            }
        }

        dialog.show()
    }

    private fun startBarcodeScanner(overlayBinding: com.radar.blewifi.databinding.EslOverlayBinding, onResult: (String) -> Unit) {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Optimized resolution for barcode scanning
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(
                    android.util.Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                ))
                .build()

            val preview = androidx.camera.core.Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                    it.setSurfaceProvider(overlayBinding.cameraPreview.surfaceProvider)
                }

            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Limit formats for faster detection if applicable (e.g., QR, EAN, Code128)
            val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8
                )
                .build()

            val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { 
                                    // Haptic feedback for scan success
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
                                    } else {
                                        @Suppress("DEPRECATION")
                                        getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    }
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))

                                    onResult(it)
                                    imageAnalysis.clearAnalyzer()
                                    return@addOnSuccessListener
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showWebViewOverlay(url: String) {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar_Fullscreen else android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setOnDismissListener { setupImmersiveMode() }
        dialog.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        val root = android.widget.RelativeLayout(this).apply {
            setBackgroundColor(if (isHighContrast) Color.WHITE else Color.BLACK)
        }

        val webView = android.webkit.WebView(this).apply {
            layoutParams = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                    runOnUiThread {
                        request.grant(request.resources)
                    }
                }
            }

            webViewClient = android.webkit.WebViewClient()

            setDownloadListener { downloadUrl, _, _, _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            loadUrl(url)
        }
        root.addView(webView)

        // Close button overlay
        val closeBtn = android.widget.Button(this).apply {
            text = "X"
            setTextColor(if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF"))
            setBackgroundColor(if (isHighContrast) Color.argb(128, 255, 255, 255) else Color.argb(170, 0, 0, 0))
            if (isHighContrast) setBackgroundResource(R.drawable.status_box_bg_white)
            typeface = android.graphics.Typeface.MONOSPACE
            val size = (40 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.RelativeLayout.LayoutParams(size, size).apply {
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT)
                setMargins(20, 20, 20, 20)
            }
        }
        closeBtn.setOnClickListener { dialog.dismiss() }
        root.addView(closeBtn)

        dialog.setContentView(root)
        dialog.show()
    }

    private var sshSession: com.jcraft.jsch.Session? = null
    private var sshChannel: com.jcraft.jsch.ChannelShell? = null
    private var sshIn: java.io.InputStream? = null
    private var sshOut: java.io.OutputStream? = null
    private val sshExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var terminalOutputBuffer = StringBuilder()
    private var isSshWindowOpen = false

    private fun showTerminalOverlay() {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar else android.R.style.Theme_Black_NoTitleBar)
        dialog.setOnDismissListener { 
            isSshWindowOpen = false
            setupImmersiveMode() 
        }
        isSshWindowOpen = true
        val overlayBinding = com.radar.blewifi.databinding.TerminalOverlayBinding.inflate(layoutInflater)
        dialog.setContentView(overlayBinding.root)

        if (isHighContrast) {
            val black = Color.BLACK
            val gray = Color.GRAY
            overlayBinding.root.setBackgroundColor(Color.WHITE)
            overlayBinding.sshLoginPage.setBackgroundColor(Color.WHITE)
            overlayBinding.sshTerminalPage.setBackgroundColor(Color.WHITE)
            overlayBinding.connectionLayout.setBackgroundColor(Color.WHITE)
            overlayBinding.terminalLayout.setBackgroundColor(Color.WHITE)
            
            overlayBinding.headerSshLogin.setBackgroundColor(Color.WHITE)
            overlayBinding.headerSshTerminal.setBackgroundColor(Color.WHITE)
            overlayBinding.terminalControlsRoot.setBackgroundColor(Color.WHITE)

            overlayBinding.ivLogoSshLogin.setImageResource(R.drawable.logo_blesp)
            
            overlayBinding.tvSshLoginTitle.setTextColor(black)
            overlayBinding.etHost.setTextColor(black)
            overlayBinding.etHost.setHintTextColor(gray)
            overlayBinding.etHost.setBackgroundResource(R.drawable.status_box_bg_white)
            
            overlayBinding.etUser.setTextColor(black)
            overlayBinding.etUser.setHintTextColor(gray)
            overlayBinding.etUser.setBackgroundResource(R.drawable.status_box_bg_white)
            
            overlayBinding.etPassword.setTextColor(black)
            overlayBinding.etPassword.setHintTextColor(gray)
            overlayBinding.etPassword.setBackgroundResource(R.drawable.status_box_bg_white)
            
            overlayBinding.btnConnect.setTextColor(Color.WHITE)
            overlayBinding.btnConnect.setBackgroundResource(R.drawable.btn_bg_black)
            
            overlayBinding.tvTerminalOutput.setTextColor(black)
            overlayBinding.svTerminal.setBackgroundResource(R.drawable.status_box_bg_white)
            
            val ctrlBtns = listOf(
                overlayBinding.btnCtrl, overlayBinding.btnTab, overlayBinding.btnCtrlC,
                overlayBinding.btnLeft, overlayBinding.btnUp, overlayBinding.btnDown, overlayBinding.btnRight
            )
            ctrlBtns.forEach {
                it.setTextColor(black)
                it.setBackgroundResource(R.drawable.btn_bg_white)
            }
            
            overlayBinding.etCommand.setTextColor(black)
            overlayBinding.etCommand.setHintTextColor(gray)
            overlayBinding.etCommand.setBackgroundResource(R.drawable.btn_bg_white)
            
            overlayBinding.btnSshLoginBack.setTextColor(black)
            overlayBinding.btnSshLoginBack.background = null
            overlayBinding.btnSshTerminalBack.setTextColor(black)
            overlayBinding.btnSshTerminalBack.setBackgroundResource(R.drawable.status_box_bg_white)
        }

        dialog.window?.let { window ->
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            
            // Ensure the dialog window fills the entire screen
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            
            // To allow SOFT_INPUT_ADJUST_RESIZE to work, we must NOT use FLAG_FULLSCREEN 
            // on the window itself if we want it to resize. 
            // Instead, we use systemUiVisibility flags and ensure the root layout fits system windows.
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        // Set green brackets for buttons
        if (isHighContrast) {
            overlayBinding.btnConnect.text = "CONNECT"
            overlayBinding.btnSshLoginBack.text = "ABORT"
            overlayBinding.btnSshTerminalBack.text = "BACK"
        } else {
            overlayBinding.btnConnect.text = android.text.Html.fromHtml("<font color='#00FF41'>[</font> CONNECT <font color='#00FF41'>]</font>", android.text.Html.FROM_HTML_MODE_LEGACY)
            overlayBinding.btnSshLoginBack.text = "ABORT"
            overlayBinding.btnSshTerminalBack.text = "BACK"
        }

        val prefs = getSharedPreferences("ssh_prefs", android.content.Context.MODE_PRIVATE)
        val historyJson = prefs.getString("host_history", "[]") ?: "[]"
        val historyList = try {
            val jsonArray = org.json.JSONArray(historyJson)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) { emptyList<String>() }

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, historyList)
        overlayBinding.etHost.setAdapter(adapter)

        overlayBinding.etHost.setText(prefs.getString("last_host", ""))
        overlayBinding.etUser.setText(prefs.getString("last_user", ""))

        overlayBinding.btnSshLoginBack.setOnClickListener { 
            dialog.dismiss()
        }

        overlayBinding.btnSshTerminalBack.setOnClickListener { 
            dialog.dismiss()
        }

        // Start Glitch Effect for SSH Window
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val glitchRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                
                val decorView = dialog.window?.decorView
                val isCritical = (1..15).random() == 1
                
                if (isCritical) {
                    decorView?.translationX = ((-8..8).random()).toFloat()
                    decorView?.translationY = ((-4..4).random()).toFloat()
                    
                    handler.postDelayed({
                        decorView?.translationX = 0f
                        decorView?.translationY = 0f
                        val nextDelay = (500..2500).random().toLong()
                        handler.postDelayed(this, nextDelay)
                    }, (50..120).random().toLong())
                } else {
                    handler.postDelayed(this, (200..600).random().toLong())
                }
            }
        }
        handler.post(glitchRunnable)

        overlayBinding.btnConnect.setOnClickListener {
            val host = overlayBinding.etHost.text.toString()
            val user = overlayBinding.etUser.text.toString()
            val pass = overlayBinding.etPassword.text.toString()

            if (host.isBlank() || user.isBlank()) return@setOnClickListener

            // Save history
            val updatedHistory = (listOf(host) + historyList).distinct().take(5)
            prefs.edit().apply {
                putString("host_history", org.json.JSONArray(updatedHistory).toString())
                putString("last_host", host)
                putString("last_user", user)
                apply()
            }

            overlayBinding.btnConnect.isEnabled = false
            if (isHighContrast) {
                overlayBinding.btnConnect.text = "CONNECTING..."
            } else {
                overlayBinding.btnConnect.text = "CONNECTING..."
            }

            Thread {
                try {
                    val jsch = com.jcraft.jsch.JSch()
                    val session = jsch.getSession(user, host, 22)
                    session.setPassword(pass)
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.connect(10000)

                    val channel = session.openChannel("shell") as com.jcraft.jsch.ChannelShell
                    channel.connect()

                    sshSession = session
                    sshChannel = channel
                    sshIn = channel.inputStream
                    sshOut = channel.outputStream

                    runOnUiThread {
                        overlayBinding.sshLoginPage.visibility = View.GONE
                        overlayBinding.sshTerminalPage.visibility = View.VISIBLE
                        
                        // Maintains keyboard compatibility by not forcing layout_fullscreen
                        dialog.window?.let { win ->
                            win.decorView.systemUiVisibility = (
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            )
                        }

                        overlayBinding.btnConnect.isEnabled = true
                        if (isHighContrast) {
                            overlayBinding.btnConnect.text = "CONNECT"
                        } else {
                            overlayBinding.btnConnect.text = "CONNECT"
                        }
                        
                        // Hide keyboard
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(overlayBinding.etCommand.windowToken, 0)
                        overlayBinding.etCommand.clearFocus()

                        startTerminalReader(overlayBinding)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        overlayBinding.btnConnect.isEnabled = true
                        overlayBinding.btnConnect.text = if (isHighContrast) "ERR: RE-TRY" else "ERR: RE-TRY"
                        Toast.makeText(this@MainActivity, "SSH Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        // Auto-scroll terminal when keyboard appears (layout resizes)
        overlayBinding.svTerminal.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                overlayBinding.svTerminal.post {
                    overlayBinding.svTerminal.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        val sendRaw = { bytes: ByteArray ->
            sshExecutor.execute {
                try {
                    sshOut?.write(bytes)
                    sshOut?.flush()
                } catch (e: Exception) {}
            }
        }

        overlayBinding.btnUp.setOnClickListener { sendRaw(byteArrayOf(27, 91, 65)) }
        overlayBinding.btnDown.setOnClickListener { sendRaw(byteArrayOf(27, 91, 66)) }
        overlayBinding.btnRight.setOnClickListener { sendRaw(byteArrayOf(27, 91, 67)) }
        overlayBinding.btnLeft.setOnClickListener { sendRaw(byteArrayOf(27, 91, 68)) }
        overlayBinding.btnTab.setOnClickListener { 
            sendRaw(byteArrayOf(9))
            blinkPink(overlayBinding.btnTab)
        }
        overlayBinding.btnCtrlC.setOnClickListener { 
            sendRaw(byteArrayOf(3))
            blinkPink(overlayBinding.btnCtrlC)
        }

        var isCtrlPressed = false
        overlayBinding.btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            if (isHighContrast) {
                overlayBinding.btnCtrl.setBackgroundResource(
                    if (isCtrlPressed) R.drawable.status_box_bg_pink else R.drawable.btn_bg_white
                )
                overlayBinding.btnCtrl.setTextColor(Color.BLACK)
            } else {
                overlayBinding.btnCtrl.setBackgroundResource(
                    if (isCtrlPressed) R.drawable.btn_bg else R.drawable.btn_bg_dim
                )
                overlayBinding.btnCtrl.setTextColor(
                    if (isCtrlPressed) android.graphics.Color.BLACK else android.graphics.Color.parseColor("#00FF41")
                )
            }
        }

        overlayBinding.etCommand.setOnEditorActionListener { v, actionId, event ->
            val rawCmd = overlayBinding.etCommand.text.toString()
            overlayBinding.etCommand.setText("")
            
            sshExecutor.execute {
                try {
                    if (isCtrlPressed && rawCmd.isNotEmpty()) {
                        val char = rawCmd[0].lowercaseChar()
                        if (char in 'a'..'z') {
                            // Control characters are 1-26 (A-Z)
                            val ctrlChar = (char.toInt() - 'a'.toInt() + 1).toByte()
                            sshOut?.write(byteArrayOf(ctrlChar))
                        } else {
                            sshOut?.write((rawCmd + "\n").toByteArray())
                        }
                        
                        runOnUiThread {
                            isCtrlPressed = false
                            if (isHighContrast) {
                                overlayBinding.btnCtrl.setBackgroundResource(R.drawable.btn_bg_white)
                                overlayBinding.btnCtrl.setTextColor(Color.BLACK)
                            } else {
                                overlayBinding.btnCtrl.setBackgroundResource(R.drawable.btn_bg_dim)
                                overlayBinding.btnCtrl.setTextColor(android.graphics.Color.parseColor("#00FF41"))
                            }
                        }
                    } else {
                        sshOut?.write((rawCmd + "\n").toByteArray())
                    }
                    sshOut?.flush()
                } catch (e: Exception) {}
            }
            true
        }

        // If session exists, skip login and restore buffer
        if (sshChannel?.isConnected == true) {
            overlayBinding.sshLoginPage.visibility = View.GONE
            overlayBinding.sshTerminalPage.visibility = View.VISIBLE
            
            // Re-apply immersive mode for existing session
            dialog.window?.let { win ->
                win.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }

            overlayBinding.tvTerminalOutput.text = terminalOutputBuffer.toString()
            overlayBinding.svTerminal.post {
                overlayBinding.svTerminal.fullScroll(View.FOCUS_DOWN)
            }
            startTerminalReader(overlayBinding)
        }

        dialog.show()
    }

    private fun startTerminalReader(overlayBinding: com.radar.blewifi.databinding.TerminalOverlayBinding) {
        Thread {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(sshIn))
            while (sshChannel?.isConnected == true) {
                try {
                    val charBuffer = CharArray(1024)
                    if (reader.ready()) {
                        val charsRead = reader.read(charBuffer)
                        if (charsRead > 0) {
                            val text = String(charBuffer, 0, charsRead)
                            runOnUiThread {
                                terminalOutputBuffer.append(text)
                                // Keep buffer reasonable size
                                if (terminalOutputBuffer.length > 10000) {
                                    terminalOutputBuffer.delete(0, terminalOutputBuffer.length - 8000)
                                }
                                overlayBinding.tvTerminalOutput.text = terminalOutputBuffer.toString()
                                overlayBinding.svTerminal.post {
                                    overlayBinding.svTerminal.fullScroll(View.FOCUS_DOWN)
                                }
                            }
                        }
                    }
                    Thread.sleep(50)
                } catch (e: Exception) { break }
            }
        }.start()
    }

    private fun startLogoAnimation() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val glitchRunnable = object : Runnable {
            override fun run() {
                val duration = (50..150).random().toLong()
                val isCritical = (1..10).random() == 1
                
                // Jitter effect
                binding.ivLogo.translationX = ((-5..5).random()).toFloat()
                binding.ivLogo.translationY = ((-2..2).random()).toFloat()
                
                if (isCritical) {
                    binding.ivLogo.setColorFilter(if ((1..2).random() == 1) Color.CYAN else Color.RED)
                    binding.ivLogo.scaleX = 1.1f
                } else {
                    binding.ivLogo.clearColorFilter()
                    binding.ivLogo.scaleX = 1.0f
                }

                handler.postDelayed({
                    binding.ivLogo.translationX = 0f
                    binding.ivLogo.translationY = 0f
                    binding.ivLogo.clearColorFilter()
                    binding.ivLogo.scaleX = 1.0f
                    
                    val nextGlitch = if (isCritical) (500..2000).random() else (100..500).random()
                    handler.postDelayed(this, nextGlitch.toLong())
                }, duration)
            }
        }
        handler.post(glitchRunnable)

        // Subtler neon pulse combined
        val neonAnim = android.animation.ValueAnimator.ofFloat(0.7f, 1f)
        neonAnim.duration = 2000
        neonAnim.repeatCount = android.animation.ValueAnimator.INFINITE
        neonAnim.repeatMode = android.animation.ValueAnimator.REVERSE
        neonAnim.addUpdateListener { anim ->
            binding.ivLogo.alpha = anim.animatedValue as Float
        }
        neonAnim.start()
    }

    private fun showChangePinDialog() {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar else android.R.style.Theme_Black_NoTitleBar)
        dialog.setOnDismissListener { setupImmersiveMode() }
        
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(if (isHighContrast) Color.WHITE else Color.BLACK)
            setPadding(60, 60, 60, 60)
            gravity = android.view.Gravity.CENTER
        }

        val title = android.widget.TextView(this).apply {
            text = " ACCESS CONTROL // PIN "
            setTextColor(if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF"))
            textSize = 20f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        root.addView(title)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentPin = prefs.getString("secret_pin", "123456") ?: "123456"

        val pinInput = android.widget.EditText(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setText(currentPin)
            setTextColor(if (isHighContrast) Color.BLACK else Color.WHITE)
            setBackgroundColor(if (isHighContrast) Color.WHITE else Color.parseColor("#111111"))
            if (isHighContrast) setBackgroundResource(R.drawable.status_box_bg_white)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 24f
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        }
        root.addView(pinInput)

        val saveBtn = android.widget.Button(this).apply {
            text = "UPDATE ACCESS CODE"
            setTextColor(if (isHighContrast) Color.WHITE else Color.BLACK)
            setBackgroundColor(if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setPadding(40, 30, 40, 30)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 40, 0, 0)
            layoutParams = params
        }
        root.addView(saveBtn)

        saveBtn.setOnClickListener {
            val newPin = pinInput.text.toString().trim()
            if (newPin.isNotEmpty()) {
                prefs.edit().putString("secret_pin", newPin).apply()
                Toast.makeText(this, "PIN UPDATED", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun showInfoDialog() {
        val title = "[ BLESP v3 ]"
        val subtitle = "Advanced Signal Analytics - v3 Beta 13"
        val disclaimer = "\"This tool is intended for authorized security testing and red-team operations only. Unauthorized use is strictly prohibited.\""
        val body = """
            Made by: Chris Édgar
            
            Website: www.mostlyawesome.de

            Mail: service@mostlyawesome.de
            
            YouTube: youtube.com/@mostlyAWESOME.Media
        """.trimIndent()

        // Added triple newline after subtitle and double newline at the end for padding
        val fullText = "$title\n$subtitle\n\n$disclaimer\n\n\n$body\n\n"
        val spannable = android.text.SpannableStringBuilder(fullText)
        
        // Style subtitle
        val subStart = fullText.indexOf(subtitle)
        val subEnd = subStart + subtitle.length
        if (subStart >= 0) {
            spannable.setSpan(
                android.text.style.RelativeSizeSpan(0.7f),
                subStart, subEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Style disclaimer
        val discStart = fullText.indexOf(disclaimer)
        val discEnd = discStart + disclaimer.length
        if (discStart >= 0) {
            spannable.setSpan(
                android.text.style.RelativeSizeSpan(0.5f),
                discStart, discEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                discStart, discEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF00FF")),
                discStart, discEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val titleText = "PROJECT INFO"
        val spannableTitle = android.text.SpannableString(titleText)
        spannableTitle.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF00FF")),
            0, titleText.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(spannableTitle)
            .setMessage(spannable)
            .setPositiveButton("OK", null)
            .show()
        dialog.setOnDismissListener { setupImmersiveMode() }

        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)

        // Background styling
        val bg = android.graphics.drawable.GradientDrawable()
        if (isHighContrast) {
            bg.setColor(android.graphics.Color.WHITE)
            bg.setStroke(4, android.graphics.Color.BLACK)
        } else {
            bg.setColor(android.graphics.Color.parseColor("#99002200")) // Translucent dark green
            bg.setStroke(2, android.graphics.Color.parseColor("#00FF41")) // Neon border
        }
        bg.cornerRadius = 60f
        dialog.window?.setBackgroundDrawable(bg)

        // Make links clickable and set colors
        val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        messageView?.let {
            it.autoLinkMask = android.text.util.Linkify.WEB_URLS or android.text.util.Linkify.EMAIL_ADDRESSES
            it.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            if (isHighContrast) {
                it.setTextColor(android.graphics.Color.BLACK)
                it.setLinkTextColor(android.graphics.Color.BLACK)
            } else {
                it.setTextColor(android.graphics.Color.parseColor("#00FF41"))
            }
            it.typeface = android.graphics.Typeface.MONOSPACE
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(if (isHighContrast) android.graphics.Color.BLACK else android.graphics.Color.parseColor("#FF00FF"))
            it.setTypeface(null, android.graphics.Typeface.BOLD)
            val params = it.layoutParams as android.widget.LinearLayout.LayoutParams
            params.bottomMargin = (10 * resources.displayMetrics.density).toInt()
            it.layoutParams = params
        }

        // Glitch effect for the dialog
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val glitchRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                
                val decorView = dialog.window?.decorView
                val isCritical = (1..15).random() == 1
                
                if (isCritical) {
                    decorView?.translationX = ((-10..10).random()).toFloat()
                    decorView?.translationY = ((-5..5).random()).toFloat()
                    messageView?.alpha = 0.5f
                    
                    handler.postDelayed({
                        decorView?.translationX = 0f
                        decorView?.translationY = 0f
                        messageView?.alpha = 1.0f
                        handler.postDelayed(this, (500..2000).random().toLong())
                    }, (50..150).random().toLong())
                } else {
                    handler.postDelayed(this, (100..500).random().toLong())
                }
            }
        }
        handler.post(glitchRunnable)
    }

    private fun setupListView() {
        deviceAdapter = DeviceAdapter { device -> showDeviceDetail(device) }
        deviceAdapter.isHighContrastMode = isHighContrastMode
        binding.listView.layoutManager = LinearLayoutManager(this)
        binding.listView.adapter = deviceAdapter
    }

    private fun updateViewVisibility() {
        if (isListView) {
            binding.radarContainer.visibility = View.GONE
            binding.listView.visibility = View.VISIBLE
            binding.infoContainer.visibility = View.GONE
            binding.btnKillswitch.visibility = View.VISIBLE
            binding.btnGlobeListTop.setImageResource(R.drawable.ic_back)
            setActiveHeaderButton(binding.btnGlobeListTop)

            // Auto-scroll to selected device
            deviceAdapter.getSelectedId()?.let { selectedId ->
                val index = displayedDevices.indexOfFirst { it.id == selectedId }
                if (index != -1) {
                    binding.listView.scrollToPosition(index)
                }
            }
        } else {
            binding.radarContainer.visibility = View.VISIBLE
            binding.listView.visibility = View.GONE
            binding.infoContainer.visibility = View.VISIBLE
            binding.btnKillswitch.visibility = View.VISIBLE
            binding.btnGlobeListTop.setImageResource(R.drawable.ic_globe)
            setActiveHeaderButton(binding.btnGlobeListTop)
        }
    }

    private fun setupMapView() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.zoomController.display.setPositions(false, CustomZoomButtonsDisplay.HorizontalPosition.RIGHT, CustomZoomButtonsDisplay.VerticalPosition.CENTER)
        val mapController = binding.mapView.controller
        mapController.setZoom(18.0)

        compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), binding.mapView)
        compassOverlay?.enableCompass()
        binding.mapView.overlays.add(compassOverlay)

        // Add a scale bar
        val scaleBarOverlay = ScaleBarOverlay(binding.mapView)
        scaleBarOverlay.setCentred(true)
        scaleBarOverlay.setScaleBarOffset(200, 10)
        binding.mapView.overlays.add(scaleBarOverlay)
    }

    private val markers = mutableMapOf<String, Marker>()
    private val iconCache = mutableMapOf<String, Drawable>()

    private fun updateMapMarkers(devices: List<ScanDevice>) {
        val userLocation = scanner.lastLocation ?: return
        val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

        // Remove markers for devices no longer in range
        val currentIds = devices.map { it.id }.toSet()
        val toRemove = markers.keys.filter { it !in currentIds }
        toRemove.forEach { id ->
            markers[id]?.let { binding.mapView.overlays.remove(it) }
            markers.remove(id)
        }

        devices.forEach { device ->
            val dist = device.distanceMeters
            // Estimate position based on distance and device's radar angle
            // Note: angle in ScanDevice is 0-360 relative to device heading
            // We need to convert it to a GeoPoint relative to userGeoPoint
            
            // Adjust angle by device rotation to get true bearing
            val bearing = (device.angle + binding.radarView.rotationDegrees) % 360.0
            val destination = calculateDestinationPoint(userGeoPoint, dist, bearing)

            var marker = markers[device.id]
            if (marker == null) {
                marker = Marker(binding.mapView)
                marker.id = device.id
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                markers[device.id] = marker
                binding.mapView.overlays.add(marker)
            }

            marker.position = destination
            marker.title = device.displayName
            marker.snippet = "${device.typeLabel} | ${device.distanceLabel} | RSSI: ${device.rssi}"
            
            // Custom Icon based on device type and status - cached
            marker.icon = getCachedMarkerIcon(device)

            marker.setOnMarkerClickListener { m, _ ->
                m.showInfoWindow()
                binding.radarView.selectedId = m.id
                binding.radarView.invalidate()
                showDeviceDetail(device)
                true
            }
        }
        binding.mapView.invalidate()
    }

    private fun getCachedMarkerIcon(device: ScanDevice): Drawable {
        val isWep = device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")
        val cacheKey = when {
            isWep -> "WEP"
            device.isAirTag -> "AIRTAG"
            else -> device.type.name
        }
        
        return iconCache.getOrPut(cacheKey) { createMarkerIcon(device) }
    }

    private fun createMarkerIcon(device: ScanDevice): Drawable {
        val size = 40
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val baseColor = when (device.type) {
            DeviceType.WIFI, DeviceType.PAGER -> Color.parseColor("#00FF41") // Green
            DeviceType.BLE, DeviceType.CAR, DeviceType.ESCOOTER, DeviceType.TV, DeviceType.COMPUTER, DeviceType.SMARTPHONE -> Color.parseColor("#FF00FF")  // Pink
            DeviceType.AIRCRAFT, DeviceType.DRONE -> Color.parseColor("#00FFFF") // Cyan
            DeviceType.LTE, DeviceType.FIVE_G -> Color.parseColor("#FFB300") // Amber
        }

        // Blinking logic for Map markers too?
        // For simplicity, let's just use the static color or the blink state if we had a global timer
        var finalColor = baseColor
        if (device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")) {
            finalColor = Color.parseColor("#FF00FF") // Pink for WEP
        } else if (device.isAirTag) {
            finalColor = Color.WHITE
        }

        paint.color = finalColor
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.BLACK
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun calculateDestinationPoint(start: GeoPoint, distanceMeters: Double, bearingDegrees: Double): GeoPoint {
        val radiusEarthKm = 6371.0
        val distKm = distanceMeters / 1000.0
        val brng = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)

        val lat2 = Math.asin(Math.sin(lat1) * Math.cos(distKm / radiusEarthKm) +
                Math.cos(lat1) * Math.sin(distKm / radiusEarthKm) * Math.cos(brng))
        val lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(distKm / radiusEarthKm) * Math.cos(lat1),
                Math.cos(distKm / radiusEarthKm) - Math.sin(lat1) * Math.sin(lat2))

        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    override fun onResume() {
        super.onResume()
        binding.radarView.startAnimation()
        binding.mapView.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        setupPagerNotificationReceiver()
        if (getSharedPreferences("pager_history", MODE_PRIVATE).getBoolean("has_unread", false)) {
            startPagerBreathing()
        } else {
            stopPagerBreathing()
        }
    }

    private fun setupPagerNotificationReceiver() {
        if (pagerNotificationReceiver == null) {
            pagerNotificationReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.radar.blewifi.ACTION_NEW_MESSAGE") {
                        startPagerBreathing()
                    }
                }
            }
            val filter = android.content.IntentFilter("com.radar.blewifi.ACTION_NEW_MESSAGE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pagerNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(pagerNotificationReceiver, filter)
            }
        }
    }

    private fun startPagerBreathing() {
        if (pagerBreatheAnim != null && pagerBreatheAnim!!.isRunning) return
        
        pagerBreatheAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            val green = Color.parseColor("#00FF41")
            val pink = Color.parseColor("#FF00FF")
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val r = (Color.red(green) * (1 - fraction) + Color.red(pink) * fraction).toInt()
                val g = (Color.green(green) * (1 - fraction) + Color.green(pink) * fraction).toInt()
                val b = (Color.blue(green) * (1 - fraction) + Color.blue(pink) * fraction).toInt()
                binding.btnPager.setColorFilter(Color.rgb(r, g, b))
            }
            start()
        }
    }

    private fun stopPagerBreathing() {
        pagerBreatheAnim?.cancel()
        pagerBreatheAnim = null
        val color = if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41")
        binding.btnPager.setColorFilter(color)
    }

    override fun onPause() {
        super.onPause()
        binding.radarView.stopAnimation()
        binding.mapView.onPause()
        sensorManager.unregisterListener(sensorListener)
        pagerNotificationReceiver?.let { 
            unregisterReceiver(it)
            pagerNotificationReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScanning()
        sshExecutor.shutdown()
        pagerNotificationReceiver?.let { unregisterReceiver(it) }
        try {
            sshChannel?.disconnect()
            sshSession?.disconnect()
        } catch (e: Exception) {}
    }

    override fun onLocationUpdated(lat: Double, lon: Double) {
        runOnUiThread {
            binding.tvCoordinates.text = String.format(java.util.Locale.US, "LAT: %.6f LON: %.6f", lat, lon)
            binding.radarView.setUserLocation(lat, lon)
            val startPoint = GeoPoint(lat, lon)
            binding.mapView.controller.animateTo(startPoint)
        }
    }

    private fun checkAndStartScanning() {
        val missingPerms = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            permLauncher.launch(missingPerms.toTypedArray())
            return
        }
        // Check BT enabled
        val btMgr = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (btMgr?.adapter?.isEnabled == false) {
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        startScanning()
    }

    private fun startTimecodeRunner() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss:SSS", java.util.Locale.US)
        val runnable = object : Runnable {
            override fun run() {
                binding.tvTimecode.text = timeFormat.format(java.util.Date().time)
                handler.postDelayed(this, 33) // ~30fps refresh is plenty for timecode
            }
        }
        handler.post(runnable)
    }

    private fun startScanning() {
        scanner.startScanning()
    }

    private fun stopScanning() {
        scanner.stopScanning()
        isScanning = false
        binding.btnScan.text = "[ SCAN ]"
        binding.statusText.text = "STANDBY"
    }

    private fun showDeviceDetail(device: ScanDevice) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra(DetailActivity.EXTRA_DEVICE, device)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showError(msg: String) {
        binding.statusText.text = "ERR: ${msg.take(40)}"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private var lastListUpdate = 0L
    private var displayedDevices = listOf<ScanDevice>()

    override fun onDevicesUpdated(devices: List<ScanDevice>) {
        runOnUiThread {
            val now = System.currentTimeMillis()
            
            val filteredForRadar = devices.filter { 
                when (it.type) {
                    DeviceType.BLE, DeviceType.CAR, DeviceType.ESCOOTER, 
                    DeviceType.TV, DeviceType.COMPUTER, DeviceType.SMARTPHONE, DeviceType.PAGER -> binding.cbBle.isChecked
                    DeviceType.AIRCRAFT, DeviceType.DRONE -> binding.cbAero.isChecked
                    DeviceType.LTE, DeviceType.FIVE_G -> binding.cbLte.isChecked
                    DeviceType.WIFI -> {
                        val is24 = it.frequency in 2400..2500
                        val is5 = it.frequency in 5000..6000
                        (is24 && binding.cb24.isChecked) || (is5 && binding.cb5.isChecked)
                    }
                }
            }.sortedBy { it.distanceMeters }

            val filteredForList = filteredForRadar

            // Only update UI if we're not in list view or if enough time has passed
            // to avoid thrashing the UI with high-frequency updates
            if (isListView) {
                // In list view, update less frequently to save CPU
                if (now - lastListUpdate < 1000) return@runOnUiThread
            }

            binding.radarView.setDevices(filteredForRadar) // Radar remains real-time
            
            // Limit marker updates as they are expensive on performance
            if (!isListView) {
                updateMapMarkers(filteredForRadar) 
            }
            
            // Freeze the list for 5 seconds to make it easier to click
            if (now - lastListUpdate >= 5000 || displayedDevices.isEmpty()) {
                displayedDevices = filteredForList
                deviceAdapter.submitList(displayedDevices)
                lastListUpdate = now
            }

            val bleC  = filteredForRadar.count { it.type == DeviceType.BLE }
            val wifiC = filteredForRadar.count { it.type == DeviceType.WIFI }
            val lteC  = filteredForRadar.count { it.type == DeviceType.LTE || it.type == DeviceType.FIVE_G }
            val carC  = filteredForRadar.count { it.type == DeviceType.CAR }
            val tvC   = filteredForRadar.count { it.type == DeviceType.TV }
            val compC = filteredForRadar.count { it.type == DeviceType.COMPUTER }
            val phnC  = filteredForRadar.count { it.type == DeviceType.SMARTPHONE }
            val aeroC = filteredForRadar.count { it.type == DeviceType.AIRCRAFT }

            val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
            val numColor = if (isHighContrast) Color.BLACK else Color.parseColor("#00FF41")
            val targetColor = if (isHighContrast) Color.BLACK else Color.parseColor("#FF00FF")
            val aeroNumColor = if (isHighContrast) Color.BLACK else Color.parseColor("#00FFFF")

            // Build status string with highlighted numbers
            val sb = android.text.SpannableStringBuilder()
            
            fun appendStat(label: String, count: Int, color: Int) {
                if (count > 0 || label == "BLE" || label == "WIFI") {
                    if (sb.isNotEmpty()) sb.append(" | ")
                    sb.append("$label: ")
                    val start = sb.length
                    sb.append(count.toString())
                    sb.setSpan(android.text.style.ForegroundColorSpan(color), start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (isHighContrast) sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            appendStat("BLE", bleC, numColor)
            appendStat("WIFI", wifiC, numColor)
            appendStat("LTE", lteC, numColor)
            appendStat("CAR", carC, targetColor)
            appendStat("TV", tvC, targetColor)
            appendStat("PC", compC, targetColor)
            appendStat("PHN", phnC, targetColor)
            
            if (binding.cbAero.isChecked) {
                appendStat("AERO", aeroC, aeroNumColor)
            }

            if (sb.isNotEmpty()) sb.append(" | ")
            sb.append("TOTAL: ")
            val startTotal = sb.length
            sb.append(filteredForRadar.size.toString())
            sb.setSpan(android.text.style.ForegroundColorSpan(numColor), startTotal, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (isHighContrast) sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), startTotal, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            binding.statusText.text = sb
            
            // Subtle flicker effect on the status box
            if (Math.random() > 0.9) {
                binding.statusText.animate().alpha(0.7f).setDuration(50).withEndAction {
                    binding.statusText.animate().alpha(1f).setDuration(50).start()
                }.start()
            }
        }
    }

    private fun startGraphUpdates() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                val cpuLoad = getCpuLoad()
                val gpuLoad = getGpuLoad()
                binding.miniGraph.addData(cpuLoad, gpuLoad)
                
                val allDevices = scanner.getDevices()
                val wifiRssi = allDevices.filter { it.type == DeviceType.WIFI }.maxOfOrNull { it.rssi } ?: -100
                val bleRssi = allDevices.filter { it.type == DeviceType.BLE }.maxOfOrNull { it.rssi } ?: -100
                // Map RSSI (-100..-30) to 0..100 scale for the EQ bars
                val wifiScale = ((wifiRssi + 100).coerceIn(0, 70) / 70f * 100f)
                val bleScale = ((bleRssi + 100).coerceIn(0, 70) / 70f * 100f)
                binding.netGraph.addData(wifiScale, bleScale)
                
                handler.postDelayed(this, 300) // Fast 300ms update for smooth movement
            }
        })
    }

    private fun getCpuLoad(): Float {
        // Real-time system-wide CPU load is restricted on modern Android.
        // We simulate a realistic load between 15% and 75%.
        return (15..55).random().toFloat() + (Math.random() * 20).toFloat()
    }

    private fun getGpuLoad(): Float {
        // GPU load simulation between 10% and 40%.
        return (10..30).random().toFloat() + (Math.random() * 10).toFloat()
    }

    override fun onScanStatusChanged(scanning: Boolean) {
        runOnUiThread {
            isScanning = scanning
            val pink = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
            val stopBg = if (isHighContrastMode) R.drawable.btn_bg_white_red else R.drawable.btn_bg_red
            val scanBg = if (isHighContrastMode) R.drawable.btn_bg_white else R.drawable.btn_bg
            
            if (scanning) {
                setCyberText(binding.btnScan, "[ STOP ]", pink)
                binding.btnScan.setBackgroundResource(stopBg)
                startStatusPulseAnimation(slow = true)
            } else {
                setCyberText(binding.btnScan, "[ SCAN ]", pink)
                binding.btnScan.setBackgroundResource(scanBg)
                startStatusPulseAnimation(slow = false)
            }
            if (!scanning) {
                binding.statusText.text = "STANDBY"
            }
        }
    }

    private fun stopStatusPulseAnimation() {
        statusPulseAnim?.cancel()
        binding.statusText.alpha = 1.0f
    }

    private fun startStatusPulseAnimation(slow: Boolean) {
        statusPulseAnim?.cancel()
        statusPulseAnim = android.animation.ObjectAnimator.ofFloat(binding.statusText, "alpha", 1.0f, 0.4f).apply {
            duration = if (slow) 1000 else 400
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }
    }

    private fun startBeep() {
        if (isBeeping) return
        isBeeping = true
        
        startNoiseBlinking()
        
        val sampleRate = 44100
        val freq = 15800.0
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
            AudioTrack.MODE_STREAM
        )

        val samples = ShortArray(minBufferSize)
        var angle = 0.0
        
        audioTrack?.play()
        
        Thread {
            while (isBeeping) {
                for (i in samples.indices) {
                    samples[i] = (kotlin.math.sin(angle) * Short.MAX_VALUE).toInt().toShort()
                    angle += 2.0 * kotlin.math.PI * freq / sampleRate
                }
                angle %= 2.0 * kotlin.math.PI
                audioTrack?.write(samples, 0, samples.size)
            }
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }.start()
    }

    private fun stopBeep() {
        isBeeping = false
        stopNoiseBlinking()
    }

    private fun startNoiseBlinking() {
        binding.btnBeep.textSize = 12f
        
        noisePulsator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.btnBeep,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f)
        ).apply {
            duration = 300
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            start()
        }

        startGlitchEffect()

        val blinker = object : Runnable {
            var isPink = true
            override fun run() {
                if (!isBeeping) return
                if (isPink) {
                    binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_pink)
                    val color = if (isHighContrastMode) Color.parseColor("#FF00FF") else Color.parseColor("#FF00FF")
                    binding.btnBeep.setTextColor(color)
                    binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color))
                } else {
                    binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_green)
                    val color = if (isHighContrastMode) Color.BLACK else Color.parseColor("#00FF41")
                    binding.btnBeep.setTextColor(color)
                    binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color))
                }
                isPink = !isPink
                handler.postDelayed(this, 300)
            }
        }
        noiseBlinkRunnable = blinker
        handler.post(blinker)
    }

    private fun startGlitchEffect() {
        val originalText = "NOISEGENERATOR"
        val glitchChars = "¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ"
        glitchRunnable = object : Runnable {
            override fun run() {
                if (!isBeeping) return
                
                val sb = StringBuilder()
                for (char in originalText) {
                    if (java.util.Random().nextFloat() < 0.15f) {
                        sb.append(glitchChars[java.util.Random().nextInt(glitchChars.length)])
                    } else {
                        sb.append(char)
                    }
                }
                binding.btnBeep.text = sb.toString()
                
                // Random translation glitch
                binding.btnBeep.translationX = (java.util.Random().nextFloat() * 10f) - 5f
                binding.btnBeep.translationY = (java.util.Random().nextFloat() * 6f) - 3f
                
                val nextDelay = (50 + java.util.Random().nextInt(150)).toLong()
                handler.postDelayed(this, nextDelay)
            }
        }
        handler.post(glitchRunnable!!)
    }

    private fun stopNoiseBlinking() {
        handler.removeCallbacks(noiseBlinkRunnable ?: return)
        handler.removeCallbacks(glitchRunnable ?: return)
        noisePulsator?.cancel()
        noisePulsator = null
        binding.btnBeep.scaleX = 1.0f
        binding.btnBeep.scaleY = 1.0f
        binding.btnBeep.translationX = 0f
        binding.btnBeep.translationY = 0f
        binding.btnBeep.text = "[ NOISEGENERATOR ]"
        binding.btnBeep.textSize = 10f
        if (isHighContrastMode) {
            binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_white)
            binding.btnBeep.setTextColor(Color.BLACK)
            binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.BLACK))
        } else {
            binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_green)
            binding.btnBeep.setTextColor(Color.parseColor("#00FF41"))
            binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF41")))
        }
    }

    override fun onError(msg: String) {
        runOnUiThread { showError(msg) }
    }

    override fun onMovementDetected(device: ScanDevice) {
        runOnUiThread {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            v?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

class DeviceAdapter(private val onClick: (ScanDevice) -> Unit) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
    private var list = listOf<ScanDevice>()
    private var selectedId: String? = null
    var isHighContrastMode = false

    fun submitList(newList: List<ScanDevice>) {
        list = newList
        notifyDataSetChanged()
    }

    fun setSelectedId(id: String?) {
        selectedId = id
        notifyDataSetChanged()
    }

    fun getSelectedId(): String? = selectedId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = list[position]
        holder.name.text = device.displayName
        
        val isSelected = device.id == selectedId
        val selectionColor = if (isHighContrastMode) Color.parseColor("#33000000") else Color.parseColor("#33FF00FF")
        holder.itemView.setBackgroundColor(if (isSelected) selectionColor else Color.TRANSPARENT)
        
        val isWep = device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")
        val isWhisper = device.isVulnerableWhisperPair
        
        val nameColor = if (isHighContrastMode) {
            Color.BLACK
        } else if (device.type == DeviceType.BLE) {
            Color.parseColor("#0066FF") // Always Blue for BLE
        } else if (device.type == DeviceType.AIRCRAFT) {
            Color.parseColor("#00FFFF") // Cyan for Aircraft
        } else {
            val caps = device.capabilities.uppercase()
            val is5GHz = device.frequency in 5000..6000
            when {
                is5GHz -> Color.parseColor("#008F22") // Darker Green
                caps.contains("WPA") -> Color.parseColor("#00FF41") // Green
                else -> Color.WHITE // Open/Other
            }
        }
        
        holder.name.setTextColor(nameColor)
        holder.dist.text = device.distanceLabel
        
        // Show manufacturer logo behind the device info
        val isApple = device.manufacturer == "0x004C" || device.name.lowercase().contains("apple") || device.isAirTag
        val isAndroid = device.manufacturer == "0x00E0" || device.name.lowercase().contains("android") || device.isFastPair
        
        if (isApple) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(if (isHighContrastMode) R.drawable.ic_apple_black else R.drawable.ic_apple)
        } else if (isAndroid) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(if (isHighContrastMode) R.drawable.ic_android_black else R.drawable.ic_android)
        } else {
            holder.mfrLogo.visibility = View.GONE
        }
        
        // Pink and glowing for close proximity (< 1m)
        if (device.distanceMeters < 1.0) {
            val closeColor = if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF")
            holder.dist.setTextColor(closeColor)
            if (!isHighContrastMode) {
                holder.dist.setShadowLayer(15f, 0f, 0f, closeColor)
            } else {
                holder.dist.setShadowLayer(0f, 0f, 0f, 0)
            }
            
            // Pulsate animation for the "glowing" effect
            holder.dist.clearAnimation()
            val anim = android.view.animation.AlphaAnimation(0.5f, 1.0f).apply {
                duration = 400
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            holder.dist.startAnimation(anim)
        } else {
            holder.dist.setTextColor(if (isHighContrastMode) Color.BLACK else Color.parseColor("#FFB300")) // Original yellow or black
            holder.dist.setShadowLayer(0f, 0f, 0f, 0)
            holder.dist.clearAnimation()
            holder.dist.alpha = 1.0f
        }

        holder.addr.text = device.address
        holder.addr.setTextColor(if (isHighContrastMode) Color.DKGRAY else Color.GRAY)

        // Only show vulnerability text/alarms if it's WEP or WhisperPair
        if (isWep) {
            holder.alarm.visibility = View.VISIBLE
            holder.alarm.setTextColor(if (isHighContrastMode) Color.BLACK else Color.RED)
            // Simple blinking animation
            holder.alarm.clearAnimation()
            val anim = android.view.animation.AlphaAnimation(0.0f, 1.0f).apply {
                duration = 500
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            holder.alarm.startAnimation(anim)
        } else {
            holder.alarm.visibility = View.GONE
            holder.alarm.clearAnimation()
        }

        if (isWhisper) {
            holder.whisper.visibility = View.VISIBLE
            holder.whisper.setTextColor(if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF"))
            holder.whisper.clearAnimation()
            val anim = android.view.animation.AlphaAnimation(0.2f, 1.0f).apply {
                duration = 800
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            holder.whisper.startAnimation(anim)
        } else {
            holder.whisper.visibility = View.GONE
            holder.whisper.clearAnimation()
        }

        if (device.isAirTag) {
            holder.airtag.visibility = View.VISIBLE
            holder.airtag.setTextColor(if (isHighContrastMode) Color.BLACK else Color.WHITE)
        } else {
            holder.airtag.visibility = View.GONE
        }

        if (device.type == DeviceType.AIRCRAFT) {
            holder.aero.visibility = View.VISIBLE
            holder.aero.setTextColor(if (isHighContrastMode) Color.BLACK else Color.CYAN)
        } else {
            holder.aero.visibility = View.GONE
        }

        if (device.isCar) {
            holder.car.visibility = View.VISIBLE
            holder.car.setTextColor(if (isHighContrastMode) Color.BLACK else Color.parseColor("#FF00FF"))
        } else {
            holder.car.visibility = View.GONE
        }

        if (device.isPubliclyConnectable) {
            holder.hijack.visibility = View.VISIBLE
            holder.hijack.setTextColor(if (isHighContrastMode) Color.BLACK else Color.YELLOW)
        } else {
            holder.hijack.visibility = View.GONE
        }

        // Add additional vulnerability info if applicable (legacy, etc.)
        val extraVulns = mutableListOf<String>()
        if (device.isLegacyBluetooth) extraVulns.add("LEGACY")
        if (device.isVulnerableBlueWhisper) extraVulns.add("BW")
        if (device.isVulnerableCVE202536911) extraVulns.add("CVE-2025-36911")
        
        if (extraVulns.isNotEmpty()) {
            holder.vuln.visibility = View.VISIBLE
            holder.vuln.text = "[ ${extraVulns.joinToString(" | ")} ]"
            holder.vuln.setTextColor(if (isHighContrastMode) Color.BLACK else Color.YELLOW)
            
            // Blink for CVE
            if (device.isVulnerableCVE202536911) {
                val anim = android.view.animation.AlphaAnimation(0.2f, 1.0f).apply {
                    duration = 500
                    repeatMode = android.view.animation.Animation.REVERSE
                    repeatCount = android.view.animation.Animation.INFINITE
                }
                holder.vuln.startAnimation(anim)
            } else {
                holder.vuln.clearAnimation()
            }
        } else {
            holder.vuln.visibility = View.GONE
            holder.vuln.clearAnimation()
        }
        
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvDeviceName)
        val dist: TextView = view.findViewById(R.id.tvDeviceDist)
        val addr: TextView = view.findViewById(R.id.tvDeviceAddr)
        val alarm: TextView = view.findViewById(R.id.tvAlarm)
        val whisper: TextView = view.findViewById(R.id.tvWhisper)
        val vuln: TextView = view.findViewById(R.id.tvVulnerability)
        val airtag: TextView = view.findViewById(R.id.tvAirTag)
        val aero: TextView = view.findViewById(R.id.tvAeroIcon)
        val car: TextView = view.findViewById(R.id.tvCarIcon)
        val hijack: TextView = view.findViewById(R.id.tvHijackIcon)
        val mfrLogo: android.widget.ImageView = view.findViewById(R.id.ivManufacturerLogo)
    }
}
