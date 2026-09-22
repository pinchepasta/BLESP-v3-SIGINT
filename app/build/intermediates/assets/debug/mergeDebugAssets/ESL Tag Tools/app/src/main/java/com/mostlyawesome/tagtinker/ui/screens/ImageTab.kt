package com.mostlyawesome.tagtinker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.mostlyawesome.tagtinker.MainViewModel
import com.mostlyawesome.tagtinker.ui.components.*

@Composable
fun ImageTab(vm: MainViewModel, onGoToIr: () -> Unit) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.loadImageFromUri(context, it) }
    }

    val cfg = vm.imageConfig

    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Drop zone / image picker
        FieldLabel("Source Image")
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { picker.launch("image/*") }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image, null,
                        modifier = Modifier.size(28.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap to pick image",
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                    Text(
                        "PNG, JPG, GIF supported",
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Threshold slider
        SliderRow(
            label         = "Threshold (B&W)",
            value         = cfg.threshold.toFloat(),
            onValueChange = { vm.updateThreshold(it.toInt()) },
            valueRange    = 0f..255f,
            displayValue  = cfg.threshold.toString(),
        )

        // Dither mode
        TtDropdown(
            label    = "Dither",
            options  = listOf("none" to "None (Hard threshold)", "floyd" to "Floyd-Steinberg", "bayer" to "Bayer 4×4"),
            selected = cfg.ditherMode to (
                when (cfg.ditherMode) {
                    "floyd" -> "Floyd-Steinberg"
                    "bayer" -> "Bayer 4×4"
                    else    -> "None (Hard threshold)"
                }
            ),
            labelFor = { it.second },
            onSelect = { vm.updateDitherMode(it.first) },
        )

        // Checkboxes
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked  = cfg.invert,
                onCheckedChange = vm::updateInvert,
                colors   = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onSurface)
            )
            Text("Invert (white on black)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked  = cfg.fitToEsl,
                onCheckedChange = vm::updateFitToEsl,
                colors   = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onSurface)
            )
            Text("Fit to ESL dimensions", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Preview
        FieldLabel("Preview — ESL Simulation")
        if (vm.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            val proc = vm.processedImage
            if (proc != null) {
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Image(
                            bitmap      = proc.bitmap.asImageBitmap(),
                            contentDescription = "ESL Preview",
                            contentScale = ContentScale.Fit,
                            modifier    = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .padding(12.dp),
                        )
                        Text(
                            "${proc.width} × ${proc.height} px",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            } else {
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "No image loaded",
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        // Status
        vm.imageStatus?.let { (ok, msg) -> StatusBox(ok, msg) }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TtButtonPrimary("Export XBM", onClick = {
                vm.exportXbm()
            })
            TtButtonAccent("→ IR File", onClick = {
                vm.buildIrFromImage()
                onGoToIr()
            })
            TtButtonGhost("Clear", onClick = vm::clearImage)
        }
    }
}
