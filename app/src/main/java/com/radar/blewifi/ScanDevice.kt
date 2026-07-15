package com.radar.blewifi

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.pow

enum class DeviceType { BLE, WIFI, LTE, FIVE_G, AIRCRAFT, CAR, DRONE, ESCOOTER, TV, COMPUTER, SMARTPHONE, PAGER }

@Parcelize
data class ScanDevice(
    val id: String,           // MAC address or BSSID
    var name: String,
    val type: DeviceType,
    var rssi: Int,
    val address: String,
    var txPower: Int = -59, // Default TX power at 1m
    // WiFi specific
    val ssid: String = "",
    val capabilities: String = "",
    val frequency: Int = 0,
    val channel: Int = 0,
    // BLE specific
    val uuids: String = "",
    val manufacturer: String = "",
    // WhisperPair vulnerability
    val isVulnerableWhisperPair: Boolean = false,
    // Other vulnerabilities
    val isLegacyBluetooth: Boolean = false, // Bluetooth < 4.0
    val isPubliclyConnectable: Boolean = false,
    val isVulnerableBlueWhisper: Boolean = false,
    val isAirTag: Boolean = false,
    val isSkimmer: Boolean = false,
    val isFastPair: Boolean = false,
    val isVulnerableCVE202536911: Boolean = false,
    val isCar: Boolean = false,
    val isEscooter: Boolean = false,
    // Pager specific
    var lastMessage: String? = null,
    var lastMessageTime: Long = 0,
    // Radar position (angle 0–360, updated each scan cycle)
    var angle: Double = 0.0,
    var distance: Double = 0.0,
    var lastSeen: Long = System.currentTimeMillis(),
    var seenCount: Int = 1,
    // Aircraft specific
    var lat: Double? = null,
    var lon: Double? = null,
    var altitude: Float? = null,
    var speed: Float? = null,
    var heading: Float? = null,
    var squawk: String? = null,
    var origin: String? = null,
    var country: String? = null,
    var destination: String? = null,
    // Cell specific
    var mcc: String? = null,
    var mnc: String? = null,
    var lac: Int? = null, // or tac for LTE
    var cid: Long? = null, // cell id (ci for LTE, nci for NR)
    var pci: Int? = null,
    var arfcn: Int? = null,
    var band: String? = null
) : Parcelable {

    /** Estimated distance in metres using Log-Distance Path Loss Model or Lat/Lon */
    val distanceMeters: Double
        get() {
            if (type == DeviceType.AIRCRAFT) {
                return distance
            }
            // environmental factor (n): 2.0 for free space, 3.0 for indoor office
            val n = if (type == DeviceType.WIFI) 2.5 else 3.0
            
            // reference power at 1 meter
            val refPower = if (type == DeviceType.WIFI) -45.0 else txPower.toDouble()
            
            return 10.0.pow((refPower - rssi) / (10.0 * n))
        }

    val distanceLabel: String
        get() {
            val d = distanceMeters
            return when {
                d < 1000.0 -> "%.0f m".format(d)
                else -> "%.1f km".format(d / 1000.0)
            }
        }

    val signalStrength: String
        get() = when {
            rssi >= -50 -> "Excellent"
            rssi >= -65 -> "Good"
            rssi >= -75 -> "Fair"
            rssi >= -85 -> "Weak"
            else        -> "Very Weak"
        }

    val typeLabel: String get() = when(type) {
        DeviceType.BLE -> "BLE"
        DeviceType.WIFI -> "WiFi"
        DeviceType.AIRCRAFT -> "Aircraft"
        DeviceType.CAR -> "Car"
        DeviceType.DRONE -> "Drone"
        DeviceType.ESCOOTER -> "Escooter"
        DeviceType.TV -> "Smart TV"
        DeviceType.COMPUTER -> "Computer"
        DeviceType.SMARTPHONE -> "Smartphone"
        DeviceType.PAGER -> "Pager"
        else -> "Cell"
    }

    val displayName: String
        get() = name.ifBlank { if (type == DeviceType.WIFI) ssid else address.takeLast(8) }
}
