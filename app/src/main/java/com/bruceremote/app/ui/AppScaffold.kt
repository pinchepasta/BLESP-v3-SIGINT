package com.bruceremote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bruceremote.app.serial.ConnectionState
import com.bruceremote.app.serial.NavDirection
import com.bruceremote.app.viewmodel.BruceViewModel
import com.hoho.android.usbserial.driver.UsbSerialDriver

private val tabs = listOf("Navigator", "Terminal", "Quick Actions", "AI Agent")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    viewModel: BruceViewModel,
    onBack: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val logLines by viewModel.logLines.collectAsState()
    val quickActions by viewModel.quickActions.collectAsState()
    val aiChatMessages by viewModel.aiChatMessages.collectAsState()

    val aiEngine by viewModel.aiEngine.collectAsState()
    val lmStudioUrl by viewModel.lmStudioUrl.collectAsState()
    val lmStudioApiKey by viewModel.lmStudioApiKey.collectAsState()
    val lmStudioModelName by viewModel.lmStudioModelName.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDevicePicker by remember { mutableStateOf(false) }

    val connected = connectionState is ConnectionState.Connected

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        TextButton(
                            onClick = onBack,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                "CLOSE",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    },
                    actions = {
                        StatusBadge(connectionState)
                        Spacer(Modifier.size(8.dp))
                        if (connected) {
                            TextButton(
                                onClick = { viewModel.disconnect() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Disconnect", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace) }
                        } else {
                            TextButton(
                                onClick = { showDevicePicker = true },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("Connect", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace) }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> NavigatorScreen(
                    display = viewModel.display,
                    isOpen = true,
                    onOpen = { viewModel.openNavigator() },
                    onClose = { viewModel.closeNavigator() },
                    onNav = { dir: NavDirection -> viewModel.sendNav(dir) },
                    onDump = { viewModel.requestDump() },
                    connected = connected
                )
                1 -> TerminalScreen(
                    lines = logLines,
                    connected = connected,
                    onSend = { viewModel.sendCommand(it) }
                )
                2 -> QuickActionsScreen(
                    actions = quickActions,
                    connected = connected,
                    onSend = { viewModel.sendCommand(it) },
                    onAddAction = { label, cmd, cat -> viewModel.addQuickAction(label, cmd, cat) },
                    onEditAction = { id, label, cmd, cat -> viewModel.editQuickAction(id, label, cmd, cat) },
                    onDeleteAction = { id -> viewModel.deleteQuickAction(id) }
                )
                3 -> AiAssistantScreen(
                    messages = aiChatMessages,
                    connected = connected,
                    aiEngine = aiEngine,
                    lmStudioUrl = lmStudioUrl,
                    lmStudioApiKey = lmStudioApiKey,
                    lmStudioModelName = lmStudioModelName,
                    onSendMessage = { viewModel.sendAiPrompt(it) },
                    onSetAiEngine = { viewModel.setAiEngine(it) },
                    onSetLmStudioConfig = { url, key, model -> viewModel.setLmStudioConfig(url, key, model) },
                    onFetchLmStudioModels = { callback -> viewModel.fetchLmStudioModels(callback) },
                    onSaveFile = { fname, content -> viewModel.saveContentToFile(fname, content) }
                )
            }
        }
    }

    if (showDevicePicker) {
        DevicePickerDialog(
            devices = viewModel.availableDevices(),
            onDismiss = { showDevicePicker = false },
            onSelect = { driver, portIndex ->
                showDevicePicker = false
                viewModel.connect(driver, portIndex)
            }
        )
    }
}

@Composable
private fun StatusBadge(state: ConnectionState) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val errorColor = MaterialTheme.colorScheme.error

    val (text, color) = when (state) {
        is ConnectionState.Connected -> "Connected" to primaryColor
        ConnectionState.Connecting -> "Connecting…" to mutedColor
        ConnectionState.RequestingPermission -> "Awaiting permission…" to mutedColor
        is ConnectionState.Error -> "Error" to errorColor
        ConnectionState.Disconnected -> "Disconnected" to mutedColor
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(Modifier.size(6.dp))
        Text(text, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePickerDialog(
    devices: List<UsbSerialDriver>,
    onDismiss: () -> Unit,
    onSelect: (UsbSerialDriver, Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select USB device", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        "No USB-serial device found. Plug in your device (and grant USB permission if prompted).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                } else {
                    devices.forEach { driver ->
                        val ports = driver.ports
                        ports.forEachIndexed { index, _ ->
                            val portSuffix = if (ports.size > 1) " (Port ${index + 1})" else ""
                            ListItem(
                                headlineContent = {
                                    Text(
                                        (driver.device.productName ?: "VID:${driver.device.vendorId} PID:${driver.device.productId}") + portSuffix,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp)
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        "${driver.javaClass.simpleName}$portSuffix",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                    )
                                }
                            )
                            Button(
                                onClick = { onSelect(driver, index) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) { Text("Connect$portSuffix", fontSize = 11.sp) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", fontSize = 11.sp) }
        }
    )
}
