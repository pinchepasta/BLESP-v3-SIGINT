package com.radar.blewifi

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.radar.blewifi.wspr.ui.theme.WSPRTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ArchiveActivity : ComponentActivity() {

    private lateinit var scanner: ScannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        enableEdgeToEdge()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("immersive_mode", true)) {
            enterImmersiveMode()
        }

        scanner = ScannerManager(this)

        setContent {
            val themeName = remember {
                prefs.getString("theme_name", "DEFAULT") ?: "DEFAULT"
            }
            
            var allDevices by remember { mutableStateOf(scanner.getArchivedDevices()) }
            var searchQuery by remember { mutableStateOf("") }
            val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

            val filteredDevices = remember(allDevices, searchQuery) {
                if (searchQuery.isEmpty()) allDevices else {
                    val q = searchQuery.lowercase()
                    allDevices.filter {
                        it.address.lowercase().contains(q) ||
                        it.name.lowercase().contains(q) ||
                        it.ssid.lowercase().contains(q) ||
                        it.typeLabel.lowercase().contains(q) ||
                        it.capabilities.lowercase().contains(q)
                    }
                }
            }

            val groupedDevices = remember(filteredDevices) {
                filteredDevices.groupBy { typeToLabel(it.type) }
            }

        WSPRTheme(themeName = themeName) {
            val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.background
            ) {
                ArchiveScreen(
                    themeName = themeName,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    groupedDevices = groupedDevices,
                    expandedCategories = expandedCategories,
                    onCategoryToggle = { cat ->
                        expandedCategories[cat] = !(expandedCategories[cat] ?: false)
                    },
                    onDeviceClick = { device ->
                        // Pass specific device coordinates/node to focus on ArchiveMapActivity
                        val intent = Intent(this, ArchiveMapActivity::class.java)
                        intent.putExtra("EXTRA_DEVICE", device)
                        startActivity(intent)
                    },
                    onExport = { exportArchive(allDevices) },
                    onOpenMap = {
                        val intent = Intent(this, ArchiveMapActivity::class.java)
                        startActivity(intent)
                    },
                    onOpenMapWithDevice = { device ->
                        val intent = Intent(this, ArchiveMapActivity::class.java)
                        intent.putExtra("EXTRA_DEVICE", device)
                        startActivity(intent)
                    },
                    onBack = {
                        finish()
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                )
            }
        }
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun typeToLabel(type: DeviceType): String = when(type) {
        DeviceType.BLE -> "BLE"
        DeviceType.WIFI -> "WiFi"
        DeviceType.AIRCRAFT -> "Aircraft"
        DeviceType.CAR -> "Car"
        DeviceType.DRONE -> "Drone"
        DeviceType.ESCOOTER -> "Escooter"
        DeviceType.TV -> "TV"
        DeviceType.COMPUTER -> "Computer"
        DeviceType.SMARTPHONE -> "Smartphone"
        DeviceType.PAGER -> "Pager"
        DeviceType.CAMERA -> "CCTV"
        DeviceType.FLIPPER -> "Flipper"
        DeviceType.HEADPHONES -> "Headphones"
        DeviceType.ROUTER -> "Router"
        DeviceType.PRINTER -> "Printer"
        DeviceType.IOT -> "IoT"
        DeviceType.WEARABLE -> "Wearable"
        DeviceType.TABLET -> "Tablet"
        DeviceType.TRACKER -> "Tracker"
        DeviceType.GAME_CONSOLE -> "Console"
        DeviceType.LTE, DeviceType.FIVE_G -> "Cell"
        else -> "Other"
    }

    private fun exportArchive(devices: List<ScanDevice>) {
        if (devices.isEmpty()) {
            Toast.makeText(this, "Archive is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val filename = "BLESP_WiGLE_${sdf.format(Date())}.csv"
        val csv = StringBuilder()
        
        // WiGLE CSV Header format:
        // Line 1: WiGLE WiFi-Format version 3, columns, etc.
        // Line 2: appRelease,model,device,display,board,brand,androidRelease
        // Line 3: MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type
        csv.append("WigleWifi-1.4,appRelease=4.0,model=${Build.MODEL},device=${Build.DEVICE},display=${Build.DISPLAY},board=${Build.BOARD},brand=${Build.BRAND},androidRelease=${Build.VERSION.RELEASE}\n")
        csv.append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")

        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        devices.forEach { d ->
            val mac = d.address
            val ssid = if (d.type == DeviceType.WIFI) d.ssid else d.name
            // Determine type string compatible with WiGLE (WIFI, BLE, CELL)
            val wigleType = when (d.type) {
                DeviceType.WIFI -> "WIFI"
                DeviceType.BLE -> "BLE"
                DeviceType.LTE, DeviceType.FIVE_G -> "CELL"
                else -> if (d.typeLabel == "Cell") "CELL" else "BLE"
            }
            val auth = if (d.type == DeviceType.WIFI) d.capabilities else "[BLE]"
            val firstSeen = timeFormat.format(Date(d.lastSeen))
            val chan = if (d.type == DeviceType.WIFI) d.channel else 0
            val rssi = d.rssi
            val lat = d.capturedLat ?: d.lat ?: 0.0
            val lon = d.capturedLon ?: d.lon ?: 0.0
            val alt = d.altitude ?: 0f
            val acc = 10.0 // Default accuracy
            
            csv.append("\"$mac\",\"$ssid\",\"$auth\",\"$firstSeen\",$chan,$rssi,$lat,$lon,$alt,$acc,\"$wigleType\"\n")
        }

        try {
            val cacheFile = File(cacheDir, filename)
            FileOutputStream(cacheFile).use { it.write(csv.toString().toByteArray()) }

            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", cacheFile)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BLESP WiGLE Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Export WiGLE CSV..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearConfirmation(themeName: String, onConfirm: () -> Unit) {
        val isHighContrast = themeName == "HIGH_CONTRAST"
        val isSummertime = themeName == "SUMMERTIME"
        val isRedNight = themeName == "RED_NIGHT"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("CLEAR ARCHIVE")
            .setMessage("Are you sure you want to delete all stored signal data?")
            .setPositiveButton("DELETE") { _, _ -> onConfirm() }
            .setNegativeButton("CANCEL", null)
            .show()

        val bg = android.graphics.drawable.GradientDrawable()
        when {
            isHighContrast -> {
                bg.setColor(AndroidColor.WHITE)
                bg.setStroke(4, AndroidColor.BLACK)
            }
            isRedNight -> {
                bg.setColor(AndroidColor.BLACK)
                bg.setStroke(2, AndroidColor.RED)
            }
            isSummertime -> {
                bg.setColor(AndroidColor.parseColor("#1A1A1A"))
                bg.setStroke(2, AndroidColor.parseColor("#ff9f6b"))
            }
            else -> {
                bg.setColor(AndroidColor.parseColor("#99220000"))
                bg.setStroke(2, AndroidColor.RED)
            }
        }
        bg.cornerRadius = 30f
        dialog.window?.setBackgroundDrawable(bg)

        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
            setTextColor(if (isHighContrast) AndroidColor.BLACK else if (isRedNight) AndroidColor.RED else if (isSummertime) AndroidColor.parseColor("#ff9f6b") else AndroidColor.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    themeName: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    groupedDevices: Map<String, List<ScanDevice>>,
    expandedCategories: Map<String, Boolean>,
    onCategoryToggle: (String) -> Unit,
    onDeviceClick: (ScanDevice) -> Unit,
    onExport: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenMapWithDevice: (ScanDevice) -> Unit = {},
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ARCHIVE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onExport) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .offset(y = (-2).dp)
                            )
                        }
                        IconButton(onClick = onOpenMap) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Map View",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .offset(y = (-2).dp)
                            )
                        }
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        "CLOSE",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = {
                    Text(
                        "SEARCH MAC / SSID / TYPE...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            if (groupedDevices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "NO ENTRIES FOUND",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    groupedDevices.forEach { (category, devices) ->
                        item {
                            CategoryHeader(
                                title = category,
                                count = devices.size,
                                isExpanded = expandedCategories[category] ?: false,
                                onToggle = { onCategoryToggle(category) }
                            )
                        }
                        if (expandedCategories[category] == true) {
                            items(devices) { device ->
                                DeviceArchiveItem(
                                    device = device,
                                    themeName = themeName,
                                    onClick = { onDeviceClick(device) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
        }
        Text(
            text = count.toString(),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp
        )
    }
}

@Composable
fun DeviceArchiveItem(
    device: ScanDevice,
    themeName: String,
    onClick: () -> Unit
) {
    val theme = try { RadarView.Theme.valueOf(themeName) } catch (e: Exception) { RadarView.Theme.DEFAULT }
    val colors = ThemeUtils.getThemeColors(theme)
    val isHighContrast = theme == RadarView.Theme.HIGH_CONTRAST
    val isRedNight = theme == RadarView.Theme.RED_NIGHT
    
    val primaryColor = MaterialTheme.colorScheme.primary
    
    val nameColor = when {
        isHighContrast -> Color.Black
        isRedNight -> Color.Red
        device.type == DeviceType.WIFI -> {
            val caps = device.capabilities.uppercase()
            val is5GHz = device.frequency in 5000..6000
            if (is5GHz) Color(colors.dim) else primaryColor
        }
        else -> primaryColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = device.displayName,
                color = nameColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (device.type == DeviceType.AIRCRAFT) BadgeText("✈", Color(0xFF00FFFF))
                if (device.isCar) BadgeText("🚗", Color(0xFFFF00FF))
                if (device.isAirTag) BadgeText("◈", Color(0xFFFF00FF))
                if (device.isPubliclyConnectable) BadgeText("⚡", Color(0xFF00FFFF))
                
                if (device.type == DeviceType.WIFI && device.capabilities.uppercase().contains("WEP")) {
                     Text("⚠️WEP!", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                
                Text(
                    text = device.distanceLabel,
                    color = primaryColor.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        
        Text(
            text = device.address,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        
        val extraVulns = mutableListOf<String>()
        if (device.isLegacyBluetooth) extraVulns.add("LEGACY")
        if (device.isVulnerableBlueWhisper) extraVulns.add("BW")
        if (device.isVulnerableCVE202536911) extraVulns.add("CVE-2025-36911")
        
        if (extraVulns.isNotEmpty()) {
            val vulnColor = if (isHighContrast) Color.Black else if (isRedNight) Color.Red else Color(colors.accent)
            Text(
                text = extraVulns.joinToString(" | "),
                color = vulnColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val addr = when {
            device.streetName != null -> "${device.streetName} ${device.houseNumber ?: ""}, ${device.cityName}".replace(" ,", ",")
            device.capturedLat != null -> "GPS: %.5f, %.5f".format(device.capturedLat, device.capturedLon)
            else -> "Awaiting GPS..."
        }
        Text(
            text = addr,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun BadgeText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(end = 4.dp)
    )
}
