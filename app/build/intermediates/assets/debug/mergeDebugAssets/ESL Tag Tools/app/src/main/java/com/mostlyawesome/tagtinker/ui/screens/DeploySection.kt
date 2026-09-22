package com.mostlyawesome.tagtinker.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import com.mostlyawesome.tagtinker.MainViewModel
import com.mostlyawesome.tagtinker.ui.components.*

@Composable
fun DeploySection(vm: MainViewModel) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val createScriptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/javascript")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(vm.multipassScript.toByteArray())
                }
                status = Pair(true, "✓ Saved to device")
            } catch (e: Exception) {
                status = Pair(false, "✗ Failed to save script")
            }
        }
    }

    TtCard {
        Column(Modifier.padding(18.dp)) {
            SectionHeader("02", "DEPLOY TO MULTIPASS")

            InfoBox {
                InfoLine("1. Upload script", "")
                Text(
                    "Copy tagtinker.js to /scripts/ on your MULTIPASS device via WebUI or SD card.",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 10.dp, start = 8.dp),
                )
                InfoLine("2. Run", "")
                Text(
                    "Navigate to Scripts → tagtinker.js on your MULTIPASS device.",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 10.dp, start = 8.dp),
                )
                InfoLine("3. Upload IR files", "")
                Text(
                    "Use the IR File tab to generate .ir payloads, then upload to your MULTIPASS SD card.",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 10.dp, start = 8.dp),
                )
                InfoLine("4. Image prep", "")
                Text(
                    "Use the Image tab to convert images to XBM format for ESL display experiments.",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            status?.let { (ok, msg) ->
                StatusBox(ok, msg, modifier = Modifier.padding(bottom = 8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TtButtonAccent(
                    label = "⬇  Share",
                    onClick = {
                        val file = vm.saveMultipassScript(context)
                        if (file != null) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type   = "text/javascript"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share esl_tools.js"))
                        } else {
                            status = Pair(false, "✗ Failed to export script")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                TtButtonAccent(
                    label = "💾  Save",
                    onClick = {
                        createScriptLauncher.launch("esl_tools.js")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
