package com.radar.blewifi

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.location.Location
import android.location.LocationListener
import android.os.Bundle

class ScannerManager(private val context: Context) {

    interface ScanListener {
        fun onDevicesUpdated(devices: List<ScanDevice>)
        fun onScanStatusChanged(scanning: Boolean)
        fun onMovementDetected(device: ScanDevice)
        fun onLocationUpdated(lat: Double, lon: Double)
        fun onError(msg: String)
    }

    private val listeners = mutableListOf<ScanListener>()

    fun addListener(listener: ScanListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ScanListener) {
        listeners.remove(listener)
    }

    private fun notifyDevicesUpdated(devices: List<ScanDevice>) {
        val list = devices.toList()
        handler.post {
<<<<<<< HEAD
            listeners.forEach { it.onDevicesUpdated(devices) }
        }
    }

    private fun addToArchive(key: String, device: ScanDevice) {
        archiveMap[key] = device
        if (archiveMap.size > 500) {
            val oldestKey = archiveMap.minByOrNull { it.value.lastSeen }?.key
            if (oldestKey != null) {
                archiveMap.remove(oldestKey)
            }
=======
            listeners.forEach { it.onDevicesUpdated(list) }
>>>>>>> parent of 5dc96b0 (v3.5.3)
        }
    }

    private fun notifyScanStatusChanged(scanning: Boolean) {
        handler.post {
            listeners.forEach { it.onScanStatusChanged(scanning) }
        }
    }

    private fun notifyMovementDetected(device: ScanDevice) {
        handler.post {
            listeners.forEach { it.onMovementDetected(device) }
        }
    }

    private fun notifyLocationUpdated(lat: Double, lon: Double) {
        handler.post {
            listeners.forEach { it.onLocationUpdated(lat, lon) }
        }
    }

    private fun notifyError(msg: String) {
        handler.post {
            listeners.forEach { it.onError(msg) }
        }
    }

    private val alarmIds = mutableSetOf<String>()

    fun setAlarm(deviceId: String, enabled: Boolean) {
        if (enabled) alarmIds.add(deviceId) else alarmIds.remove(deviceId)
    }

    fun isAlarmEnabled(deviceId: String): Boolean = alarmIds.contains(deviceId)

    fun getDevices(): List<ScanDevice> = deviceMap.values.toList()

    val deviceMap = ConcurrentHashMap<String, ScanDevice>()
    val archiveMap = ConcurrentHashMap<String, ScanDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var ioExecutor: ExecutorService? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var wifiManager: WifiManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var locationManager: android.location.LocationManager? = null
    private var scanning = false
    val isScanning: Boolean get() = scanning
    private var isBackgroundMode = false
    var isMapActive = false
    var lastLocation: Location? = null
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()
    private var ouiDatabase: Map<String, String> = emptyMap()

