package com.radar.blewifi

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.os.*
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.radar.blewifi.wspr.ui.theme.WSPRTheme
import java.util.*

@SuppressLint("MissingPermission")
class FlipperRemoteActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE = "extra_device"
        val SERVICE_UUID: UUID = UUID.fromString("00003080-0000-1000-8000-00805f9b34fb")
        val RX_CHAR_UUID: UUID = UUID.fromString("00003081-0000-1000-8000-00805f9b34fb")
        val TX_CHAR_UUID: UUID = UUID.fromString("00003082-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private val screenState = mutableStateOf(Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888))
    private var connectionStatus = mutableStateOf("DISCONNECTED")
    private val showPairingDialog = mutableStateOf(false)
    private var pairingDevice: BluetoothDevice? = null
    private var targetAddress: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var inboundBuffer = ByteArray(0)
    private var isBound = false

    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device?.address != targetAddress) return

            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                    Log.d("FlipperRemote", "Pairing request variant: $variant")
                    
                    pairingDevice = device
                    try {
                        abortBroadcast()
                    } catch (e: Exception) {
                        Log.e("FlipperRemote", "Could not abort pairing broadcast", e)
                    }
                    
                    showPairingDialog.value = true
                    mainHandler.post { connectionStatus.value = "PAIRING PIN NEEDED" }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    Log.d("FlipperRemote", "Bond state changed: $state")
                    when (state) {
                        BluetoothDevice.BOND_BONDED -> {
                            showPairingDialog.value = false
                            mainHandler.post { 
                                connectionStatus.value = "BONDED"
                                mainHandler.postDelayed({
                                    targetAddress?.let { connectToFlipper(it) }
                                }, 1000)
                            }
                        }
                        BluetoothDevice.BOND_NONE -> {
                            showPairingDialog.value = false
                            mainHandler.post { connectionStatus.value = "NOT PAIRED" }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        val scanDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DEVICE, ScanDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<ScanDevice>(EXTRA_DEVICE)
        }

        if (scanDevice == null) {
            Toast.makeText(this, "No Flipper device provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        targetAddress = scanDevice.address
        
        // Register receiver with protection
        if (!isBound) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                priority = Int.MAX_VALUE
            }
            registerReceiver(pairingReceiver, filter)
            isBound = true
        }

        connectToFlipper(scanDevice.address)

        setContent {
            val prefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }
            val themeName by remember { mutableStateOf(prefs.getString("theme_name", "DEFAULT") ?: "DEFAULT") }

            WSPRTheme(themeName = themeName) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        FlipperRemoteContent(
                            deviceName = scanDevice.name,
                            status = connectionStatus.value,
                            screenBitmap = screenState.value,
                            showPairingDialog = showPairingDialog.value,
                            onPairingConfirm = { code -> 
                                pairingDevice?.setPin(code.toByteArray())
                                showPairingDialog.value = false
                            },
                            onPairingDismiss = { showPairingDialog.value = false },
                            onBackPressed = { finish() },
                            onSendKey = { key, type -> sendInputKey(key, type) }
                        )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try {
                unregisterReceiver(pairingReceiver)
            } catch (e: Exception) {
                Log.e("FlipperRemote", "Error unregistering receiver", e)
            }
            isBound = false
        }
        disconnectFlipper()
    }

    private var isConnecting = false

    private fun connectToFlipper(address: String) {
        if (isConnecting) {
            Log.d("FlipperRemote", "Connection attempt already in progress to $address")
            return
        }
        isConnecting = true
        targetAddress = address
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            connectionStatus.value = "BLE DISABLED"
            isConnecting = false
            return
        }

        val device = adapter.getRemoteDevice(address)
        
        // Close previous GATT completely to avoid status 133
        bluetoothGatt?.disconnect()
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e("FlipperRemote", "Error closing old GATT instance", e)
        }
        bluetoothGatt = null
        txCharacteristic = null
        
        connectionStatus.value = "CONNECTING..."
        Log.d("FlipperRemote", "Connecting to $address...")
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        private var isConfigured = false

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("FlipperRemote", "GATT State Changed: status=$status, newState=$newState, currentBondState=${gatt.device.bondState}")
            
            val device = gatt.device
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("FlipperRemote", "GATT Error encountered: status=$status")
                isConnecting = false
                isConfigured = false
                txCharacteristic = null
                gatt.disconnect()
                try { gatt.close() } catch(e: Exception) {}
                if (bluetoothGatt == gatt) bluetoothGatt = null
                
                if (device.bondState == BluetoothDevice.BOND_BONDING) {
                    mainHandler.post { connectionStatus.value = "PAIRING..." }
                    return 
                }

                mainHandler.post { connectionStatus.value = "RETRYING ($status)" }
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed({ targetAddress?.let { connectToFlipper(it) } }, 3000)
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnecting = false
                isConfigured = false
                val bondState = device.bondState
                mainHandler.post { 
                    connectionStatus.value = if (bondState == BluetoothDevice.BOND_BONDED) "CONNECTED" else "PAIRING..."
                }
                
                mainHandler.postDelayed({
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        Log.d("FlipperRemote", "Bonded. Initiating service discovery.")
                        gatt.discoverServices()
                    } else if (device.bondState == BluetoothDevice.BOND_NONE) {
                        Log.d("FlipperRemote", "Not bonded. Triggering createBond.")
                        device.createBond()
                    }
                }, 1200)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnecting = false
                isConfigured = false
                txCharacteristic = null
                gatt.disconnect()
                try { gatt.close() } catch(e: Exception) {}
                if (bluetoothGatt == gatt) bluetoothGatt = null
                
                if (device.bondState == BluetoothDevice.BOND_BONDING) {
                    mainHandler.post { connectionStatus.value = "PAIRING..." }
                } else {
                    mainHandler.post { connectionStatus.value = "DISCONNECTED" }
                    mainHandler.removeCallbacksAndMessages(null)
                    mainHandler.postDelayed({ targetAddress?.let { connectToFlipper(it) } }, 2500)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            Log.d("FlipperRemote", "Services discovered")
            mainHandler.post { connectionStatus.value = "SETTING MTU..." }
            gatt.requestMtu(512)
            
            // Fallback for MTU stalling
            mainHandler.postDelayed({
                if (!isConfigured) setupRpc(gatt)
            }, 2000)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("FlipperRemote", "MTU: $mtu")
            if (!isConfigured) setupRpc(gatt)
        }

        private fun setupRpc(gatt: BluetoothGatt) {
            if (isConfigured) return
            val service = gatt.getService(SERVICE_UUID)
            val rxChar = service?.getCharacteristic(RX_CHAR_UUID)
            val txChar = service?.getCharacteristic(TX_CHAR_UUID)
            
            if (rxChar != null && txChar != null) {
                txCharacteristic = txChar
                gatt.setCharacteristicNotification(rxChar, true)
                val descriptor = rxChar.getDescriptor(CCCD_UUID)
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
            } else {
                mainHandler.post { connectionStatus.value = "RPC SERVICE ERROR" }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                isConfigured = true
                mainHandler.post { 
                    connectionStatus.value = "READY"
                    startScreenStreaming()
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == RX_CHAR_UUID) {
                handleInboundData(characteristic.value)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("FlipperRemote", "Write failed: $status")
            }
        }
    }

    private fun disconnectFlipper() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun startScreenStreaming() {
        // Main Message with GuiStartScreenStreamRequest (Tag 8)
        val payload = byteArrayOf(
            0x08, 0x01,       // command_id = 1
            0x42, 0x00        // gui_start_screen_stream_request (Tag 8, Length 0)
        )
        sendRpcPacket(payload)
    }

    private fun sendInputKey(key: String, type: String) {
        val keyVal = when(key) { "UP"->0; "DOWN"->1; "LEFT"->2; "RIGHT"->3; "OK"->4; "BACK"->5; else->5 }
        val typeVal = when(type) { "PRESS"->0; "RELEASE"->1; "SHORT"->2; "LONG"->3; else->2 }
        
        // GuiSendInputEventRequest (Tag 62) -> 0xF2 0x03
        val inner = byteArrayOf(
            0x08, keyVal.toByte(), 
            0x10, typeVal.toByte()
        )
        // Main Message wrapping
        val payload = ByteArray(inner.size + 5)
        payload[0] = 0x08
        payload[1] = 0x05 // random command_id
        payload[2] = 0xF2.toByte()
        payload[3] = 0x03.toByte()
        payload[4] = inner.size.toByte()
        System.arraycopy(inner, 0, payload, 5, inner.size)
        
        sendRpcPacket(payload) 
    }

    private fun sendRpcPacket(payload: ByteArray) {
        val char = txCharacteristic ?: return
        val gatt = bluetoothGatt ?: return
        
        // Standard Flipper RPC framing: [Length Varint] [Payload]
        val header = if (payload.size < 128) {
            byteArrayOf(payload.size.toByte())
        } else {
            byteArrayOf(
                (payload.size and 0x7F or 0x80).toByte(),
                (payload.size shr 7).toByte()
            )
        }
        
        val frame = ByteArray(header.size + payload.size)
        System.arraycopy(header, 0, frame, 0, header.size)
        System.arraycopy(payload, 0, frame, header.size, payload.size)
        
        char.value = frame
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt.writeCharacteristic(char)
    }

    private fun handleInboundData(data: ByteArray) {
        inboundBuffer += data
        
        var processedUntil = 0
        while (inboundBuffer.size - processedUntil >= 1030) {
            var foundFrame = false
            for (i in processedUntil..inboundBuffer.size - 1030) {
                // Look for GuiScreenFrame sequence: Tag 9, Len 1027, Tag 1, Len 1024
                // 0x4A, 0x83, 0x08, 0x0A, 0x80, 0x08
                if (inboundBuffer[i] == 0x4A.toByte() && 
                    inboundBuffer[i+1] == 0x83.toByte() && 
                    inboundBuffer[i+2] == 0x08.toByte() &&
                    inboundBuffer[i+3] == 0x0A.toByte() &&
                    inboundBuffer[i+4] == 0x80.toByte() &&
                    inboundBuffer[i+5] == 0x08.toByte()) {
                    
                    val pixelOffset = i + 6
                    val pixelData = inboundBuffer.copyOfRange(pixelOffset, pixelOffset + 1024)
                    
                    val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
                    for (j in 0 until 1024) {
                        val b = pixelData[j].toInt() and 0xFF
                        for (bit in 0 until 8) {
                            val x = (j * 8 + bit) % 128
                            val y = (j * 8 + bit) / 128
                            if (y < 64) {
                                bitmap.setPixel(x, y, if ((b and (1 shl bit)) != 0) AndroidColor.BLACK else AndroidColor.TRANSPARENT)
                            }
                        }
                    }

                    mainHandler.post {
                        screenState.value = bitmap
                    }
                    processedUntil = i + 1030
                    foundFrame = true
                    break
                }
            }
            if (!foundFrame) break
        }
        
        if (processedUntil > 0) {
            inboundBuffer = inboundBuffer.copyOfRange(processedUntil, inboundBuffer.size)
        }
        
        // If buffer gets too large without finding a frame, purge old data
        if (inboundBuffer.size > 8192) {
            inboundBuffer = inboundBuffer.copyOfRange(inboundBuffer.size - 2048, inboundBuffer.size)
        }
    }
}

