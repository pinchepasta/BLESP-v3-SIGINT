package com.espsdr.jammer.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * USB-serial transport for the ESP-SDR Jammer shell.
 *
 * Mirrors the WebSerial layer in jammer.html: one line-oriented reader, one
 * ordered write queue, CR-terminated commands.
 */
class UsbSerialController(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.espsdr.jammer.USB_PERMISSION"
        private const val BAUD = 115200
        private const val WRITE_TIMEOUT_MS = 1000
        private const val RX_LIMIT = 16 * 1024
    }

    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeJob: Job? = null
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val rx = StringBuilder()

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 4096)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Prober with explicit rules for ESP32-S3 / custom CDC devices. */
    private val prober: UsbSerialProber by lazy {
        val table = UsbSerialProber.getDefaultProbeTable()
        // ESP32-S3 / ESP32-C3 / ESP32-S2 USB CDC-ACM
        table.addProduct(0x303A, 0x1001, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x4001, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x1002, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x0002, CdcAcmSerialDriver::class.java)
        UsbSerialProber(table)
    }

    fun listDevices(): List<UsbDevice> =
        usbManager.deviceList.values.mapNotNull { device ->
            if (prober.probeDevice(device) != null) device
            else if (device.vendorId != 0) device // Allow any attached USB serial device so user can connect to it!
            else null
        }.sortedBy { it.deviceName }

    fun hasPermission(device: UsbDevice) = usbManager.hasPermission(device)

    // ---------------------------------------------------------------- connect

    suspend fun connect(device: UsbDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!ensurePermission(device)) {
                return@withContext Result.failure(IllegalStateException("USB permission denied"))
            }
            val driver = prober.probeDevice(device) ?: CdcAcmSerialDriver(device)
            val handle = usbManager.openDevice(device)
                ?: return@withContext Result.failure(IllegalStateException("openDevice() failed"))
            val p = driver.ports.firstOrNull()
                ?: return@withContext Result.failure(IllegalStateException("device has no ports"))

            p.open(handle)
            p.setParameters(BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { p.setDTR(true) }
            runCatching { p.setRTS(true) }

            port = p
            startReader(p)
            startWriter()
            _connected.value = true
            Result.success(Unit)
        } catch (t: Throwable) {
            closeQuietly()
            Result.failure(t)
        }
    }

    private suspend fun ensurePermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_USB_PERMISSION) return
                    runCatching { context.unregisterReceiver(this) }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (cont.isActive) cont.resume(granted)
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(
                context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags or PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager.requestPermission(device, pi)
        }
    }

    // ------------------------------------------------------------------ write

    private fun startWriter() {
        writeJob?.cancel()
        writeJob = scope.launch {
            for (bytes in writeQueue) {
                val p = port ?: continue
                runCatching { p.write(bytes, WRITE_TIMEOUT_MS) }
            }
        }
    }

    /** Same contract as the web UI: append CR, keep order, never block the UI. */
    fun send(command: String) {
        writeQueue.trySend((command + "\r").toByteArray(Charsets.UTF_8))
    }

    // ------------------------------------------------------------------- read

    private fun startReader(p: UsbSerialPort) {
        ioManager = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) = feed(data)
            override fun onRunError(e: Exception) {
                _lines.tryEmit("# serial error: ${e.message}")
                closeQuietly()
            }
        }).also { it.start() }
    }

    private fun feed(data: ByteArray) {
        synchronized(rx) {
            rx.append(String(data, Charsets.UTF_8))
            var nl = rx.indexOf("\n")
            while (nl >= 0) {
                val line = rx.substring(0, nl).removeSuffix("\r")
                rx.delete(0, nl + 1)
                _lines.tryEmit(line)
                nl = rx.indexOf("\n")
            }
            if (rx.length > RX_LIMIT) rx.delete(0, rx.length - RX_LIMIT / 2)
        }
    }

    // -------------------------------------------------------------- teardown

    fun disconnect() {
        scope.launch { closeQuietly() }
    }

    private fun closeQuietly() {
        runCatching { ioManager?.stop() }
        ioManager = null
        runCatching { port?.close() }
        port = null
        _connected.value = false
    }

    fun dispose() {
        closeQuietly()
        writeJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }
}