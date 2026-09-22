package com.mostlyawesome.tagtinker.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mostlyawesome.tagtinker.ui.theme.*
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Full-screen barcode scanner using CameraX + ML Kit.
 * Tuned for small ESL barcodes: high-res, continuous focus, zoom control.
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context     = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // State
    var scanState   by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var confirming  by remember { mutableStateOf<String?>(null) }
    var confirmCount by remember { mutableIntStateOf(0) }
    var zoomLevel   by remember { mutableFloatStateOf(1f) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo  by remember { mutableStateOf<CameraInfo?>(null) }

    val CONFIRM_NEEDED = 3
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner  = remember { BarcodeScanning.getClient() }

    // Scan line animation
    val scanLineAnim = rememberInfiniteTransition(label = "scanLine")
    val scanLineY by scanLineAnim.animateFloat(
        initialValue   = 0.15f,
        targetValue    = 0.85f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanLineY"
    )

    // Corner anim when confirming
    val cornerScale by animateFloatAsState(
        targetValue    = if (confirming != null) 1.08f else 1f,
        animationSpec  = spring(stiffness = Spring.StiffnessMedium),
        label          = "cornerScale"
    )

    // Process a detected barcode with debounce confirmation
    fun onDetected(raw: String) {
        if (scanState is ScanState.Success) return
        if (raw == confirming) {
            confirmCount++
            if (confirmCount >= CONFIRM_NEEDED) {
                scanState = ScanState.Success(raw)
                // Haptic feedback
                try {
                    val vib = ContextCompat.getSystemService(context, Vibrator::class.java)
                    vib?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) {}
            }
        } else {
            confirming   = raw
            confirmCount = 1
            scanState    = ScanState.Confirming(raw)
        }
    }

    // Complete scan after success animation
    LaunchedEffect(scanState) {
        if (scanState is ScanState.Success) {
            kotlinx.coroutines.delay(700)
            onBarcodeScanned((scanState as ScanState.Success).value)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Camera Preview ───────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType          = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update   = { previewView ->
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview  = Preview.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && scanState !is ScanState.Success) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val best = barcodes.firstOrNull {
                                        it.rawValue != null && isEslBarcode(it)
                                    } ?: barcodes.firstOrNull { it.rawValue != null }
                                    best?.rawValue?.let { raw -> onDetected(raw) }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        provider.unbindAll()
                        val cam = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        cameraControl = cam.cameraControl
                        cameraInfo    = cam.cameraInfo
                        cam.cameraControl.setLinearZoom(0f)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // ── Viewfinder overlay ───────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width; val H = size.height
            // Viewfinder box — wider than tall for ESL barcodes
            val boxW = W * 0.85f
            val boxH = H * 0.25f
            val bx   = (W - boxW) / 2f
            val by   = (H - boxH) / 2f - H * 0.06f

            // Darken outside
            val path = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, W, H))
                addRect(androidx.compose.ui.geometry.Rect(bx, by, bx + boxW, by + boxH))
                fillType = PathFillType.EvenOdd
            }
            drawPath(path, Color.Black.copy(alpha = 0.65f))

            // Corner brackets
            val cornerLen = 28.dp.toPx()
            val cornerW   = 3.dp.toPx()
            val cornerColor = when (scanState) {
                is ScanState.Success    -> Color(0xFF22C55E)
                is ScanState.Confirming -> Color(0xFFF07030)
                else                    -> Color.White
            }
            val cx = bx; val cy = by
            val cxe = bx + boxW; val cye = by + boxH
            // TL
            drawLine(cornerColor, Offset(cx, cy), Offset(cx + cornerLen, cy), cornerW)
            drawLine(cornerColor, Offset(cx, cy), Offset(cx, cy + cornerLen), cornerW)
            // TR
            drawLine(cornerColor, Offset(cxe, cy), Offset(cxe - cornerLen, cy), cornerW)
            drawLine(cornerColor, Offset(cxe, cy), Offset(cxe, cy + cornerLen), cornerW)
            // BL
            drawLine(cornerColor, Offset(cx, cye), Offset(cx + cornerLen, cye), cornerW)
            drawLine(cornerColor, Offset(cx, cye), Offset(cx, cye - cornerLen), cornerW)
            // BR
            drawLine(cornerColor, Offset(cxe, cye), Offset(cxe - cornerLen, cye), cornerW)
            drawLine(cornerColor, Offset(cxe, cye), Offset(cxe, cye - cornerLen), cornerW)

            // Scan line (only while scanning)
            if (scanState is ScanState.Scanning || scanState is ScanState.Confirming) {
                val lineY = by + (boxH * scanLineY)
                drawLine(
                    brush  = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFF00D4FF), Color(0xFF00D4FF), Color.Transparent),
                        startX = bx, endX = bx + boxW
                    ),
                    start  = Offset(bx, lineY),
                    end    = Offset(bx + boxW, lineY),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        // ── Top bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            Text(
                "Scan ESL Tag",
                color      = Color.White,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // Torch toggle
            IconButton(onClick = {
                torchEnabled = !torchEnabled
                cameraControl?.enableTorch(torchEnabled)
            }) {
                Icon(
                    if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    "Torch",
                    tint = if (torchEnabled) Color(0xFFFFD700) else Color.White,
                )
            }
        }

        // ── Status pill (center-above box) ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .offset(y = (-120).dp),
            contentAlignment = Alignment.Center,
        ) {
            val (pillColor, pillText) = when (val s = scanState) {
                is ScanState.Scanning   -> Pair(Color.White.copy(alpha = 0.85f), "Hold barcode steady in frame")
                is ScanState.Confirming -> Pair(Color(0xFFF07030), "Confirming: ${s.value.take(20)}…")
                is ScanState.Success    -> Pair(Color(0xFF22C55E), "✓ Scanned!")
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xCC000000),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Text(
                    pillText,
                    color      = pillColor,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        // ── Zoom slider ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Zoom chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                listOf(1f, 1.5f, 2f, 3f).forEach { zoom ->
                    val isActive = kotlin.math.abs(zoomLevel - zoom) < 0.1f
                    Surface(
                        shape  = RoundedCornerShape(50),
                        color  = if (isActive) Color.White else Color(0x66FFFFFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                zoomLevel = zoom
                                // Convert to linear: 1x=0, maxZoom varies. Approximate.
                                val linear = (zoom - 1f) / 9f  // assumes 10x max
                                cameraControl?.setLinearZoom(linear.coerceIn(0f, 1f))
                            }
                    ) {
                        Text(
                            "${zoom}×",
                            color    = if (isActive) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Continuous zoom slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ZoomOut, null, tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp))
                Slider(
                    value         = (zoomLevel - 1f) / 9f,
                    onValueChange = { linear ->
                        zoomLevel = 1f + linear * 9f
                        cameraControl?.setLinearZoom(linear)
                    },
                    modifier      = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors        = SliderDefaults.colors(
                        thumbColor       = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    )
                )
                Icon(Icons.Default.ZoomIn, null, tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "${"%.1f".format(zoomLevel)}× zoom  •  ML Kit  •  CameraX",
                color    = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────
sealed class ScanState {
    object Scanning : ScanState()
    data class Confirming(val value: String) : ScanState()
    data class Success(val value: String) : ScanState()
}

/** Heuristic: prefer numeric-heavy barcodes typical of ESL tags */
fun isEslBarcode(barcode: Barcode): Boolean {
    val raw = barcode.rawValue ?: return false
    val numericRatio = raw.count { it.isDigit() }.toFloat() / raw.length
    return numericRatio > 0.7f && raw.length >= 8
}