@Composable
fun FlipperRemoteContent(
    deviceName: String,
    status: String,
    screenBitmap: Bitmap,
    showPairingDialog: Boolean,
    onPairingConfirm: (String) -> Unit,
    onPairingDismiss: () -> Unit,
    onBackPressed: () -> Unit,
    onSendKey: (String, String) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    var pairingCode by remember { mutableStateOf("") }

    if (status == "RPC SERVICE ERROR") {
        AlertDialog(
            onDismissRequest = { /* Keep dialog visible */ },
            title = {
                Text(
                    "RPC Service Error",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Text(
                    "The required RPC services were not found on this Flipper. " +
                    "For full remote features and firmware updates, please use the official Flipper Mobile app.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/flipperdevices/Flipper-Android-App"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("VISIT REPOSITORY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onBackPressed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = onPairingDismiss,
            title = {
                Text(
                    "Pairing Request",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Enter the 6-digit PIN code displayed on your Flipper's screen.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 6) pairingCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        placeholder = { 
                            Text(
                                "000000", 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace
                            ) 
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (pairingCode.length == 6) onPairingConfirm(pairingCode) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.secondary,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (pairingCode.length == 6) onPairingConfirm(pairingCode) },
                    enabled = pairingCode.length == 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PAIR DEVICE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onPairingDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(deviceName.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(status, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Physical Flipper Body (Whisper Pair / Hacker Aesthetic)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(0.75f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .border(2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Flipper Screen (Black Frame, Neon Green Content)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black, RoundedCornerShape(2.dp))
                            .padding(2.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = screenBitmap.asImageBitmap(),
                            contentDescription = "Flipper Screen",
                            modifier = Modifier.fillMaxSize(),
                            filterQuality = FilterQuality.None,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Control Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // D-Pad
                    Box(modifier = Modifier.size(140.dp)) {
                        FlipperDpadButton("▲", Modifier.align(Alignment.TopCenter)) { type ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSendKey("UP", type)
                        }
                        FlipperDpadButton("▼", Modifier.align(Alignment.BottomCenter)) { type ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSendKey("DOWN", type)
                        }
                        FlipperDpadButton("◀", Modifier.align(Alignment.CenterStart)) { type ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSendKey("LEFT", type)
                        }
                        FlipperDpadButton("▶", Modifier.align(Alignment.CenterEnd)) { type ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSendKey("RIGHT", type)
                        }
                        
                        // OK Center Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .align(Alignment.Center)
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            try {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                onSendKey("OK", "PRESS")
                                                tryAwaitRelease()
                                            } finally {
                                                onSendKey("OK", "RELEASE")
                                            }
                                        },
                                        onLongPress = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onSendKey("OK", "LONG")
                                        },
                                        onTap = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onSendKey("OK", "SHORT")
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("OK", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Hardware-style Back Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(50.dp, 36.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            try {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                onSendKey("BACK", "PRESS")
                                                tryAwaitRelease()
                                            } finally {
                                                onSendKey("BACK", "RELEASE")
                                            }
                                        },
                                        onTap = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onSendKey("BACK", "SHORT")
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.6f), CircleShape))
                        }
                        Text("BACK", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(0.2f))
    }
}

@Composable
fun FlipperDpadButton(icon: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    Box(
        modifier = modifier
            .size(46.dp)
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        try {
                            onClick("PRESS")
                            tryAwaitRelease()
                        } finally {
                            onClick("RELEASE")
                        }
                    },
                    onLongPress = {
                        onClick("LONG")
                    },
                    onTap = {
                        onClick("SHORT")
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
    }
}
