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
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
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
    private val executor = Executors.newFixedThreadPool(4)
    private val gson: Gson = Gson()
    private val startTime = System.currentTimeMillis()

    private var audioRecording = false
    private var videoRecording = false

    fun start() {
        if (isRunning) return
        isRunning = true

        executor.execute {
            try {
                serverSocket = ServerSocket(8888, 50, InetAddress.getByName("0.0.0.0"))
                Log.i(TAG, "BLESP API Server successfully bound to all interfaces on port 8888. IP: ${getIpAddress()}")

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
            // GET / or /index.html - Embedded Web Dashboard App (Must be first or explicitly checked early)
            method == "GET" && (uri == "/" || uri == "/index.html" || uri.isBlank()) -> {
                sendResponse(output, 200, "OK", getDashboardHtml(), "text/html")
            }

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
        val printWriter = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
        printWriter.write("HTTP/1.1 200 OK\r\n")
        printWriter.write("Content-Type: text/event-stream\r\n")
        printWriter.write("Cache-Control: no-cache\r\n")
        printWriter.write("Connection: keep-alive\r\n")
        printWriter.write("Access-Control-Allow-Origin: *\r\n\r\n")
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
                    printWriter.write("data: $payload\n\n")
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
        val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
        writer.write("HTTP/1.1 $statusCode $statusMsg\r\n")
        writer.write("Content-Type: $contentType; charset=utf-8\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n")
        writer.write("Access-Control-Allow-Origin: *\r\n")
        writer.write("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.write("Access-Control-Allow-Headers: Content-Type, Authorization\r\n\r\n")
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

    private fun getDashboardHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BLESP TACTICAL HUB</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css" rel="stylesheet">
    <style>
        @import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&display=swap');
        body { font-family: 'JetBrains+Mono', monospace; background-color: #050505; color: #00ff41; }
        .cyber-grid { background-image: linear-gradient(rgba(0, 255, 65, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(0, 255, 65, 0.05) 1px, transparent 1px); background-size: 30px 30px; }
        .hud-card { background: rgba(10, 10, 10, 0.8); border: 1px solid rgba(0, 255, 65, 0.2); box-shadow: 0 0 15px rgba(0, 255, 65, 0.1); }
        .btn-action { background: rgba(0, 255, 65, 0.1); border: 1px solid #00ff41; color: #00ff41; transition: all 0.3s; }
        .btn-action:hover { background: #00ff41; color: #000; box-shadow: 0 0 20px #00ff41; }
        .btn-danger { background: rgba(255, 0, 0, 0.1); border: 1px solid #ff0000; color: #ff0000; }
        .btn-danger:hover { background: #ff0000; color: #000; box-shadow: 0 0 20px #ff0000; }
        .status-pulse { animation: pulse 2s infinite; }
        @keyframes pulse { 0% { opacity: 1; } 50% { opacity: 0.3; } 100% { opacity: 1; } }
    </style>
</head>
<body class="cyber-grid min-h-screen p-4">
    <header class="flex flex-col md:flex-row justify-between items-center mb-6 p-4 hud-card rounded-lg">
        <div class="flex items-center space-x-4">
            <div class="w-12 h-12 rounded-full border-2 border-green-500 flex items-center justify-center status-pulse">
                <i class="fas fa-radar text-2xl"></i>
            </div>
            <div>
                <h1 class="text-2xl font-bold tracking-widest">BLESP TACTICAL HUB</h1>
                <p id="system-uptime" class="text-xs text-green-700">UPTIME: 00:00:00</p>
            </div>
        </div>
        <div class="flex space-x-6 mt-4 md:mt-0 text-sm">
            <div class="text-center"><p class="text-green-700">CALLSIGN</p><p id="stat-callsign" class="font-bold">-</p></div>
            <div class="text-center"><p class="text-green-700">BATTERY</p><p id="stat-battery" class="font-bold">- %</p></div>
            <div class="text-center"><p class="text-green-700">TARGETS</p><p id="stat-targets" class="font-bold">-</p></div>
        </div>
    </header>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-6">
            <div class="hud-card p-4 rounded-lg">
                <div class="flex justify-between items-center mb-4 border-b border-green-900 pb-2">
                    <h2 class="text-lg font-bold"><i class="fas fa-satellite-dish mr-2"></i>LIVE RADAR FEED</h2>
                    <div class="flex space-x-2">
                        <button onclick="apiCall('/api/v1/scan/toggle', 'POST')" class="px-3 py-1 btn-action rounded text-xs">TOGGLE SCAN</button>
                        <button onclick="apiCall('/api/v1/archive/save', 'POST')" class="px-3 py-1 btn-action rounded text-xs">SAVE ARCHIVE</button>
                    </div>
                </div>
                <div class="overflow-x-auto">
                    <table class="w-full text-left text-xs">
                        <thead>
                            <tr class="text-green-700 border-b border-green-900">
                                <th class="p-2">TYPE</th>
                                <th class="p-2">DISPLAY NAME / SSID</th>
                                <th class="p-2">ADDRESS</th>
                                <th class="p-2">RSSI</th>
                                <th class="p-2">VENDOR</th>
                                <th class="p-2">DIST</th>
                            </tr>
                        </thead>
                        <tbody id="device-list"></tbody>
                    </table>
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="hud-card p-4 rounded-lg">
                    <h2 class="text-lg font-bold mb-4 border-b border-green-900 pb-2"><i class="fas fa-video mr-2"></i>TACTICAL RECORDING</h2>
                    <div class="grid grid-cols-2 gap-4">
                        <button id="btn-audio" onclick="apiCall('/api/v1/record/audio', 'POST')" class="p-4 hud-card btn-action rounded-lg flex flex-col items-center">
                            <i class="fas fa-microphone text-2xl mb-2"></i>
                            <span class="text-xs">AUDIO REC</span>
                        </button>
                        <button id="btn-video" onclick="apiCall('/api/v1/record/video', 'POST')" class="p-4 hud-card btn-action rounded-lg flex flex-col items-center">
                            <i class="fas fa-camera text-2xl mb-2"></i>
                            <span class="text-xs">VIDEO REC</span>
                        </button>
                    </div>
                </div>
                <div class="hud-card p-4 rounded-lg">
                    <h2 class="text-lg font-bold mb-4 border-b border-green-900 pb-2"><i class="fas fa-bolt mr-2"></i>VISUAL ACTIONS</h2>
                    <div class="grid grid-cols-2 gap-4">
                        <button onclick="apiCall('/api/v1/trigger/glitch', 'POST')" class="p-4 hud-card btn-action rounded-lg flex flex-col items-center">
                            <i class="fas fa-ghost text-2xl mb-2"></i>
                            <span class="text-xs">GLITCH EFFECT</span>
                        </button>
                        <button onclick="apiCall('/api/v1/trigger/glitch', 'POST')" class="p-4 hud-card btn-danger rounded-lg flex flex-col items-center">
                            <i class="fas fa-bell text-2xl mb-2"></i>
                            <span class="text-xs">TRIGGER BROADCAST</span>
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <div class="space-y-6">
            <div class="hud-card p-4 rounded-lg flex flex-col h-[500px]">
                <h2 class="text-lg font-bold mb-4 border-b border-green-900 pb-2"><i class="fas fa-comment-dots mr-2"></i>TACTICAL PAGER</h2>
                <div id="message-list" class="flex-grow overflow-y-auto space-y-2 mb-4 pr-2 text-xs"></div>
                <div class="space-y-2">
                    <input id="pager-target" type="text" placeholder="TARGET CALLSIGN (ALL)" class="w-full bg-black border border-green-900 p-2 rounded text-xs text-green-400 focus:outline-none focus:border-green-500">
                    <div class="flex space-x-2">
                        <input id="pager-text" type="text" placeholder="MESSAGE CONTENT..." class="flex-grow bg-black border border-green-900 p-2 rounded text-xs text-green-400 focus:outline-none focus:border-green-500">
                        <button onclick="sendMessage()" class="btn-action px-4 py-2 rounded text-xs">SEND</button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script>
        function apiCall(endpoint, method, body = null) {
            const options = { method: method };
            if (body) {
                options.headers = { 'Content-Type': 'application/json' };
                options.body = JSON.stringify(body);
            }
            return fetch(endpoint, options).then(r => r.json()).then(d => {
                updateStatus();
                return d;
            });
        }

        function updateStatus() {
            fetch('/api/v1/status').then(r => r.json()).then(s => {
                document.getElementById('stat-callsign').textContent = s.callsign;
                document.getElementById('stat-battery').textContent = s.battery_percent + ' %';
                document.getElementById('stat-targets').textContent = s.target_count;
                
                const uptime = s.uptime_seconds;
                const h = Math.floor(uptime / 3600).toString().padStart(2, '0');
                const m = Math.floor((uptime % 3600) / 60).toString().padStart(2, '0');
                const sec = (uptime % 60).toString().padStart(2, '0');
                document.getElementById('system-uptime').textContent = 'UPTIME: ' + h + ':' + m + ':' + sec;

                document.getElementById('btn-audio').style.boxShadow = s.audio_recording ? '0 0 20px #ff0000' : '';
                document.getElementById('btn-video').style.boxShadow = s.video_recording ? '0 0 20px #ff0000' : '';
            });
        }

        function updateDevices(devices) {
            const tbody = document.getElementById('device-list');
            tbody.innerHTML = '';
            devices.forEach(d => {
                const tr = document.createElement('tr');
                tr.className = 'border-b border-green-900/30 hover:bg-green-500/5 transition';
                tr.innerHTML = '<td class="p-2 text-green-500 font-bold">' + (d.typeLabel || d.type) + '</td>' +
                    '<td class="p-2">' + (d.displayName || d.ssid || d.name || 'UNKNOWN') + '</td>' +
                    '<td class="p-2 text-gray-500">' + d.address + '</td>' +
                    '<td class="p-2 ' + (d.rssi > -60 ? 'text-green-400' : 'text-amber-500') + '">' + d.rssi + ' dBm</td>' +
                    '<td class="p-2 text-[10px] text-green-800">' + (d.vendor || '-') + '</td>' +
                    '<td class="p-2">' + (d.distanceLabel || '-') + '</td>';
                tbody.appendChild(tr);
            });
        }

        function loadMessages() {
            fetch('/api/v1/pager/messages').then(r => r.json()).then(data => {
                const list = document.getElementById('message-list');
                list.innerHTML = '';
                if (data.chats) {
                    Object.keys(data.chats).forEach(user => {
                        const msgs = data.chats[user];
                        if (Array.isArray(msgs)) {
                            msgs.forEach(m => {
                                const div = document.createElement('div');
                                div.className = 'p-2 rounded bg-green-900/20 border-l-2 border-green-500';
                                div.innerHTML = '<span class="text-green-500 font-bold">@' + user + ':</span> <span class="text-gray-300">' + (m.text || m) + '</span>';
                                list.appendChild(div);
                            });
                        }
                    });
                }
            });
        }

        function sendMessage() {
            const target = document.getElementById('pager-target').value || 'ALL';
            const text = document.getElementById('pager-text').value;
            if (!text) return;
            apiCall('/api/v1/pager/send', 'POST', { target, text }).then(() => {
                document.getElementById('pager-text').value = '';
                loadMessages();
            });
        }

        const source = new EventSource('/api/v1/stream');
        source.onmessage = function(event) {
            const data = JSON.parse(event.data);
            if (data.devices) {
                updateDevices(data.devices);
                document.getElementById('stat-targets').textContent = data.count;
            }
        };

        setInterval(updateStatus, 1000);
        setInterval(loadMessages, 5000);
        updateStatus();
        loadMessages();
        fetch('/api/v1/devices').then(r => r.json()).then(d => updateDevices(d.devices));
    </script>
</body>
</html>
"""
    }
}