    private val vendorMap = mapOf(
        "00:50:F2" to "Microsoft",
        "00:03:93" to "Apple",
        "00:05:02" to "Apple",
        "00:0D:93" to "Apple",
        "00:10:FA" to "Apple",
        "00:16:CB" to "Apple",
        "00:17:F2" to "Apple",
        "00:19:E3" to "Apple",
        "00:1B:63" to "Apple",
        "00:1C:B3" to "Apple",
        "00:1D:4F" to "Apple",
        "00:1E:52" to "Apple",
        "00:1E:C2" to "Apple",
        "00:21:E9" to "Apple",
        "00:22:41" to "Apple",
        "00:23:12" to "Apple",
        "00:23:32" to "Apple",
        "00:23:6C" to "Apple",
        "00:24:36" to "Apple",
        "00:25:00" to "Apple",
        "00:25:4B" to "Apple",
        "00:25:BC" to "Apple",
        "00:26:08" to "Apple",
        "00:26:4A" to "Apple",
        "00:26:B0" to "Apple",
        "00:26:BB" to "Apple",
        "28:CF:E9" to "Apple",
        "D8:30:62" to "Apple",
        "70:35:60" to "Apple",
        "B8:C7:5D" to "Apple",
        "F0:D1:A9" to "Apple",
        "00:23:4D" to "Samsung",
        "00:00:F0" to "Samsung",
        "00:07:AB" to "Samsung",
        "00:0D:E6" to "Samsung",
        "00:12:47" to "Samsung",
        "00:12:FB" to "Samsung",
        "00:13:77" to "Samsung",
        "00:15:99" to "Samsung",
        "00:15:B9" to "Samsung",
        "00:16:32" to "Samsung",
        "00:16:DB" to "Samsung",
        "00:17:C9" to "Samsung",
        "00:17:D5" to "Samsung",
        "00:18:AF" to "Samsung",
        "00:19:2D" to "Samsung",
        "00:1A:8A" to "Samsung",
        "00:1B:98" to "Samsung",
        "00:1C:62" to "Samsung",
        "00:1D:6B" to "Samsung",
        "00:1E:7D" to "Samsung",
        "00:1F:CC" to "Samsung",
        "00:21:D2" to "Samsung",
        "00:24:54" to "Samsung",
        "00:24:91" to "Samsung",
        "00:26:37" to "Samsung",
        "00:04:0E" to "AVM (Fritz!Box)",
        "00:15:0C" to "AVM (Fritz!Box)",
        "00:1C:4A" to "AVM (Fritz!Box)",
        "00:1F:3F" to "AVM (Fritz!Box)",
        "24:65:11" to "AVM (Fritz!Box)",
        "34:31:C4" to "AVM (Fritz!Box)",
        "38:10:D5" to "AVM (Fritz!Box)",
        "44:4E:6D" to "AVM (Fritz!Box)",
        "9C:C7:A6" to "AVM (Fritz!Box)",
        "BC:05:43" to "AVM (Fritz!Box)",
        "C0:25:06" to "AVM (Fritz!Box)",
        "CC:CE:1E" to "AVM (Fritz!Box)",
        "E0:28:6D" to "AVM (Fritz!Box)",
        "00:11:32" to "Synology",
        "00:1D:0F" to "Synology",
        "00:22:4D" to "Synology",
        "90:09:D7" to "Synology",
        "D0:50:99" to "Synology",
        "00:1A:11" to "Google",
        "3C:5A:B4" to "Google",
        "D8:EB:97" to "Google",
        "E4:F0:42" to "Google",
        "F4:F5:D8" to "Google",
        "00:18:D1" to "Google",
        "00:1A:2B" to "Google",
        "00:1E:64" to "HP",
        "00:22:64" to "HP",
        "00:23:7D" to "HP",
        "00:24:81" to "HP",
        "00:25:B3" to "HP",
        "00:26:55" to "HP",
        "00:10:83" to "HP",
        "00:11:0A" to "HP",
        "00:11:85" to "HP",
        "00:12:79" to "HP",
        "00:13:21" to "HP",
        "00:14:38" to "HP",
        "00:15:60" to "HP",
        "00:16:35" to "HP",
        "00:17:08" to "HP",
        "00:18:71" to "HP",
        "00:19:BB" to "HP",
        "00:1A:4B" to "HP",
        "00:1B:78" to "HP",
        "00:1C:C4" to "HP",
        "00:0B:85" to "Cisco",
        "00:0D:BD" to "Cisco",
        "00:0E:D7" to "Cisco",
        "00:0F:23" to "Cisco",
        "00:0F:34" to "Cisco",
        "00:0F:90" to "Cisco",
        "00:11:20" to "Cisco",
        "00:11:21" to "Cisco",
        "00:11:5C" to "Cisco",
        "00:11:92" to "Cisco",
        "00:11:BB" to "Cisco",
        "00:12:00" to "Cisco",
        "00:12:01" to "Cisco",
        "00:12:43" to "Cisco",
        "00:12:44" to "Cisco",
        "00:12:7F" to "Cisco",
        "00:12:80" to "Cisco",
        "00:12:DA" to "Cisco",
        "00:13:19" to "Cisco",
        "00:13:1A" to "Cisco",
        "00:13:60" to "Cisco",
        "00:13:61" to "Cisco",
        "00:13:7F" to "Cisco",
        "00:13:80" to "Cisco",
        "00:13:C3" to "Cisco",
        "00:13:C4" to "Cisco",
        "00:14:1B" to "Cisco",
        "00:14:1C" to "Cisco",
        "00:14:69" to "Cisco",
        "00:14:6A" to "Cisco",
        "00:14:A8" to "Cisco",
        "00:14:A9" to "Cisco",
        "00:14:F1" to "Cisco",
        "00:14:F2" to "Cisco",
        "00:15:2B" to "Cisco",
        "00:15:2C" to "Cisco",
        "00:15:62" to "Cisco",
        "00:15:63" to "Cisco",
        "00:15:C6" to "Cisco",
        "00:15:C7" to "Cisco",
        "00:15:FA" to "Cisco",
        "00:15:FB" to "Cisco",
        "00:16:35" to "Cisco",
        "00:16:36" to "Cisco",
        "00:16:46" to "Cisco",
        "00:16:47" to "Cisco",
        "00:16:9C" to "Cisco",
        "00:16:9D" to "Cisco",
        "00:16:C7" to "Cisco",
        "00:16:C8" to "Cisco",
        "00:17:0E" to "Cisco",
        "00:17:0F" to "Cisco",
        "00:17:59" to "Cisco",
        "00:17:5A" to "Cisco",
        "00:17:94" to "Cisco",
        "00:17:95" to "Cisco",
        "00:17:DF" to "Cisco",
        "00:17:E0" to "Cisco",
        "00:18:18" to "Cisco",
        "00:18:19" to "Cisco",
        "00:18:73" to "Cisco",
        "00:18:74" to "Cisco",
        "00:18:B9" to "Cisco",
        "00:18:BA" to "Cisco",
        "00:19:06" to "Cisco",
        "00:19:07" to "Cisco",
        "00:19:55" to "Cisco",
        "00:19:56" to "Cisco",
        "00:19:A9" to "Cisco",
        "00:19:AA" to "Cisco",
        "00:19:E7" to "Cisco",
        "00:19:E8" to "Cisco",
        "00:1A:2F" to "Cisco",
        "00:1A:30" to "Cisco",
        "00:1A:6C" to "Cisco",
        "00:1A:6D" to "Cisco",
        "00:1A:A1" to "Cisco",
        "00:1A:A2" to "Cisco",
        "00:1A:E2" to "Cisco",
        "00:1A:E3" to "Cisco",
        "00:1B:0C" to "Cisco",
        "00:1B:0D" to "Cisco",
        "00:1B:2A" to "Cisco",
        "00:1B:2B" to "Cisco",
        "00:1B:53" to "Cisco",
        "00:1B:54" to "Cisco",
        "00:1B:8F" to "Cisco",
        "00:1B:90" to "Cisco",
        "00:1B:D4" to "Cisco",
        "00:1B:D5" to "Cisco",
        "00:1C:0E" to "Cisco",
        "00:1C:0F" to "Cisco",
        "00:1C:57" to "Cisco",
        "00:1C:58" to "Cisco",
        "00:1C:B0" to "Cisco",
        "00:1C:B1" to "Cisco",
        "00:1C:F6" to "Cisco",
        "00:1C:F9" to "Cisco",
        "00:1D:45" to "Cisco",
        "00:1D:46" to "Cisco",
        "00:1D:70" to "Cisco",
        "00:1D:71" to "Cisco",
        "00:1D:A1" to "Cisco",
        "00:1D:A2" to "Cisco",
        "00:1D:E5" to "Cisco",
        "00:1D:E6" to "Cisco",
        "00:1E:13" to "Cisco",
        "00:1E:14" to "Cisco",
        "00:1E:49" to "Cisco",
        "00:1E:4A" to "Cisco",
        "00:1E:79" to "Cisco",
        "00:1E:7A" to "Cisco",
        "00:1E:BD" to "Cisco",
        "00:1E:BE" to "Cisco",
        "00:1E:F6" to "Cisco",
        "00:1E:F7" to "Cisco",
        "00:1F:26" to "Cisco",
        "00:1F:27" to "Cisco",
        "00:1F:6C" to "Cisco",
        "00:1F:6D" to "Cisco",
        "00:1F:9D" to "Cisco",
        "00:1F:9E" to "Cisco",
        "00:1F:CA" to "Cisco",
        "00:1F:CB" to "Cisco",
        "00:21:1B" to "Cisco",
        "00:21:1C" to "Cisco",
        "00:21:55" to "Cisco",
        "00:21:56" to "Cisco",
        "00:21:A0" to "Cisco",
        "00:21:A1" to "Cisco",
        "00:21:D7" to "Cisco",
        "00:21:D8" to "Cisco",
        "00:22:0D" to "Cisco",
        "00:22:0E" to "Cisco",
        "00:22:55" to "Cisco",
        "00:22:56" to "Cisco",
        "00:22:90" to "Cisco",
        "00:22:91" to "Cisco",
        "00:22:BD" to "Cisco",
        "00:22:BE" to "Cisco",
        "00:23:04" to "Cisco",
        "00:23:05" to "Cisco",
        "00:23:5D" to "Cisco",
        "00:23:5E" to "Cisco",
        "00:23:97" to "Cisco",
        "00:23:98" to "Cisco",
        "00:23:EA" to "Cisco",
        "00:23:EB" to "Cisco",
        "00:24:13" to "Cisco",
        "00:24:14" to "Cisco",
        "00:24:50" to "Cisco",
        "00:24:51" to "Cisco",
        "00:24:97" to "Cisco",
        "00:24:98" to "Cisco",
        "00:24:C3" to "Cisco",
        "00:24:C4" to "Cisco",
        "00:24:F7" to "Cisco",
        "00:24:F9" to "Cisco",
        "00:25:45" to "Cisco",
        "00:25:46" to "Cisco",
        "00:25:83" to "Cisco",
        "00:25:84" to "Cisco",
        "00:25:B4" to "Cisco",
        "00:25:B5" to "Cisco",
        "00:26:0A" to "Cisco",
        "00:26:0B" to "Cisco",
        "00:26:51" to "Cisco",
        "00:26:52" to "Cisco",
        "00:26:98" to "Cisco",
        "00:26:99" to "Cisco",
        "00:26:CB" to "Cisco",
        "00:26:CC" to "Cisco",
        "00:0C:43" to "Ralink",
        "00:17:A5" to "Ralink",
        "00:1E:E3" to "Ralink",
        "1C:BF:CE" to "Ralink",
        "00:03:7F" to "Atheros",
        "00:13:74" to "Atheros",
        "00:80:48" to "Atheros",
        "00:1C:10" to "Atheros",
        "00:10:75" to "Seagate",
        "00:1C:C0" to "Seagate",
        "00:00:5E" to "ICANN/IANA",
        "00:00:0C" to "Cisco",
        "00:01:C9" to "Shanghai Radisys",
        "00:02:6F" to "Senao",
        "00:03:0F" to "Digital",
        "00:03:2F" to "Global Village",
        "00:03:6B" to "Cisco",
        "00:03:E3" to "Cisco",
        "00:04:4B" to "NVIDIA",
        "00:04:75" to "3Com",
        "00:04:76" to "3Com",
        "00:04:7D" to "Pelco",
        "00:04:96" to "Extreme Networks",
        "00:04:A3" to "Microchip",
        "00:04:BD" to "Motorola",
        "00:05:5D" to "D-Link",
        "00:05:B1" to "ASRock",
        "00:06:25" to "Linksys",
        "00:06:5B" to "Dell",
        "00:07:40" to "ADTEK",
        "00:07:E9" to "Intel",
        "00:08:54" to "Netgear",
        "00:08:74" to "Dell",
        "00:08:A1" to "Raymarine",
        "00:09:5B" to "Netgear",
        "00:0A:E4" to "Alpha Networks",
        "00:0B:6B" to "Wistron",
        "00:0C:F1" to "Intel",
        "00:0D:0B" to "Buffalo",
        "00:0E:08" to "Cisco",
        "00:0E:35" to "Intel",
        "00:0F:1F" to "Wistron",
        "00:0F:66" to "Cisco",
        "00:0F:EA" to "Giga-Byte",
        "00:10:60" to "Iomega",
        "00:11:09" to "Asus",
        "00:11:24" to "Apple",
        "00:11:50" to "Belkin",
        "00:11:6B" to "Iomega",
        "00:12:17" to "Cisco",
        "00:12:F0" to "Intel",
        "00:13:02" to "Intel",
        "00:13:CE" to "Intel",
        "00:13:E8" to "Intel",
        "00:14:22" to "Dell",
        "00:14:BF" to "Linksys",
        "00:15:AF" to "AzureWave",
        "00:16:6F" to "Intel",
        "00:16:76" to "Intel",
        "00:16:EA" to "Intel",
        "00:17:31" to "Asus",
        "00:17:C4" to "Synology",
        "00:18:39" to "Cisco",
        "00:18:4D" to "Netgear",
        "00:18:8B" to "Dell",
        "00:18:DE" to "Intel",
        "00:18:F8" to "Cisco",
        "00:19:21" to "Cisco",
        "00:19:70" to "ZTE",
        "00:19:D1" to "Intel",
        "00:19:E3" to "Apple",
        "00:1A:4D" to "Giga-Byte",
        "00:1A:70" to "Cisco",
        "00:1A:A0" to "Dell",
        "00:1A:E9" to "Intel",
        "00:1B:11" to "D-Link",
        "00:1B:21" to "Intel",
        "00:1B:63" to "Apple",
        "00:1B:77" to "Intel",
        "00:1C:10" to "Cisco",
        "00:1C:23" to "Dell",
        "00:1C:25" to "Intel",
        "00:1C:43" to "Senao",
        "00:1C:BF" to "Intel",
        "00:1D:09" to "Dell",
        "00:1D:60" to "ASRock",
        "00:1D:72" to "Apple",
        "00:1D:E1" to "Intel",
        "00:1E:37" to "Cisco",
        "00:1E:4F" to "Dell",
        "00:1E:52" to "Apple",
        "00:1E:8C" to "Asus",
        "00:1E:C2" to "Apple",
        "00:1F:33" to "Netgear",
        "00:1F:3C" to "Intel",
        "00:1F:5B" to "Apple",
        "00:1F:F3" to "Apple",
        "00:21:00" to "Gemtek",
        "00:21:29" to "Cisco",
        "00:21:5A" to "Intel",
        "00:21:6A" to "Intel",
        "00:21:70" to "Dell",
        "00:21:85" to "Cisco",
        "00:21:9B" to "Dell",
        "00:21:E9" to "Apple",
        "00:22:15" to "Cisco",
        "00:22:19" to "Dell",
        "00:22:3F" to "Netgear",
        "00:22:41" to "Apple",
        "00:22:43" to "AzureWave",
        "00:22:6B" to "Cisco",
        "00:22:69" to "Intel",
        "00:22:FA" to "Intel",
        "00:23:12" to "Apple",
        "00:23:32" to "Apple",
        "00:23:ae" to "Dell",
        "00:23:df" to "Apple",
        "00:24:36" to "Apple",
        "00:24:D7" to "Intel",
        "00:24:E8" to "Dell",
        "00:25:00" to "Apple",
        "00:25:4B" to "Apple",
        "00:25:BC" to "Apple",
        "00:26:08" to "Apple",
        "00:26:4A" to "Apple",
        "00:26:B0" to "Apple",
        "00:26:BB" to "Apple",
        "00:26:C6" to "Intel",
        "00:27:0E" to "Intel",
        "00:27:10" to "Apple",
        "00:30:48" to "Supermicro",
        "00:40:05" to "Casio",
        "00:50:56" to "VMware",
        "00:50:BA" to "D-Link",
        "00:50:C2" to "TP-Link",
        "00:90:4B" to "Cisco",
        "00:90:F5" to "CLEVO",
        "00:A0:C9" to "Intel",
        "00:A0:D1" to "Netgear",
        "00:C0:CA" to "ALFA",
        "00:E0:4C" to "Realtek",
        "00:E0:4D" to "Realtek",
        "20:47:ED" to "Texas Instruments",
        "28:CF:E9" to "Apple",
        "2C:41:38" to "Hewlett Packard",
        "30:05:5C" to "Brother",
        "34:15:9E" to "Samsung",
        "3C:D9:2B" to "Hewlett Packard",
        "40:61:86" to "Micro-Star",
        "44:AD:D9" to "Nintendo",
        "48:5D:60" to "AzureWave",
        "4C:ED:DE" to "Kingston",
        "50:46:5D" to "Asus",
        "54:E1:AD" to "Nintendo",
        "58:67:1A" to "Xiaomi",
        "60:45:BD" to "LG Electronics",
        "64:70:02" to "TP-Link",
        "68:5D:43" to "Intel",
        "70:35:60" to "Apple",
        "74:E5:43" to "Intel",
        "80:1F:02" to "Edimax",
        "84:3A:4B" to "Intel",
        "88:AE:ED" to "Apple",
        "8C:85:90" to "Apple",
        "90:B1:1C" to "Apple",
        "98:01:A7" to "AzureWave",
        "A0:0B:BA" to "Apple",
        "A4:77:33" to "Google",
        "AC:81:12" to "Xiaomi",
        "B0:48:7A" to "Apple",
        "B4:0B:44" to "Apple",
        "B8:27:EB" to "Raspberry Pi",
        "BC:5F:F4" to "ASRock",
        "C0:3F:0E" to "Apple",
        "C4:2C:03" to "Apple",
        "C8:3A:35" to "Tenda",
        "CC:2D:21" to "Apple",
        "D0:03:4B" to "Apple",
        "D4:9A:20" to "Apple",
        "D8:30:62" to "Apple",
        "DC:2B:61" to "Apple",
        "E0:B9:4D" to "Apple",
        "E4:CE:8F" to "Apple",
        "EC:35:86" to "Xiaomi",
        "F0:76:6F" to "Apple",
        "F4:0F:24" to "Apple",
        "F8:1E:DF" to "Apple",
        "FC:18:3C" to "Apple",
        "44:AD:D9" to "Nintendo",
        "54:E1:AD" to "Nintendo",
        "E0:31:9E" to "Valve",
        "E4:17:D8" to "8BitDo",
        "D8:80:3C" to "Huami",
        "D0:8A:55" to "Skullcandy",
        "D4:72:08" to "Bragi",
        "D4:9C:28" to "Jaybird",
        "E0:D1:E6" to "Jawbone",
        "F0:3E:BF" to "Gogoro",
        "D4:EC:0C" to "Harley-Davidson",
        "D0:0E:A4" to "Porsche",
        "D0:B4:98" to "Bosch",
        "E4:2A:D3" to "Magneti Marelli",
        "D0:FA:1D" to "Qihoo 360",
        "E0:DA:DC" to "JVC Kenwood",
        "E4:C6:E6" to "Mophie"
    )

