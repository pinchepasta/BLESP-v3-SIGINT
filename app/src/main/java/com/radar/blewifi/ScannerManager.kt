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
            listeners.forEach { it.onDevicesUpdated(list) }
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
    private var isBackgroundMode = false
    var lastLocation: Location? = null
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    init {
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleScanner = bluetoothManager.adapter.bluetoothLeScanner
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        loadArchive()
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

    fun getArchivedDevices(): List<ScanDevice> = archiveMap.values.toList()

    private val locationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            notifyLocationUpdated(location.latitude, location.longitude)
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
        ioExecutor = Executors.newFixedThreadPool(4)
        
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
                } else {
                    deviceMap[deviceId] = ScanDevice(
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
                } else {
                    deviceMap[id] = ScanDevice(
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
                if (existing != null) {
                    existing.lastSeen = now
                    existing.rssi = rssi
                } else {
                    deviceMap[id] = ScanDevice(
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
            val bytes = record.bytes
            // AirTag pattern: Manufacturer data starting with 0x4C 0x00 0x12 0x19
            record.getManufacturerSpecificData(0x004c)?.let { data ->
                if (data.size >= 2 && data[0].toInt() == 0x12 && data[1].toInt() == 0x19) {
                    isAirTag = true
                    name = "Apple AirTag"
                }
            }
        }

        val existing = deviceMap[address]
        if (existing != null) {
            existing.lastSeen = now
            existing.rssi = rssi
            if (name.isNotEmpty()) existing.name = name
            existing.seenCount++
        } else {
            deviceMap[address] = ScanDevice(
                id = address,
                name = if (name.isEmpty()) "BLE Device" else name,
                type = type,
                rssi = rssi,
                address = address,
                isAirTag = isAirTag,
                lastSeen = now
            )
        }
    }

    private fun processWifiResults() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val results = wifiManager?.scanResults ?: return
        val now = System.currentTimeMillis()

        for (res in results) {
            val id = res.BSSID
            val existing = deviceMap[id]
            if (existing != null) {
                existing.lastSeen = now
                existing.rssi = res.level
            } else {
                deviceMap[id] = ScanDevice(
                    id = id,
                    name = res.SSID,
                    type = DeviceType.WIFI,
                    rssi = res.level,
                    address = id,
                    ssid = res.SSID,
                    capabilities = res.capabilities,
                    frequency = res.frequency,
                    channel = frequencyToChannel(res.frequency),
                    lastSeen = now
                )
            }
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
