package com.radar.blewifi

import android.animation.ValueAnimator
import android.Manifest
import android.annotation.SuppressLint
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
import com.radar.blewifi.wspr.WsprActivity
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.net.Uri
import android.widget.AdapterView
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
<<<<<<< HEAD
<<<<<<< HEAD
import android.text.Html
=======
>>>>>>> parent of 1ed2e71 (v3.5.3)
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
=======
>>>>>>> parent of 5dc96b0 (v3.5.3)
import androidx.core.graphics.ColorUtils
import com.radar.blewifi.databinding.ActivityMainBinding
import com.radar.blewifi.databinding.TerminalOverlayBinding
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import com.jcraft.jsch.*
<<<<<<< HEAD
<<<<<<< HEAD
import com.radar.blewifi.masdr.SdrRemoteActivity
=======
>>>>>>> parent of 1ed2e71 (v3.5.3)
import com.radar.blewifi.pbj.PbjActivity
=======
>>>>>>> parent of 5dc96b0 (v3.5.3)
import kotlin.math.abs
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
    private var noiseHoldRunnable: Runnable? = null
    private var isNoiseHoldTriggered = false
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
            list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Note: MANAGE_EXTERNAL_STORAGE is a special permission handled via Settings intent,
            // but we list it here for completeness or if using a custom perm handler.
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

    private var selectedKeyUri: android.net.Uri? = null
    private var selectedKeyName: String? = null
    private val keyPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedKeyUri = it
            selectedKeyName = getFileName(it)
            Toast.makeText(this, getString(R.string.terminal_key_selected_toast, selectedKeyName), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: android.net.Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "unknown_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fix for SSH modern private keys
        try {
            // Remove existing BC if any to ensure our version is used
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            
            if (Security.getProvider("EdDSA") == null) {
                Security.insertProviderAt(EdDSASecurityProvider(), 2)
            }
            android.util.Log.d("SSH_DEBUG", "Security providers initialized (BC at pos 1)")
        } catch (e: Exception) {
            android.util.Log.e("SSH_DEBUG", "Failed to register security providers", e)
        }

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
        val colors = ThemeUtils.getThemeColors(currentTheme)
        binding.netGraph.setColors(
            String.format("#%06X", (0xFFFFFF and colors.primary)),
            String.format("#88%06X", (0xFFFFFF and colors.accent))
        )
        binding.miniGraph.setColors(
            String.format("#%06X", (0xFFFFFF and colors.primary)),
            String.format("#88%06X", (0xFFFFFF and colors.accent))
        )

        checkAndStartScanning()

        setupImmersiveMode()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Start Timecode Runner
        startTimecodeRunner()
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        binding.radarView.onDeviceClickListener = object : RadarView.OnDeviceClickListener {
            override fun onDeviceClicked(device: ScanDevice, isConfirm: Boolean) {
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

                // Show deep device insights dialog with MAC details and OS Fingerprinting only on second click
                if (isConfirm) {
                    showDeviceDetailsDialog(device)
                }
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

        // Neon pulsate animation removed


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
            scanner.isMapActive = isChecked
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

        // Logo click listeners
        binding.ivLogo.setOnClickListener {
            startLogoAnimation()
            showAboutDialog()
        }

        binding.btnExt.setOnClickListener {
            setActiveButton(binding.btnExt)
            showExtOverlay()
        }
        binding.btnTerminal.setOnClickListener {
            setActiveButton(binding.btnTerminal)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, getString(R.string.terminal_storage_permission_msg), Toast.LENGTH_LONG).show()
            } else {
                showTerminalOverlay()
            }
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
            val intent = Intent(this, EslActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnQr.setOnClickListener {
            val intent = Intent(this, com.radar.blewifi.qr.ui.QrActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }


        binding.btnWspr.setOnClickListener {
            val intent = Intent(this, com.radar.blewifi.wspr.WsprActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnDrone.setOnClickListener {
            val intent = Intent(this, com.radar.blewifi.drone.DroneActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        binding.btnThemeToggle.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var touchDownX = 0f
        var touchDownY = 0f

        @SuppressLint("ClickableViewAccessibility")
        binding.btnBeep.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    isNoiseHoldTriggered = false

                    if (!isBeeping) {
                        noiseHoldRunnable?.let { handler.removeCallbacks(it) }
                        binding.btnBeep.animate().cancel()
                        binding.btnBeep.animate()
                            .scaleX(1.15f)
                            .scaleY(1.15f)
                            .setDuration(3000L)
                            .start()

                        noiseHoldRunnable = Runnable {
                            isNoiseHoldTriggered = true
                            startBeep()
                        }
                        handler.postDelayed(noiseHoldRunnable!!, 3000L)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isBeeping && !isNoiseHoldTriggered) {
                        val dx = abs(event.x - touchDownX)
                        val dy = abs(event.y - touchDownY)
                        val isOutOfBounds = event.x < 0 || event.x > view.width || event.y < 0 || event.y > view.height
                        if (dx > touchSlop || dy > touchSlop || isOutOfBounds) {
                            noiseHoldRunnable?.let { handler.removeCallbacks(it) }
                            noiseHoldRunnable = null
                            binding.btnBeep.animate().cancel()
                            binding.btnBeep.scaleX = 1.0f
                            binding.btnBeep.scaleY = 1.0f
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isBeeping) {
                        noiseHoldRunnable?.let { handler.removeCallbacks(it) }
                        noiseHoldRunnable = null
                        binding.btnBeep.animate().cancel()
                        binding.btnBeep.scaleX = 1.0f
                        binding.btnBeep.scaleY = 1.0f
                    } else {
                        if (!isNoiseHoldTriggered) {
                            val isWithinBounds = event.x >= 0 && event.x <= view.width && event.y >= 0 && event.y <= view.height
                            if (isWithinBounds) {
                                stopBeep()
                            }
                        }
                    }
                    isNoiseHoldTriggered = false
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (!isBeeping) {
                        noiseHoldRunnable?.let { handler.removeCallbacks(it) }
                        noiseHoldRunnable = null
                        binding.btnBeep.animate().cancel()
                        binding.btnBeep.scaleX = 1.0f
                        binding.btnBeep.scaleY = 1.0f
                    }
                    isNoiseHoldTriggered = false
                    true
                }
                else -> false
            }
        }
        binding.btnBeep.setOnClickListener { }



        // Initial tinting removed as updateThemeUI() handles it below

        setActiveButton(binding.btnScan)
        startGraphUpdates()

        updateThemeUI()
    }

    private fun showAboutActivity() {
        val intent = Intent(this, AboutActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        startActivity(intent, options.toBundle())
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
        val colors = ThemeUtils.getThemeColors(theme)
        
        val bgColor = colors.background
        val headerColor = colors.header
        val primaryColor = colors.primary
        val textColor = primaryColor
        val accentColor = colors.accent

        binding.root.setBackgroundColor(bgColor)
        binding.headerContainer.setBackgroundColor(headerColor)
        binding.headerBar.setBackgroundColor(colors.headerBar)
        
        binding.statusText.setTextColor(textColor)
        binding.statusText.setBackgroundResource(when(theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.status_box_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.status_box_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.status_box_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.status_box_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.status_box_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.status_box_bg_summertime
            RadarView.Theme.MORIO -> R.drawable.status_box_bg_morio
            RadarView.Theme.FLIPPER -> R.drawable.status_box_bg_flipper
            else -> R.drawable.status_box_bg_green
        })
        
        binding.tvCoordinates.setTextColor(textColor)
        binding.tvOverlayNetworkName.setTextColor(accentColor)
        binding.tvCoordinates.setBackgroundColor(ColorUtils.setAlphaComponent(textColor, 51))
        binding.tvTimecode.setTextColor(accentColor)
        binding.tvTimecode.setBackgroundColor(ColorUtils.setAlphaComponent(accentColor, 51))
        
        binding.listView.setBackgroundColor(bgColor)
        binding.bottomBar.setBackgroundColor(headerColor)
        
        val filterRow = (binding.cbBle.parent as View)
        filterRow.setBackgroundResource(when(theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.status_box_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.status_box_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.status_box_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.status_box_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.btn_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime
            RadarView.Theme.MORIO -> R.drawable.btn_bg_morio
            RadarView.Theme.FLIPPER -> R.drawable.status_box_bg_flipper
            else -> R.drawable.status_box_bg
        })


        binding.btnWspr.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnDrone.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnGlobeListTop.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnKillswitch.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnThemeToggle.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnStats.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnArchive.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnEsl.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnQr.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnPager.imageTintList = ColorStateList.valueOf(textColor)
        binding.btnBeep.setTextColor(textColor)
        binding.btnBeep.compoundDrawableTintList = ColorStateList.valueOf(textColor)

        // Force tactical buttons
        val btnBg = when (theme) {
            RadarView.Theme.RED_NIGHT -> R.drawable.btn_bg_red
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.btn_bg_white
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.btn_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.btn_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.btn_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime
            RadarView.Theme.MORIO -> R.drawable.btn_bg_morio
            RadarView.Theme.FLIPPER -> R.drawable.btn_bg_flipper
            else -> R.drawable.btn_bg
        }
        binding.btnScan.setBackgroundResource(btnBg)
        binding.btnExt.setBackgroundResource(btnBg)
        binding.btnTerminal.setBackgroundResource(btnBg)
        binding.btnClear.setBackgroundResource(btnBg)
        binding.btnBeep.setBackgroundResource(btnBg)
        binding.btnSave.setBackgroundResource(btnBg)

        binding.netGraph.setColors(
            String.format("#%06X", (0xFFFFFF and textColor)),
            String.format("#88%06X", (0xFFFFFF and accentColor))
        )
        binding.miniGraph.setColors(
            String.format("#%06X", (0xFFFFFF and textColor)),
            String.format("#88%06X", (0xFFFFFF and accentColor))
        )

        val checkBoxes = listOf(binding.cbBle, binding.cb24, binding.cb5, binding.cbAero, binding.cbMap, binding.cbCams)
        checkBoxes.forEach {
            it.setTextColor(accentColor)
            it.buttonTintList = ColorStateList.valueOf(accentColor)
        }

        binding.btnThemeToggle.setImageResource(when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.ic_theme_toggle_light
            RadarView.Theme.RED_NIGHT -> R.drawable.ic_theme_toggle_red
            else -> R.drawable.ic_theme_toggle
        })
        
        // Set text for buttons
        val scanText = if (isScanning) "[ STOP ]" else "[ SCAN ]"
        setCyberText(binding.btnScan, scanText, if (binding.btnScan.scaleX > 1.05f) accentColor else textColor, forceAllGreen = binding.btnScan.scaleX <= 1.05f)
        setCyberText(binding.btnExt, "[ EXT ]", if (binding.btnExt.scaleX > 1.05f) accentColor else textColor, forceAllGreen = binding.btnExt.scaleX <= 1.05f)
        setCyberText(binding.btnTerminal, "[ SSH ]", if (binding.btnTerminal.scaleX > 1.05f) accentColor else textColor, forceAllGreen = binding.btnTerminal.scaleX <= 1.05f)
        setCyberText(binding.btnClear, "[ CLEAR ]", if (binding.btnClear.scaleX > 1.05f) accentColor else textColor, forceAllGreen = binding.btnClear.scaleX <= 1.05f)
        setCyberText(binding.btnSave, "[ SAVE ]", if (binding.btnSave.scaleX > 1.05f) accentColor else textColor, forceAllGreen = binding.btnSave.scaleX <= 1.05f)

        binding.btnStats.setColorFilter(textColor)
        binding.btnPager.setColorFilter(textColor)
        binding.btnEsl.setColorFilter(textColor)
        binding.btnQr.setColorFilter(textColor)
        binding.btnWspr.setColorFilter(textColor)
        binding.btnDrone.setColorFilter(textColor)
        binding.btnGlobeListTop.setColorFilter(textColor)
        binding.btnKillswitch.setColorFilter(accentColor)
        binding.btnArchive.setImageResource(if (theme == RadarView.Theme.HIGH_CONTRAST) R.drawable.ic_archive_light else if (theme == RadarView.Theme.RED_NIGHT) R.drawable.ic_archive_red else R.drawable.ic_archive)
        binding.btnArchive.setColorFilter(textColor)
        binding.btnThemeToggle.setColorFilter(textColor)
        
        binding.miniGraph.theme = theme
        binding.netGraph.theme = theme
        
        // Logo tinting
        binding.ivLogo.imageTintList = null
        
        // Scan status changed removed logo references
        setActiveButton(if (binding.btnScan.scaleX > 1.05f) binding.btnScan else if (binding.btnExt.scaleX > 1.05f) binding.btnExt else if (binding.btnTerminal.scaleX > 1.05f) binding.btnTerminal else binding.btnClear)
        updateViewVisibility()
        
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
        
        val theme = currentTheme
        val colors = ThemeUtils.getThemeColors(theme)
        val primaryColor = colors.primary
        val secondaryColor = colors.accent

        val dimBg = when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.btn_bg_white_dim
            RadarView.Theme.RED_NIGHT -> R.drawable.btn_bg_black
            RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime_dim
            RadarView.Theme.MORIO -> R.drawable.btn_bg_morio_dim
            else -> R.drawable.btn_bg_dim
        }
        val activeBg = when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.btn_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.btn_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.btn_bg_neon
            RadarView.Theme.MORIO -> R.drawable.btn_bg_morio
            RadarView.Theme.NARANJA -> R.drawable.btn_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.btn_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime
            RadarView.Theme.FLIPPER -> R.drawable.btn_bg_flipper
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
        val colors = ThemeUtils.getThemeColors(currentTheme)
        val primaryColor = colors.primary
        
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
            
            if (insideColor == Color.BLACK || currentTheme == RadarView.Theme.PINK || currentTheme == RadarView.Theme.BUBBLEGUM || currentTheme == RadarView.Theme.SUMMERTIME) {
                spannable.setSpan(android.text.style.StyleSpan(Typeface.BOLD), openIdx + 1, closeIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            // Any other text is primary
            if (openIdx > 0) spannable.setSpan(android.text.style.ForegroundColorSpan(primaryColor), 0, openIdx, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (closeIdx < text.length - 1) spannable.setSpan(android.text.style.ForegroundColorSpan(primaryColor), closeIdx + 1, text.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            button.setTextColor(if (forceAllGreen) primaryColor else contentColor)
            if (contentColor == Color.BLACK) button.setTypeface(null, Typeface.BOLD)
            button.text = text
            return
        }
        button.text = spannable
    }

    private fun blinkPink(button: Button) {
        val colors = ThemeUtils.getThemeColors(currentTheme)
        button.setBackgroundResource(colors.statusBoxRes)
        button.setTextColor(colors.accent)
        button.postDelayed({
            button.setBackgroundResource(colors.buttonDimRes)
            button.setTextColor(colors.primary)
        }, 100)
    }

    private fun showExtOverlay() {
        val theme = currentTheme
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        
        // Theme-based colors
        val colors = ThemeUtils.getThemeColors(theme)
        val primaryColor = colors.primary
        val secondaryColor = colors.accent
        val bgColor = colors.background
        val inputTextColor = if (isHighContrast) Color.BLACK else Color.WHITE
        val statusBoxRes = when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.status_box_bg_white
            RadarView.Theme.RED_NIGHT -> R.drawable.status_box_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.status_box_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.status_box_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.status_box_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.status_box_bg_summertime
            RadarView.Theme.MORIO -> R.drawable.status_box_bg_morio
            RadarView.Theme.FLIPPER -> R.drawable.status_box_bg_flipper
            else -> R.drawable.status_box_bg
        }
        val buttonBgRes = when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> R.drawable.btn_bg_black
            RadarView.Theme.RED_NIGHT -> R.drawable.btn_bg_red
            RadarView.Theme.PINK -> R.drawable.status_box_bg_pink
            RadarView.Theme.NEON -> R.drawable.btn_bg_neon
            RadarView.Theme.NARANJA -> R.drawable.btn_bg_naranja
            RadarView.Theme.BUBBLEGUM -> R.drawable.btn_bg_bubblegum
            RadarView.Theme.SUMMERTIME -> R.drawable.btn_bg_summertime
            RadarView.Theme.MORIO -> R.drawable.btn_bg_morio
            RadarView.Theme.FLIPPER -> R.drawable.btn_bg_flipper
            else -> R.drawable.btn_bg
        }
        val buttonTextColor = if (isHighContrast) Color.WHITE else Color.BLACK

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
            text = " " + getString(R.string.terminal_external_node_title) + " "
            setTextColor(primaryColor)
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }
        root.addView(title)

        // IP Input Field
        val ipLabel = android.widget.TextView(this).apply {
            text = getString(R.string.terminal_target_ip_label)
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
                text = getString(R.string.terminal_btn_uplink)
            } else {
                text = android.text.Html.fromHtml("<font color='$primaryHex'>[</font> " + getString(R.string.terminal_btn_uplink) + " <font color='$primaryHex'>]</font>", android.text.Html.FROM_HTML_MODE_LEGACY)
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

        // Flipper Button (Directly under Uplink)
        val flipperBtn = android.widget.Button(this).apply {
            if (isHighContrast) {
                text = getString(R.string.terminal_btn_flipper)
            } else {
                text = android.text.Html.fromHtml("<font color='$primaryHex'>[</font> " + getString(R.string.terminal_btn_flipper) + " <font color='$primaryHex'>]</font>", android.text.Html.FROM_HTML_MODE_LEGACY)
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
            ).apply { setMargins(40, 10, 40, 20) }
        }
        root.addView(flipperBtn)

        // Cancel Button
        val cancelBtn = android.widget.Button(this).apply {
            text = getString(R.string.terminal_btn_abort)
            setTextColor(secondaryColor)
            background = null
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 16f
            setPadding(0, 40, 0, 0)
        }
        root.addView(cancelBtn)

        flipperBtn.setOnClickListener {
            dialog.dismiss()
            val flippers = scanner.getDevices().filter { it.type == DeviceType.FLIPPER }
            val archived = scanner.getArchivedDevices().filter { it.type == DeviceType.FLIPPER }
            
            when {
                flippers.size == 1 -> showFlipperRemoteControl(flippers[0])
                flippers.isEmpty() && archived.isNotEmpty() -> showFlipperRemoteControl(archived[0])
                else -> showFlipperDiscoveryDialog()
            }
        }

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

    private fun showFlipperDiscoveryDialog() {
        val theme = currentTheme
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        val themeColors = ThemeUtils.getThemeColors(theme)
        val primaryColor = themeColors.primary
        val backgroundColor = themeColors.background
        val textColor = if (isHighContrast) Color.BLACK else Color.WHITE
        
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Material_Light_Dialog_NoActionBar else android.R.style.Theme_Material_Dialog_NoActionBar)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundColor))

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val title = android.widget.TextView(this).apply {
            text = "FLIPPER DISCOVERY"
            setTextColor(primaryColor)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        root.addView(title)

        val flippers = scanner.getDevices().filter { it.type == DeviceType.FLIPPER }

        if (flippers.isEmpty()) {
            val emptyMsg = android.widget.TextView(this).apply {
                text = "NO FLIPPERS DETECTED\nENSURE BLE IS ON"
                setTextColor(textColor)
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 20, 0, 40)
            }
            root.addView(emptyMsg)
        } else {
            flippers.forEach { device: ScanDevice ->
                val btn = android.widget.Button(this).apply {
                    text = "${device.name}\n${device.address}"
                    setTextColor(textColor)
                    setBackgroundResource(if (isHighContrast) android.R.drawable.btn_default else 0)
                    typeface = android.graphics.Typeface.MONOSPACE
                    textSize = 16f
                    setPadding(20, 20, 20, 20)
                    setOnClickListener {
                        dialog.dismiss()
                        showFlipperRemoteControl(device)
                    }
                }
                root.addView(btn)
            }
        }

        val closeBtn = android.widget.Button(this).apply {
            text = "[ CLOSE ]"
            setTextColor(primaryColor)
            background = null
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
        }
        closeBtn.setOnClickListener { dialog.dismiss() }
        root.addView(closeBtn)

        dialog.setContentView(root)
        dialog.show()
    }

    private fun showFlipperRemoteControl(device: ScanDevice) {
        val intent = android.content.Intent(this, FlipperRemoteActivity::class.java).apply {
            putExtra(FlipperRemoteActivity.EXTRA_DEVICE, device)
        }
        startActivity(intent)
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

    private var sshSession: Session? = null
    private var sshChannel: ChannelShell? = null
    private var sshIn: java.io.InputStream? = null
    private var sshOut: java.io.OutputStream? = null
    private val sshExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val sshWriteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var ansiParser: AnsiParser? = null

    private val terminalOutputBuffer = SpannableStringBuilder()
    private var isSshWindowOpen = false
    private var activeTerminalBinding: com.radar.blewifi.databinding.TerminalOverlayBinding? = null
    private var isReaderRunning = false

    private fun showTerminalOverlay() {
        val theme = currentTheme
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        
        val dialog = android.app.Dialog(this, if (isHighContrast) android.R.style.Theme_Light_NoTitleBar else android.R.style.Theme_Black_NoTitleBar)
        dialog.setOnDismissListener { 
            isSshWindowOpen = false
            activeTerminalBinding = null
            setupImmersiveMode() 
        }
        isSshWindowOpen = true
        val overlayBinding = com.radar.blewifi.databinding.TerminalOverlayBinding.inflate(layoutInflater)
        activeTerminalBinding = overlayBinding
        dialog.setContentView(overlayBinding.root)

        // Theme-based colors and resources
        val colors = ThemeUtils.getThemeColors(theme)
        val primaryColor = colors.primary
        val secondaryColor = colors.accent
        val bgColor = colors.background
        val headerColor = colors.header
        val inputTextColor = if (isHighContrast) Color.BLACK else Color.WHITE
        val hintColor = ColorUtils.setAlphaComponent(primaryColor, 128)
        
        val buttonBgRes = colors.buttonRes
        val buttonTextColor = if (theme == RadarView.Theme.HIGH_CONTRAST) Color.WHITE else Color.BLACK
        val statusBoxRes = colors.statusBoxRes


        // Apply colors to views
        overlayBinding.root.setBackgroundColor(bgColor)
        overlayBinding.sshLoginPage.setBackgroundColor(bgColor)
        overlayBinding.sshTerminalPage.setBackgroundColor(bgColor)
        overlayBinding.connectionLayout.setBackgroundColor(bgColor)
        overlayBinding.terminalLayout.setBackgroundColor(bgColor)
        
        overlayBinding.headerSshTerminal.setBackgroundColor(headerColor)

        overlayBinding.tvSshLoginTitle.setTextColor(primaryColor)
        
        val editTexts = listOf(overlayBinding.etHost, overlayBinding.etUser, overlayBinding.etPassword)
        editTexts.forEach {
            it.setTextColor(inputTextColor)
            it.setHintTextColor(hintColor)
            it.setBackgroundResource(statusBoxRes)
        }
        
        // Ensure etCommand stays transparent and functional as a proxy
        overlayBinding.etCommand.apply {
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            setHintTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            // Use textNoSuggestions to avoid interfering with terminal input
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        
        overlayBinding.spKeyFile.setBackgroundResource(statusBoxRes)

        // Setup Key File Spinner
        val searchPaths = listOf(
            java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "BLESP/ssh"),
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "Download/BLESP/ssh"),
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "Downloads/BLESP/ssh"),
            java.io.File("/sdcard/Download/BLESP/ssh"),
            java.io.File("/sdcard/Downloads/BLESP/ssh"),
            java.io.File("/sdcard"),
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "")
        )
        
        val keyFilesMap = mutableMapOf<String, java.io.File>()
        searchPaths.forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("id_") || file.name.endsWith(".key") || file.name.endsWith(".pem") || file.name.endsWith(".pub"))) {
                        keyFilesMap[file.name] = file
                    }
                }
            }
        }

        val keyOptions = listOf(getString(R.string.terminal_key_none), getString(R.string.terminal_key_browse)) + keyFilesMap.keys.toList().sorted()
        val keyAdapter = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, keyOptions) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val text = if (position == 1 && selectedKeyName != null) getString(R.string.terminal_key_label, selectedKeyName) else keyOptions[position]
                (v as TextView).text = text
                v.setTextColor(inputTextColor)
                v.typeface = android.graphics.Typeface.MONOSPACE
                v.gravity = android.view.Gravity.CENTER
                v.setPadding(0, 0, 0, 0)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                v.setBackgroundColor(bgColor)
                val text = if (position == 1 && selectedKeyName != null) getString(R.string.terminal_key_label, selectedKeyName) else keyOptions[position]
                (v as TextView).text = text
                (v as TextView).setTextColor(inputTextColor)
                v.typeface = android.graphics.Typeface.MONOSPACE
                v.gravity = android.view.Gravity.CENTER
                v.setPadding(32, 32, 32, 32)
                return v
            }
        }
        overlayBinding.spKeyFile.adapter = keyAdapter
        
        overlayBinding.spKeyFile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (keyOptions[position] == getString(R.string.terminal_key_browse)) {
                    keyPickerLauncher.launch(arrayOf("*/*"))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Match selection if last key used is present
        val sshPrefs = getSharedPreferences("ssh_prefs", android.content.Context.MODE_PRIVATE)
        val lastKey = sshPrefs.getString("last_key", getString(R.string.terminal_key_none))
        val keyIndex = keyOptions.indexOf(lastKey)
        if (keyIndex >= 0) overlayBinding.spKeyFile.setSelection(keyIndex)

        overlayBinding.btnConnect.setTextColor(buttonTextColor)
        overlayBinding.btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        if (theme == RadarView.Theme.HIGH_CONTRAST) {
            overlayBinding.btnConnect.setBackgroundResource(R.drawable.btn_bg_black)
        } else {
             overlayBinding.btnConnect.setBackgroundResource(buttonBgRes)
        }
        
        overlayBinding.tvTerminalOutput.setTextColor(primaryColor)
        overlayBinding.svTerminal.setBackgroundResource(statusBoxRes)
        
        // Style Login Close button (footer style)
        overlayBinding.btnSshLoginClose.setTextColor(secondaryColor)

        val terminalButtons = listOf(
            overlayBinding.btnSshTerminalClose,
            overlayBinding.btnSshLogout
        )
        terminalButtons.forEach {
            val isLogout = it == overlayBinding.btnSshLogout
            it.setTextColor(if (isLogout) Color.RED else secondaryColor)
            
            if (theme == RadarView.Theme.HIGH_CONTRAST) {
                it.setBackgroundResource(R.drawable.status_box_bg_black)
            } else {
                it.setBackgroundResource(if (isLogout) R.drawable.status_box_bg_red else R.drawable.status_box_bg_pink)
            }
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

        // Set themed brackets for buttons
        val primaryHex = String.format("#%06X", (0xFFFFFF and primaryColor))
        if (isHighContrast) {
            overlayBinding.btnConnect.text = "CONNECT"
        } else {
            overlayBinding.btnConnect.text = android.text.Html.fromHtml("<font color='$primaryHex'>[</font> CONNECT <font color='$primaryHex'>]</font>", android.text.Html.FROM_HTML_MODE_LEGACY)
        }
        overlayBinding.btnSshLoginClose.text = getString(R.string.btn_close)
        overlayBinding.btnSshTerminalClose.text = getString(R.string.btn_close)

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

        overlayBinding.btnSshLoginClose.setOnClickListener { 
            dialog.dismiss()
        }

        overlayBinding.btnSshTerminalClose.setOnClickListener {
            dialog.dismiss()
        }

        overlayBinding.btnSshLogout.setOnClickListener {
            logoutSSH()
            overlayBinding.sshTerminalPage.visibility = View.GONE
            overlayBinding.sshLoginPage.visibility = View.VISIBLE
            overlayBinding.btnConnect.isEnabled = true
            overlayBinding.btnConnect.text = getString(R.string.terminal_btn_connect)
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
            val selectedKey = overlayBinding.spKeyFile.selectedItem as String

            if (host.isBlank() || user.isBlank()) return@setOnClickListener

            // Save history
            val updatedHistory = (listOf(host) + historyList).distinct().take(5)
            prefs.edit().apply {
                putString("host_history", org.json.JSONArray(updatedHistory).toString())
                putString("last_host", host)
                putString("last_user", user)
                putString("last_key", selectedKey)
                apply()
            }

            overlayBinding.btnConnect.isEnabled = false
            overlayBinding.btnConnect.text = getString(R.string.terminal_status_connecting)

            Thread {
                try {
                    val jsch = JSch()
                    jsch.removeAllIdentity() // Clear previous identities to avoid pollution
                    
                    JSch.setLogger(object : com.jcraft.jsch.Logger {
                        override fun isEnabled(level: Int) = true
                        override fun log(level: Int, message: String) {
                            android.util.Log.d("SSH_DEBUG", message)
                        }
                    })
                    
                    if (selectedKey != getString(R.string.terminal_key_none)) {
                        if (selectedKey == getString(R.string.terminal_key_browse) && selectedKeyUri != null) {
                            try {
                                contentResolver.openInputStream(selectedKeyUri!!)?.use { inputStream ->
                                    val keyBytes = inputStream.readBytes()
                                    val passphraseBytes = if (pass.isNotEmpty()) pass.toByteArray() else null
                                    
                                    val kp = KeyPair.load(jsch, keyBytes, null)
                                    if (kp.isEncrypted && passphraseBytes != null) {
                                        kp.decrypt(passphraseBytes)
                                    }
                                    jsch.addIdentity(selectedKeyName ?: "selected_key", keyBytes, null, passphraseBytes)
                                    android.util.Log.d("SSH_DEBUG", "Added identity from URI: $selectedKeyName")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SSH_DEBUG", "Failed to load key from URI: ${e.message}")
                            }
                        } else {
                            val keyFile = keyFilesMap[selectedKey]
                            if (keyFile != null && keyFile.exists()) {
                                // ... existing key file logic ...
                                val privateKeyPath = if (keyFile.name.endsWith(".pub")) {
                                    keyFile.absolutePath.removeSuffix(".pub")
                                } else {
                                    keyFile.absolutePath
                                }
                                
                                val privFile = java.io.File(privateKeyPath)
                                if (privFile.exists()) {
                                    try {
                                        val keyBytes = privFile.readBytes()
                                        val passphraseBytes = if (pass.isNotEmpty()) pass.toByteArray() else null
                                        
                                        val kp = KeyPair.load(jsch, keyBytes, null)
                                        if (kp.isEncrypted && passphraseBytes != null) {
                                            kp.decrypt(passphraseBytes)
                                        }
                                        val pubFile = java.io.File("$privateKeyPath.pub")
                                        val pubBytes = if (pubFile.exists()) pubFile.readBytes() else null
                                        jsch.addIdentity(privFile.absolutePath, keyBytes, pubBytes, passphraseBytes)
                                        android.util.Log.d("SSH_DEBUG", "Added identity: ${privFile.name}")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SSH_DEBUG", "Failed to load key ${privFile.name}: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

                    val session = jsch.getSession(user, host, 22)
                    if (pass.isNotBlank()) {
                        session.setPassword(pass)
                    }
                    session.setConfig("StrictHostKeyChecking", "no")
                    
                    // Explicitly set preferred order and include keyboard-interactive for better compatibility
                    session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
                    session.setConfig("PubkeyAcceptedAlgorithms", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
                    session.setConfig("server_host_key", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ssh-rsa")

                    // Handle interactive auth prompts (common cause of "user auth fail" or "auth cancel")
                    session.setUserInfo(object : UserInfo, UIKeyboardInteractive {
                        override fun getPassphrase(): String? = pass
                        override fun getPassword(): String? = pass
                        override fun promptPassword(message: String?): Boolean = true
                        override fun promptPassphrase(message: String?): Boolean = true
                        override fun promptYesNo(message: String?): Boolean = true
                        override fun showMessage(message: String?) {
                            android.util.Log.d("SSH_DEBUG", "UserInfo Message: $message")
                        }
                        override fun promptKeyboardInteractive(
                            destination: String?,
                            name: String?,
                            instruction: String?,
                            prompt: Array<out String>?,
                            echo: BooleanArray?
                        ): Array<String>? {
                            android.util.Log.d("SSH_DEBUG", "Keyboard Interactive Prompt: $instruction")
                            if (prompt != null && prompt.isNotEmpty()) {
                                return Array(prompt.size) { pass }
                            }
                            return arrayOf()
                        }
                    })

                    session.connect(10000)

                    val channel = session.openChannel("shell") as ChannelShell
                    
                    // Initial PTY size calculation
                    val paint = overlayBinding.tvTerminalOutput.paint
                    paint.typeface = android.graphics.Typeface.MONOSPACE
                    val charWidth = paint.measureText("M")
                    val charHeight = overlayBinding.tvTerminalOutput.lineHeight.toFloat()
                    
                    val initialWidth = if (overlayBinding.svTerminal.width > 0) overlayBinding.svTerminal.width else resources.displayMetrics.widthPixels
                    val initialHeight = if (overlayBinding.svTerminal.height > 0) overlayBinding.svTerminal.height else (resources.displayMetrics.heightPixels * 0.5).toInt()

                    val cols = (initialWidth / charWidth).toInt().coerceIn(40, 250)
                    val rows = (initialHeight / charHeight).toInt().coerceIn(10, 100)

                    channel.setPtyType("xterm-256color")
                    channel.setPtySize(cols, rows, (cols * charWidth).toInt(), (rows * charHeight).toInt())
                    
                    channel.connect()

                    sshSession = session
                    sshChannel = channel
                    sshIn = channel.getInputStream()
                    sshOut = channel.getOutputStream()
                    ansiParser = AnsiParser(cols, rows)

                    runOnUiThread {
                        overlayBinding.sshLoginPage.visibility = View.GONE
                        overlayBinding.sshTerminalPage.visibility = View.VISIBLE
                        
                        // Force initial size
                        overlayBinding.tvTerminalOutput.requestLayout()

                        // Setup dynamic resize listener
                        overlayBinding.svTerminal.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            val p = overlayBinding.tvTerminalOutput.paint
                            val cW = p.measureText("M")
                            val cH = overlayBinding.tvTerminalOutput.lineHeight.toFloat()
                            
                            val newCols = (overlayBinding.svTerminal.width / cW).toInt().coerceIn(40, 250)
                            val newRows = (overlayBinding.svTerminal.height / cH).toInt().coerceIn(10, 100)
                            
                            if (ansiParser?.cols != newCols || ansiParser?.rows != newRows) {
                                sshExecutor.execute {
                                    try {
                                        sshChannel?.setPtySize(newCols, newRows, (newCols * cW).toInt(), (newRows * cH).toInt())
                                        ansiParser?.resize(newCols, newRows)
                                        
                                        runOnUiThread {
                                            // Ensure TextView update is requested
                                            overlayBinding.tvTerminalOutput.requestLayout()
                                            
                                            // Scroll to bottom after resize if needed
                                            overlayBinding.svTerminal.post {
                                                overlayBinding.svTerminal.fullScroll(View.FOCUS_DOWN)
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                        
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
                            overlayBinding.btnConnect.text = getString(R.string.terminal_btn_connect)
                        } else {
                            overlayBinding.btnConnect.text = getString(R.string.terminal_btn_connect)
                        }
                        
                        // Request focus and show keyboard when session starts
                        overlayBinding.etCommand.requestFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(overlayBinding.etCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

                        startTerminalReader()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        overlayBinding.btnConnect.isEnabled = true
                        overlayBinding.btnConnect.text = getString(R.string.terminal_status_error)
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
            sshWriteExecutor.execute {
                try {
                    sshOut?.write(bytes)
                    sshOut?.flush()
                } catch (e: Exception) {
                    android.util.Log.e("SSH_WRITE", "Error sending raw: ${e.message}")
                }
            }
        }

        overlayBinding.tvTerminalOutput.setOnClickListener {
            overlayBinding.etCommand.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(overlayBinding.etCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        overlayBinding.svTerminal.setOnClickListener {
            overlayBinding.etCommand.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(overlayBinding.etCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        
        // Ensure proxy is ready
        overlayBinding.etCommand.setText(" ")
        overlayBinding.etCommand.setSelection(1)
        overlayBinding.etCommand.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return
                val currentText = s.toString()

                // If the text is not our single-space proxy, something was typed or deleted
                if (currentText != " ") {
                    val textToSend = if (currentText.isEmpty()) {
                        null // Signifies backspace
                    } else if (currentText.length > 1 && currentText.startsWith(" ")) {
                        currentText.substring(1)
                    } else if (currentText.length == 1 && currentText != " ") {
                        currentText
                    } else {
                        currentText.replace(" ", "")
                    }

                    sshWriteExecutor.execute {
                        try {
                            if (textToSend == null) {
                                sshOut?.write(byteArrayOf(127)) // DEL (Standard for Backspace in Linux terminals)
                            } else {
                                for (char in textToSend) {
                                    sshOut?.write(char.toString().toByteArray(Charsets.UTF_8))
                                }
                            }
                            sshOut?.flush()
                        } catch (e: Exception) {
                            android.util.Log.e("SSH_WRITE", "Error sending text: ${e.message}")
                        }
                    }

                    isUpdating = true
                    overlayBinding.etCommand.setText(" ")
                    overlayBinding.etCommand.setSelection(1)
                    isUpdating = false
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Catch hardware backspace and other keys that don't trigger TextWatcher
        overlayBinding.etCommand.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DEL -> {
                        sshWriteExecutor.execute {
                            try {
                                sshOut?.write(byteArrayOf(127)) // DEL
                                sshOut?.flush()
                            } catch (e: Exception) {}
                        }
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        sshWriteExecutor.execute {
                            try {
                                sshOut?.write("\r".toByteArray()) // CR (Carriage Return)
                                sshOut?.flush()
                            } catch (e: Exception) {}
                        }
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        sshWriteExecutor.execute {
                            try {
                                sshOut?.write(byteArrayOf(27, '['.code.toByte(), 'A'.code.toByte()))
                                sshOut?.flush()
                            } catch (e: Exception) {}
                        }
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        sshWriteExecutor.execute {
                            try {
                                sshOut?.write(byteArrayOf(27, '['.code.toByte(), 'B'.code.toByte()))
                                sshOut?.flush()
                            } catch (e: Exception) {}
                        }
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        sshWriteExecutor.execute {
                            try {
                                sshOut?.write(byteArrayOf(27, '['.code.toByte(), 'C'.code.toByte()))
                                sshOut?.flush()
                            } catch (e: Exception) {}
                        }
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        sshWriteExecutor.execute {
                            try {
                                sshOut?.write(byteArrayOf(27, '['.code.toByte(), 'D'.code.toByte()))
                                sshOut?.flush()
                            } catch (e: Exception) {}
                        }
                        return@setOnKeyListener true
                    }
                }
            }
            false
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

            overlayBinding.tvTerminalOutput.text = ansiParser?.render() ?: ""
            overlayBinding.svTerminal.post {
                overlayBinding.svTerminal.fullScroll(View.FOCUS_DOWN)
            }
            
            // Request focus and show keyboard for existing session
            overlayBinding.etCommand.postDelayed({
                overlayBinding.etCommand.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(overlayBinding.etCommand, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 300)

            startTerminalReader()
        }

        dialog.show()
    }

    private fun startTerminalReader() {
        if (isReaderRunning) return
        isReaderRunning = true
        val channel = sshChannel
        val inputStream = sshIn
        val parser = ansiParser
        Thread {
            val buffer = ByteArray(8192)
            while (channel?.isConnected == true) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead <= 0) break
                    val text = String(buffer, 0, bytesRead)
                    
                    sshExecutor.execute {
                        val currentParser = parser ?: ansiParser
                        currentParser?.parse(text)
                        val rendered = currentParser?.render()
                        
                        if (rendered != null && (currentParser == ansiParser || parser == null)) {
                            runOnUiThread {
                                activeTerminalBinding?.let { binding ->
                                    binding.tvTerminalOutput.setText(rendered, TextView.BufferType.SPANNABLE)
                                    binding.svTerminal.post {
                                        binding.svTerminal.fullScroll(View.FOCUS_DOWN)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { 
                    android.util.Log.e("SSH_READER", "Error: ${e.message}")
                    break 
                }
            }
            isReaderRunning = false
        }.start()
    }

    private fun logoutSSH() {
        sshWriteExecutor.execute {
            try {
                sshOut?.close()
                sshIn?.close()
                sshChannel?.disconnect()
                sshSession?.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("SSH_LOGOUT", "Error during logout: ${e.message}")
            } finally {
                sshOut = null
                sshIn = null
                sshChannel = null
                sshSession = null
                ansiParser = null
                runOnUiThread {
                    activeTerminalBinding?.tvTerminalOutput?.text = ""
                }
            }
        }
    }

    private fun startLogoAnimation() {
        binding.ivLogo.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
            binding.ivLogo.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        }.start()
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
        val colors = ThemeUtils.getThemeColors(currentTheme)
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
            val discColor = colors.accent
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(discColor),
                discStart, discEnd,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val titleText = "PROJECT INFO"
        val spannableTitle = android.text.SpannableString(titleText)
        val titleColor = colors.primary
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
        
        // Background styling
        val bg = android.graphics.drawable.GradientDrawable()
        if (isHighContrast) {
            bg.setColor(Color.WHITE)
            bg.setStroke(4, Color.BLACK)
        } else {
            bg.setColor(if (currentTheme == RadarView.Theme.DEFAULT) Color.parseColor("#99002200") else Color.BLACK)
            bg.setStroke(2, colors.primary)
        }
        bg.cornerRadius = 60f
        dialog.window?.setBackgroundDrawable(bg)

        // Make links clickable and set colors
        val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        messageView?.let {
            it.autoLinkMask = android.text.util.Linkify.WEB_URLS or android.text.util.Linkify.EMAIL_ADDRESSES
            it.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            
            val linkColor = colors.accent

            it.setTextColor(colors.primary)
            it.setLinkTextColor(linkColor)
            it.typeface = Typeface.MONOSPACE
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(colors.accent)
            it.setTypeface(null, Typeface.BOLD)
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

    private fun showDeviceDetailsDialog(device: ScanDevice) {
        val colors = ThemeUtils.getThemeColors(currentTheme)
        val titleText = "DEVICE DEEP INSIGHTS"
        
        val macAddress = device.address
        val vendorText = device.vendor ?: "Unknown Vendor"
        val isRandomized = if (device.isRandomizedMac) "YES (Private/Locally Administered)" else "NO (Hardware MAC)"
        val osFingerprintText = device.osFingerprint ?: "Scanning/Unknown OS"
        
        val details = """
            IDENTIFIER
            Name: ${device.displayName}
            Type: ${device.typeLabel}
            
            MAC ADDRESS & VENDOR
            MAC Address: $macAddress
            OUI Vendor: $vendorText
            Randomized MAC: $isRandomized
            
            OS FINGERPRINTING
            Detected OS: $osFingerprintText
            Method: Open ports & banner heuristic response
            
            SIGNAL ANALYTICS
            RSSI: ${device.rssi} dBm (${device.signalStrength})
            Est. Distance: ${device.distanceLabel}
            Seen Count: ${device.seenCount}
        """.trimIndent()

        val spannable = android.text.SpannableStringBuilder(details)
        
        // Highlight headers in secondary color
        val headers = listOf("IDENTIFIER", "MAC ADDRESS & VENDOR", "OS FINGERPRINTING", "SIGNAL ANALYTICS")
        headers.forEach { header ->
            val start = details.indexOf(header)
            if (start >= 0) {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(colors.accent),
                    start, start + header.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, start + header.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        val spannableTitle = android.text.SpannableString(titleText)
        spannableTitle.setSpan(
            android.text.style.ForegroundColorSpan(colors.primary),
            0, titleText.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(spannableTitle)
            .setMessage(spannable)
            .setPositiveButton("CLOSE", null)
            .show()
            
        dialog.setOnDismissListener { setupImmersiveMode() }

        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val bg = android.graphics.drawable.GradientDrawable()
        if (isHighContrast) {
            bg.setColor(Color.WHITE)
            bg.setStroke(4, Color.BLACK)
        } else {
            bg.setColor(if (currentTheme == RadarView.Theme.DEFAULT) Color.parseColor("#99002200") else Color.BLACK)
            bg.setStroke(2, colors.primary)
        }
        bg.cornerRadius = 60f
        dialog.window?.setBackgroundDrawable(bg)

        val messageView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        messageView?.let {
            it.setTextColor(colors.primary)
            it.typeface = Typeface.MONOSPACE
            it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(colors.accent)
            it.setTypeface(null, Typeface.BOLD)
        }
        
        // Cyberpunk minor glitch jitter effect
        val glitchRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val decorView = dialog.window?.decorView
                if ((1..20).random() == 1) {
                    decorView?.translationX = ((-6..6).random()).toFloat()
                    handler.postDelayed({
                        decorView?.translationX = 0f
                        handler.postDelayed(this, (400..1500).random().toLong())
                    }, 80L)
                } else {
                    handler.postDelayed(this, 250L)
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
            val bearing = (device.angle + (binding.radarView.rotationDegrees + 180f)) % 360.0
            val destination = when {
                device.type == DeviceType.WIFI && device.capturedLat != null -> {
                    GeoPoint(device.capturedLat!!, device.capturedLon!!)
                }
                device.type == DeviceType.CAMERA || device.type == DeviceType.AIRCRAFT -> {
                    if (device.lat != null && device.lon != null) {
                        GeoPoint(device.lat!!, device.lon!!)
                    } else {
                        calculateDestinationPoint(userGeoPoint, dist, bearing)
                    }
                }
                else -> calculateDestinationPoint(userGeoPoint, dist, bearing)
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
            var snippetText = "${device.typeLabel} | ${device.distanceLabel} | RSSI: ${device.rssi}"
            if (device.streetName != null) {
                val addrStr = "${device.streetName} ${device.houseNumber ?: ""}, ${device.cityName}".replace(" ,", ",")
                snippetText += "\nLoc: $addrStr"
            } else if (device.capturedLat != null) {
                snippetText += "\nLoc: %.5f, %.5f".format(device.capturedLat, device.capturedLon)
            }
            marker.snippet = snippetText
            
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
        
        val colors = ThemeUtils.getThemeColors(currentTheme)
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        
        val baseColor = when (device.type) {
            DeviceType.WIFI, DeviceType.PAGER, DeviceType.ROUTER, DeviceType.PRINTER, DeviceType.IOT -> colors.primary
            DeviceType.BLE, DeviceType.CAR, DeviceType.ESCOOTER, DeviceType.TV, DeviceType.COMPUTER, DeviceType.SMARTPHONE, DeviceType.FLIPPER, DeviceType.HEADPHONES, DeviceType.WEARABLE, DeviceType.TABLET, DeviceType.TRACKER, DeviceType.GAME_CONSOLE -> colors.accent
            DeviceType.AIRCRAFT, DeviceType.DRONE -> colors.accent
            DeviceType.LTE, DeviceType.FIVE_G -> colors.primary
            DeviceType.CAMERA -> if (isHighContrast) Color.BLACK else Color.RED
            DeviceType.SUBGHZ -> colors.accent
        }

        var finalColor = baseColor
        if (device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")) {
            finalColor = colors.accent
        } else if (device.isAirTag) {
            finalColor = if (isHighContrast) Color.BLACK else Color.WHITE
        }

        paint.color = finalColor
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (isHighContrast) Color.WHITE else Color.BLACK
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
            
            val colors = ThemeUtils.getThemeColors(currentTheme)
            val colorStart = colors.primary
            val colorEnd = ColorUtils.setAlphaComponent(colors.accent, 128)
            
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
        val colors = ThemeUtils.getThemeColors(currentTheme)
        binding.btnPager.setColorFilter(colors.primary)
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
                    DeviceType.TV, DeviceType.COMPUTER, DeviceType.SMARTPHONE, DeviceType.PAGER, DeviceType.HEADPHONES,
                    DeviceType.WEARABLE, DeviceType.TABLET, DeviceType.IOT, DeviceType.TRACKER, DeviceType.GAME_CONSOLE -> binding.cbBle.isChecked
                    DeviceType.AIRCRAFT, DeviceType.DRONE -> binding.cbAero.isChecked
                    DeviceType.LTE, DeviceType.FIVE_G -> true // Always show or handle differently if needed
                    DeviceType.CAMERA -> binding.cbCams.isChecked
                    DeviceType.FLIPPER -> binding.cbBle.isChecked
                    DeviceType.SUBGHZ -> binding.cbBle.isChecked
                    DeviceType.ROUTER, DeviceType.PRINTER -> binding.cb24.isChecked || binding.cb5.isChecked
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
            
<<<<<<< HEAD
<<<<<<< HEAD
            // Throttle map marker updates when map view is active to save CPU
            if (!isListView && binding.mapView.visibility == View.VISIBLE) {
                if (now - lastMapUpdate >= 500L) {
                    updateMapMarkers(filteredForRadar) 
                    lastMapUpdate = now
                }
=======
            // Limit marker updates as they are expensive on performance
            if (!isListView) {
                updateMapMarkers(filteredForRadar) 
>>>>>>> parent of 5dc96b0 (v3.5.3)
=======
            // Only update map markers when map view is actually visible to save CPU
            if (!isListView && binding.mapView.visibility == View.VISIBLE) {
                updateMapMarkers(filteredForRadar) 
>>>>>>> parent of 1ed2e71 (v3.5.3)
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

            val colors = ThemeUtils.getThemeColors(currentTheme)
            val numColor = colors.primary
            val targetColor = colors.accent
            val aeroNumColor = colors.accent
            val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST

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
        binding.btnBeep.animate().cancel()
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
            var isAccent = true
            override fun run() {
                if (!isBeeping) return
                val colors = ThemeUtils.getThemeColors(currentTheme)
                if (isAccent) {
                    binding.btnBeep.setBackgroundResource(colors.statusBoxRes)
                    binding.btnBeep.setTextColor(colors.accent)
                    binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(colors.accent))
                } else {
                    binding.btnBeep.setBackgroundResource(colors.buttonRes)
                    binding.btnBeep.setTextColor(colors.primary)
                    binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(colors.primary))
                }
                isAccent = !isAccent
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
        noiseHoldRunnable?.let { handler.removeCallbacks(it) }
        noiseHoldRunnable = null
        binding.btnBeep.animate().cancel()
        noiseBlinkRunnable?.let { handler.removeCallbacks(it) }
        glitchRunnable?.let { handler.removeCallbacks(it) }
        noisePulsator?.cancel()
        noisePulsator = null
        binding.btnBeep.scaleX = 1.0f
        binding.btnBeep.scaleY = 1.0f
        binding.btnBeep.translationX = 0f
        binding.btnBeep.translationY = 0f
        binding.btnBeep.text = "[ NOISEGENERATOR ]"
        binding.btnBeep.textSize = 10f
        
        val colors = ThemeUtils.getThemeColors(currentTheme)
        binding.btnBeep.setBackgroundResource(colors.statusBoxRes)
        binding.btnBeep.setTextColor(colors.primary)
        binding.btnBeep.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(colors.primary))
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
        
        val colors = ThemeUtils.getThemeColors(theme)
        val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = theme == RadarView.Theme.RED_NIGHT
        
        val isSelected = device.id == selectedId
        val selectionColor = ColorUtils.setAlphaComponent(colors.accent, 51) // 33 hex is ~51/255 (20%)
        
        holder.itemView.setBackgroundColor(if (isSelected) selectionColor else Color.TRANSPARENT)
        
        val isWep = device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")
        val isWhisper = device.isVulnerableWhisperPair
        
        val nameColor = if (isHighContrast) Color.BLACK 
            else if (isRedNight) Color.RED
            else if (device.type == DeviceType.WIFI) {
                val caps = device.capabilities.uppercase()
                val is5GHz = device.frequency in 5000..6000
                if (is5GHz) colors.dim
                else if (caps.contains("WPA")) colors.primary
                else colors.primary
            } else colors.primary
        
        holder.name.setTextColor(nameColor)
        holder.dist.text = device.distanceLabel
        
        // Show manufacturer logo
        val isApple = device.manufacturer == "0x004C" || device.name.lowercase().contains("apple") || device.isAirTag
        val isAndroid = device.manufacturer == "0x00E0" || device.name.lowercase().contains("android") || device.isFastPair
        
        if (isApple || isAndroid) {
            holder.mfrLogo.visibility = View.VISIBLE
            holder.mfrLogo.setImageResource(if (isApple) {
                if (isHighContrast) R.drawable.ic_apple_black else if (isRedNight) R.drawable.ic_apple_red else R.drawable.ic_apple
            } else {
                if (isHighContrast) R.drawable.ic_android_black else if (isRedNight) R.drawable.ic_android_red else R.drawable.ic_android
            })
            if (!isHighContrast && !isRedNight && theme != RadarView.Theme.DEFAULT) {
                holder.mfrLogo.setColorFilter(colors.primary)
            } else {
                holder.mfrLogo.clearColorFilter()
            }
        } else {
            holder.mfrLogo.visibility = View.GONE
        }
        
        // Pink and glowing for close proximity (< 1m)
        if (device.distanceMeters < 1.0) {
            val closeColor = if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent
            holder.dist.setTextColor(closeColor)
            if (!isHighContrast && !isRedNight) {
                holder.dist.setShadowLayer(15f, 0f, 0f, closeColor)
            } else {
                holder.dist.setShadowLayer(0f, 0f, 0f, 0)
            }
            
            holder.dist.clearAnimation()
            val anim = android.view.animation.AlphaAnimation(0.5f, 1.0f).apply {
                duration = 400
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            holder.dist.startAnimation(anim)
        } else {
            holder.dist.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) ColorUtils.setAlphaComponent(Color.RED, 153) else colors.dim)
            holder.dist.setShadowLayer(0f, 0f, 0f, 0)
            holder.dist.clearAnimation()
            holder.dist.alpha = 1.0f
        }

        holder.addr.text = if (device.streetName != null) {
            val addrStr = "${device.streetName} ${device.houseNumber ?: ""}, ${device.cityName}".replace(" ,", ",")
            "${device.address} | ADDR: $addrStr"
        } else if (device.capturedLat != null) {
            "${device.address} | ADDR: %.5f, %.5f".format(device.capturedLat, device.capturedLon)
        } else if (device.lat != null) {
            "${device.address} | ADDR: %.5f, %.5f".format(device.lat, device.lon)
        } else {
            "${device.address} | ADDR: Awaiting GPS..."
        }
        holder.addr.setTextColor(if (isHighContrast) Color.DKGRAY else ColorUtils.setAlphaComponent(colors.primary, 102))

        // Only show vulnerability text/alarms if it's WEP or WhisperPair
        if (isWep) {
            holder.alarm.visibility = View.VISIBLE
            holder.alarm.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent)
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
            holder.whisper.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent)
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
            holder.airtag.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.primary)
        } else {
            holder.airtag.visibility = View.GONE
        }

        if (device.type == DeviceType.AIRCRAFT) {
            holder.aero.visibility = View.VISIBLE
            holder.aero.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent)
        } else {
            holder.aero.visibility = View.GONE
        }

        if (device.isCar) {
            holder.car.visibility = View.VISIBLE
            holder.car.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent)
        } else {
            holder.car.visibility = View.GONE
        }

        if (device.isPubliclyConnectable) {
            holder.hijack.visibility = View.VISIBLE
            holder.hijack.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent)
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
            holder.vuln.setTextColor(if (isHighContrast) Color.BLACK else if (isRedNight) Color.RED else colors.accent)
            
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