    private fun getVendor(mac: String): String {
        val cleanMac = mac.replace(":", "").uppercase()
        if (cleanMac.length < 6) return "Unknown"
        
        val oui6 = cleanMac.substring(0, 6)
        val masterMatch = ouiDatabase[oui6]

        if (masterMatch != null) {
            return masterMatch
        }
        
        val formattedOui = if (mac.contains(":")) {
            mac.substring(0, 8).uppercase()
        } else {
            // Re-format to XX:XX:XX
            "${cleanMac.substring(0, 2)}:${cleanMac.substring(2, 4)}:${cleanMac.substring(4, 6)}"
        }
        return vendorMap[formattedOui] ?: "Unknown"
    }

    private fun getDeviceTypeFromOui(mac: String): DeviceType? {
        val cleanMac = mac.replace(":", "").uppercase()
        if (cleanMac.length < 6) return null
        
        val oui6 = cleanMac.substring(0, 6)
        val mfrStr = (ouiDatabase[oui6] ?: "").lowercase()
        
        if (mfrStr.isEmpty()) return null

        return when {
            mfrStr.contains("mobile") || mfrStr.contains("apple") || mfrStr.contains("samsung") -> DeviceType.SMARTPHONE
            mfrStr.contains("tablet") || mfrStr.contains("ipad") -> DeviceType.TABLET
            mfrStr.contains("computer") || mfrStr.contains("laptop") || mfrStr.contains("desktop") || mfrStr.contains("dell") || mfrStr.contains("hp") || mfrStr.contains("lenovo") -> DeviceType.COMPUTER
            mfrStr.contains("router") || mfrStr.contains("network") || mfrStr.contains("cisco") || mfrStr.contains("tp-link") || mfrStr.contains("d-link") || mfrStr.contains("netgear") || mfrStr.contains("ubiquiti") -> DeviceType.ROUTER
            mfrStr.contains("printer") || mfrStr.contains("brother") || mfrStr.contains("epson") || mfrStr.contains("canon") -> DeviceType.PRINTER
            mfrStr.contains("tv") || mfrStr.contains("television") || mfrStr.contains("lg electronics") || mfrStr.contains("sony") || mfrStr.contains("vizio") -> DeviceType.TV
            mfrStr.contains("camera") || mfrStr.contains("video") || mfrStr.contains("hikvision") || mfrStr.contains("dahua") -> DeviceType.CAMERA
            mfrStr.contains("wearable") || mfrStr.contains("watch") || mfrStr.contains("fitbit") || mfrStr.contains("garmin") -> DeviceType.WEARABLE
            mfrStr.contains("audio") || mfrStr.contains("headphone") || mfrStr.contains("speaker") || mfrStr.contains("bose") || mfrStr.contains("jbl") || mfrStr.contains("skullcandy") -> DeviceType.HEADPHONES
            mfrStr.contains("car") || mfrStr.contains("vehicle") || mfrStr.contains("automotive") || mfrStr.contains("porsche") || mfrStr.contains("tesla") || mfrStr.contains("harley-davidson") || mfrStr.contains("ford") || mfrStr.contains("toyota") -> DeviceType.CAR
            mfrStr.contains("console") || mfrStr.contains("game") || mfrStr.contains("nintendo") || mfrStr.contains("playstation") || mfrStr.contains("valve") || mfrStr.contains("8bitdo") || mfrStr.contains("xbox") -> DeviceType.GAME_CONSOLE
            mfrStr.contains("iot") || mfrStr.contains("smart") || mfrStr.contains("tuya") || mfrStr.contains("espressif") -> DeviceType.IOT
            else -> null
        }
    }

