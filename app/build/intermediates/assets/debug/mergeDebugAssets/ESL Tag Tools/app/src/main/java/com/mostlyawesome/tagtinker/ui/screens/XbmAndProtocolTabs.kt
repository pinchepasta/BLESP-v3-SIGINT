package com.mostlyawesome.tagtinker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mostlyawesome.tagtinker.MainViewModel
import com.mostlyawesome.tagtinker.ui.components.*

// ── XBM Export Tab ───────────────────────────────────────────────────────────
@Composable
fun XbmTab(vm: MainViewModel) {
    val context = LocalContext.current
    var status  by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoBox {
            Text(
                "XBM for MULTIPASS Firmware",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "MULTIPASS's display.drawXBitmap() accepts monochrome XBM-format bitmaps. " +
                "Use this tab to get the raw XBM data from your processed image for embedding in MULTIPASS JS scripts.",
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }

        FieldLabel("Generated XBM Data")
        MonoOutputBox(
            text        = vm.xbmOutput,
            placeholder = "// Load an image in the Image tab, then click Export XBM",
            modifier    = Modifier.heightIn(min = 80.dp, max = 200.dp),
        )

        FieldLabel("MULTIPASS JS Snippet")
        MonoOutputBox(
            text        = vm.jsSnippet,
            placeholder = "// Snippet will appear here after export",
            modifier    = Modifier.heightIn(min = 80.dp, max = 200.dp),
        )

        status?.let { (ok, msg) -> StatusBox(ok, msg) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TtButtonAccent("↺  Regenerate", onClick = {
                if (vm.processedImage != null) {
                    vm.exportXbm()
                    status = Pair(true, "✓ XBM regenerated")
                } else {
                    status = Pair(false, "✗ Load and process an image first")
                }
            })
            TtButtonGhost("Copy XBM", onClick = {
                if (vm.xbmOutput.isBlank()) { status = Pair(false, "✗ No XBM data to copy"); return@TtButtonGhost }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("XBM", vm.xbmOutput))
                status = Pair(true, "Copied XBM to clipboard")
            })
            TtButtonGhost("Copy Snippet", onClick = {
                if (vm.jsSnippet.isBlank()) { status = Pair(false, "✗ No snippet to copy"); return@TtButtonGhost }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Snippet", vm.jsSnippet))
                status = Pair(true, "Copied snippet to clipboard")
            })
        }
    }
}

// ── Protocol Info Tab ────────────────────────────────────────────────────────
@Composable
fun ProtocolTab(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoBox {
            Text("Hanshow / Newton ESL Protocol (via furrtek)", fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("IR carrier:", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MonoCode("1.255 MHz")
                Text("Modulation:", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MonoCode("38 kHz")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text("Encoding:", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MonoCode("Manchester / NRZ")
            }
            Spacer(Modifier.height(10.dp))
            Text("Packet Structure", fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            MonoCode("[SYNC 9ms] [PLID 7B] [CMD 1B] [PAYLOAD NB] [CRC-8]")
            Spacer(Modifier.height(10.dp))
            Text("PLID Encoding", fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Barcode → strip prefix → last 13 digits → BCD-encode into 7 bytes (MSB)",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))
            Text("Commands", fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoCode("0x10 Text")
                MonoCode("0x20 Image")
                MonoCode("0x30 Test")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                MonoCode("0x40 LED")
                MonoCode("0x50 Clear")
            }
            Spacer(Modifier.height(10.dp))
            Text("CRC", fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("XOR of all packet bytes (PLID + CMD + Payload)",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // PLID calculator live display
        FieldLabel("PLID Calculator")
        InfoBox {
            val barcode = vm.eslTarget.barcode
            InfoLine("Barcode:", barcode)
            InfoLine("PLID bytes:", vm.plidHex)
            InfoLine("CRC (text cmd):", vm.crcHex)
        }

        InfoBox {
            Text("MULTIPASS JS IR API", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            InfoLine("ir.transmitFile(path)", "— transmit .ir file")
            InfoLine("ir.read(timeout)", "— receive IR signal")
            InfoLine("ir.readRaw(timeout)", "— raw IR data")
            Spacer(Modifier.height(6.dp))
            Text("Upload scripts to /scripts/, IR files to /infrared/ on your MULTIPASS device SD card.",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}
