package com.radar.blewifi

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.radar.blewifi.wspr.ui.theme.WSPRTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import java.io.File

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("immersive_mode", true)) {
            enterImmersiveMode()
        }

        setContent {
            var themeName by remember {
                mutableStateOf(prefs.getString("theme_name", "DEFAULT") ?: "DEFAULT")
            }
            var immersiveMode by remember {
                mutableStateOf(prefs.getBoolean("immersive_mode", true))
            }

            WSPRTheme(themeName = themeName) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        themeName = themeName,
                        immersiveMode = immersiveMode,
                        onThemeChange = { newTheme ->
                            themeName = newTheme
                            prefs.edit().putString("theme_name", newTheme).apply()
                        },
                        onImmersiveChange = { enabled ->
                            immersiveMode = enabled
                            prefs.edit().putBoolean("immersive_mode", enabled).apply()
                            if (enabled) enterImmersiveMode() else exitImmersiveMode()
                        },
                        onDeleteConfirm = {
                            try {
                                // Clear SharedPreferences for archive
                                val archivePrefs = getSharedPreferences("radar_archive",
                                    MODE_PRIVATE
                                )
                                archivePrefs.edit().clear().apply()

                                // Delete files in cache directory
                                cacheDir.deleteRecursively()

                                Toast.makeText(this, "DATA PURGED SUCCESSFULLY", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this, "PURGE FAILED: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
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

    private fun exitImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeName: String,
    immersiveMode: Boolean,
    onThemeChange: (String) -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    onDeleteConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CONFIGURATION",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
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
                    modifier = Modifier
                        .height(32.dp)
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            SectionLabel("COLOR THEME")
            
            ThemeSelector(
                currentTheme = themeName,
                onThemeSelected = onThemeChange
            )

            Spacer(Modifier.height(32.dp))
            Divider(color = Color(0xFF222222), thickness = 1.dp)
            Spacer(Modifier.height(32.dp))

            SectionLabel("HUD PARAMETERS")

            SettingsToggle(
                label = "IMMERSIVE OVERLAY",
                description = "FORCE FULLSCREEN TACTICAL OVERLAY.",
                checked = immersiveMode,
                onCheckedChange = onImmersiveChange
            )

            Spacer(Modifier.height(32.dp))

            SecretInfoBox(
                title = "SECRET AUDIO",
                commands = "START CODE: 77872\nSTOP  CODE: 77873",
                path = "PATH: /Download/BLESP"
            )

            Spacer(Modifier.height(32.dp))

            SecretInfoBox(
                title = "SECRET VIDEO",
                commands = "START CODE: 77874\nSTOP  CODE: 77875",
                path = "PATH: /Download/BLESP"
            )

            Spacer(Modifier.height(32.dp))
            Divider(color = Color(0xFF222222), thickness = 1.dp)
            Spacer(Modifier.height(32.dp))

            SectionLabel("DATA MANAGEMENT")

            DataManagementSection(onDeleteConfirm = onDeleteConfirm)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun ThemeSelector(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val themes = RadarView.Theme.entries.map { it.name }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { expanded = true }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = currentTheme.replace("_", " "),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp
        )
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth(0.8f)
        ) {
            themes.forEach { theme ->
                DropdownMenuItem(
                    text = {
                        Text(
                            theme.replace("_", " "),
                            fontFamily = FontFamily.Monospace,
                            color = if (theme == currentTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onThemeSelected(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        Text(
            text = description,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SecretInfoBox(
    title: String,
    commands: String,
    path: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = commands,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = path,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.05.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataManagementSection(onDeleteConfirm: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var randomCode by remember { mutableStateOf("") }
    var inputCode by remember { mutableStateOf("") }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = {
                        // Regular tap does nothing but guide the user
                    },
                    onLongClick = {
                        // Generate a random 4-digit code
                        val code = (1000..9999).random().toString()
                        randomCode = code
                        inputCode = ""
                        showDialog = true
                    }
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PURGE ALL STORAGE & CACHE",
                    color = Color.Red,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "PRESS AND HOLD TO INITIATE PURGE.",
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = "CONFIRM SECURITY CODE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Red
                )
            },
            text = {
                Column {
                    Text(
                        text = "ENTER THE FOLLOWING CODE TO PROCEED:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = randomCode,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 4) inputCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        placeholder = {
                            Text(
                                "0000",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onDeleteConfirm()
                    },
                    enabled = inputCode == randomCode
                ) {
                    Text(
                        text = "EXECUTE PURGE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (inputCode == randomCode) Color.Red else Color.Gray
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        text = "ABORT",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

