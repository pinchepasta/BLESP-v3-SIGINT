package com.radar.blewifi

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Embedded Lightweight Wi-Fi REST & Stream API Server for BLESP Device.
 * Exposes full device state, live radar detection target list, control commands,
 * tactical pager messaging, background recording, alarms, and settings.
 */
class BlespApiServer(
    private val context: Context,
    private val port: Int = 8888
) {

    private val TAG = "BlespApiServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newCachedThreadPool()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val startTime = System.currentTimeMillis()

    private var audioRecording = false
    private var videoRecording = false

    fun start() {
        if (isRunning) return
        isRunning = true

        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "BLESP API Server started on port $port. IP: ${getIpAddress()}")

                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = serverSocket?.accept() ?: break
                    executor.execute { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Server error: ${e.message}", e)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server: ${e.message}")
        }
    }

    fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        if (sAddr.isNotEmpty() && sAddr.indexOf(':') < 0) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10000
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullUrl = parts[1]

            var contentLength = 0
            val headers = mutableMapOf<String, String>()

            var line: String?
            while (true) {
                line = input.readLine()
                if (line.isNullOrEmpty()) break
                val headerParts = line.split(":", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].trim().lowercase()] = headerParts[1].trim()
                    if (headerParts[0].equals("content-length", ignoreCase = true)) {
                        contentLength = headerParts[1].trim().toIntOrNull() ?: 0
                    }
                }
            }

            var body = ""
            if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                var bytesRead = 0
                while (bytesRead < contentLength) {
                    val read = input.read(charBuffer, bytesRead, contentLength - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }
                body = String(charBuffer, 0, bytesRead)
            }

            // Handle CORS OPTIONS preflight
            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "", "text/plain")
                socket.close()
                return
            }

            val uri = fullUrl.substringBefore("?")
            val queryParams = parseQueryParams(fullUrl)

            routeRequest(method, uri, queryParams, body, output, socket)

        } catch (e: Exception) {
            Log.e(TAG, "Client handler exception: ${e.message}")
        } finally {
            try {
                if (!socket.isClosed) socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!url.contains("?")) return map
        val query = url.substringAfter("?")
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = URLDecoder.decode(parts[1], "UTF-8")
            } else if (parts.size == 1) {
                map[parts[0]] = ""
            }
        }
        return map
    }

    private fun routeRequest(
        method: String,
        uri: String,
        query: Map<String, String>,
        body: String,
        output: OutputStream,
        socket: Socket
    ) {
        val app = context.applicationContext as? RadarApplication
        val scanner = app?.scanner

        when {
            // GET /api/v1/status
            method == "GET" && uri == "/api/v1/status" -> {
                val status = getSystemStatus(app)
                sendJsonResponse(output, 200, status)
            }

            // GET /api/v1/devices
            method == "GET" && uri == "/api/v1/devices" -> {
                val devices = scanner?.getDevices() ?: emptyList()
                val filtered = filterDevices(devices, query)
                sendJsonResponse(output, 200, mapOf(
                    "count" to filtered.size,
                    "total" to devices.size,
                    "devices" to filtered
                ))
            }

            // GET /api/v1/devices/{id}
            method == "GET" && uri.startsWith("/api/v1/devices/") -> {
                val id = uri.removePrefix("/api/v1/devices/")
                val device = scanner?.getDevices()?.firstOrNull { it.id == id || it.address == id }
                if (device != null) {
                    sendJsonResponse(output, 200, device)
                } else {
                    sendJsonResponse(output, 404, mapOf("error" to "Device not found with ID/Address: $id"))
                }
            }

            // POST /api/v1/scan/start
            method == "POST" && uri == "/api/v1/scan/start" -> {
                scanner?.startScanning(true)
                sendJsonResponse(output, 200, mapOf("status" to "ok", "scanning" to true))
            }

            // POST /api/v1/scan/stop
            method == "POST" && uri == "/api/v1/scan/stop" -> {
                scanner?.stopScanning()
                sendJsonResponse(output, 200, mapOf("status" to "ok", "scanning" to false))
            }

            // POST /api/v1/scan/toggle
            method == "POST" && uri == "/api/v1/scan/toggle" -> {
                val isScanning = scanner?.isScanning ?: false
                if (isScanning) {
                    scanner?.stopScanning()
                } else {
                    scanner?.startScanning(true)
                }
                sendJsonResponse(output, 200, mapOf("status" to "ok", "scanning" to !isScanning))
            }

            // GET /api/v1/archive
            method == "GET" && uri == "/api/v1/archive" -> {
                val archived = scanner?.getArchivedDevices() ?: emptyList()
                val filtered = filterDevices(archived, query)
                sendJsonResponse(output, 200, mapOf(
                    "count" to filtered.size,
                    "total" to archived.size,
                    "archived_devices" to filtered
                ))
            }

            // POST /api/v1/archive/save
            method == "POST" && uri == "/api/v1/archive/save" -> {
                scanner?.forceSaveArchive()
                sendJsonResponse(output, 200, mapOf("status" to "ok", "message" to "Archive saved successfully"))
            }

            // POST /api/v1/alarm
            method == "POST" && uri == "/api/v1/alarm" -> {
                val req = parseJson<Map<String, Any>>(body)
                val deviceId = req?.get("id")?.toString()
                val enabled = req?.get("enabled") as? Boolean ?: true

                if (deviceId.isNullOrEmpty()) {
                    sendJsonResponse(output, 400, mapOf("error" to "Missing device 'id' field"))
                } else {
                    scanner?.setAlarm(deviceId, enabled)
                    sendJsonResponse(output, 200, mapOf(
                        "status" to "ok",
                        "id" to deviceId,
                        "alarm_enabled" to enabled
                    ))
                }
            }

            // GET /api/v1/pager/messages
            method == "GET" && uri == "/api/v1/pager/messages" -> {
                val messages = getPagerHistory()
                sendJsonResponse(output, 200, mapOf("chats" to messages))
            }

            // POST /api/v1/pager/send
            method == "POST" && uri == "/api/v1/pager/send" -> {
                val req = parseJson<Map<String, String>>(body)
                val targetCallsign = req?.get("target") ?: "ALL"
                val text = req?.get("text") ?: ""

                if (text.isBlank()) {
                    sendJsonResponse(output, 400, mapOf("error" to "Message text cannot be blank"))
                } else {
                    app?.nearbyManager?.sendMessage(targetCallsign, text)
                    app?.recordSentMessage(if (targetCallsign.isNotBlank()) "@$targetCallsign:$text" else text)
                    sendJsonResponse(output, 200, mapOf(
                        "status" to "sent",
                        "target" to targetCallsign,
                        "text" to text
                    ))
                }
            }

            // POST /api/v1/record/audio
            method == "POST" && uri == "/api/v1/record/audio" -> {
                val req = parseJson<Map<String, String>>(body)
                val action = req?.get("action")?.lowercase() ?: "toggle"

                val intent = Intent(context, AudioRecordService::class.java)
                if (action == "start" || (action == "toggle" && !audioRecording)) {
                    intent.action = AudioRecordService.ACTION_START
                    context.startService(intent)
                    audioRecording = true
                } else {
                    intent.action = AudioRecordService.ACTION_STOP
                    context.startService(intent)
                    audioRecording = false
                }
                sendJsonResponse(output, 200, mapOf(
                    "status" to "ok",
                    "audio_recording" to audioRecording
                ))
            }

            // POST /api/v1/record/video
            method == "POST" && uri == "/api/v1/record/video" -> {
                val req = parseJson<Map<String, String>>(body)
                val action = req?.get("action")?.lowercase() ?: "toggle"

                val intent = Intent(context, VideoRecordService::class.java)
                if (action == "start" || (action == "toggle" && !videoRecording)) {
                    intent.action = VideoRecordService.ACTION_START
                    context.startService(intent)
                    videoRecording = true
                } else {
                    intent.action = VideoRecordService.ACTION_STOP
                    context.startService(intent)
                    videoRecording = false
                }
                sendJsonResponse(output, 200, mapOf(
                    "status" to "ok",
                    "video_recording" to videoRecording
                ))
            }

            // POST /api/v1/trigger/glitch
            method == "POST" && uri == "/api/v1/trigger/glitch" -> {
                context.sendBroadcast(Intent("com.radar.blewifi.ACTION_NEW_MESSAGE"))
                sendJsonResponse(output, 200, mapOf("status" to "ok", "message" to "Visual effect triggered"))
            }

            // GET /api/v1/config
            method == "GET" && uri == "/api/v1/config" -> {
                val prefsSettings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val prefsPager = context.getSharedPreferences("pager_prefs", Context.MODE_PRIVATE)
                sendJsonResponse(output, 200, mapOf(
                    "theme" to prefsSettings.getString("theme_name", "DEFAULT"),
                    "immersive_mode" to prefsSettings.getBoolean("immersive_mode", true),
                    "user_callsign" to prefsPager.getString("user_id", "UNIT_000")
                ))
            }

            // POST /api/v1/config/theme
            method == "POST" && uri == "/api/v1/config/theme" -> {
                val req = parseJson<Map<String, String>>(body)
                val newTheme = req?.get("theme")?.uppercase()
                if (newTheme.isNullOrEmpty()) {
                    sendJsonResponse(output, 400, mapOf("error" to "Missing 'theme' parameter"))
                } else {
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putString("theme_name", newTheme).apply()
                    sendJsonResponse(output, 200, mapOf("status" to "ok", "theme" to newTheme))
                }
            }

            // GET /api/v1/stream - Server-Sent Events (SSE) Live Stream
            method == "GET" && uri == "/api/v1/stream" -> {
                handleSseStream(output, socket, scanner)
            }

            else -> {
                sendJsonResponse(output, 404, mapOf(
                    "error" to "Endpoint not found",
                    "path" to uri,
                    "method" to method
                ))
            }
        }
    }

    private fun handleSseStream(output: OutputStream, socket: Socket, scanner: ScannerManager?) {
        val printWriter = PrintWriter(output, true)
        printWriter.print("HTTP/1.1 200 OK\r\n")
        printWriter.print("Content-Type: text/event-stream\r\n")
        printWriter.print("Cache-Control: no-cache\r\n")
        printWriter.print("Connection: keep-alive\r\n")
        printWriter.print("Access-Control-Allow-Origin: *\r\n\r\n")
        printWriter.flush()

        var lastDeviceCount = -1
        while (isRunning && !socket.isClosed) {
            try {
                val devices = scanner?.getDevices() ?: emptyList()
                if (devices.size != lastDeviceCount) {
                    lastDeviceCount = devices.size
                    val payload = gson.toJson(mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "count" to devices.size,
                        "devices" to devices
                    ))
                    printWriter.print("data: $payload\n\n")
                    printWriter.flush()
                }
                Thread.sleep(1000)
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun filterDevices(devices: List<ScanDevice>, query: Map<String, String>): List<ScanDevice> {
        var list = devices

        val typeFilter = query["type"]?.uppercase()
        if (!typeFilter.isNullOrEmpty()) {
            list = list.filter { it.type.name == typeFilter || it.typeLabel.equals(typeFilter, ignoreCase = true) }
        }

        val minRssi = query["min_rssi"]?.toIntOrNull()
        if (minRssi != null) {
            list = list.filter { it.rssi >= minRssi }
        }

        val search = query["search"]?.lowercase()
        if (!search.isNullOrEmpty()) {
            list = list.filter {
                it.displayName.lowercase().contains(search) ||
                it.address.lowercase().contains(search) ||
                it.ssid.lowercase().contains(search) ||
                (it.vendor?.lowercase()?.contains(search) == true)
            }
        }

        val limit = query["limit"]?.toIntOrNull()
        if (limit != null && limit > 0) {
            list = list.take(limit)
        }

        return list
    }

    private fun getSystemStatus(app: RadarApplication?): Map<String, Any?> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Float = if (scale > 0) level * 100 / scale.toFloat() else 0f

        val scanner = app?.scanner
        val loc = scanner?.lastLocation

        val prefsPager = context.getSharedPreferences("pager_prefs", Context.MODE_PRIVATE)
        val prefsSettings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        return mapOf(
            "app_name" to "BLESP Radar",
            "version" to "3.1.4",
            "uptime_seconds" to (System.currentTimeMillis() - startTime) / 1000,
            "ip_address" to getIpAddress(),
            "port" to port,
            "callsign" to prefsPager.getString("user_id", "UNIT_000"),
            "theme" to prefsSettings.getString("theme_name", "DEFAULT"),
            "battery_percent" to batteryPct.toInt(),
            "scanning_active" to (scanner?.isScanning ?: false),
            "target_count" to (scanner?.getDevices()?.size ?: 0),
            "archive_count" to (scanner?.getArchivedDevices()?.size ?: 0),
            "audio_recording" to audioRecording,
            "video_recording" to videoRecording,
            "location" to mapOf(
                "latitude" to loc?.latitude,
                "longitude" to loc?.longitude,
                "altitude" to loc?.altitude
            )
        )
    }

    private fun getPagerHistory(): Any {
        val prefs = context.getSharedPreferences("pager_history", Context.MODE_PRIVATE)
        val json = prefs.getString("all_messages", "{}") ?: "{}"
        return try {
            gson.fromJson(json, Any::class.java)
        } catch (_: Exception) {
            emptyMap<String, Any>()
        }
    }

    private inline fun <reified T> parseJson(json: String): T? {
        return try {
            gson.fromJson(json, object : TypeToken<T>() {}.type)
        } catch (_: Exception) {
            null
        }
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, bodyObj: Any) {
        val jsonString = gson.toJson(bodyObj)
        sendResponse(output, statusCode, getStatusMessage(statusCode), jsonString, "application/json")
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusMsg: String,
        body: String,
        contentType: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(output, false)
        writer.print("HTTP/1.1 $statusCode $statusMsg\r\n")
        writer.print("Content-Type: $contentType; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: Content-Type, Authorization\r\n\r\n")
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    private fun getStatusMessage(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "OK"
    }
}
