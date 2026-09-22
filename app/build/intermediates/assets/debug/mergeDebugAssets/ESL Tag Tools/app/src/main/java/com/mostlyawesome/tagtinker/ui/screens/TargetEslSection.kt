package com.mostlyawesome.tagtinker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.mostlyawesome.tagtinker.MainViewModel
import com.mostlyawesome.tagtinker.ui.components.*

@Composable
fun TargetEslSection(
    vm: MainViewModel,
    onOpenScanner: () -> Unit,
) {
    val target = vm.eslTarget

    TtCard {
        Column(Modifier.padding(18.dp)) {
            SectionHeader("01", "TARGET ESL")

            WarningBar(
                title = "⚠  Research Use Only",
                body  = "Only use on hardware you personally own or have explicit authorization to test. Do not use in stores or on deployed systems."
            )

            // Barcode field + scan button
            FieldLabel("Barcode / Tag ID")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                OutlinedTextField(
                    value         = target.barcode,
                    onValueChange = vm::updateBarcode,
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    textStyle     = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                FilledTonalIconButton(
                    onClick = onOpenScanner,
                    modifier = Modifier.size(48.dp),
                    colors  = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Scan barcode", modifier = Modifier.size(22.dp))
                }
            }

            // Width / Height
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Width (px)")
                    OutlinedTextField(
                        value         = target.widthPx.toString(),
                        onValueChange = { vm.updateEslWidth(it.toIntOrNull() ?: target.widthPx) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Height (px)")
                    OutlinedTextField(
                        value         = target.heightPx.toString(),
                        onValueChange = { vm.updateEslHeight(it.toIntOrNull() ?: target.heightPx) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }

            // TX Repeats
            SliderRow(
                label        = "TX Repeats",
                value        = target.txRepeats.toFloat(),
                onValueChange = { vm.updateTxRepeats(it.toInt()) },
                valueRange   = 1f..500f,
                displayValue = target.txRepeats.toString(),
                modifier     = Modifier.padding(bottom = 10.dp),
            )

            // Inter-frame Delay
            SliderRow(
                label        = "Inter-frame Delay (ms)",
                value        = target.delayMs.toFloat(),
                onValueChange = { vm.updateDelay(it.toInt()) },
                valueRange   = 1f..100f,
                displayValue = "${target.delayMs}ms",
            )

            // PLID / CRC grid
            GridInfo(listOf("PLID" to vm.plidHex, "CRC-8" to vm.crcHex))

            Spacer(Modifier.height(14.dp))
            TtButtonGhost("↻  Recalculate", onClick = vm::recalcPlid)
        }
    }
}
