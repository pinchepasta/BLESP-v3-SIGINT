package com.radar.blewifi

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.*
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
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
    private var isConnecting = false
    private var gattRetryCount = 0
    private var isUnlockAttempted = false

    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            }
            Log.d("FlipperRemote", "pairingReceiver onReceive action=${intent.action}, device=${device?.address}")
            if (device?.address != targetAddress) return

            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                    val pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
                    Log.d("FlipperRemote", "Pairing request received: variant=$variant, key=$pairingKey")
                    
                    pairingDevice = device
                    
                    when (variant) {
                        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION, 2, 3 -> {
                            Log.d("FlipperRemote", "Auto-confirming numeric comparison / passkey variant ($variant)")
                            try {
                                device?.setPairingConfirmation(true)
                            } catch (e: Exception) {
                                Log.e("FlipperRemote", "Error setting pairing confirmation", e)
                            }
                            showPairingDialog.value = false
                            connectionStatus.value = "PAIRING CONFIRMED"
                        }
                        BluetoothDevice.PAIRING_VARIANT_PIN -> {
                            Log.d("FlipperRemote", "PIN required ($variant). Displaying PIN input dialog.")
                            showPairingDialog.value = true
                            mainHandler.post { connectionStatus.value = "ENTER PIN FROM FLIPPER" }
                        }
                        else -> {
                            try {
                                device?.setPairingConfirmation(true)
                            } catch (e: Exception) {
                                showPairingDialog.value = true
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    Log.d("FlipperRemote", "Bond state changed: prev=$prevState -> new=$state")
                    when (state) {
                        BluetoothDevice.BOND_BONDING -> {
                            mainHandler.post { connectionStatus.value = "PAIRING IN PROGRESS..." }
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            showPairingDialog.value = false
                            gattRetryCount = 0
                            mainHandler.post { 
                                connectionStatus.value = "PAIRED! CONNECTING..."
                                mainHandler.postDelayed({
                                    targetAddress?.let { connectToFlipper(it) }
                                }, 500)
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
                        onRetry = {
                            gattRetryCount = 0
                            isUnlockAttempted = false
                            targetAddress?.let { connectToFlipper(it) }
                        },
                        onForcePairing = {
                            triggerForcePairing()
                        },
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
            try { unregisterReceiver(pairingReceiver) } catch (e: Exception) { Log.e("FlipperRemote", "Error unregistering receiver", e) }
            isBound = false
        }
        disconnectFlipper()
    }

    private fun unbondDevice(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            val res = method.invoke(device)
            Log.d("FlipperRemote", "Successfully unbonded device ${device.address}, res=$res")
        } catch (e: Exception) {
            Log.e("FlipperRemote", "Failed to unbond device", e)
        }
    }

    private fun triggerForcePairing() {
        val address = targetAddress ?: return
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        val device = adapter.getRemoteDevice(address)
        
        Log.d("FlipperRemote", "Triggering force pairing for $address...")
        connectionStatus.value = "PAIRING REQUEST SENT..."
        isUnlockAttempted = false
        
        disconnectFlipper()
        unbondDevice(device)
        
        mainHandler.postDelayed({
            Log.d("FlipperRemote", "Calling device.createBond() for ${device.address}...")
            val success = device.createBond()
            Log.d("FlipperRemote", "createBond() returned: $success")
            if (success) {
                connectionStatus.value = "CHECK FLIPPER SCREEN FOR PIN"
            } else {
                connectionStatus.value = "PAIRING INITIATED"
            }
        }, 800)
    }

    private fun connectToFlipper(address: String) {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            connectionStatus.value = "BLE DISABLED"
            isConnecting = false
            return
        }

        targetAddress = address
        val device = adapter.getRemoteDevice(address)

        if (bluetoothGatt != null) {
            Log.d("FlipperRemote", "Cleaning up existing GATT session before reconnecting to $address...")
            val oldGatt = bluetoothGatt
            bluetoothGatt = null
            txCharacteristic = null
            refreshGattCache(oldGatt)
            try { oldGatt?.disconnect() } catch (e: Exception) {}
            mainHandler.postDelayed({
                try { oldGatt?.close() } catch (e: Exception) {}
                mainHandler.postDelayed({ performConnect(device) }, 400)
            }, 300)
            return
        }

        performConnect(device)
    }

    private fun performConnect(device: BluetoothDevice) {
        connectionStatus.value = "CONNECTING..."
        Log.d("FlipperRemote", "Performing connect to ${device.address} (BondState=${device.bondState})...")
        isConnecting = true

        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.d("FlipperRemote", "Device is not bonded. Triggering createBond and awaiting BOND_BONDED...")
            connectionStatus.value = "PAIRING (CHECK FLIPPER SCREEN)..."
            device.createBond()
            return
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun refreshGattCache(gatt: BluetoothGatt?): Boolean {
        if (gatt == null) return false
        return try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            (refreshMethod.invoke(gatt) as? Boolean) ?: false
        } catch (e: Exception) {
            Log.e("FlipperRemote", "Could not refresh GATT cache", e)
            false
        }
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
                
                refreshGattCache(gatt)
                try { gatt.disconnect() } catch (e: Exception) {}
                mainHandler.postDelayed({
                    try { gatt.close() } catch (e: Exception) {}
                    if (bluetoothGatt == gatt) bluetoothGatt = null
                }, 300)
                
                if (device.bondState == BluetoothDevice.BOND_BONDING) {
                    mainHandler.post { connectionStatus.value = "PAIRING..." }
                    return 
                }

                gattRetryCount++
                if (gattRetryCount >= 3) {
                    mainHandler.post { connectionStatus.value = "GATT ERROR ($status)" }
                } else {
                    mainHandler.post { connectionStatus.value = "RETRYING ($status)..." }
                    mainHandler.removeCallbacksAndMessages(null)
                    mainHandler.postDelayed({ targetAddress?.let { connectToFlipper(it) } }, 2000)
                }
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnecting = false
                isConfigured = false
                gattRetryCount = 0
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
                }, 800)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnecting = false
                isConfigured = false
                txCharacteristic = null
                try { gatt.close() } catch(e: Exception) {}
                if (bluetoothGatt == gatt) bluetoothGatt = null
                
                if (device.bondState == BluetoothDevice.BOND_BONDING) {
                    mainHandler.post { connectionStatus.value = "PAIRING..." }
                } else {
                    mainHandler.post { connectionStatus.value = "DISCONNECTED" }
                    gattRetryCount++
                    if (gattRetryCount < 3) {
                        mainHandler.removeCallbacksAndMessages(null)
                        mainHandler.postDelayed({ targetAddress?.let { connectToFlipper(it) } }, 2000)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            Log.d("FlipperRemote", "Services discovered. Requesting MTU 512...")
            mainHandler.post { connectionStatus.value = "SETTING MTU..." }
            gatt.requestMtu(512)
            
            mainHandler.postDelayed({
                if (!isConfigured) setupRpc(gatt)
            }, 1200)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("FlipperRemote", "MTU set to: $mtu")
            if (!isConfigured) setupRpc(gatt)
        }

        private fun setupRpc(gatt: BluetoothGatt) {
            if (isConfigured) return
            Log.d("FlipperRemote", "setupRpc called. Discovered services count: ${gatt.services.size}")
            
            var foundService: BluetoothGattService? = null
            var rxChar: BluetoothGattCharacteristic? = null
            var txChar: BluetoothGattCharacteristic? = null

            val knownServiceUuids = listOf(
                UUID.fromString("30800001-51e3-417b-9294-51cd141be538"),
                UUID.fromString("30800000-51e3-417b-9294-51cd141be538"),
                UUID.fromString("00003080-0000-1000-8000-00805f9b34fb")
            )

            for (suid in knownServiceUuids) {
                val s = gatt.getService(suid)
                if (s != null) {
                    foundService = s
                    Log.d("FlipperRemote", "Found matching service by exact UUID: ${s.uuid}")
                    break
                }
            }

            if (foundService == null) {
                for (s in gatt.services) {
                    Log.d("FlipperRemote", "Device Service: ${s.uuid}")
                    if (s.uuid.toString().lowercase().contains("3080")) {
                        foundService = s
                        Log.d("FlipperRemote", "Found service matching '3080': ${s.uuid}")
                        break
                    }
                }
            }

            if (foundService == null) {
                for (s in gatt.services) {
                    val sUuidStr = s.uuid.toString().lowercase()
                    if (!sUuidStr.startsWith("0000180") && !sUuidStr.startsWith("0000181")) {
                        var hasNotify = false
                        var hasWrite = false
                        for (c in s.characteristics) {
                            val props = c.properties
                            if ((props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                                hasNotify = true
                            }
                            if ((props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                                hasWrite = true
                            }
                        }
                        if (hasNotify && hasWrite) {
                            foundService = s
                            Log.d("FlipperRemote", "Fallback: Found custom service with Notify+Write: ${s.uuid}")
                            break
                        }
                    }
                }
            }

            if (foundService != null) {
                for (c in foundService.characteristics) {
                    Log.d("FlipperRemote", "Service ${foundService.uuid} Char: ${c.uuid}, props=${c.properties}")
                }

                val knownRxUuids = listOf(
                    UUID.fromString("30810001-51e3-417b-9294-51cd141be538"),
                    UUID.fromString("00003081-0000-1000-8000-00805f9b34fb")
                )
                val knownTxUuids = listOf(
                    UUID.fromString("30820001-51e3-417b-9294-51cd141be538"),
                    UUID.fromString("00003082-0000-1000-8000-00805f9b34fb")
                )

                for (ruid in knownRxUuids) {
                    rxChar = foundService.getCharacteristic(ruid)
                    if (rxChar != null) break
                }
                for (tuid in knownTxUuids) {
                    txChar = foundService.getCharacteristic(tuid)
                    if (txChar != null) break
                }

                if (rxChar == null || txChar == null) {
                    for (c in foundService.characteristics) {
                        val cStr = c.uuid.toString().lowercase()
                        val props = c.properties
                        if (rxChar == null && (cStr.contains("3081") || (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0)) {
                            rxChar = c
                        } else if (txChar == null && (cStr.contains("3082") || (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0)) {
                            txChar = c
                        }
                    }
                }
            }

            if (rxChar != null && txChar != null) {
                Log.d("FlipperRemote", "RPC setup successful! RX Char: ${rxChar.uuid}, TX Char: ${txChar.uuid}")
                txCharacteristic = txChar
                gatt.setCharacteristicNotification(rxChar, true)

                val descriptor = rxChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                } else {
                    Log.w("FlipperRemote", "CCCD Descriptor not found, starting streaming directly")
                    isConfigured = true
                    mainHandler.post {
                        connectionStatus.value = "READY"
                        mainHandler.postDelayed({ startScreenStreaming() }, 200)
                    }
                }
            } else {
                Log.e("FlipperRemote", "Failed to setup RPC. Service: ${foundService?.uuid}, RX: ${rxChar?.uuid}, TX: ${txChar?.uuid}")
                mainHandler.post { connectionStatus.value = "RPC SERVICE ERROR" }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("FlipperRemote", "onDescriptorWrite called for ${descriptor.uuid}, status=$status")
            if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("FlipperRemote", "CCCD Write successful. System ready.")
                isConfigured = true
                mainHandler.post { 
                    connectionStatus.value = "READY"
                    mainHandler.postDelayed({ startScreenStreaming() }, 200)
                }
            } else {
                Log.w("FlipperRemote", "onDescriptorWrite completed with status: $status")
                if (!isConfigured) {
                    isConfigured = true
                    mainHandler.post {
                        connectionStatus.value = "READY"
                        mainHandler.postDelayed({ startScreenStreaming() }, 200)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            Log.d("FlipperRemote", "onCharacteristicChanged: received ${bytes.size} bytes: ${bytes.joinToString(" ") { "%02X".format(it) }}")
            handleInboundData(bytes)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d("FlipperRemote", "onCharacteristicWrite: char=${characteristic.uuid}, status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("FlipperRemote", "Characteristic write failed with status: $status")
            }
        }
    }

    private fun disconnectFlipper() {
        val gatt = bluetoothGatt
        bluetoothGatt = null
        txCharacteristic = null
        if (gatt != null) {
            try { gatt.disconnect() } catch (e: Exception) {}
            try { gatt.close() } catch (e: Exception) {}
        }
    }

    private fun startScreenStreaming() {
        Log.d("FlipperRemote", "Sending start screen stream request...")
        val payload = byteArrayOf(
            0x08, 0x01,       // command_id = 1
            0x42, 0x00        // gui_start_screen_stream_request (Tag 8, Length 0)
        )
        sendRpcPacket(payload)
    }

    private fun sendInputKey(key: String, type: String) {
        val keyVal = when(key) { "UP"->0; "DOWN"->1; "LEFT"->2; "RIGHT"->3; "OK"->4; "BACK"->5; else->5 }
        val typeVal = when(type) { "PRESS"->0; "RELEASE"->1; "SHORT"->2; "LONG"->3; else->2 }
        
        val inner = byteArrayOf(
            0x08, keyVal.toByte(), 
            0x10, typeVal.toByte()
        )
        val payload = ByteArray(inner.size + 5)
        payload[0] = 0x08
        payload[1] = 0x05 // command_id
        payload[2] = 0xF2.toByte()
        payload[3] = 0x03.toByte()
        payload[4] = inner.size.toByte()
        System.arraycopy(inner, 0, payload, 5, inner.size)
        
        sendRpcPacket(payload) 
    }

    private fun sendRpcPacket(payload: ByteArray) {
        val char = txCharacteristic
        val gatt = bluetoothGatt
        if (char == null || gatt == null) {
            Log.e("FlipperRemote", "sendRpcPacket failed: char or gatt is null")
            return
        }
        
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
        
        Log.d("FlipperRemote", "Writing RPC Frame (${frame.size} bytes) with WRITE_TYPE_DEFAULT to char ${char.uuid}")

        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, frame, writeType)
        } else {
            @Suppress("DEPRECATION")
            char.value = frame
            @Suppress("DEPRECATION")
            char.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    private fun handleInboundData(data: ByteArray) {
        if (data.isEmpty()) return
        inboundBuffer += data
        Log.d("FlipperRemote", "Inbound buffer size: ${inboundBuffer.size}")

        if (inboundBuffer.size >= 5) {
            for (i in 0..inboundBuffer.size - 5) {
                if (inboundBuffer[i] == 0x08.toByte() && inboundBuffer[i+2] == 0x10.toByte()) {
                    val cmdStatus = inboundBuffer[i+3].toInt() and 0xFF
                    Log.d("FlipperRemote", "Parsed RPC Response status: $cmdStatus")
                    if (cmdStatus == 3) {
                        Log.w("FlipperRemote", "Flipper returned ERROR_LOCKED (status 3). Attempting auto-unlock via BACK key...")
                        if (!isUnlockAttempted) {
                            isUnlockAttempted = true
                            mainHandler.post {
                                connectionStatus.value = "UNLOCKING FLIPPER..."
                                sendInputKey("BACK", "PRESS")
                                mainHandler.postDelayed({
                                    sendInputKey("BACK", "RELEASE")
                                    mainHandler.postDelayed({
                                        startScreenStreaming()
                                    }, 500)
                                }, 150)
                            }
                        } else {
                            mainHandler.post {
                                connectionStatus.value = "FLIPPER IS LOCKED (PRESS BACK ON FLIPPER)"
                            }
                        }
                    }
                }
            }
        }
        
        var processedUntil = 0
        while (inboundBuffer.size - processedUntil >= 1027) {
            var foundFrame = false
            for (i in processedUntil..inboundBuffer.size - 1027) {
                val isDataHeader = inboundBuffer[i] == 0x0A.toByte() && 
                                   inboundBuffer[i+1] == 0x80.toByte() && 
                                   inboundBuffer[i+2] == 0x08.toByte()
                val isGuiHeader = (i + 5 < inboundBuffer.size) &&
                                  inboundBuffer[i] == 0x4A.toByte() && 
                                  inboundBuffer[i+1] == 0x83.toByte() && 
                                  inboundBuffer[i+2] == 0x08.toByte() &&
                                  inboundBuffer[i+3] == 0x0A.toByte() &&
                                  inboundBuffer[i+4] == 0x80.toByte() &&
                                  inboundBuffer[i+5] == 0x08.toByte()

                if (isDataHeader || isGuiHeader) {
                    val pixelOffset = if (isGuiHeader) i + 6 else i + 3
                    if (inboundBuffer.size - pixelOffset >= 1024) {
                        Log.d("FlipperRemote", "FOUND SCREEN FRAME at offset $i!")
                        val pixelData = inboundBuffer.copyOfRange(pixelOffset, pixelOffset + 1024)
                        
                        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
                        for (j in 0 until 1024) {
                            val b = pixelData[j].toInt() and 0xFF
                            val x = j % 128
                            val page = j / 128
                            for (bit in 0 until 8) {
                                val y = page * 8 + bit
                                if ((b and (1 shl bit)) != 0) {
                                    bitmap.setPixel(x, y, AndroidColor.WHITE)
                                } else {
                                    bitmap.setPixel(x, y, AndroidColor.TRANSPARENT)
                                }
                            }
                        }

                        mainHandler.post {
                            screenState.value = bitmap
                            connectionStatus.value = "READY (STREAMING)"
                        }
                        processedUntil = pixelOffset + 1024
                        foundFrame = true
                        break
                    }
                }
            }
            if (!foundFrame) break
        }
        
        if (processedUntil > 0) {
            inboundBuffer = inboundBuffer.copyOfRange(processedUntil, inboundBuffer.size)
        }
        
        if (inboundBuffer.size > 16384) {
            inboundBuffer = inboundBuffer.copyOfRange(inboundBuffer.size - 4096, inboundBuffer.size)
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
    onRetry: () -> Unit,
    onForcePairing: () -> Unit,
    onBackPressed: () -> Unit,
    onSendKey: (String, String) -> Unit
) {
    val view = LocalView.current
    var pairingCode by remember { mutableStateOf("") }

    if (status == "RPC SERVICE ERROR" || status.startsWith("GATT ERROR") || status.contains("LOCKED") || status.contains("NOT PAIRED")) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    if (status.contains("LOCKED")) "Flipper Screen Locked" else "Flipper Bluetooth Setup",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        if (status.contains("LOCKED")) {
                            "Your Flipper is currently locked.\n\n" +
                            "• Press BACK on your Flipper to unlock its home screen.\n" +
                            "• Or tap 'TRIGGER PAIRING PIN' below to send a new pairing request."
                        } else {
                            "Connection to '$deviceName' requires Bluetooth pairing.\n\n" +
                            "If Flipper does not show a PIN popup:\n" +
                            "1. On Flipper go to: Settings -> Bluetooth -> Forget All Bonded Devices.\n" +
                            "2. Then tap 'TRIGGER PAIRING PIN' below."
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onForcePairing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("TRIGGER PAIRING PIN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RECONNECT", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onBackPressed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DISCOVER DEVICES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
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
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
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
                        Image(
                            bitmap = screenBitmap.asImageBitmap(),
                            contentDescription = "Flipper Screen",
                            modifier = Modifier.fillMaxSize(),
                            filterQuality = FilterQuality.None,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
