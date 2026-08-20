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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private var currentTheme: RadarView.Theme = RadarView.Theme.DEFAULT

    private var audioTrack: AudioTrack? = null
    private var isBeeping = false
    private var noiseBlinkRunnable: Runnable? = null
    private var noisePulsator: android.animation.ObjectAnimator? = null
    private var glitchRunnable: Runnable? = null
    private var noiseVibrator: Vibrator? = null
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        val themeName = getSharedPreferences("settings", MODE_PRIVATE).getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name)
        
        scanner = ScannerManager(this)
        scanner.addListener(this)
        
        // Setup graphs initial colors
        if (currentTheme == RadarView.Theme.SUMMERTIME) {
            binding.netGraph.setColors("#ff9f6b", "#886befff")
            binding.miniGraph.setColors("#ff9f6b", "#886befff")
        } else {
            binding.netGraph.setColors("#00FF41", "#99FF00FF")
            binding.miniGraph.setColors("#00FF41", "#99FF00FF")
        }

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

        binding.cbAero.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.showAero = isChecked
        }

        binding.cbCams.setOnCheckedChangeListener { _, isChecked ->
            binding.radarView.showCams = isChecked
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
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnArchive.setOnClickListener {
            val intent = Intent(this, ArchiveActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnPager.setOnClickListener {
            stopPagerBreathing()
            getSharedPreferences("pager_history", MODE_PRIVATE).edit().putBoolean("has_unread", false).apply()
            val intent = Intent(this, PagerActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnEsl.setOnClickListener {
            val packageName = "com.mostlyawesome.tagtinker"
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchepasta/ESL-Tag-Tools"))
                startActivity(browserIntent)
            }
        }

        binding.btnThemeToggle.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnBeep.setOnClickListener {
            if (isBeeping) {
                stopBeep()
            } else {
                startBeep()
            }
        }

        binding.btnInfo.setOnClickListener {
            showAboutDialog()
        }

        // Initial tinting removed as updateThemeUI() handles it below

        setActiveButton(binding.btnScan)
        startLogoAnimation()
        startGraphUpdates()

        updateThemeUI()
    }

    private fun showAboutDialog() {
        val intent = Intent(this, AboutActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, options.toBundle())
    }

    private fun View.padding(dp: Int) {
        val px = (dp * resources.displayMetrics.density).toInt()
        setPadding(px, px, px, px)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isSshWindowOpen) {
            setupImmersiveMode()
        }
    }

    private fun updateThemeFromPrefs() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name)

        binding.radarView.theme = currentTheme
        deviceAdapter.theme = currentTheme
        deviceAdapter.notifyDataSetChanged()
        updateThemeUI()
    }

    private fun updateThemeUI() {
        val theme = currentTheme
        
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = theme == RadarView.Theme.RED_NIGHT
        val isPink = theme == RadarView.Theme.PINK
        val isNeon = theme == RadarView.Theme.NEON
        val isNaranja = theme == RadarView.Theme.NARANJA
        val isBubblegum = theme == RadarView.Theme.BUBBLEGUM
        val isSummertime = theme == RadarView.Theme.SUMMERTIME
        val isMorio = theme == RadarView.Theme.MORIO
        
        val bgColor = when {
            isHighContrast -> Color.WHITE
            isRedNight || isPink || isNeon || isNaranja || isBubblegum || isSummertime || isMorio -> Color.BLACK
            else -> Color.BLACK
        }
        val headerColor = when {
            isHighContrast -> Color.parseColor("#F0F0F0")
            isRedNight -> Color.parseColor("#0A0000")
            isPink -> Color.parseColor("#2A002A")
            isNeon -> Color.parseColor("#1A1A00")
            isNaranja -> Color.parseColor("#1A0F00")
            isBubblegum -> Color.parseColor("#1A001A")
            isSummertime -> Color.parseColor("#2A1F1A")
            isMorio -> Color.parseColor("#244f48")
            else -> Color.parseColor("#0A0A0A")
        }
        val textColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }
        val accentColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#00FDFF")
            isSummertime -> Color.parseColor("#6befff")
            isMorio -> Color.parseColor("#c8f29e")
            else -> Color.parseColor("#FF00FF")
        }

        binding.root.setBackgroundColor(bgColor)
        binding.headerContainer.setBackgroundColor(headerColor)
        binding.headerBar.setBackgroundColor(when {
            isHighContrast -> Color.LTGRAY
            isRedNight -> Color.parseColor("#1A0000")
            isPink -> Color.parseColor("#330033")
            isNeon -> Color.parseColor("#333300")
            isNaranja -> Color.parseColor("#331E00")
            isBubblegum -> Color.parseColor("#2A002A")
            isSummertime -> Color.parseColor("#1A1A1A")
            isMorio -> Color.parseColor("#0A1412")
            else -> Color.parseColor("#0F0F0F")
        })
        
        binding.statusText.setTextColor(textColor)
        binding.statusText.setBackgroundResource(when {
            isHighContrast -> R.drawable.status_box_bg_white
            isRedNight -> R.drawable.status_box_bg_red
            isPink -> R.drawable.status_box_bg_pink
            isNeon -> R.drawable.status_box_bg_neon
            isNaranja -> R.drawable.status_box_bg_naranja
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.btn_bg_summertime
            isMorio -> R.drawable.btn_bg_morio
            else -> R.drawable.status_box_bg
        })
        
        binding.tvCoordinates.setTextColor(textColor)
        binding.tvOverlayNetworkName.setTextColor(accentColor)
        binding.tvCoordinates.setBackgroundColor(when {
            isHighContrast -> Color.parseColor("#1A000000")
            isRedNight -> Color.parseColor("#33FF0000")
            isPink -> Color.parseColor("#33FF00FF")
            isNeon -> Color.parseColor("#33E6FB04")
            isNaranja -> Color.parseColor("#33FF8C00")
            isBubblegum -> Color.parseColor("#33FF00FF")
            isSummertime -> Color.parseColor("#33ff9f6b")
            isMorio -> Color.parseColor("#33c3ac3a")
            else -> Color.parseColor("#44000000")
        })
        binding.tvTimecode.setTextColor(accentColor)
        binding.tvTimecode.setBackgroundColor(when {
            isHighContrast -> Color.parseColor("#1A000000")
            isRedNight -> Color.parseColor("#33FF0000")
            isPink -> Color.parseColor("#33FF00FF")
            isNeon -> Color.parseColor("#33E6FB04")
            isNaranja -> Color.parseColor("#33FF8C00")
            isBubblegum -> Color.parseColor("#3300FDFF")
            isSummertime -> Color.parseColor("#336befff")
            isMorio -> Color.parseColor("#33c8f29e")
            else -> Color.parseColor("#44000000")
        })
        
        binding.listView.setBackgroundColor(bgColor)
        binding.bottomBar.setBackgroundColor(headerColor)
        
        val filterRow = (binding.cbBle.parent as View)
        filterRow.setBackgroundResource(when {
            isHighContrast -> R.drawable.status_box_bg_white
            isRedNight -> R.drawable.status_box_bg_red
            isPink -> R.drawable.status_box_bg_pink
            isNeon -> R.drawable.status_box_bg_neon
            isNaranja -> R.drawable.status_box_bg_naranja
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.btn_bg_summertime
            else -> R.drawable.status_box_bg
        })

        binding.btnInfo.imageTintList = ColorStateList.valueOf(textColor)

        // Force tactical buttons
        when {
            isRedNight -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_red)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_red)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_red)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_red)
            }
            isHighContrast -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_white)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_white)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_white)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_white)
            }
            isPink -> {
                binding.btnScan.setBackgroundResource(R.drawable.status_box_bg_pink)
                binding.btnExt.setBackgroundResource(R.drawable.status_box_bg_pink)
                binding.btnTerminal.setBackgroundResource(R.drawable.status_box_bg_pink)
                binding.btnClear.setBackgroundResource(R.drawable.status_box_bg_pink)
            }
            isNeon -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_neon)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_neon)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_neon)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_neon)
            }
            isNaranja -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_naranja)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_naranja)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_naranja)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_naranja)
            }
            isBubblegum -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_bubblegum)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_bubblegum)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_bubblegum)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_bubblegum)
            }
            isSummertime -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_summertime)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_summertime)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_summertime)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_summertime)
            }
            isMorio -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_morio)
            }
            isMorio -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg_morio)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg_morio)
            }
            else -> {
                binding.btnScan.setBackgroundResource(R.drawable.btn_bg)
                binding.btnExt.setBackgroundResource(R.drawable.btn_bg)
                binding.btnTerminal.setBackgroundResource(R.drawable.btn_bg)
                binding.btnClear.setBackgroundResource(R.drawable.btn_bg)
            }
        }

        val checkBoxes = listOf(binding.cbBle, binding.cb24, binding.cb5, binding.cbAero, binding.cbMap, binding.cbCams)
        checkBoxes.forEach {
            val cbColor = when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isPink -> Color.parseColor("#FF00FF")
                isNeon -> Color.parseColor("#E6FB04")
                isNaranja -> Color.parseColor("#FF8C00")
                isBubblegum -> Color.parseColor("#00FDFF")
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.parseColor("#00AA2A")
            }
            it.setTextColor(cbColor)
            it.buttonTintList = android.content.res.ColorStateList.valueOf(cbColor)
        }

        binding.btnThemeToggle.setImageResource(when {
            isHighContrast -> R.drawable.ic_theme_toggle_light
            isRedNight -> R.drawable.ic_theme_toggle_red
            isPink || isNeon || isNaranja || isBubblegum || isSummertime || isMorio -> R.drawable.ic_theme_toggle
            else -> R.drawable.ic_theme_toggle
        })
        
        val iconTint = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }
        val secondaryTint = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#00FDFF")
            isSummertime -> Color.parseColor("#6befff")
            isMorio -> Color.parseColor("#c8f29e")
            else -> Color.parseColor("#FF00FF")
        }

        // Set text for buttons
        val scanText = if (isScanning) "[ STOP ]" else "[ SCAN ]"
        setCyberText(binding.btnScan, scanText, if (binding.btnScan.scaleX > 1.05f) secondaryTint else textColor, forceAllGreen = binding.btnScan.scaleX <= 1.05f)
        setCyberText(binding.btnExt, "[ EXT ]", if (binding.btnExt.scaleX > 1.05f) secondaryTint else textColor, forceAllGreen = binding.btnExt.scaleX <= 1.05f)
        setCyberText(binding.btnTerminal, "[ SSH ]", if (binding.btnTerminal.scaleX > 1.05f) secondaryTint else textColor, forceAllGreen = binding.btnTerminal.scaleX <= 1.05f)
        setCyberText(binding.btnClear, "[ CLEAR ]", if (binding.btnClear.scaleX > 1.05f) secondaryTint else textColor, forceAllGreen = binding.btnClear.scaleX <= 1.05f)

        binding.btnStats.setColorFilter(iconTint)
        binding.btnPager.setColorFilter(iconTint)
        binding.btnGlobeListTop.setColorFilter(iconTint)
        binding.btnKillswitch.setColorFilter(secondaryTint)
        binding.btnEsl.setColorFilter(iconTint)
        binding.btnArchive.setImageResource(if (isHighContrast) R.drawable.ic_archive_light else if (isRedNight) R.drawable.ic_archive_red else R.drawable.ic_archive)
        binding.btnArchive.setColorFilter(iconTint)
        binding.btnThemeToggle.setColorFilter(iconTint)
        
        binding.miniGraph.theme = currentTheme
        binding.netGraph.theme = currentTheme
        
        if (isSummertime) {
            binding.miniGraph.setColors("#ff9f6b", "#886befff")
            binding.netGraph.setColors("#ff9f6b", "#886befff")
        } else if (isMorio) {
            binding.miniGraph.setColors("#c3ac3a", "#88c8f29e")
            binding.netGraph.setColors("#c3ac3a", "#88c8f29e")
        } else {
            // Restore defaults for other themes if needed, or let the theme property handle it
            // The MiniGraphView theme setter already handles colors based on currentTheme.
        }


        // Tint ivLogo for RED_NIGHT
        if (isRedNight) {
            binding.ivLogo.setColorFilter(Color.RED)
        } else {
            binding.ivLogo.clearColorFilter()
        }

        when {
            isHighContrast -> {
                binding.btnBeep.setTextColor(Color.BLACK)
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.BLACK))
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_white)
            }
            isRedNight -> {
                binding.btnBeep.setTextColor(Color.RED)
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.RED))
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_red)
            }
            isPink -> {
                binding.btnBeep.setTextColor(Color.parseColor("#FF00FF"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF00FF")))
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_pink)
            }
            isNeon -> {
                binding.btnBeep.setTextColor(Color.parseColor("#E6FB04"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E6FB04")))
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_neon)
            }
            isNaranja -> {
                binding.btnBeep.setTextColor(Color.parseColor("#FF8C00"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF8C00")))
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_naranja)
            }
            isBubblegum -> {
                binding.btnBeep.setTextColor(Color.parseColor("#FF00FF"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF00FF")))
                binding.btnBeep.setBackgroundResource(R.drawable.btn_bg_bubblegum)
            }
            isSummertime -> {
                binding.btnBeep.setTextColor(Color.parseColor("#ff9f6b"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#ff9f6b")))
                binding.btnBeep.setBackgroundResource(R.drawable.btn_bg_summertime)
            }
            isMorio -> {
                binding.btnBeep.setTextColor(Color.parseColor("#c3ac3a"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#c3ac3a")))
                binding.btnBeep.setBackgroundResource(R.drawable.btn_bg_morio)
            }
            else -> {
                binding.btnBeep.setTextColor(Color.parseColor("#00FF41"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF41")))
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_green)
            }
        }
        
        // Update button states
        setActiveButton(if (binding.btnScan.scaleX > 1.05f) binding.btnScan else if (binding.btnExt.scaleX > 1.05f) binding.btnExt else if (binding.btnTerminal.scaleX > 1.05f) binding.btnTerminal else binding.btnClear)
        updateViewVisibility()
        
        // Ensure RadarView is updated
        binding.radarView.theme = theme
        binding.miniGraph.theme = theme
        binding.netGraph.theme = theme
        
        if (theme == RadarView.Theme.SUMMERTIME) {
            binding.miniGraph.setColors("#ff9f6b", "#886befff")
            binding.netGraph.setColors("#ff9f6b", "#886befff")
        } else if (theme == RadarView.Theme.MORIO) {
            binding.miniGraph.setColors("#c3ac3a", "#88c8f29e")
            binding.netGraph.setColors("#c3ac3a", "#88c8f29e")
        }

        if (::deviceAdapter.isInitialized) {
            deviceAdapter.theme = theme
            deviceAdapter.notifyDataSetChanged()
        }
    }

    private fun setupImmersiveMode() {
        if (isSshWindowOpen) return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("immersive_mode", true)) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setActiveButton(activeButton: Button) {
        activeAnimator?.cancel()
        
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        val isPink = currentTheme == RadarView.Theme.PINK
        val isNeon = currentTheme == RadarView.Theme.NEON
        val isNaranja = currentTheme == RadarView.Theme.NARANJA
        val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM
        val isSummertime = currentTheme == RadarView.Theme.SUMMERTIME
        val isMorio = currentTheme == RadarView.Theme.MORIO

        val primaryColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }
        val secondaryColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#00FDFF")
            isSummertime -> Color.parseColor("#6befff")
            isMorio -> Color.parseColor("#c8f29e")
            else -> Color.parseColor("#FF00FF")
        }
        val dimBg = when {
            isHighContrast -> R.drawable.btn_bg_white_dim
            isRedNight -> R.drawable.btn_bg_black
            isPink -> R.drawable.status_box_bg_pink // Assume we have or use pink with alpha
            isNeon -> R.drawable.btn_bg_neon // Assume we have or use neon with alpha
            isNaranja -> R.drawable.btn_bg_naranja // Assume we have or use naranja with alpha
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.btn_bg_summertime_dim
            isMorio -> R.drawable.btn_bg_morio_dim
            else -> R.drawable.btn_bg_dim
        }
        val activeBg = when {
            isHighContrast -> R.drawable.btn_bg_white
            isRedNight -> R.drawable.btn_bg_red
            isPink -> R.drawable.status_box_bg_pink
            isNeon -> R.drawable.btn_bg_neon
            isMorio -> R.drawable.btn_bg_morio
            isNaranja -> R.drawable.btn_bg_naranja
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.btn_bg_summertime
            else -> R.drawable.btn_bg
        }

        // Reset all buttons
        listOf(binding.btnScan, binding.btnExt, binding.btnTerminal, binding.btnClear).forEach {
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.setBackgroundResource(dimBg)
            setCyberText(it, it.text.toString(), primaryColor, forceAllGreen = true)
        }
        
        activeButton.setBackgroundResource(activeBg)
        
        setCyberText(activeButton, activeButton.text.toString(), secondaryColor)
        
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
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        val isPink = currentTheme == RadarView.Theme.PINK
        val isNeon = currentTheme == RadarView.Theme.NEON
        val isNaranja = currentTheme == RadarView.Theme.NARANJA
        val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM
        val isSummertime = currentTheme == RadarView.Theme.SUMMERTIME
        val isMorio = currentTheme == RadarView.Theme.MORIO
        
        val primaryColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }
        
        // Find brackets
        val openIdx = text.indexOf("[")
        val closeIdx = text.lastIndexOf("]")
        
        if (openIdx != -1 && closeIdx != -1) {
            // Color brackets as primary
            spannable.setSpan(android.text.style.ForegroundColorSpan(primaryColor), openIdx, openIdx + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.ForegroundColorSpan(primaryColor), closeIdx, closeIdx + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            // Color text inside
            val insideColor = if (forceAllGreen) primaryColor else contentColor
            spannable.setSpan(android.text.style.ForegroundColorSpan(insideColor), openIdx + 1, closeIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            if (contentColor == Color.parseColor("#FF00FF") || contentColor == Color.BLACK || contentColor == Color.parseColor("#00FDFF") || contentColor == Color.parseColor("#ff9f6b") || contentColor == Color.parseColor("#6befff")) {
                spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), openIdx + 1, closeIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            // Any other text is primary
            if (openIdx > 0) spannable.setSpan(android.text.style.ForegroundColorSpan(primaryColor), 0, openIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (closeIdx < text.length - 1) spannable.setSpan(android.text.style.ForegroundColorSpan(primaryColor), closeIdx + 1, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            button.setTextColor(if (forceAllGreen) primaryColor else contentColor)
            if (contentColor == Color.parseColor("#FF00FF") || contentColor == Color.BLACK) button.setTypeface(null, android.graphics.Typeface.BOLD)
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
        val theme = currentTheme
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        
        // Theme-based colors
        val primaryColor: Int
        val secondaryColor: Int
        val bgColor: Int
        val inputBg: Int
        val inputTextColor: Int
        val statusBoxRes: Int
        val buttonBgRes: Int
        val buttonTextColor: Int

        when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> {
                primaryColor = Color.BLACK
                secondaryColor = Color.BLACK
                bgColor = Color.WHITE
                inputTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg_white
                buttonBgRes = R.drawable.btn_bg_black
                buttonTextColor = Color.WHITE
            }
            RadarView.Theme.RED_NIGHT -> {
                primaryColor = Color.RED
                secondaryColor = Color.RED
                bgColor = Color.BLACK
                inputTextColor = Color.RED
                statusBoxRes = R.drawable.status_box_bg_red
                buttonBgRes = R.drawable.btn_bg_red
                buttonTextColor = Color.BLACK
            }
            RadarView.Theme.PINK -> {
                primaryColor = Color.parseColor("#FF00FF")
                secondaryColor = Color.parseColor("#FF00FF")
                bgColor = Color.BLACK
                inputTextColor = Color.parseColor("#FF00FF")
                statusBoxRes = R.drawable.status_box_bg_pink
                buttonBgRes = R.drawable.status_box_bg_pink
                buttonTextColor = Color.BLACK
            }
            RadarView.Theme.NEON -> {
                primaryColor = Color.parseColor("#E6FB04")
                secondaryColor = Color.parseColor("#E6FB04")
                bgColor = Color.BLACK
                inputTextColor = Color.parseColor("#E6FB04")
                statusBoxRes = R.drawable.status_box_bg_neon
                buttonBgRes = R.drawable.btn_bg_neon
                buttonTextColor = Color.BLACK
            }
            RadarView.Theme.NARANJA -> {
                primaryColor = Color.parseColor("#FF8C00")
                secondaryColor = Color.parseColor("#FF8C00")
                bgColor = Color.BLACK
                inputTextColor = Color.parseColor("#FF8C00")
                statusBoxRes = R.drawable.status_box_bg_naranja
                buttonBgRes = R.drawable.btn_bg_naranja
                buttonTextColor = Color.BLACK
            }
            RadarView.Theme.BUBBLEGUM -> {
                primaryColor = Color.parseColor("#00FDFF") // Turquoise
                secondaryColor = Color.parseColor("#FF00FF") // Magenta
                bgColor = Color.BLACK
                inputTextColor = Color.parseColor("#FF00FF")
                statusBoxRes = R.drawable.status_box_bg_bubblegum
                buttonBgRes = R.drawable.btn_bg_bubblegum
                buttonTextColor = Color.BLACK
            }
            RadarView.Theme.SUMMERTIME -> {
                primaryColor = Color.parseColor("#ff9f6b")
                secondaryColor = Color.parseColor("#6befff")
                bgColor = Color.BLACK
                inputTextColor = Color.parseColor("#ff9f6b")
                statusBoxRes = R.drawable.btn_bg_summertime
                buttonBgRes = R.drawable.btn_bg_summertime
                buttonTextColor = Color.BLACK
            }
            RadarView.Theme.MORIO -> {
                primaryColor = Color.parseColor("#c3ac3a")
                secondaryColor = Color.parseColor("#c8f29e")
                bgColor = Color.BLACK
                inputTextColor = Color.parseColor("#c3ac3a")
                statusBoxRes = R.drawable.status_box_bg_morio
                buttonBgRes = R.drawable.btn_bg_morio
                buttonTextColor = Color.BLACK
            }
            else -> { // DEFAULT
                primaryColor = Color.parseColor("#00FF41")
                secondaryColor = Color.parseColor("#FF00FF")
                bgColor = Color.BLACK
                inputTextColor = Color.WHITE
                statusBoxRes = R.drawable.status_box_bg
                buttonBgRes = R.drawable.btn_bg
                buttonTextColor = Color.BLACK
            }
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setOnDismissListener { setupImmersiveMode() }
        dialog.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(40, 40, 40, 40)
            gravity = android.view.Gravity.CENTER
        }

        // Title
        val title = android.widget.TextView(this).apply {
            text = " EXTERNAL NODE // MULTIPASS "
            setTextColor(primaryColor)
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }
        root.addView(title)

        // IP Input Field
        val ipLabel = android.widget.TextView(this).apply {
            text = "TARGET IP ADDRESS:"
            setTextColor(primaryColor)
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
            setTextColor(inputTextColor)
            setHintTextColor(primaryColor) // Using primary for hint alpha if needed
            setBackgroundResource(statusBoxRes)
            
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 18f
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_PHONE or android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        root.addView(ipInput)

        // Connect Button
        val primaryHex = String.format("#%06X", (0xFFFFFF and primaryColor))
        val goBtn = android.widget.Button(this).apply {
            if (isHighContrast) {
                text = "INITIATE UPLINK"
            } else {
                text = android.text.Html.fromHtml("<font color='$primaryHex'>[</font> INITIATE UPLINK <font color='$primaryHex'>]</font>", android.text.Html.FROM_HTML_MODE_LEGACY)
            }
            setTextColor(buttonTextColor)
            setBackgroundResource(buttonBgRes)
            backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
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
            setTextColor(secondaryColor)
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

    private fun showWebViewOverlay(url: String) {
        val isHighContrast = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("high_contrast", false)
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar_Fullscreen else android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setOnDismissListener { setupImmersiveMode() }
        dialog.window?.let { window ->
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
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
        val theme = currentTheme
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar else android.R.style.Theme_Black_NoTitleBar)
        dialog.setOnDismissListener { 
            isSshWindowOpen = false
            setupImmersiveMode() 
        }
        isSshWindowOpen = true
        val overlayBinding = com.radar.blewifi.databinding.TerminalOverlayBinding.inflate(layoutInflater)
        dialog.setContentView(overlayBinding.root)

        // Theme-based colors
        val primaryColor: Int
        val secondaryColor: Int
        val bgColor: Int
        val headerColor: Int
        val inputTextColor: Int
        val hintColor: Int
        val buttonBgRes: Int
        val buttonTextColor: Int
        val statusBoxRes: Int

        when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> {
                primaryColor = Color.BLACK
                secondaryColor = Color.BLACK
                bgColor = Color.WHITE
                headerColor = Color.WHITE
                inputTextColor = Color.BLACK
                hintColor = Color.GRAY
                buttonBgRes = R.drawable.btn_bg_black
                buttonTextColor = Color.WHITE
                statusBoxRes = R.drawable.status_box_bg_white
            }
            RadarView.Theme.RED_NIGHT -> {
                primaryColor = Color.RED
                secondaryColor = Color.RED
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#0A0000")
                inputTextColor = Color.RED
                hintColor = Color.parseColor("#330000")
                buttonBgRes = R.drawable.btn_bg_red
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg_red
            }
            RadarView.Theme.PINK -> {
                primaryColor = Color.parseColor("#FF00FF")
                secondaryColor = Color.parseColor("#FF00FF")
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#0F000F")
                inputTextColor = Color.parseColor("#FF00FF")
                hintColor = Color.parseColor("#330033")
                buttonBgRes = R.drawable.status_box_bg_pink
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg_pink
            }
            RadarView.Theme.NEON -> {
                primaryColor = Color.parseColor("#E6FB04")
                secondaryColor = Color.parseColor("#E6FB04")
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#0F0F00")
                inputTextColor = Color.parseColor("#E6FB04")
                hintColor = Color.parseColor("#333801")
                buttonBgRes = R.drawable.btn_bg_neon
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg_neon
            }
            RadarView.Theme.NARANJA -> {
                primaryColor = Color.parseColor("#FF8C00")
                secondaryColor = Color.parseColor("#FF8C00")
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#0F0A00")
                inputTextColor = Color.parseColor("#FF8C00")
                hintColor = Color.parseColor("#331C00")
                buttonBgRes = R.drawable.btn_bg_naranja
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg_naranja
            }
            RadarView.Theme.BUBBLEGUM -> {
                primaryColor = Color.parseColor("#00FDFF") // Turquoise
                secondaryColor = Color.parseColor("#FF00FF") // Magenta
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#050005")
                inputTextColor = Color.parseColor("#FF00FF")
                hintColor = Color.parseColor("#330033")
                buttonBgRes = R.drawable.btn_bg_bubblegum
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg_bubblegum
            }
            RadarView.Theme.SUMMERTIME -> {
                primaryColor = Color.parseColor("#ff9f6b")
                secondaryColor = Color.parseColor("#6befff")
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#0F0906")
                inputTextColor = Color.parseColor("#ff9f6b")
                hintColor = Color.parseColor("#331f15")
                buttonBgRes = R.drawable.btn_bg_summertime
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.btn_bg_summertime
            }
            else -> { // DEFAULT
                primaryColor = Color.parseColor("#00FF41")
                secondaryColor = Color.parseColor("#FF00FF")
                bgColor = Color.BLACK
                headerColor = Color.parseColor("#0F0F0F")
                inputTextColor = Color.WHITE
                hintColor = Color.parseColor("#004400")
                buttonBgRes = R.drawable.btn_bg
                buttonTextColor = Color.BLACK
                statusBoxRes = R.drawable.status_box_bg
            }
        }


        // Apply colors to views
        overlayBinding.root.setBackgroundColor(bgColor)
        overlayBinding.sshLoginPage.setBackgroundColor(bgColor)
        overlayBinding.sshTerminalPage.setBackgroundColor(bgColor)
        overlayBinding.connectionLayout.setBackgroundColor(bgColor)
        overlayBinding.terminalLayout.setBackgroundColor(bgColor)
        
        overlayBinding.headerSshLogin.setBackgroundColor(headerColor)
        overlayBinding.headerSshTerminal.setBackgroundColor(headerColor)
        overlayBinding.terminalControlsRoot.setBackgroundColor(headerColor)

        overlayBinding.ivLogoSshLogin.setImageResource(R.drawable.logo3)
        if (theme != RadarView.Theme.HIGH_CONTRAST && theme != RadarView.Theme.DEFAULT) {
            overlayBinding.ivLogoSshLogin.setColorFilter(primaryColor)
        } else {
            overlayBinding.ivLogoSshLogin.clearColorFilter()
        }
        
        overlayBinding.tvSshLoginTitle.setTextColor(primaryColor)
        
        val editTexts = listOf(overlayBinding.etHost, overlayBinding.etUser, overlayBinding.etPassword, overlayBinding.etCommand)
        editTexts.forEach {
            it.setTextColor(inputTextColor)
            it.setHintTextColor(hintColor)
            if (it != overlayBinding.etCommand) {
                it.setBackgroundResource(statusBoxRes)
            } else {
                it.setBackgroundResource(if (theme == RadarView.Theme.DEFAULT) R.drawable.btn_bg_dim else statusBoxRes)
            }
        }
        
        overlayBinding.btnConnect.setTextColor(buttonTextColor)
        overlayBinding.btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        if (theme == RadarView.Theme.HIGH_CONTRAST) {
            overlayBinding.btnConnect.setBackgroundResource(R.drawable.btn_bg_black)
        } else {
             overlayBinding.btnConnect.setBackgroundResource(buttonBgRes)
        }
        
        overlayBinding.tvTerminalOutput.setTextColor(primaryColor)
        overlayBinding.svTerminal.setBackgroundResource(statusBoxRes)
        
        val ctrlBtns = listOf(
            overlayBinding.btnCtrl, overlayBinding.btnTab, overlayBinding.btnCtrlC,
            overlayBinding.btnLeft, overlayBinding.btnUp, overlayBinding.btnDown, overlayBinding.btnRight
        )
        ctrlBtns.forEach {
            it.setTextColor(primaryColor)
            it.setBackgroundResource(if (theme == RadarView.Theme.DEFAULT) R.drawable.btn_bg_dim else statusBoxRes)
        }
        
        overlayBinding.btnSshLoginBack.setTextColor(secondaryColor)
        overlayBinding.btnSshLoginBack.background = null
        overlayBinding.btnSshTerminalBack.setTextColor(secondaryColor)
        overlayBinding.btnSshTerminalBack.setBackgroundResource(statusBoxRes)

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

        // Set themed brackets for buttons
        val primaryHex = String.format("#%06X", (0xFFFFFF and primaryColor))
        if (isHighContrast) {
            overlayBinding.btnConnect.text = "CONNECT"
        } else {
            overlayBinding.btnConnect.text = android.text.Html.fromHtml("<font color='$primaryHex'>[</font> CONNECT <font color='$primaryHex'>]</font>", android.text.Html.FROM_HTML_MODE_LEGACY)
        }
        overlayBinding.btnSshLoginBack.text = "ABORT"
        overlayBinding.btnSshTerminalBack.text = "BACK"

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
        val theme = currentTheme
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = theme == RadarView.Theme.RED_NIGHT
        val isPink = theme == RadarView.Theme.PINK
        val isNeon = theme == RadarView.Theme.NEON
        val isNaranja = theme == RadarView.Theme.NARANJA
        val isBubblegum = theme == RadarView.Theme.BUBBLEGUM
        val isSummertime = theme == RadarView.Theme.SUMMERTIME
        val isMorio = theme == RadarView.Theme.MORIO
        
        val primaryColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#00FDFF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#FF00FF")
        }

        val accentColor = when {
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#6befff")
            isMorio -> Color.parseColor("#c8f29e")
            else -> primaryColor
        }
        
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
            setTextColor(primaryColor)
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
            setTextColor(if (isHighContrast) Color.BLACK else if (isBubblegum) Color.parseColor("#FF00FF") else Color.WHITE)
            setBackgroundResource(when {
                isHighContrast -> R.drawable.status_box_bg_white
                isRedNight -> R.drawable.status_box_bg_red
                isPink -> R.drawable.status_box_bg_pink
                isNeon -> R.drawable.status_box_bg_neon
                isNaranja -> R.drawable.status_box_bg_naranja
                isBubblegum -> R.drawable.status_box_bg_bubblegum
                isSummertime -> R.drawable.status_box_bg_summertime
                isMorio -> R.drawable.status_box_bg_morio
                else -> R.drawable.status_box_bg
            })
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
            setBackgroundResource(when {
                isHighContrast -> R.drawable.btn_bg_black
                isRedNight -> R.drawable.btn_bg_red
                isPink -> R.drawable.status_box_bg_pink
                isNeon -> R.drawable.btn_bg_neon
                isNaranja -> R.drawable.btn_bg_naranja
                isBubblegum -> R.drawable.btn_bg_bubblegum
                isSummertime -> R.drawable.btn_bg_summertime
                isMorio -> R.drawable.btn_bg_morio
                else -> R.drawable.status_box_bg_green
            })
            backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
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
                android.widget.Toast.makeText(this@MainActivity, "PIN UPDATED", android.widget.Toast.LENGTH_SHORT).show()
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
            val discColor = when (currentTheme) {
                RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
                RadarView.Theme.RED_NIGHT -> Color.RED
                RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
                RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
                RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
                RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
                RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
                RadarView.Theme.MORIO -> Color.parseColor("#c8f29e")
                else -> Color.parseColor("#FF00FF")
            }
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(discColor),
                discStart, discEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val titleText = "PROJECT INFO"
        val spannableTitle = android.text.SpannableString(titleText)
        val titleColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            RadarView.Theme.MORIO -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#FF00FF")
        }
        spannableTitle.setSpan(
            android.text.style.ForegroundColorSpan(titleColor),
            0, titleText.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(spannableTitle)
            .setMessage(spannable)
            .setPositiveButton("OK", null)
            .show()
        dialog.setOnDismissListener { setupImmersiveMode() }

        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        val isPink = currentTheme == RadarView.Theme.PINK
        val isNeon = currentTheme == RadarView.Theme.NEON
        val isNaranja = currentTheme == RadarView.Theme.NARANJA
        val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM
        val isSummertime = currentTheme == RadarView.Theme.SUMMERTIME
        val isMorio = currentTheme == RadarView.Theme.MORIO

        // Background styling
        val bg = android.graphics.drawable.GradientDrawable()
        when {
            isHighContrast -> {
                bg.setColor(android.graphics.Color.WHITE)
                bg.setStroke(4, android.graphics.Color.BLACK)
            }
            isRedNight -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.RED)
            }
            isPink -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.parseColor("#FF00FF"))
            }
            isNeon -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.parseColor("#E6FB04"))
            }
            isNaranja -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.parseColor("#FF8C00"))
            }
            isBubblegum -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.parseColor("#00FDFF"))
            }
            isSummertime -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.parseColor("#6befff"))
            }
            isMorio -> {
                bg.setColor(android.graphics.Color.BLACK)
                bg.setStroke(2, android.graphics.Color.parseColor("#c8f29e"))
            }
            else -> {
                bg.setColor(android.graphics.Color.parseColor("#99002200")) // Translucent dark green
                bg.setStroke(2, android.graphics.Color.parseColor("#00FF41")) // Neon border
            }
        }
        bg.cornerRadius = 60f
        dialog.window?.setBackgroundDrawable(bg)

        // Make links clickable and set colors
        val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        messageView?.let {
            it.autoLinkMask = android.text.util.Linkify.WEB_URLS or android.text.util.Linkify.EMAIL_ADDRESSES
            it.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            
            val linkColor = when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isPink -> Color.parseColor("#FF00FF")
                isNeon -> Color.parseColor("#E6FB04")
                isNaranja -> Color.parseColor("#FF8C00")
                isBubblegum -> Color.parseColor("#00FDFF")
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.parseColor("#00FF41")
            }

            when {
                isHighContrast -> {
                    it.setTextColor(android.graphics.Color.BLACK)
                    it.setLinkTextColor(android.graphics.Color.BLACK)
                }
                isRedNight -> {
                    it.setTextColor(android.graphics.Color.RED)
                    it.setLinkTextColor(android.graphics.Color.RED)
                }
                isPink -> {
                    it.setTextColor(android.graphics.Color.parseColor("#FF00FF"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#FF00FF"))
                }
                isNeon -> {
                    it.setTextColor(android.graphics.Color.parseColor("#E6FB04"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#E6FB04"))
                }
                isNaranja -> {
                    it.setTextColor(android.graphics.Color.parseColor("#FF8C00"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#FF8C00"))
                }
                isBubblegum -> {
                    it.setTextColor(android.graphics.Color.parseColor("#FF00FF"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#00FDFF"))
                }
                isSummertime -> {
                    it.setTextColor(android.graphics.Color.parseColor("#ff9f6b"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#6befff"))
                }
                isMorio -> {
                    it.setTextColor(android.graphics.Color.parseColor("#c3ac3a"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#c8f29e"))
                }
                else -> {
                    it.setTextColor(android.graphics.Color.parseColor("#00FF41"))
                    it.setLinkTextColor(android.graphics.Color.parseColor("#00FF41"))
                }
            }
            it.typeface = android.graphics.Typeface.MONOSPACE
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(when {
                isHighContrast -> android.graphics.Color.BLACK
                isRedNight -> android.graphics.Color.RED
                isPink -> android.graphics.Color.parseColor("#FF00FF")
                isNeon -> android.graphics.Color.parseColor("#E6FB04")
                isNaranja -> android.graphics.Color.parseColor("#FF8C00")
                isBubblegum -> android.graphics.Color.parseColor("#00FDFF")
                isSummertime -> android.graphics.Color.parseColor("#6befff")
                isMorio -> android.graphics.Color.parseColor("#c8f29e")
                else -> android.graphics.Color.parseColor("#FF00FF")
            })
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
        deviceAdapter.theme = currentTheme
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
            binding.btnGlobeListTop.setImageResource(R.drawable.ic_file)
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
            val destination = if (device.type == DeviceType.CAMERA || device.type == DeviceType.AIRCRAFT) {
                if (device.lat != null && device.lon != null) {
                    GeoPoint(device.lat!!, device.lon!!)
                } else {
                    calculateDestinationPoint(userGeoPoint, dist, bearing)
                }
            } else {
                calculateDestinationPoint(userGeoPoint, dist, bearing)
            }

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
            DeviceType.CAMERA -> Color.RED
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
        updateThemeFromPrefs()
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
            
            val colorStart = when (currentTheme) {
                RadarView.Theme.RED_NIGHT -> Color.RED
                RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
                RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
                RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
                RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
                RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
                RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
                else -> Color.parseColor("#00FF41")
            }
            val colorEnd = when (currentTheme) {
                RadarView.Theme.RED_NIGHT -> Color.parseColor("#660000")
                RadarView.Theme.PINK -> Color.parseColor("#880088")
                RadarView.Theme.NEON -> Color.parseColor("#888800")
                RadarView.Theme.NARANJA -> Color.parseColor("#884400")
                RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
                RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
                RadarView.Theme.HIGH_CONTRAST -> Color.GRAY
                else -> Color.parseColor("#FF00FF") // Keep pink accent for default? Or maybe #004411?
            }
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val r = (Color.red(colorStart) * (1 - fraction) + Color.red(colorEnd) * fraction).toInt()
                val g = (Color.green(colorStart) * (1 - fraction) + Color.green(colorEnd) * fraction).toInt()
                val b = (Color.blue(colorStart) * (1 - fraction) + Color.blue(colorEnd) * fraction).toInt()
                binding.btnPager.setColorFilter(Color.rgb(r, g, b))
            }
            start()
        }
    }

    private fun stopPagerBreathing() {
        pagerBreatheAnim?.cancel()
        pagerBreatheAnim = null
        val color = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            else -> Color.parseColor("#00FF41")
        }
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
        updateThemeUI()
        binding.statusText.text = "STANDBY"
    }

    private fun showDeviceDetail(device: ScanDevice) {
        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra(DetailActivity.EXTRA_DEVICE, device)
        val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, options.toBundle())
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
            
            // Limit UI updates to 10 FPS for radar, even if scanner is faster
            // (Scanner is already throttled to 1-3s in high density, but let's be safe)
            val minUpdateInterval = if (devices.size > 200) 100L else 50L
            if (now - lastListUpdate < minUpdateInterval && !isListView) return@runOnUiThread

            val filteredForRadar = devices.filter { 
                when (it.type) {
                    DeviceType.BLE, DeviceType.CAR, DeviceType.ESCOOTER, 
                    DeviceType.TV, DeviceType.COMPUTER, DeviceType.SMARTPHONE, DeviceType.PAGER -> binding.cbBle.isChecked
                    DeviceType.AIRCRAFT, DeviceType.DRONE -> binding.cbAero.isChecked
                    DeviceType.LTE, DeviceType.FIVE_G -> true // Always show or handle differently if needed
                    DeviceType.CAMERA -> binding.cbCams.isChecked
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
                // In list view, update less frequently to save CPU (1s)
                if (now - lastListUpdate < 1000) return@runOnUiThread
            }

            binding.radarView.setDevices(filteredForRadar)
            
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
            val camC  = filteredForRadar.count { it.type == DeviceType.CAMERA }

            val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
            val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
            val isPink = currentTheme == RadarView.Theme.PINK
            val isNeon = currentTheme == RadarView.Theme.NEON
            val isNaranja = currentTheme == RadarView.Theme.NARANJA
            val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM
            val isSummertime = currentTheme == RadarView.Theme.SUMMERTIME
            
            val numColor = when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isPink -> Color.parseColor("#FF00FF")
                isNeon -> Color.parseColor("#E6FB04")
                isNaranja -> Color.parseColor("#FF8C00")
                isBubblegum -> Color.parseColor("#FF00FF")
                isSummertime -> Color.parseColor("#ff9f6b")
                else -> Color.parseColor("#00FF41")
            }
            val targetColor = when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isPink -> Color.parseColor("#FF00FF")
                isNeon -> Color.parseColor("#E6FB04")
                isNaranja -> Color.parseColor("#FF8C00")
                isBubblegum -> Color.parseColor("#00FDFF")
                isSummertime -> Color.parseColor("#6befff")
                else -> Color.parseColor("#FF00FF")
            }
            val aeroNumColor = when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isPink -> Color.parseColor("#FF00FF")
                isNeon -> Color.parseColor("#E6FB04")
                isNaranja -> Color.parseColor("#FF8C00")
                isBubblegum -> Color.parseColor("#00FDFF")
                isSummertime -> Color.parseColor("#6befff")
                else -> Color.parseColor("#00FFFF")
            }

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
            if (binding.cbCams.isChecked) {
                appendStat("CAMS", camC, Color.RED)
            }

            if (sb.isNotEmpty()) sb.append(" | ")
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
            
            // Re-apply theme colors to the scan button via updateThemeUI to ensure consistency
            updateThemeUI()
            
            if (scanning) {
                startStatusPulseAnimation(slow = true)
            } else {
                startStatusPulseAnimation(slow = false)
                // Use a Spannable if necessary or just ensure it's colored
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
        
        // Start low intensity pulsing vibration
        noiseVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        
        noiseVibrator?.let { vibrator ->
            val timings = longArrayOf(0, 200, 200) // Start immediately, 200ms on, 200ms off
            val amplitudes = intArrayOf(0, 40, 0)   // Low intensity (40/255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        }
        
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
        noiseVibrator?.cancel()
        noiseVibrator = null
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
                    val color = Color.parseColor("#FF00FF")
                    binding.btnBeep.setTextColor(color)
                    binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color))
                } else {
                    val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
                    val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
                    
                    binding.btnBeep.setBackgroundResource(when {
                        isHighContrast -> R.drawable.status_box_bg_white
                        isRedNight -> R.drawable.status_box_bg_red
                        else -> R.drawable.status_box_bg_green
                    })
                    
                    val color = when {
                        isHighContrast -> Color.BLACK
                        isRedNight -> Color.RED
                        else -> Color.parseColor("#00FF41")
                    }
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
        
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        val isPink = currentTheme == RadarView.Theme.PINK
        val isNeon = currentTheme == RadarView.Theme.NEON
        val isNaranja = currentTheme == RadarView.Theme.NARANJA
        val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM
        
        when {
            isHighContrast -> {
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_white)
                binding.btnBeep.setTextColor(Color.BLACK)
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.BLACK))
            }
            isRedNight -> {
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_red)
                binding.btnBeep.setTextColor(Color.RED)
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.RED))
            }
            isPink -> {
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_pink)
                binding.btnBeep.setTextColor(Color.parseColor("#FF00FF"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF00FF")))
            }
            isNeon -> {
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_neon)
                binding.btnBeep.setTextColor(Color.parseColor("#E6FB04"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E6FB04")))
            }
            isNaranja -> {
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_naranja)
                binding.btnBeep.setTextColor(Color.parseColor("#FF8C00"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF8C00")))
            }
            isBubblegum -> {
                binding.btnBeep.setBackgroundResource(R.drawable.btn_bg_bubblegum)
                binding.btnBeep.setTextColor(Color.parseColor("#FF00FF"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF00FF")))
            }
            else -> {
                binding.btnBeep.setBackgroundResource(R.drawable.status_box_bg_green)
                binding.btnBeep.setTextColor(Color.parseColor("#00FF41"))
                binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF41")))
            }
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
    var theme: RadarView.Theme = RadarView.Theme.DEFAULT

    private val isHighContrast: Boolean get() = theme == RadarView.Theme.HIGH_CONTRAST
    private val isRedNight: Boolean get() = theme == RadarView.Theme.RED_NIGHT
    private val isPink: Boolean get() = theme == RadarView.Theme.PINK
    private val isNeon: Boolean get() = theme == RadarView.Theme.NEON
    private val isNaranja: Boolean get() = theme == RadarView.Theme.NARANJA
    private val isBubblegum: Boolean get() = theme == RadarView.Theme.BUBBLEGUM
    private val isSummertime: Boolean get() = theme == RadarView.Theme.SUMMERTIME
    private val isMorio: Boolean get() = theme == RadarView.Theme.MORIO

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
        val selectionColor = when {
            isHighContrast -> Color.parseColor("#33000000")
            isRedNight -> Color.parseColor("#33FF0000")
            isPink -> Color.parseColor("#33FF00FF")
            isNeon -> Color.parseColor("#33E6FB04")
            isNaranja -> Color.parseColor("#33FF8C00")
            isBubblegum -> Color.parseColor("#33FF00FF")
            isSummertime -> Color.parseColor("#33ff9f6b")
            isMorio -> Color.parseColor("#33c3ac3a")
            else -> Color.parseColor("#33FF00FF")
        }
        holder.itemView.setBackgroundColor(if (isSelected) selectionColor else Color.TRANSPARENT)
        
        val isWep = device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")
        val isWhisper = device.isVulnerableWhisperPair
        
        val nameColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            device.type == DeviceType.BLE -> Color.parseColor("#0066FF")
            device.type == DeviceType.AIRCRAFT -> Color.parseColor("#00FFFF")
            else -> {
                val caps = device.capabilities.uppercase()
                val is5GHz = device.frequency in 5000..6000
                when {
                    is5GHz -> Color.parseColor("#008F22")
                    caps.contains("WPA") -> Color.parseColor("#00FF41")
                    else -> Color.WHITE
                }
            }
        }
        
        holder.name.setTextColor(nameColor)
        holder.dist.text = device.distanceLabel
        
        // Show manufacturer logo behind the device info
        val isApple = device.manufacturer == "0x004C" || device.name.lowercase().contains("apple") || device.isAirTag
        val isAndroid = device.manufacturer == "0x00E0" || device.name.lowercase().contains("android") || device.isFastPair
        
        if (isApple) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(when {
                isHighContrast -> R.drawable.ic_apple_black
                isRedNight -> R.drawable.ic_apple_red
                isPink || isNeon || isNaranja || isBubblegum || isSummertime || isMorio -> R.drawable.ic_apple
                else -> R.drawable.ic_apple
            })
            if (isSummertime) holder.mfrLogo.setColorFilter(Color.parseColor("#ff9f6b")) 
            else if (isMorio) holder.mfrLogo.setColorFilter(Color.parseColor("#c3ac3a"))
            else holder.mfrLogo.clearColorFilter()
        } else if (isAndroid) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(when {
                isHighContrast -> R.drawable.ic_android_black
                isRedNight -> R.drawable.ic_android_red
                isPink || isNeon || isNaranja || isBubblegum || isSummertime || isMorio -> R.drawable.ic_android
                else -> R.drawable.ic_android
            })
            if (isSummertime) holder.mfrLogo.setColorFilter(Color.parseColor("#ff9f6b")) 
            else if (isMorio) holder.mfrLogo.setColorFilter(Color.parseColor("#c3ac3a"))
            else holder.mfrLogo.clearColorFilter()
        } else {
            holder.mfrLogo.visibility = View.GONE
        }
        
        // Pink and glowing for close proximity (< 1m)
        if (device.distanceMeters < 1.0) {
            val closeColor = when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isPink -> Color.parseColor("#FF00FF")
                isNeon -> Color.parseColor("#E6FB04")
                isNaranja -> Color.parseColor("#FF8C00")
                isBubblegum -> Color.parseColor("#00FDFF")
                isSummertime -> Color.parseColor("#ff9f6b")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.parseColor("#FF00FF")
            }
            holder.dist.setTextColor(closeColor)
            if (!isHighContrast && !isRedNight) {
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
            holder.dist.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.parseColor("#990000")
                isPink -> Color.parseColor("#990099")
                isNeon -> Color.parseColor("#B3C403")
                isNaranja -> Color.parseColor("#995400")
                isBubblegum -> Color.parseColor("#008A8C")
                isSummertime -> Color.parseColor("#ff9f6b")
                isMorio -> Color.parseColor("#244f48")
                else -> Color.parseColor("#FFB300")
            })
            holder.dist.setShadowLayer(0f, 0f, 0f, 0)
            holder.dist.clearAnimation()
            holder.dist.alpha = 1.0f
        }

        holder.addr.text = device.address
        holder.addr.setTextColor(when {
            isHighContrast -> Color.DKGRAY
            isRedNight -> Color.parseColor("#660000")
            isPink -> Color.parseColor("#660066")
            isNeon -> Color.parseColor("#666600")
            isNaranja -> Color.parseColor("#663B00")
            isBubblegum -> Color.parseColor("#004E50")
            isSummertime -> Color.parseColor("#66ff9f6b")
            isMorio -> Color.parseColor("#66c3ac3a")
            else -> Color.GRAY
        })

        // Only show vulnerability text/alarms if it's WEP or WhisperPair
        if (isWep) {
            holder.alarm.visibility = View.VISIBLE
            holder.alarm.setTextColor(when {
                isHighContrast -> Color.BLACK
                else -> Color.RED
            })
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
            holder.whisper.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isBubblegum -> Color.parseColor("#00FDFF")
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.parseColor("#FF00FF")
            })
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
            holder.airtag.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isSummertime -> Color.parseColor("#ff9f6b")
                isMorio -> Color.parseColor("#c3ac3a")
                else -> Color.WHITE
            })
        } else {
            holder.airtag.visibility = View.GONE
        }

        if (device.type == DeviceType.AIRCRAFT) {
            holder.aero.visibility = View.VISIBLE
            holder.aero.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.CYAN
            })
        } else {
            holder.aero.visibility = View.GONE
        }

        if (device.isCar) {
            holder.car.visibility = View.VISIBLE
            holder.car.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.parseColor("#FF00FF")
            })
        } else {
            holder.car.visibility = View.GONE
        }

        if (device.isPubliclyConnectable) {
            holder.hijack.visibility = View.VISIBLE
            holder.hijack.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.YELLOW
            })
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
            holder.vuln.setTextColor(when {
                isHighContrast -> Color.BLACK
                isRedNight -> Color.RED
                isSummertime -> Color.parseColor("#6befff")
                isMorio -> Color.parseColor("#c8f29e")
                else -> Color.YELLOW
            })
            
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
