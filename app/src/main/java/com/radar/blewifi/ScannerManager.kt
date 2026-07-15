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

    fun isAlarmEnabled(deviceId: String) = alarmIds.contains(deviceId)

    fun getDevices() = deviceMap.values.toList()
    val deviceMap = java.util.concurrent.ConcurrentHashMap<String, ScanDevice>()
    private val archiveMap = java.util.concurrent.ConcurrentHashMap<String, ScanDevice>()
    private val handler   = Handler(Looper.getMainLooper())
    private val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var bleScanner: BluetoothLeScanner? = null
    private var wifiManager: WifiManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var locationManager: android.location.LocationManager? = null
    private var scanning = false
    private var isBackgroundMode = false
    var lastLocation: android.location.Location? = null
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    init {
        loadArchive()
    }

    private fun loadArchive() {
        try {
            val prefs = context.getSharedPreferences("scan_archive", Context.MODE_PRIVATE)
            val json = prefs.getString("archived_devices", null)
            if (json != null) {
                val type = object : TypeToken<Map<String, ScanDevice>>() {}.type
                val archived: Map<String, ScanDevice> = gson.fromJson(json, type)
                archiveMap.putAll(archived)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScannerManager", "Failed to load archive", e)
        }
    }

    private var lastArchiveSaveTime = 0L

    private fun saveArchive() {
        val now = System.currentTimeMillis()
        // Throttle saves to once every 15 seconds
        if (now - lastArchiveSaveTime < 15000) return
        lastArchiveSaveTime = now

        val archiveSnapshot = java.util.HashMap(archiveMap)
        ioExecutor.execute {
            try {
                val prefs = context.getSharedPreferences("scan_archive", Context.MODE_PRIVATE)
                val json = gson.toJson(archiveSnapshot)
                prefs.edit().putString("archived_devices", json).apply()
                android.util.Log.d("ScannerManager", "Archive saved (${archiveSnapshot.size} entries) on background thread")
            } catch (e: Exception) {
                android.util.Log.e("ScannerManager", "Failed to save archive", e)
            }
        }
    }

    fun forceSaveArchive() {
        lastArchiveSaveTime = 0L
        saveArchive()
    }

    fun getArchivedDevices(): List<ScanDevice> = archiveMap.values.toList().sortedByDescending { it.lastSeen }

    private val locationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
            lastLocation = location
            notifyLocationUpdated(location.latitude, location.longitude)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
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
            notifyError("BLE scan failed: error $errorCode")
        }
    }

    fun startScanning(isBackground: Boolean = false) {
        if (scanning) return
        scanning = true
        isBackgroundMode = isBackground
        notifyScanStatusChanged(true)
        startBleScanning()
        startWifiScanning()
        startCellScanning()
        startAircraftScanning()
        startLocationUpdates()
        schedulePeriodicUpdate()
    }

    private fun startAircraftScanning() {
        handler.post(object : Runnable {
            override fun run() {
                if (!scanning) return
                fetchAircraftData()
                val interval = if (isBackgroundMode) 30000L else 5000L
                handler.postDelayed(this, interval)
            }
        })
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
                // Silently fail or log
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
                
                // Get Country from API if available (usually 'ownOp' or 'r' fields in some ADS-B APIs, 
                // but let's look for common ones or derive from registration)
                val country = ac.optString("r", "UNKNOWN") // 'r' is often registration which starts with country prefix

                // Calculate distance and bearing from user
                val results = FloatArray(3)
                android.location.Location.distanceBetween(userLoc.latitude, userLoc.longitude, lat, lon, results)
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
                        rssi = -50, // Dummy
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
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return
        }
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        triggerCellScan()
    }

    private fun triggerCellScan() {
        if (!scanning) return
        processCellResults()
        // Cell scanning is relatively low power, 10s is fine
        handler.postDelayed({ triggerCellScan() }, 10000)
    }

    private fun processCellResults() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        
        val tm = telephonyManager ?: context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm == null) return

        val allCellInfo = try {
            tm.allCellInfo
        } catch (e: Exception) {
            null
        } ?: return

        for (info in allCellInfo) {
            if (info == null) continue
            try {
                var id: String? = null
                var type: DeviceType? = null
                var dbm = Int.MAX_VALUE
                var name = ""
                
                var mcc: String? = null
                var mnc: String? = null
                var lac: Int? = null
                var cid: Long? = null
                var pci: Int? = null
                var arfcn: Int? = null
                var band: String? = null

                when (info) {
                    is CellInfoLte -> {
                        val identity = info.cellIdentity
                        val lteCi = identity.ci
                        id = "LTE_${if (lteCi != Int.MAX_VALUE && lteCi != 0) lteCi else "PCI_${identity.pci}"}"
                        type = DeviceType.LTE
                        dbm = info.cellSignalStrength.dbm
                        name = "LTE Tower"
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            mcc = identity.mccString
                            mnc = identity.mncString
                        } else {
                            @Suppress("DEPRECATION")
                            mcc = if (identity.mcc != Int.MAX_VALUE) identity.mcc.toString() else null
                            @Suppress("DEPRECATION")
                            mnc = if (identity.mnc != Int.MAX_VALUE) identity.mnc.toString() else null
                        }
                        lac = identity.tac // Tracking Area Code
                        cid = if (lteCi != Int.MAX_VALUE) lteCi.toLong() else null
                        pci = if (identity.pci != Int.MAX_VALUE) identity.pci else null
                        arfcn = if (identity.earfcn != Int.MAX_VALUE) identity.earfcn else null
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            band = identity.bands.joinToString(",")
                        }
                    }
                    is CellInfoWcdma -> {
                        val identity = info.cellIdentity
                        id = "3G_${identity.cid}"
                        type = DeviceType.LTE
                        dbm = info.cellSignalStrength.dbm
                        name = "3G Tower"
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            mcc = identity.mccString
                            mnc = identity.mncString
                        } else {
                            @Suppress("DEPRECATION")
                            mcc = if (identity.mcc != Int.MAX_VALUE) identity.mcc.toString() else null
                            @Suppress("DEPRECATION")
                            mnc = if (identity.mnc != Int.MAX_VALUE) identity.mnc.toString() else null
                        }
                        lac = if (identity.lac != Int.MAX_VALUE) identity.lac else null
                        cid = if (identity.cid != Int.MAX_VALUE) identity.cid.toLong() else null
                        arfcn = if (identity.uarfcn != Int.MAX_VALUE) identity.uarfcn else null
                    }
                    is CellInfoGsm -> {
                        val identity = info.cellIdentity
                        id = "2G_${identity.cid}"
                        type = DeviceType.LTE
                        dbm = info.cellSignalStrength.dbm
                        name = "2G Tower"
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            mcc = identity.mccString
                            mnc = identity.mncString
                        } else {
                            @Suppress("DEPRECATION")
                            mcc = if (identity.mcc != Int.MAX_VALUE) identity.mcc.toString() else null
                            @Suppress("DEPRECATION")
                            mnc = if (identity.mnc != Int.MAX_VALUE) identity.mnc.toString() else null
                        }
                        lac = if (identity.lac != Int.MAX_VALUE) identity.lac else null
                        cid = if (identity.cid != Int.MAX_VALUE) identity.cid.toLong() else null
                        arfcn = if (identity.arfcn != Int.MAX_VALUE) identity.arfcn else null
                    }
                }

                // API 29+ for 5G
                if (id == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && info is CellInfoNr) {
                    val identity = info.cellIdentity as? CellIdentityNr
                    if (identity != null) {
                        val nci = identity.nci
                        id = "5G_${if (nci != Long.MAX_VALUE && nci != 0L) nci else "PCI_${identity.pci}"}"
                        type = DeviceType.FIVE_G
                        dbm = info.cellSignalStrength.dbm
                        name = "5G Tower"
                        
                        mcc = identity.mccString
                        mnc = identity.mncString
                        lac = if (identity.tac != Int.MAX_VALUE) identity.tac else null
                        cid = if (nci != Long.MAX_VALUE) nci else null
                        pci = if (identity.pci != Int.MAX_VALUE) identity.pci else null
                        arfcn = if (identity.nrarfcn != Int.MAX_VALUE) identity.nrarfcn else null
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            band = identity.bands.joinToString(",")
                        }
                    }
                }

                if (id != null && type != null && dbm != Int.MAX_VALUE && dbm != 0) {
                    val finalId = id
                    val finalType = type
                    val existing = deviceMap[finalId]
                    if (existing != null) {
                        existing.rssi = dbm
                        existing.lastSeen = System.currentTimeMillis()
                        existing.mcc = mcc
                        existing.mnc = mnc
                        existing.lac = lac
                        existing.cid = cid
                        existing.pci = pci
                        existing.arfcn = arfcn
                        existing.band = band
                    } else {
                        deviceMap[finalId] = ScanDevice(
                            id = finalId,
                            name = name,
                            type = finalType,
                            rssi = dbm,
                            address = finalId,
                            angle = ((finalId.hashCode() and 0x7FFFFFFF) % 360).toDouble(),
                            mcc = mcc,
                            mnc = mnc,
                            lac = lac,
                            cid = cid,
                            pci = pci,
                            arfcn = arfcn,
                            band = band
                        )
                    }
                }
            } catch (e: Exception) {
                // Skip individual cell failures
            }
        }
    }

    fun stopScanning() {
        scanning = false
        notifyScanStatusChanged(false)
        handler.removeCallbacksAndMessages(null)
        try { bleScanner?.stopScan(bleScanCallback) } catch (_: Exception) {}
        try { context.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
    }

    private fun startLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        
        // Initialize lastLocation with last known location to avoid delay in aircraft fetching
        try {
            val lastGps = locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            val lastNet = locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            lastLocation = lastGps ?: lastNet
        } catch (e: SecurityException) {}

        try {
            locationManager?.requestLocationUpdates(
                android.location.LocationManager.GPS_PROVIDER,
                1000L,
                0.5f,
                locationListener
            )
            locationManager?.requestLocationUpdates(
                android.location.LocationManager.NETWORK_PROVIDER,
                1000L,
                0.5f,
                locationListener
            )
        } catch (e: SecurityException) {
            notifyError("Location permission error")
        }
    }

    private fun startBleScanning() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            notifyError("Missing BLE permissions")
            return
        }
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter   = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            notifyError("Bluetooth not available or disabled")
            return
        }
        bleScanner = adapter.bluetoothLeScanner
        
        // Phase 1: Initial burst (Skip or shorten in background)
        val initialBurstMode = if (isBackgroundMode) ScanSettings.SCAN_MODE_BALANCED else ScanSettings.SCAN_MODE_LOW_LATENCY
        val burstDuration = if (isBackgroundMode) 2000L else 5000L
        
        performBleScan(initialBurstMode)
        
        handler.postDelayed({
            if (scanning) {
                stopBleScanning()
                // Phase 2: Switch to periodic short scans to save CPU/Battery
                scheduleNextBleScanCycle()
            }
        }, burstDuration)
    }

    private fun performBleScan(mode: Int) {
        if (!scanning) return
        val settings = ScanSettings.Builder()
            .setScanMode(mode)
            .setReportDelay(0)
            .build()
        try {
            bleScanner?.startScan(null, settings, bleScanCallback)
        } catch (e: Exception) {
            notifyError("BLE scan error: ${e.message}")
        }
    }

    private fun stopBleScanning() {
        try {
            bleScanner?.stopScan(bleScanCallback)
        } catch (e: Exception) {
            // Ignore stop errors
        }
    }

    private fun scheduleNextBleScanCycle() {
        if (!scanning) return
        
        // Increase delay between scans in background, but keep it snappy in foreground
        val waitInterval = if (isBackgroundMode) 3000L else 500L
        val scanDuration = if (isBackgroundMode) 3000L else 4000L
        val scanMode = if (isBackgroundMode) ScanSettings.SCAN_MODE_BALANCED else ScanSettings.SCAN_MODE_LOW_LATENCY

        handler.postDelayed({
            if (scanning) {
                performBleScan(scanMode)
                
                handler.postDelayed({
                    stopBleScanning()
                    scheduleNextBleScanCycle()
                }, scanDuration)
            }
        }, waitInterval)
    }

    private fun startWifiScanning() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            notifyError("Missing WiFi location permission")
            return
        }
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(wifiReceiver, filter)
        }
        triggerWifiScan()
    }

    private fun triggerWifiScan() {
        if (!scanning) return
        try {
            wifiManager?.startScan()
        } catch (_: Exception) {}
        
        // Significantly reduce WiFi scan frequency in background (Android has strict limits anyway)
        val nextScan = if (isBackgroundMode) 60000L else 5000L
        handler.postDelayed({ triggerWifiScan() }, nextScan)
    }

    private fun schedulePeriodicUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!scanning) return
                
                // Track scan time persistently
                val prefs = context.getSharedPreferences("scan_stats", Context.MODE_PRIVATE)
                val totalTime = prefs.getLong("total_scan_time_ms", 0)
                val tick = if (isBackgroundMode) 5000L else 1000L
                prefs.edit().putLong("total_scan_time_ms", totalTime + tick).apply()

                pruneStaleDevices()
                notifyDevicesUpdated(deviceMap.values.toList())
                handler.postDelayed(this, tick)
            }
        }, 1000)
    }

    private fun processBleResult(result: android.bluetooth.le.ScanResult) {
        val addr = result.device.address ?: return
        
        // Pager check - do this for both new and existing devices to get live messages
        val isPager = (result.scanRecord?.serviceUuids?.any { 
            it.uuid.toString().uppercase().startsWith("0000B1E5") 
        } ?: false) || (result.scanRecord?.serviceData?.keys?.any {
            it.uuid.toString().uppercase().startsWith("0000B1E5")
        } ?: false)

        var pagerMsg: String? = null
        if (isPager) {
            val pUuid = android.os.ParcelUuid.fromString("0000B1E5-0000-1000-8000-00805F9B34FB")
            result.scanRecord?.getServiceData(pUuid)?.let { data ->
                pagerMsg = String(data, Charsets.UTF_8)
            }
        }

        val existing = deviceMap[addr]
        if (existing != null) {
            val oldRssi = existing.rssi
            existing.rssi = result.rssi
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val tx = result.txPower
                if (tx != 127) existing.txPower = tx
            }
            existing.lastSeen = System.currentTimeMillis()
            existing.seenCount++
            
            // Update pager message if new one arrived
            if (isPager && pagerMsg != null) {
                // If it's a pager, we prioritize the name in the scan record
                val scanName = result.scanRecord?.deviceName
                if (!scanName.isNullOrEmpty()) {
                    existing.name = scanName
                }

                val isNewContent = pagerMsg != existing.lastMessage
                val isOldEnough = (System.currentTimeMillis() - existing.lastMessageTime) > 3000 // Reduced from 5s for better responsiveness
                
                if (isNewContent || isOldEnough) {
                    existing.lastMessage = pagerMsg
                    existing.lastMessageTime = System.currentTimeMillis()
                    // Notify immediately for pager messages to ensure realtime feel
                    notifyDevicesUpdated(deviceMap.values.toList())
                }
            }

            if (alarmIds.contains(addr) && Math.abs(oldRssi - result.rssi) > 8) {
                notifyMovementDetected(existing)
            }
            
            // Update name if it was previously unknown
            if (existing.name.isEmpty() && !result.device.name.isNullOrEmpty()) {
                existing.name = result.device.name!!
            }
            return
        }

        // Only do heavy parsing for NEW devices to save CPU
        val device = result.device
        val name   = device.name ?: ""
        val rssi   = result.rssi
        val mfr    = result.scanRecord?.manufacturerSpecificData?.let { data ->
            if (data.size() > 0) "0x%04X".format(data.keyAt(0)) else ""
        } ?: ""
        val uuids  = result.scanRecord?.serviceUuids?.joinToString(", ") { it.uuid.toString().take(8) } ?: ""
        val isWhisperPair = result.scanRecord?.serviceUuids?.any { 
            it.uuid.toString().uppercase().contains("0000FE2C") 
        } ?: false
        
        val isBlueWhisper = result.scanRecord?.advertiseFlags == 0x01
        val isLegacy = result.device.type == android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC
        val isConnectable = result.isConnectable
        
        val sData = result.scanRecord?.getServiceData(android.os.ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"))
        val isFastPair = sData != null

        val isVulnerableCVE = (mfr == "0x004C" && isLegacy) || (mfr == "0x0059" && !isConnectable)

        val txPower = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            result.txPower.takeIf { it != 127 } ?: -59
        } else {
            -59
        }

        val mData = result.scanRecord?.getManufacturerSpecificData(0x004C)
        val isAirTag = mData != null && mData.size >= 2 && mData[0] == 0x12.toByte() && mData[1] == 0x19.toByte()

        val isSkimmer = name.uppercase().contains("HC-05") || 
                        name.uppercase().contains("HC-06") ||
                        uuids.uppercase().contains("00001101")

        val isCar = name.uppercase().contains("CAR") || 
                    name.uppercase().contains("AUTO") || 
                    name.uppercase().contains("TESLA") ||
                    name.uppercase().contains("BMW") ||
                    name.uppercase().contains("AUDI") ||
                    name.uppercase().contains("SYNC") || // Ford
                    name.uppercase().contains("MY MINI") ||
                    name.uppercase().contains("VOLVO") ||
                    name.uppercase().contains("MERCEDES") ||
                    name.uppercase().contains("TOYOTA") ||
                    name.uppercase().contains("MAZDA") ||
                    name.uppercase().contains("CHEVROLET") ||
                    name.uppercase().contains("UCONNECT") || // Jeep/Chrysler
                    name.uppercase().contains("INFOTAINMENT") ||
                    name.uppercase().contains("BT_CAR") ||
                    name.uppercase().contains("KIA") ||
                    name.uppercase().contains("HYUNDAI") ||
                    name.uppercase().contains("HONDA") ||
                    name.uppercase().contains("NISSAN") ||
                    name.uppercase().contains("SUBARU") ||
                    name.uppercase().contains("VW") ||
                    name.uppercase().contains("VOLKSWAGEN") ||
                    name.uppercase().contains("PORSCHE") ||
                    name.uppercase().contains("LEXUS") ||
                    name.uppercase().contains("RIVIAN") ||
                    name.uppercase().contains("LUCID") ||
                    name.uppercase().contains("CARPLAY") ||
                    name.uppercase().contains("HANDS-FREE") ||
                    name.uppercase().contains("HFP") ||
                    name.uppercase().contains("MY_CAR") ||
                    name.uppercase().contains("MB BLUETOOTH") ||
                    name.uppercase().contains("BT-CAR") ||
                    name.uppercase().contains("CAR-BT") ||
                    name.uppercase().contains("MYCAR")

        val isDrone = name.uppercase().contains("DJI") ||
                      name.uppercase().contains("MAVIC") ||
                      name.uppercase().contains("PHANTOM") ||
                      name.uppercase().contains("AVATA") ||
                      name.uppercase().contains("DRONE") ||
                      name.uppercase().contains("PARROT") ||
                      name.uppercase().contains("ANAFI") ||
                      name.uppercase().contains("AUTEL") ||
                      name.uppercase().contains("SKYDIO") ||
                      name.uppercase().contains("TELLO") ||
                      name.uppercase().contains("FIMI") ||
                      name.uppercase().contains("HUBSAN") ||
                      name.uppercase().contains("PIXHAWK") ||
                      name.uppercase().contains("ARDUPILOT") ||
                      name.uppercase().contains("MAVLINK")

        val isEscooter = name.uppercase().contains("SCOOTER") || 
                         name.uppercase().contains("XIAOMI") || 
                         name.uppercase().contains("NINEBOT") ||
                         name.uppercase().contains("SEGWAY") ||
                         name.uppercase().contains("M365") ||
                         name.uppercase().contains("LIME") ||
                         name.uppercase().contains("BIRD") ||
                         name.uppercase().contains("TIER") ||
                         name.uppercase().contains("VOI") ||
                         name.uppercase().contains("DOTT") ||
                         name.uppercase().contains("BEAM") ||
                         name.uppercase().contains("CIRC") ||
                         name.uppercase().contains("FLASH") ||
                         name.uppercase().contains("WIND")

        val isTV = name.uppercase().contains("TV") || 
                   name.uppercase().contains("BRAVIA") || 
                   name.uppercase().contains("VIZIO") || 
                   name.uppercase().contains("SAMSUNG") || 
                   name.uppercase().contains("LG") || 
                   name.uppercase().contains("ROKU") || 
                   name.uppercase().contains("FIRETV")

        val isComputer = name.uppercase().contains("LAPTOP") || 
                         name.uppercase().contains("DESKTOP") || 
                         name.uppercase().contains("MACBOOK") || 
                         name.uppercase().contains("WINDOWS") || 
                         name.uppercase().contains("LINUX") || 
                         name.uppercase().contains("SURFACE")

        val isSmartphone = name.uppercase().contains("PHONE") || 
                           name.uppercase().contains("IPHONE") || 
                           name.uppercase().contains("ANDROID") || 
                           name.uppercase().contains("GALAXY") || 
                           name.uppercase().contains("PIXEL")

        val newDevice = ScanDevice(
            id           = addr,
            name         = if (isPager) name.ifEmpty { "PAGER_UNIT" } else if (isAirTag && name.isEmpty()) "AirTag" else name,
            type         = when {
                isPager -> DeviceType.PAGER
                isDrone -> DeviceType.DRONE
                isCar -> DeviceType.CAR
                isEscooter -> DeviceType.ESCOOTER
                isTV -> DeviceType.TV
                isComputer -> DeviceType.COMPUTER
                isSmartphone -> DeviceType.SMARTPHONE
                else -> DeviceType.BLE
            },
            rssi         = rssi,
            address      = addr,
            txPower      = txPower,
            manufacturer = mfr,
            uuids        = uuids,
            isVulnerableWhisperPair = isWhisperPair,
            isVulnerableBlueWhisper = isBlueWhisper,
            isLegacyBluetooth = isLegacy,
            isPubliclyConnectable = isConnectable,
            isAirTag     = isAirTag,
            isSkimmer    = isSkimmer,
            isFastPair   = isFastPair,
            isVulnerableCVE202536911 = isVulnerableCVE,
            isCar        = isCar,
            isEscooter   = isEscooter,
            angle        = ((addr.hashCode() and 0x7FFFFFFF) % 360).toDouble(),
            lastMessage  = pagerMsg,
            lastMessageTime = if (pagerMsg != null) System.currentTimeMillis() else 0
        )
        deviceMap[addr] = newDevice
    }

    private fun processWifiResults() {
        val results = try { wifiManager?.scanResults } catch (_: Exception) { null } ?: return
        results.forEach { r: android.net.wifi.ScanResult ->
            val bssid = r.BSSID ?: return@forEach
            val ssid  = r.SSID.ifBlank { "Hidden Network" }
            val freq  = r.frequency
            val ch    = frequencyToChannel(freq)
            val caps  = r.capabilities ?: ""

            // Detection of WiFi Pagers (ID encoded in SSID: PGR_[NAME]_[MSG])
            val isWifiPager = ssid.startsWith("PGR_")
            var wifiPagerName = ""
            var wifiPagerMsg: String? = null
            
            if (isWifiPager) {
                val parts = ssid.split("_")
                if (parts.size >= 2) wifiPagerName = parts[1]
                if (parts.size >= 3) wifiPagerMsg = parts.drop(2).joinToString("_")
            }

            val isCar = !isWifiPager && (ssid.uppercase().contains("CAR") || 
                        ssid.uppercase().contains("AUTO") || 
                        ssid.uppercase().contains("TESLA") ||
                        ssid.uppercase().contains("BMW") ||
                        ssid.uppercase().contains("AUDI") ||
                        ssid.uppercase().contains("SYNC") ||
                        ssid.uppercase().contains("UCONNECT") ||
                        ssid.uppercase().contains("INFOTAINMENT") ||
                        ssid.uppercase().contains("FORD") ||
                        ssid.uppercase().contains("TOYOTA") ||
                        ssid.uppercase().contains("HYUNDAI") ||
                        ssid.uppercase().contains("VW_") ||
                        ssid.uppercase().contains("HONDA") ||
                        ssid.uppercase().contains("MYCAR") ||
                        ssid.uppercase().contains("CAR_WIFI") ||
                        ssid.uppercase().contains("VOLVO"))

            val isDrone = ssid.uppercase().contains("DJI") ||
                          ssid.uppercase().contains("MAVIC") ||
                          ssid.uppercase().contains("PHANTOM") ||
                          ssid.uppercase().contains("SPARK") ||
                          ssid.uppercase().contains("TELLO") ||
                          ssid.uppercase().contains("DRONE") ||
                          ssid.uppercase().contains("PARROT") ||
                          ssid.uppercase().contains("BEBOP") ||
                          ssid.uppercase().contains("ANAFI") ||
                          ssid.uppercase().contains("AUTEL") ||
                          ssid.uppercase().contains("SKY-HERO")

            val isEscooter = ssid.uppercase().contains("SCOOTER") || 
                             ssid.uppercase().contains("LIME") || 
                             ssid.uppercase().contains("BIRD")

            val isTV = ssid.uppercase().contains("TV") || 
                       ssid.uppercase().contains("BRAVIA") || 
                       ssid.uppercase().contains("VIZIO") || 
                       ssid.uppercase().contains("SAMSUNG") || 
                       ssid.uppercase().contains("LG") || 
                       ssid.uppercase().contains("ROKU") || 
                       ssid.uppercase().contains("FIRETV")

            val isComputer = ssid.uppercase().contains("LAPTOP") || 
                             ssid.uppercase().contains("DESKTOP") || 
                             ssid.uppercase().contains("MACBOOK") || 
                             ssid.uppercase().contains("WINDOWS") || 
                             ssid.uppercase().contains("LINUX") || 
                             ssid.uppercase().contains("SURFACE")

            val isSmartphone = ssid.uppercase().contains("PHONE") || 
                               ssid.uppercase().contains("IPHONE") || 
                               ssid.uppercase().contains("ANDROID") || 
                               ssid.uppercase().contains("GALAXY") || 
                               ssid.uppercase().contains("PIXEL")

            val existing = deviceMap[bssid]
            if (existing != null) {
                val oldRssi = existing.rssi
                existing.rssi    = r.level
                existing.lastSeen = System.currentTimeMillis()
                existing.seenCount++

                if (isWifiPager) {
                    existing.name = wifiPagerName
                    if (wifiPagerMsg != null && wifiPagerMsg != existing.lastMessage) {
                        existing.lastMessage = wifiPagerMsg
                        existing.lastMessageTime = System.currentTimeMillis()
                        notifyDevicesUpdated(deviceMap.values.toList())
                    }
                }
                
                if (alarmIds.contains(bssid) && Math.abs(oldRssi - r.level) > 8) {
                    notifyMovementDetected(existing)
                }
            } else {
                deviceMap[bssid] = ScanDevice(
                    id           = bssid,
                    name         = if (isWifiPager) wifiPagerName else ssid,
                    type         = when {
                        isWifiPager -> DeviceType.PAGER
                        isDrone -> DeviceType.DRONE
                        isCar -> DeviceType.CAR
                        isEscooter -> DeviceType.ESCOOTER
                        isTV -> DeviceType.TV
                        isComputer -> DeviceType.COMPUTER
                        isSmartphone -> DeviceType.SMARTPHONE
                        else -> DeviceType.WIFI
                    },
                    rssi         = r.level,
                    address      = bssid,
                    ssid         = ssid,
                    capabilities = caps,
                    frequency    = freq,
                    channel      = ch,
                    isCar        = isCar,
                    isEscooter   = isEscooter,
                    angle        = ((bssid.hashCode() and 0x7FFFFFFF) % 360).toDouble(),
                    lastMessage  = wifiPagerMsg,
                    lastMessageTime = if (wifiPagerMsg != null) System.currentTimeMillis() else 0
                )
            }
        }
    }

    private fun pruneStaleDevices() {
        val now  = System.currentTimeMillis()
        val iter = deviceMap.entries.iterator()
        
        // Persistently track total unique devices discovered
        val prefs = context.getSharedPreferences("scan_stats", Context.MODE_PRIVATE)
        val discoveredDeviceIds = prefs.getStringSet("discovered_device_ids", mutableSetOf()) ?: mutableSetOf()
        val currentIds = discoveredDeviceIds.toMutableSet()
        var changed = false
        var archiveChanged = false

        while (iter.hasNext()) {
            val entry = iter.next()
            val device = entry.value
            
            // Add or Update in archive
            val archived = archiveMap[device.id]
            if (archived == null) {
                archiveMap[device.id] = device.copy()
                archiveChanged = true
            } else {
                // Update fields in archive
                archived.lastSeen = device.lastSeen
                archived.rssi = device.rssi
                if (device.name.isNotEmpty() && archived.name.isEmpty()) {
                    archived.name = device.name
                    archiveChanged = true
                }
                // Always mark changed if it's a new "session" or just periodic update
                // We'll let the throttled save handle the frequency
                archiveChanged = true
            }

            if (!currentIds.contains(device.id)) {
                currentIds.add(device.id)
                changed = true
                
                // Track category-specific totals
                when (device.type) {
                    DeviceType.BLE -> incrementStat(prefs, "total_ble")
                    DeviceType.WIFI -> incrementStat(prefs, "total_wifi")
                    DeviceType.AIRCRAFT -> incrementStat(prefs, "total_aircraft")
                    DeviceType.CAR -> incrementStat(prefs, "total_car")
                    DeviceType.DRONE -> incrementStat(prefs, "total_drone")
                    DeviceType.TV -> incrementStat(prefs, "total_tv")
                    DeviceType.COMPUTER -> incrementStat(prefs, "total_computer")
                    DeviceType.SMARTPHONE -> incrementStat(prefs, "total_smartphone")
                    DeviceType.PAGER -> incrementStat(prefs, "total_pager")
                    else -> {}
                }
                
                if (device.isAirTag) incrementStat(prefs, "total_airtag")
                if (device.isVulnerableWhisperPair) incrementStat(prefs, "total_whisper")
                if (device.type == DeviceType.WIFI) {
                    if (device.capabilities.contains("WEP")) incrementStat(prefs, "total_wep")
                    if (device.capabilities.isEmpty() || (device.capabilities.contains("[ESS]") && !device.capabilities.contains("WPA"))) incrementStat(prefs, "total_open")
                }
            }

            val timeout = if (device.type == DeviceType.PAGER) 120_000L else 15_000L
            if (now - device.lastSeen > timeout) iter.remove()
        }
        
        // Cap archiveMap to 2000 entries to prevent OOM, keeping most recent
        if (archiveMap.size > 2000) {
            val sortedEntries = archiveMap.entries.toList().sortedBy { it.value.lastSeen }
            val toRemove = archiveMap.size - 2000
            for (i in 0 until toRemove) {
                archiveMap.remove(sortedEntries[i].key)
            }
            archiveChanged = true
        }

        if (changed) {
            prefs.edit().putStringSet("discovered_device_ids", currentIds).apply()
        }
        if (archiveChanged) {
            saveArchive()
        }
    }

    private fun incrementStat(prefs: android.content.SharedPreferences, key: String) {
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5180..5825 -> (freq - 5180) / 5 + 36
        else               -> 0
    }
}
