package com.bruceremote.app.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Bruce uses USB CDC-ACM at 115200 baud, 8N1 (confirmed by the firmware's serial command
 * docs and its own web navigator tool). This class owns the physical connection; all
 * protocol decoding lives in [SerialStreamParser] / [BruceDisplayRenderer].
 */
class UsbSerialManager(
    private val context: Context,
    private val onDataReceived: (ByteArray) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private var pendingDriver: UsbSerialDriver? = null
    private var pendingPortIndex: Int = 0
    private var pendingBaudRate: Int = 115200

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val driver = pendingDriver
            if (granted && driver != null) {
                openPort(driver, pendingPortIndex, pendingBaudRate)
            } else {
                _state.value = ConnectionState.Error("USB permission denied")
            }
            pendingDriver = null
        }
    }

    private var receiverRegistered = false

    fun availableDevices(): List<UsbSerialDriver> =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

    fun connect(driver: UsbSerialDriver, portIndex: Int = 0, baudRate: Int = 115200) {
        registerReceiverIfNeeded()
        val device = driver.device
        if (usbManager.hasPermission(device)) {
            openPort(driver, portIndex, baudRate)
            return
        }
        pendingDriver = driver
        pendingPortIndex = portIndex
        pendingBaudRate = baudRate
        _state.value = ConnectionState.RequestingPermission
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun openPort(driver: UsbSerialDriver, portIndex: Int, baudRate: Int) {
        _state.value = ConnectionState.Connecting
        try {
            val connection = usbManager.openDevice(driver.device)
                ?: run {
                    _state.value = ConnectionState.Error("Could not open USB device")
                    return
                }
            val targetIndex = portIndex.coerceIn(0, (driver.ports.size - 1).coerceAtLeast(0))
            val serialPort = driver.ports[targetIndex]
            serialPort.open(connection)
            serialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            try {
                serialPort.setDTR(true)
                serialPort.setRTS(true)
            } catch (_: UnsupportedOperationException) {
                // Not all drivers support DTR/RTS
            }

            port = serialPort

            val manager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    onDataReceived(data)
                }

                override fun onRunError(e: Exception) {
                    _state.value = ConnectionState.Error(e.message ?: "Serial I/O error")
                }
            })
            ioManager = manager
            ioExecutor.submit(manager)

            val label = (driver.device.productName ?: "USB device") + (if (driver.ports.size > 1) " [Port ${targetIndex + 1}]" else "")
            _state.value = ConnectionState.Connected(label)
        } catch (e: IOException) {
            _state.value = ConnectionState.Error(e.message ?: "Failed to open serial port")
        }
    }

    fun disconnect() {
        ioManager?.listener = null
        ioManager?.stop()
        ioManager = null
        try {
            port?.close()
        } catch (_: IOException) {
            // ignore
        }
        port = null
        _state.value = ConnectionState.Disconnected
    }

    /** Sends a text line to the serial interface. */
    fun sendLine(text: String) {
        sendRaw((text + "\r\n").toByteArray(Charsets.UTF_8))
    }

    fun sendRaw(bytes: ByteArray) {
        try {
            port?.write(bytes, 1000)
        } catch (e: IOException) {
            _state.value = ConnectionState.Error(e.message ?: "Write failed")
        }
    }

    fun teardown() {
        disconnect()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(permissionReceiver)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
            receiverRegistered = false
        }
        ioExecutor.shutdown()
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.bruceremote.app.USB_PERMISSION"

        fun deviceLabel(device: UsbDevice): String =
            device.productName ?: "VID:${device.vendorId} PID:${device.productId}"
    }
}
