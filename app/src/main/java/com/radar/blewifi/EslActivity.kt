package com.radar.blewifi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.radar.blewifi.wspr.ui.theme.WSPRTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class EslActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fullscreen immersive mode
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            val prefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }
            var themeName by remember { 
                mutableStateOf(prefs.getString("theme_name", "DEFAULT") ?: "DEFAULT") 
            }
            
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EslApp()
                }
            }
        }
    }
}

@Composable
fun cyberAnnotatedString(
    text: String,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary
): AnnotatedString {
    val openIdx = text.indexOf("[")
    val closeIdx = text.lastIndexOf("]")

    return buildAnnotatedString {
        if (openIdx != -1 && closeIdx != -1) {
            // Text before brackets
            if (openIdx > 0) {
                withStyle(SpanStyle(color = primaryColor)) {
                    append(text.substring(0, openIdx))
                }
            }

            // Inside brackets
            val insideText = text.substring(openIdx + 1, closeIdx)
            val isBold = when (secondaryColor) {
                Color(0xFFFF00FF), // PINK / BUBBLEGUM
                Color.Black,       // HIGH_CONTRAST
                Color(0xFF00FDFF), // BUBBLEGUM ACCENT
                Color(0xFFFF9F6B), // SUMMERTIME PRIMARY
                Color(0xFF6BEFFF)  -> true // SUMMERTIME ACCENT
                else -> false
            }
            withStyle(SpanStyle(
                color = secondaryColor,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            )) {
                append(insideText)
            }

            // Text after brackets
            if (closeIdx < text.length - 1) {
                withStyle(SpanStyle(color = primaryColor)) {
                    append(text.substring(closeIdx + 1))
                }
            }
        } else {
            withStyle(SpanStyle(color = primaryColor)) {
                append(text)
            }
        }
    }
}

@Composable
fun Modifier.glitchEffect(
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    var translationX by remember { mutableFloatStateOf(0f) }
    var translationY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }

    var colorFilter by remember { mutableStateOf<ColorFilter?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        // Glitch loop
        while (true) {
            val isCritical = (1..10).random() == 1
            val duration = (50..150).random().toLong()

            translationX = ((-5..5).random()).toFloat()
            translationY = ((-2..2).random()).toFloat()
            if (isCritical) {
                scale = 1.1f
                colorFilter = ColorFilter.tint(if ((1..2).random() == 1) Color.Cyan else Color.Red)
            }

            kotlinx.coroutines.delay(duration)

            translationX = 0f
            translationY = 0f
            scale = 1f
            colorFilter = null

            val nextGlitch = if (isCritical) (500..2000).random() else (100..500).random()
            kotlinx.coroutines.delay(nextGlitch.toLong())
        }
    }

    return this.graphicsLayer {
        this.translationX = translationX
        this.translationY = translationY
        this.scaleX = scale
        this.scaleY = scale
        this.alpha = animatedAlpha
    }.then(
        if (colorFilter != null) {
            Modifier.drawWithContent {
                drawContent()
                val color = if (isCriticalGlitch(translationX, translationY)) Color.Cyan else Color.Red
                drawRect(color = color.copy(alpha = 0.3f), blendMode = BlendMode.SrcAtop)
            }
        } else Modifier
    )
}