    private fun detectDeviceType(name: String, address: String, defaultType: DeviceType): DeviceType {
        val lowerName = name.lowercase()
        val ouiType = getDeviceTypeFromOui(address)
        val vendor = getVendor(address).lowercase()

        return when {
            // Trackers
            lowerName.contains("airtag") || lowerName.contains("smarttag") || lowerName.contains("tile") || lowerName.contains("nut") -> DeviceType.TRACKER
            
            // Consoles
            lowerName.contains("nintendo") || lowerName.contains("switch") || lowerName.contains("playstation") || 
            lowerName.contains("ps4") || lowerName.contains("ps5") || lowerName.contains("xbox") || 
            lowerName.contains("steam deck") || lowerName.contains("8bitdo") || vendor.contains("nintendo") || 
            vendor.contains("valve") || vendor.contains("8bitdo") -> DeviceType.GAME_CONSOLE

            // Smartphones
            lowerName.contains("iphone") || lowerName.contains("galaxy") || lowerName.contains("pixel") || 
            lowerName.contains("android") || lowerName.contains("phone") || lowerName.contains("mobile") ||
            lowerName.contains("huawei") || lowerName.contains("xiaomi") || lowerName.contains("oneplus") ||
            lowerName.contains("oppo") || lowerName.contains("vivo") || lowerName.contains("realme") ||
            lowerName.contains("sm-") || lowerName.contains("motorola") || lowerName.contains("redmi") || 
            lowerName.contains("poco") || lowerName.contains("xperia") || lowerName.contains("moto ") -> DeviceType.SMARTPHONE

            // Headphones / Audio
            lowerName.contains("airpods") || lowerName.contains("buds") || lowerName.contains("headphone") || 
            lowerName.contains("audio") || lowerName.contains("beat") || lowerName.contains("wh-") || 
            lowerName.contains("wf-") || lowerName.contains("headset") || lowerName.contains("earphone") ||
            lowerName.contains("sony") || lowerName.contains("bose") || lowerName.contains("jbl") ||
            lowerName.contains("sennheiser") || lowerName.contains("jabra") || lowerName.contains("skullcandy") ||
            lowerName.contains("freebuds") || lowerName.contains("echo buds") || lowerName.contains("marshall") ||
            vendor.contains("skullcandy") || vendor.contains("bragi") || vendor.contains("jaybird") -> DeviceType.HEADPHONES

            // Computers / Tablets
            lowerName.contains("macbook") || lowerName.contains("laptop") || lowerName.contains("surface") || 
            lowerName.contains("desktop") || lowerName.contains("pc") || lowerName.contains("computer") -> DeviceType.COMPUTER
            lowerName.contains("ipad") || lowerName.contains("tablet") || lowerName.contains("tab") -> DeviceType.TABLET

            // OUI Type (if name didn't give a strong hint)
            ouiType != null -> ouiType

            // Vehicles
            lowerName.contains("tesla") || lowerName.contains("car") || lowerName.contains("sync") || 
            lowerName.contains("bmw") || lowerName.contains("ford") || lowerName.contains("toyota") || 
            lowerName.contains("honda") || lowerName.contains("audi") || lowerName.contains("mercedes") ||
            vendor.contains("porsche") || vendor.contains("harley-davidson") || vendor.contains("bosch") -> DeviceType.CAR
            
            // Drones
            lowerName.contains("dji") || lowerName.contains("drone") || lowerName.contains("mavic") || 
            lowerName.contains("phantom") || lowerName.contains("parrot") || vendor.contains("dji") -> DeviceType.DRONE
            
            // E-Scooters
            lowerName.contains("scooter") || lowerName.contains("mijia") || lowerName.contains("ninebot") || 
            lowerName.contains("segway") || vendor.contains("gogoro") -> DeviceType.ESCOOTER
            
            // Flipper Zero
            lowerName.contains("flipper") -> DeviceType.FLIPPER
            
            // TV
            lowerName.contains("tv") || lowerName.contains("television") || (lowerName.contains("samsung") && lowerName.contains("series")) -> DeviceType.TV

            // Default
            else -> defaultType
        }
    }

