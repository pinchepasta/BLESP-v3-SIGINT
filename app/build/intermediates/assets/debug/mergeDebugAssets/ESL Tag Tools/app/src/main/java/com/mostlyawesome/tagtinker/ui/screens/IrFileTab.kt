package com.mostlyawesome.tagtinker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import com.mostlyawesome.tagtinker.MainViewModel
import com.mostlyawesome.tagtinker.data.*
import com.mostlyawesome.tagtinker.ui.components.*

@Composable
fun IrFileTab(vm: MainViewModel) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val cfg     = vm.irConfig

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(vm.generatedIr.toByteArray())
                }
                status = Pair(true, "✓ Saved to device")
            } catch (e: Exception) {
                status = Pair(false, "✗ Failed to save file")
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Command Type
        TtDropdown(
            label    = "Command Type",
            options  = IrCommand.entries,
            selected = IrCommand.entries.firstOrNull { it.value == cfg.commandType } ?: IrCommand.TEXT,
            labelFor = { it.label },
            onSelect = { vm.updateIrCommand(it.value) },
        )

        // Text content (visible only for text command)
        if (cfg.commandType == EslProtocol.CMD_TEXT) {
            FieldLabel("Text Content")
            OutlinedTextField(
                value         = cfg.textContent,
                onValueChange = vm::updateIrText,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                label = { Text("Max 32 chars", fontSize = 11.sp) },
            )
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked  = cfg.invertText,
                    onCheckedChange = vm::updateIrInvert,
                    colors   = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onSurface)
                )
                Text("Invert text", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Color clear (visible for CLEAR command)
        if (cfg.commandType == EslProtocol.CMD_CLEAR) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked  = cfg.colorClear,
                    onCheckedChange = vm::updateColorClear,
                    colors   = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.onSurface)
                )
                Text("Color clear (vs. black clear)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // LED mode (visible for LED command)
        if (cfg.commandType == EslProtocol.CMD_LED) {
            TtDropdown(
                label    = "LED Mode",
                options  = LedMode.entries,
                selected = LedMode.entries.firstOrNull { it.value == cfg.ledMode } ?: LedMode.OFF,
                labelFor = { it.label },
                onSelect = { vm.updateLedMode(it.value) },
            )
        }

        // Generate button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TtButtonPrimary(
                label   = "Generate .ir File",
                enabled = !vm.isGeneratingIr,
                onClick = {
                    status = null
                    vm.generateIrFile()
                }
            )
            
            if (vm.isGeneratingIr) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Update status when generation finishes
        LaunchedEffect(vm.generatedIr, vm.isGeneratingIr) {
            if (!vm.isGeneratingIr && vm.generatedIr.isNotBlank()) {
                status = Pair(true, "✓ IR file generated — ${vm.generatedIrCount} timing values")
            }
        }

        status?.let { (ok, msg) -> StatusBox(ok, msg) }

        // Output
        FieldLabel("Generated .ir Content")
        MonoOutputBox(
            text        = vm.generatedIr,
            placeholder = "// Click Generate to build a Flipper-compatible .ir file\n// for use with MULTIPASS's ir.transmitFile() API",
            modifier    = Modifier.heightIn(min = 80.dp, max = 220.dp),
        )

        // Download / copy (shown when content exists)
        if (vm.generatedIr.isNotBlank() && !vm.isGeneratingIr) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TtButtonAccent("⬇  Share", enabled = !vm.isGeneratingIr, onClick = {
                    val file = vm.saveIrToFile(context)
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share .ir File"))
                    }
                })

                TtButtonAccent("💾  Save", enabled = !vm.isGeneratingIr, onClick = {
                    createFileLauncher.launch("tagtinker_esl.ir")
                })

                TtButtonGhost("Copy", enabled = !vm.isGeneratingIr, onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("IR file", vm.generatedIr))
                    status = Pair(true, "Copied to clipboard")
                })
            }
        }
    }
}
