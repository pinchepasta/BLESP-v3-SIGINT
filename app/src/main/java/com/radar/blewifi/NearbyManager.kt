package com.radar.blewifi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import java.util.*

class NearbyManager(private val context: Context, private var myCallsign: String) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.radar.blewifi.PAGER_SERVICE"
    private val PAGER_UUID = UUID.fromString("0000B1E5-0000-1000-8000-00805F9B34FB")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val listeners = mutableListOf<NearbyListener>()

    fun updateMyCallsign(newName: String) {
        if (myCallsign != newName) {
            myCallsign = newName
            stop()
            start()
        }
    }

    fun addListener(listener: NearbyListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: NearbyListener) {
        listeners.remove(listener)
    }

    private fun notifyMessageReceived(sender: String, text: String) {
        listeners.forEach { it.onMessageReceived(sender, text) }
    }

    private fun notifyVoiceMessageReceived(sender: String, filePath: String) {
        listeners.forEach { it.onVoiceMessageReceived(sender, filePath) }
    }

    private fun notifyFileReceived(sender: String, filePath: String, fileName: String) {
        listeners.forEach { it.onFileReceived(sender, filePath, fileName) }
    }

    private fun notifyDeviceFound(callsign: String) {
        listeners.forEach { it.onDeviceFound(callsign) }
    }

    private fun notifyDeviceLost(callsign: String) {
        listeners.forEach { it.onDeviceLost(callsign) }
    }

    interface NearbyListener {
        fun onMessageReceived(senderCallsign: String, text: String)
        fun onVoiceMessageReceived(senderCallsign: String, filePath: String)
        fun onFileReceived(senderCallsign: String, filePath: String, fileName: String)
        fun onDeviceFound(callsign: String)
        fun onDeviceLost(callsign: String)
    }

    private val endpointToCallsign = mutableMapOf<String, String>()
    private val incomingPayloads = mutableMapOf<Long, Payload>()
    private val payloadMetadata = mutableMapOf<Long, String>()

    fun start() {
        startAdvertising()
        startDiscovery()
    }

    fun isConnected(targetCallsign: String): Boolean {
        return endpointToCallsign.values.contains(targetCallsign)
    }

    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(myCallsign, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { Log.d("Nearby", "Advertising started successfully") }
            .addOnFailureListener { Log.e("Nearby", "Adv failed: ${it.message}") }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { Log.d("Nearby", "Discovery started successfully") }
            .addOnFailureListener { Log.e("Nearby", "Disc failed: ${it.message}") }
    }

    fun sendMessage(targetCallsign: String, text: String) {
        // 1. Send via Google Nearby (Connected)
        val payload = Payload.fromBytes(text.toByteArray(Charsets.UTF_8))
        val endpoints = endpointToCallsign.filter { it.value == targetCallsign }.keys
        if (endpoints.isNotEmpty()) {
            connectionsClient.sendPayload(endpoints.toList(), payload)
        }

        // 2. Broadcast via BLE Advertising (Passive/Unconnected)
        // Format: @TARGET:MESSAGE
        broadcastViaBle("@$targetCallsign:$text")
    }

    fun sendVoiceMessage(targetCallsign: String, file: java.io.File) {
        val payload = Payload.fromFile(file)
        val endpoints = endpointToCallsign.filter { it.value == targetCallsign }.keys
        if (endpoints.isNotEmpty()) {
            val metadata = "METADATA:${payload.id}:${file.name}"
            connectionsClient.sendPayload(endpoints.toList(), Payload.fromBytes(metadata.toByteArray(Charsets.UTF_8)))
            connectionsClient.sendPayload(endpoints.toList(), payload)
        }
    }

    fun sendFile(targetCallsign: String, file: java.io.File) {
        val payload = Payload.fromFile(file)
        val endpoints = endpointToCallsign.filter { it.value == targetCallsign }.keys
        if (endpoints.isNotEmpty()) {
            val metadata = "METADATA:${payload.id}:${file.name}"
            connectionsClient.sendPayload(endpoints.toList(), Payload.fromBytes(metadata.toByteArray(Charsets.UTF_8)))
            connectionsClient.sendPayload(endpoints.toList(), payload)
        }
    }

    private var activeAdvertiseCallback: AdvertiseCallback? = null

    private fun broadcastViaBle(text: String) {
        val advertiser = advertiser ?: return
        
        // Stop previous broadcast if active
        activeAdvertiseCallback?.let { 
            try {
                advertiser.stopAdvertising(it)
            } catch (e: SecurityException) {
                Log.e("Nearby", "Stop advertising failed", e)
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(10000) // Broadcast for 10 seconds
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Include callsign and message in Service Data
        // Note: BLE Adv data is limited to 31 bytes. 
        // We'll broadcast the message text; ScannerManager will use the Device Name as the sender.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(PAGER_UUID))
            .addServiceData(ParcelUuid(PAGER_UUID), text.toByteArray(Charsets.UTF_8).take(20).toByteArray())
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("Nearby", "BLE Broadcast started: $text")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("Nearby", "BLE Broadcast failed: $errorCode")
            }
        }

        activeAdvertiseCallback = callback
        try {
            // Update device name to callsign so scanners see who sent it
            bluetoothAdapter?.name = myCallsign
            advertiser.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            Log.e("Nearby", "Missing permissions for BLE Advertise", e)
        }
    }

    fun broadcastMessage(text: String) {
        val payload = Payload.fromBytes(text.toByteArray(Charsets.UTF_8))
        if (endpointToCallsign.keys.isNotEmpty()) {
            connectionsClient.sendPayload(endpointToCallsign.keys.toList(), payload)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(myCallsign, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            val callsign = endpointToCallsign.remove(endpointId)
            callsign?.let { notifyDeviceLost(it) }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            endpointToCallsign[endpointId] = info.endpointName
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                endpointToCallsign[endpointId]?.let { notifyDeviceFound(it) }
            } else {
                endpointToCallsign.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val callsign = endpointToCallsign.remove(endpointId)
            callsign?.let { notifyDeviceLost(it) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val sender = endpointToCallsign[endpointId] ?: "Unknown"
            if (payload.type == Payload.Type.BYTES) {
                val text = String(payload.asBytes()!!, Charsets.UTF_8)
                if (text.startsWith("METADATA:")) {
                    val parts = text.split(":")
                    if (parts.size >= 3) {
                        val filePayloadId = parts[1].toLongOrNull()
                        val fileName = parts[2]
                        if (filePayloadId != null) {
                            payloadMetadata[filePayloadId] = fileName
                        }
                    }
                } else {
                    notifyMessageReceived(sender, text)
                }
            } else if (payload.type == Payload.Type.FILE) {
                incomingPayloads[payload.id] = payload
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val sender = endpointToCallsign[endpointId] ?: "Unknown"
                val payload = incomingPayloads.remove(update.payloadId)
                if (payload?.type == Payload.Type.FILE) {
                    val file = payload.asFile()?.asJavaFile()
                    if (file != null) {
                        val originalName = payloadMetadata.remove(update.payloadId) ?: file.name
                        // Always notify as file received; PagerActivity will detect if it's audio or image
                        notifyFileReceived(sender, file.absolutePath, originalName)
                    }
                }
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE || 
                       update.status == PayloadTransferUpdate.Status.CANCELED) {
                incomingPayloads.remove(update.payloadId)
                payloadMetadata.remove(update.payloadId)
            }
        }
    }
}