    private fun loadOuiDatabase() {
        ioExecutor?.execute {
            val cacheFile = File(context.cacheDir, "oui_database.json")
            val sevenDaysMillis = 7 * 24 * 60 * 60 * 1000L
            
            if (cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified() < sevenDaysMillis)) {
                try {
                    val body = cacheFile.readText()
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    ouiDatabase = gson.fromJson(body, type)
                    android.util.Log.d("ScannerManager", "Loaded ${ouiDatabase.size} OUI entries from local cache")
                    return@execute
                } catch (e: Exception) {
                    android.util.Log.e("ScannerManager", "Failed to load OUI from cache, falling back to network", e)
                }
            }

            val url = "https://gist.githubusercontent.com/hemantapkh/540326bd00a9d6b53efffe98733183e7/raw"
            val request = Request.Builder().url(url).build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@execute
                        val type = object : TypeToken<Map<String, List<String>>>() {}.type
                        val rawData: Map<String, List<String>> = gson.fromJson(body, type)
                        
                        val inverted = mutableMapOf<String, String>()
                        rawData.forEach { (manufacturer, ouis) ->
                            ouis.forEach { oui ->
                                inverted[oui.uppercase()] = manufacturer
                            }
                        }
                        ouiDatabase = inverted
                        
                        // Save to cache
                        cacheFile.writeText(gson.toJson(inverted))
                        
                        android.util.Log.d("ScannerManager", "Fetched and cached ${ouiDatabase.size} OUI entries from gist")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScannerManager", "Failed to fetch OUI database from network", e)
            }
        }
    }

    init {
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleScanner = bluetoothManager.adapter.bluetoothLeScanner
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        loadArchive()
        ioExecutor = Executors.newFixedThreadPool(4)
        loadOuiDatabase()
    }

    private fun loadArchive() {
        try {
            val prefs = context.getSharedPreferences("radar_archive", Context.MODE_PRIVATE)
            val json = prefs.getString("devices", null) ?: return
            val type = object : TypeToken<Map<String, ScanDevice>>() {}.type
            val loaded: Map<String, ScanDevice> = gson.fromJson(json, type)
            archiveMap.putAll(loaded)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var lastArchiveSaveTime = 0L
    private fun saveArchive() {
        val now = System.currentTimeMillis()
        if (now - lastArchiveSaveTime < 60000) return
        forceSaveArchive()
        lastArchiveSaveTime = now
    }

    fun forceSaveArchive() {
        val prefs = context.getSharedPreferences("radar_archive", Context.MODE_PRIVATE)
        val json = gson.toJson(archiveMap)
        prefs.edit().putString("devices", json).apply()
    }

    fun archiveCurrentDevices() {
        deviceMap.forEach { (id, device) ->
            archiveMap[id] = device
        }
        forceSaveArchive()
    }

    fun getArchivedDevices(): List<ScanDevice> = archiveMap.values.toList()

    private val locationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            notifyLocationUpdated(location.latitude, location.longitude)
            
            // Retroactively update devices that were waiting for GPS
            ioExecutor?.execute {
                deviceMap.values.forEach { device ->
                    if (device.capturedLat == null && !archiveMap.containsKey(device.id)) {
                        device.capturedLat = location.latitude
                        device.capturedLon = location.longitude
                        reverseGeocodeWifi(device)
                    }
                }
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent.action) {
                processWifiResults()
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processBleResult(result)
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { processBleResult(it) }
        }
        override fun onScanFailed(errorCode: Int) {
            notifyError("BLE Scan Failed: $errorCode")
        }
    }

    fun startScanning(background: Boolean = false) {
        if (scanning) return
        scanning = true
        isBackgroundMode = background
        if (ioExecutor == null || ioExecutor?.isShutdown == true) {
            ioExecutor = Executors.newFixedThreadPool(4)
        }
        
        startBleScanning()
        startWifiScanning()
        startCellScanning()
        startAircraftScanning()
        startLocationUpdates()
        schedulePeriodicUpdate()
        notifyScanStatusChanged(true)
    }

    private fun startAircraftScanning() {
        handler.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                fetchAircraftData()
                fetchCameraData()
                val interval = if (isBackgroundMode) 30000L else 5000L
                handler.postDelayed(this, interval)
            }
        })
    }

    private fun fetchCameraData() {
        val loc = lastLocation ?: return
        
        val radius = 1000 // 1km
        val query = """
            [out:json][timeout:25];
            (
              node["man_made"="surveillance"](around:$radius,${loc.latitude},${loc.longitude});
              way["man_made"="surveillance"](around:$radius,${loc.latitude},${loc.longitude});
              node["camera:type"](around:$radius,${loc.latitude},${loc.longitude});
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val url = "https://sunders.uber.space/cameras.php?lat=${loc.latitude}&lon=${loc.longitude}&radius=$radius"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "BLEWifiRadar/1.0")
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("ScannerManager", "Camera fetch failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return
                    val body = it.body?.string() ?: return
                    processCameraJson(body)
                }
            }
        })
    }

    private fun processCameraJson(json: String) {
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return
            
            val now = System.currentTimeMillis()
            val userLoc = lastLocation ?: return

            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val type = element.optString("type")
                if (type != "node") continue

                val camId = element.optLong("id")
                val deviceId = "CAM_$camId"
                val lat = element.optDouble("lat", Double.NaN)
                val lon = element.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val tags = element.optJSONObject("tags")
                val camType = tags?.optString("camera:type", "fixed") ?: "fixed"
                val operator = tags?.optString("operator", "Unknown")
                val name = tags?.optString("name", "CCTV ($camType)") ?: "CCTV ($camType)"

                val results = FloatArray(3)
                Location.distanceBetween(userLoc.latitude, userLoc.longitude, lat, lon, results)
                val distance = results[0].toDouble()
                val bearing = results[1].toDouble()

                val existing = deviceMap[deviceId]
                if (existing != null) {
                    existing.lastSeen = now
                    existing.distance = distance
                    existing.angle = bearing
                    if (existing.capturedLat == null && !archiveMap.containsKey(deviceId)) {
                        existing.capturedLat = lat
                        existing.capturedLon = lon
                        reverseGeocodeWifi(existing)
                    }
                } else {
                    val newDev = ScanDevice(
                        id = deviceId,
                        name = name,
                        type = DeviceType.CAMERA,
                        rssi = -70,
                        address = "OSM:$camId",
                        lat = lat,
                        lon = lon,
                        cameraType = camType,
                        manufacturer = operator ?: "",
                        angle = bearing,
                        distance = distance,
                        lastSeen = now
                    )
                    newDev.capturedLat = lat
                    newDev.capturedLon = lon
                    reverseGeocodeWifi(newDev)
                    deviceMap[deviceId] = newDev
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchAircraftData() {
        val loc = lastLocation
        if (loc == null) {
            android.util.Log.d("ScannerManager", "fetchAircraftData: lastLocation is null")
            return
        }
        val url = "https://api.adsb.lol/v2/lat/${loc.latitude}/lon/${loc.longitude}/dist/50" // 50km radius
        android.util.Log.d("ScannerManager", "fetching aircraft from: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "BLEWifiRadar/1.0")
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("ScannerManager", "Aircraft fetch failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        android.util.Log.e("ScannerManager", "Aircraft response unsuccessful: ${it.code}")
                        return
                    }
                    val body = it.body?.string() ?: return
                    android.util.Log.d("ScannerManager", "Aircraft response: ${body.take(100)}...")
                    processAircraftJson(body)
                }
            }
        })
    }

    private fun processAircraftJson(json: String) {
        try {
            val root = JSONObject(json)
            val acArray = root.optJSONArray("ac") ?: return
            
            val now = System.currentTimeMillis()
            val userLoc = lastLocation ?: return

            for (i in 0 until acArray.length()) {
                val ac = acArray.getJSONObject(i)
                val hex = ac.optString("hex", "")
                if (hex.isEmpty()) continue

                val lat = ac.optDouble("lat", Double.NaN)
                val lon = ac.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val flight = ac.optString("flight", "UFO").trim()
                val alt = ac.optDouble("alt_baro", 0.0).toFloat()
                val speed = ac.optDouble("gs", 0.0).toFloat()
                val track = ac.optDouble("track", 0.0).toFloat()
                val squawk = ac.optString("squawk", "")
                val origin = ac.optString("origin", "UNKNOWN")
                val dest = ac.optString("dest", "UNKNOWN")
                val country = ac.optString("r", "UNKNOWN")

                val results = FloatArray(3)
                Location.distanceBetween(userLoc.latitude, userLoc.longitude, lat, lon, results)
                val distance = results[0].toDouble()
                val bearing = results[1].toDouble()

                val id = "AC_$hex"
                val existing = deviceMap[id]
                if (existing != null) {
                    existing.lastSeen = now
                    existing.name = flight
                    existing.angle = bearing
                    existing.distance = distance
                    existing.lat = lat
                    existing.lon = lon
                    existing.altitude = alt
                    existing.speed = speed
                    existing.heading = track
                    existing.squawk = squawk
                    existing.origin = origin
                    existing.country = country
                    existing.destination = dest
                    if (existing.capturedLat == null && !archiveMap.containsKey(id)) {
                        existing.capturedLat = lat
                        existing.capturedLon = lon
                        reverseGeocodeWifi(existing)
                    }
                } else {
                    val newDev = ScanDevice(
                        id = id,
                        name = flight,
                        type = DeviceType.AIRCRAFT,
                        rssi = -50,
                        address = hex,
                        lat = lat,
                        lon = lon,
                        altitude = alt,
                        speed = speed,
                        heading = track,
                        squawk = squawk,
                        origin = origin,
                        country = country,
                        destination = dest,
                        angle = bearing,
                        distance = distance,
                        lastSeen = now
                    )
                    newDev.capturedLat = lat
                    newDev.capturedLon = lon
                    reverseGeocodeWifi(newDev)
                    deviceMap[id] = newDev
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCellScanning() {
        handler.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                triggerCellScan()
                handler.postDelayed(this, 10000)
            }
        })
    }

    private fun triggerCellScan() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        ioExecutor?.execute {
            processCellResults()
        }
    }

    private fun processCellResults() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            val allInfo = telephonyManager?.allCellInfo ?: return
            val now = System.currentTimeMillis()
            
            for (info in allInfo) {
                val id: String
                val type: DeviceType
                var rssi = -100
                var mcc: String? = null
                var mnc: String? = null
                var lac: Int? = null
                var cid: Long? = null
                var pci: Int? = null
                var arfcn: Int? = null
                
                when (info) {
                    is CellInfoLte -> {
                        val cellId = info.cellIdentity
                        val signal = info.cellSignalStrength
                        id = "LTE_${cellId.ci}_${cellId.pci}"
                        type = DeviceType.LTE
                        rssi = signal.dbm
                        mcc = cellId.mccString
                        mnc = cellId.mncString
                        lac = cellId.tac
                        cid = cellId.ci.toLong()
                        pci = cellId.pci
                        arfcn = cellId.earfcn
                    }
                    is CellInfoGsm -> {
                        val cellId = info.cellIdentity
                        val signal = info.cellSignalStrength
                        id = "GSM_${cellId.cid}"
                        type = DeviceType.LTE
                        rssi = signal.dbm
                        mcc = cellId.mccString
                        mnc = cellId.mncString
                        lac = cellId.lac
                        cid = cellId.cid.toLong()
                        arfcn = cellId.arfcn
                    }
                    is CellInfoWcdma -> {
                        val cellId = info.cellIdentity
                        val signal = info.cellSignalStrength
                        id = "WCDMA_${cellId.cid}"
                        type = DeviceType.LTE
                        rssi = signal.dbm
                        mcc = cellId.mccString
                        mnc = cellId.mncString
                        lac = cellId.lac
                        cid = cellId.cid.toLong()
                        pci = cellId.psc
                        arfcn = cellId.uarfcn
                    }
                    else -> continue
                }

                val existing = deviceMap[id]
                val currentLoc = lastLocation
                if (existing != null) {
                    existing.lastSeen = now
                    existing.rssi = rssi
                    if (existing.capturedLat == null && currentLoc != null && !archiveMap.containsKey(id)) {
                        existing.capturedLat = currentLoc.latitude
                        existing.capturedLon = currentLoc.longitude
                        reverseGeocodeWifi(existing)
                    }
                } else {
                    val newDev = ScanDevice(
                        id = id,
                        name = "Cell Tower",
                        type = type,
                        rssi = rssi,
                        address = id,
                        mcc = mcc,
                        mnc = mnc,
                        lac = lac,
                        cid = cid,
                        pci = pci,
                        arfcn = arfcn,
                        lastSeen = now
                    )
                    if (currentLoc != null && !archiveMap.containsKey(id)) {
                        newDev.capturedLat = currentLoc.latitude
                        newDev.capturedLon = currentLoc.longitude
                        reverseGeocodeWifi(newDev)
                    }
                    deviceMap[id] = newDev
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopScanning() {
        scanning = false
        stopBleScanning()
        context.unregisterReceiver(wifiReceiver)
        locationManager?.removeUpdates(locationListener)
        ioExecutor?.shutdown()
        notifyScanStatusChanged(false)
    }

    private fun startLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            locationManager?.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                2000L, 5f, locationListener
            )
            locationManager?.requestLocationUpdates(
                android.location.LocationManager.NETWORK_PROVIDER,
                5000L, 10f, locationListener
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startBleScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        performBleScan(ScanSettings.SCAN_MODE_LOW_LATENCY)
    }

    private fun performBleScan(mode: Int) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        val settings = ScanSettings.Builder().setScanMode(mode).build()
        try {
            bleScanner?.startScan(null, settings, bleScanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopBleScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        try {
            bleScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleNextBleScanCycle() {
        // Not used in this simplified version
    }

    private fun startWifiScanning() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiReceiver, filter)
        
        handler.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                triggerWifiScan()
                // Android throttles WiFi scans to 4 per 2 minutes for foreground apps.
                // 30 seconds ensures we stay within this limit.
                val interval = if (isBackgroundMode) 600000L else 30000L
                handler.postDelayed(this, interval)
            }
        })
    }

    private fun triggerWifiScan() {
        wifiManager?.startScan()
    }

    private fun schedulePeriodicUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                pruneStaleDevices()
                notifyDevicesUpdated(deviceMap.values.toList())
                saveArchive()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun processBleResult(result: ScanResult) {
        val now = System.currentTimeMillis()
        val device = result.device
        val scanRecord = result.scanRecord
        val address = device.address ?: return
        
        var name = result.device.name ?: scanRecord?.deviceName ?: ""
        val rssi = result.rssi
        
        // Check for specific device types based on scan record
        var type = DeviceType.BLE
        var isAirTag = false
        var isSkimmer = false
        
        scanRecord?.let { record ->
            // AirTag pattern: Manufacturer data starting with 0x4C 0x00 0x12 0x19
            record.getManufacturerSpecificData(0x004c)?.let { data ->
                if (data.size >= 2 && (data[0].toInt() and 0xFF) == 0x12 && (data[1].toInt() and 0xFF) == 0x19) {
                    isAirTag = true
                    name = "Apple AirTag"
                }
            }

            // Flipper Zero specific BLE Service UUID check
            record.serviceUuids?.forEach { uuid ->
                if (uuid.uuid.toString().lowercase().startsWith("00003080")) {
                    type = DeviceType.FLIPPER
                    if (name.isEmpty()) name = "Flipper Zero"
                }
            }
        }

        if (type == DeviceType.BLE) {
            type = detectDeviceType(name, address, DeviceType.BLE)
        }
        if (type == DeviceType.TRACKER) isAirTag = true

        val existing = deviceMap[address]
        val currentLoc = lastLocation
        if (existing != null) {
            existing.lastSeen = now
            existing.rssi = rssi
            if (name.isNotEmpty()) existing.name = name
            existing.seenCount++
            if (existing.vendor == null) existing.vendor = getVendor(address)
            // Update type if we found a better one
            if (type != DeviceType.BLE) {
                existing.type = type
            }
            if (existing.capturedLat == null && currentLoc != null && !archiveMap.containsKey(address)) {
                existing.capturedLat = currentLoc.latitude
                existing.capturedLon = currentLoc.longitude
                reverseGeocodeWifi(existing)
            }
        } else {
            val newDev = ScanDevice(
                id = address,
                name = if (name.isEmpty()) "BLE Device" else name,
                type = type,
                rssi = rssi,
                address = address,
                isAirTag = isAirTag,
                lastSeen = now
            )
            newDev.vendor = getVendor(address)
            if (currentLoc != null && !archiveMap.containsKey(address)) {
                newDev.capturedLat = currentLoc.latitude
                newDev.capturedLon = currentLoc.longitude
                reverseGeocodeWifi(newDev)
            }
            deviceMap[address] = newDev
        }
    }

    private fun processWifiResults() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val results = wifiManager?.scanResults ?: return
        val now = System.currentTimeMillis()
        val currentLoc = lastLocation

        for (res in results) {
            val id = res.BSSID
            val name = res.SSID
            val wifiType = detectDeviceType(name, id, DeviceType.WIFI)

            val existing = deviceMap[id]
            if (existing != null) {
                existing.lastSeen = now
                existing.rssi = res.level
                if (existing.vendor == null) existing.vendor = getVendor(id)
                if (wifiType != DeviceType.WIFI) existing.type = wifiType
                if (existing.capturedLat == null && currentLoc != null && !archiveMap.containsKey(id)) {
                    existing.capturedLat = currentLoc.latitude
                    existing.capturedLon = currentLoc.longitude
                    reverseGeocodeWifi(existing)
                }
            } else {
                val newDev = ScanDevice(
                    id = id,
                    name = name,
                    type = wifiType,
                    rssi = res.level,
                    address = id,
                    ssid = res.SSID,
                    capabilities = res.capabilities,
                    frequency = res.frequency,
                    channel = frequencyToChannel(res.frequency),
                    lastSeen = now
                )
                newDev.vendor = getVendor(id)
                if (currentLoc != null && !archiveMap.containsKey(id)) {
                    newDev.capturedLat = currentLoc.latitude
                    newDev.capturedLon = currentLoc.longitude
                    reverseGeocodeWifi(newDev)
                }
                // Trigger fingerprinting for new WiFi devices
                fingerprintDevice(newDev)
                deviceMap[id] = newDev
            }
        }
    }

    private fun reverseGeocodeWifi(device: ScanDevice) {
        val lat = device.capturedLat ?: return
        val lon = device.capturedLon ?: return
        ioExecutor?.execute {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lon, 1, object : android.location.Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: List<android.location.Address>) {
                            if (addresses.isNotEmpty()) {
                                val addr = addresses[0]
                                device.streetName = addr.thoroughfare ?: "Unknown Street"
                                device.houseNumber = addr.subThoroughfare ?: ""
                                device.cityName = addr.locality ?: addr.subAdminArea ?: "Unknown City"
                            }
                        }
                        override fun onError(errorMessage: String?) {}
                    })
                } else {
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        device.streetName = addr.thoroughfare ?: "Unknown Street"
                        device.houseNumber = addr.subThoroughfare ?: ""
                        device.cityName = addr.locality ?: addr.subAdminArea ?: "Unknown City"
                    }
                }
            } catch (e: Exception) {
                // No fallback to fake names if offline/no provider
            }
        }
    }

    private fun fingerprintDevice(device: ScanDevice) {
        if (device.type != DeviceType.WIFI) return
        
        // Simulating OS fingerprinting logic
        // In a real scenario, this would involve a background port scan
        ioExecutor?.execute {
            val address = device.address
            // Placeholder logic: Heuristics based on SSID or OUI if ports aren't available
            val vendor = device.vendor?.lowercase() ?: ""
            val name = device.name.lowercase()
            
            var os = when {
                vendor.contains("apple") -> "macOS/iOS"
                vendor.contains("microsoft") -> "Windows"
                vendor.contains("google") -> "Android"
                vendor.contains("samsung") -> "Android"
                vendor.contains("avm") || name.contains("fritz") -> "Fritz!OS (Router)"
                vendor.contains("synology") || name.contains("nas") -> "DiskStation Manager (Linux)"
                vendor.contains("brother") || vendor.contains("hp") || name.contains("printer") -> "Embedded RTOS (Printer)"
                else -> "Linux (Generic)"
            }
            
            // Randomize a bit for simulation if it's "Linux (Generic)"
            if (os == "Linux (Generic)") {
                val r = (0..3).random()
                os = when(r) {
                    0 -> "Linux 5.x Kernel"
                    1 -> "Embedded RouterOS"
                    2 -> "Android 14"
                    else -> "Linux (Generic)"
                }
            }
            
            device.osFingerprint = os
        }
    }

    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        val timeout = if (isBackgroundMode) 600000L else 30000L
        val it = deviceMap.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val device = entry.value
            
            // Sticky mode for tracked devices (alarms)
            if (isAlarmEnabled(device.id)) {
                if (now - device.lastSeen > 600000L) { // Keep tracked devices for 10 mins
                    archiveMap[entry.key] = device
                    it.remove()
                }
                continue
            }

            // Don't prune Cameras or Aircraft as quickly if they are from static data/API
            val deviceTimeout = when(device.type) {
                DeviceType.CAMERA -> 3600000L // 1 hour
                DeviceType.AIRCRAFT -> 60000L
                DeviceType.WIFI -> if (isBackgroundMode) 600000L else 65000L // WiFi scans are throttled
                else -> timeout
            }
            
            if (now - device.lastSeen > deviceTimeout) {
                archiveMap[entry.key] = device
                it.remove()
            }
        }
    }

    private fun incrementStat(prefs: android.content.SharedPreferences, key: String) {
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            else -> 0
        }
    }
}