private fun isCriticalGlitch(tx: Float, ty: Float): Boolean {
    return (tx + ty).toInt() % 2 == 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EslApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var barcode by remember { mutableStateOf("N4611470825513274") }
    var widthPx by remember { mutableStateOf("200") }
    var heightPx by remember { mutableStateOf("80") }
    var txRepeats by remember { mutableFloatStateOf(200f) }
    var delayMs by remember { mutableFloatStateOf(15f) }
    
    var isScanning by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedImage by remember { mutableStateOf<ImageProcessor.ProcessedImage?>(null) }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("IMAGE", "IR FILE", "XBM", "PROTO")

    var irOutput by remember { mutableStateOf("// Generated .ir content will appear here") }
    var xbmOutput by remember { mutableStateOf("// XBM Data") }
    var irCommandIndex by remember { mutableStateOf(0) }
    val irCommands = listOf("0x10 Text", "0x20 Image", "0x30 Test", "0x40 LED", "0x50 Clear")

    // Calculated values
    val plid = remember(barcode) { EslProtocol.barcodeToPlid(barcode) }
    val plidHex = remember(plid) { EslProtocol.plidHex(plid) }
    val crcHex = remember(plid) { EslProtocol.crcHex(plid, EslProtocol.CMD_TEXT) }

    val selectImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val bmp = BitmapFactory.decodeStream(inputStream)
                        withContext(Dispatchers.Main) {
                            sourceBitmap = bmp
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error loading image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Effect for re-processing image when params change
    LaunchedEffect(sourceBitmap, widthPx, heightPx) {
        val bmp = sourceBitmap ?: return@LaunchedEffect
        val w = widthPx.toIntOrNull() ?: 200
        val h = heightPx.toIntOrNull() ?: 80
        
        withContext(Dispatchers.Default) {
            val result = ImageProcessor.processImage(
                source = bmp,
                targetW = w,
                targetH = h,
                threshold = 128,
                ditherMode = "floyd",
                invert = false,
                fitToEsl = true
            )
            withContext(Dispatchers.Main) {
                processedImage = result
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Section 01: Target ESL
                    TacticalCard(title = "TARGET ESL") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabelledTextField(
                                label = "BARCODE / TAG ID",
                                value = barcode,
                                onValueChange = { barcode = it }
                            )
                            
                            Button(
                                onClick = { isScanning = true },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    cyberAnnotatedString("[SCAN BARCODE]"),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LabelledTextField(
                                    label = "WIDTH (PX)",
                                    value = widthPx,
                                    onValueChange = { widthPx = it },
                                    modifier = Modifier.weight(1f),
                                    keyboardType = KeyboardType.Number
                                )
                                LabelledTextField(
                                    label = "HEIGHT (PX)",
                                    value = heightPx,
                                    onValueChange = { heightPx = it },
                                    modifier = Modifier.weight(1f),
                                    keyboardType = KeyboardType.Number
                                )
                            }

                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TX REPEATS", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    Text(txRepeats.toInt().toString(), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                                Slider(
                                    value = txRepeats,
                                    onValueChange = { txRepeats = it },
                                    valueRange = 0f..500f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("DELAY (MS)", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    Text(delayMs.toInt().toString(), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                                Slider(
                                    value = delayMs,
                                    onValueChange = { delayMs = it },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(plidHex, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(crcHex, color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                }

                item {
                    // Tab Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .padding(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> { // IMAGE
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                                            selectImageLauncher.launch(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            cyberAnnotatedString("[ADD IMAGE]", secondaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        processedImage?.bitmap?.let {
                                            Image(
                                                bitmap = it.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Text(
                                        processedImage?.let { "${it.width} x ${it.height} PX (PROCESSED)" } ?: "NO IMAGE",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                processedImage?.let {
                                                    xbmOutput = ImageProcessor.exportXbm(it, false)
                                                    selectedTab = 2
                                                } ?: Toast.makeText(context, "Process image first", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                cyberAnnotatedString("[EXPORT XBM]"),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val proc = processedImage
                                                if (proc != null) {
                                                    coroutineScope.launch(Dispatchers.Default) {
                                                        val payload = ImageProcessor.imageToIrPayload(proc, false)
                                                        val ir = EslProtocol.buildIrFile(barcode, EslProtocol.CMD_IMAGE, payload)
                                                        withContext(Dispatchers.Main) {
                                                            irOutput = ir
                                                            selectedTab = 1
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.secondary
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                cyberAnnotatedString("[GENERATE IR]"),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> { // IR FILE
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("COMMAND TYPE", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    
                                    var expanded by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { expanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                                        ) {
                                            Text(irCommands[irCommandIndex], color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                                        }
                                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            irCommands.forEachIndexed { index, cmd ->
                                                DropdownMenuItem(
                                                    text = { Text(cmd, fontFamily = FontFamily.Monospace) },
                                                    onClick = {
                                                        irCommandIndex = index
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val commands = listOf(
                                                EslProtocol.CMD_TEXT,
                                                EslProtocol.CMD_IMAGE,
                                                EslProtocol.CMD_TEST,
                                                EslProtocol.CMD_LED,
                                                EslProtocol.CMD_CLEAR
                                            )
                                            val command = commands[irCommandIndex]
                                            val payload = when (command) {
                                                EslProtocol.CMD_TEXT  -> EslProtocol.textPayload("BLESP", false)
                                                EslProtocol.CMD_TEST  -> EslProtocol.testPayload()
                                                EslProtocol.CMD_LED   -> EslProtocol.ledPayload(1)
                                                EslProtocol.CMD_CLEAR -> EslProtocol.clearPayload(false)
                                                else -> byteArrayOf()
                                            }
                                            irOutput = EslProtocol.buildIrFile(barcode, command, payload)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            cyberAnnotatedString("[GENERATE IR FILE]"),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .background(Color.Black, RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(irOutput, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    }
                                }
                            }
                            2 -> { // XBM
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(Color.Black, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(xbmOutput, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                            }
                            3 -> { // PROTO
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        "HANSHOW / NEWTON ESL PROTOCOL\n\nIR carrier: 1.255 MHz\nModulation: 38 kHz\nEncoding: Manchester / NRZ\n\nPacket Structure:\nSYNC 9ms, PLID 7B, CMD 1B, PAYLOAD NB, CRC-8",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val haptic = LocalHapticFeedback.current
                    // Deploy Section
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (irOutput.startsWith("//") || irOutput.isEmpty()) {
                                Toast.makeText(context, "Generate IR content first", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BLESP")
                                    if (!folder.exists()) folder.mkdirs()
                                    val b = barcode.filter { it.isLetterOrDigit() }.takeLast(8)
                                    val fileName = "esl_$b.ir"
                                    val file = File(folder, fileName)
                                    FileOutputStream(file).use { it.write(irOutput.toByteArray()) }
                                    Toast.makeText(context, "Exported to Downloads/BLESP/$fileName", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            cyberAnnotatedString("[EXPORT SCRIPT]"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }
            }

            if (isScanning) {
                BarcodeScannerView(
                    onBarcodeDetected = {
                        barcode = it
                        isScanning = false
                        vibrate(context)
                    },
                    onClose = { isScanning = false }
                )
            }
        }
    }
}

@Composable
fun TacticalCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            cyberAnnotatedString(title),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun LabelledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true
        )
    }
}

@Composable
fun BarcodeScannerView(onBarcodeDetected: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
                    )
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        barcode.rawValue?.let { 
                                            onBarcodeDetected(it)
                                            return@addOnSuccessListener
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    } catch (e: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.5f))
        }
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(2.dp, Color.Green, RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(24.dp))
            Text("ALIGN BARCODE", color = Color.Green, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }
    }
}

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
}
