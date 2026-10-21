package com.radar.blewifi.masdr

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Thin client for the mostly-a-SDR /api/v1 HTTP/JSON API.
 * Bearer-token auth, JSON in / JSON out, everything off the UI thread.
 */
class Api(private val prefs: Prefs) {

    class Res(val code: Int, val text: String) {
        val ok: Boolean get() = code in 200..299
        val json: JSONObject get() = try {
            if (text.trimStart().startsWith("{")) JSONObject(text) else JSONObject()
        } catch (e: Exception) { JSONObject() }
        val error: String get() = json.optString("error",
            if (text.isBlank()) "HTTP $code" else text.take(200))
    }

    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    private fun base() = "http://${prefs.host}:${prefs.port}"

    fun enc(s: String): String = try { URLEncoder.encode(s, "UTF-8") } catch (e: Exception) { s }

    fun shutdown() {
        try {
            pool.shutdownNow()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun open(method: String, path: String, body: JSONObject?): HttpURLConnection {
        val c = URL(base() + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 6000
        c.readTimeout = 40000
        if (prefs.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${prefs.token}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        return c
    }

    fun call(method: String, path: String, body: JSONObject? = null, cb: (Res) -> Unit) {
        if (pool.isShutdown || pool.isTerminated) return
        try {
            pool.execute {
                val r = try {
                    val c = open(method, path, body)
                    val code = c.responseCode
                    val txt = (if (code in 200..299) c.inputStream else c.errorStream)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    Res(code, txt)
                } catch (e: Exception) {
                    Res(0, "{\"error\":\"${(e.message ?: "network error").replace("\"", "'")}\"}")
                }
                main.post {
                    try {
                        cb(r)
                    } catch (e: Exception) {
                        // ignore UI callback errors if activity is finishing/destroyed
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore execution rejection if thread pool is shutting down
        }
    }

    fun get(path: String, cb: (Res) -> Unit) = call("GET", path, null, cb)
    fun post(path: String, body: JSONObject?, cb: (Res) -> Unit) = call("POST", path, body ?: JSONObject(), cb)
    fun delete(path: String, cb: (Res) -> Unit) = call("DELETE", path, null, cb)

    /** Raw-body upload: POST /api/v1/upload?kind=&name= */
    fun upload(kind: String, name: String, uri: Uri, cr: ContentResolver, cb: (Res) -> Unit) {
        if (pool.isShutdown || pool.isTerminated) return
        try {
            pool.execute {
                val r = try {
                    val safe = displayName(uri, cr) ?: name
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    val c = URL("${base()}/api/v1/upload?kind=$kind&name=${enc(safe)}")
                        .openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.connectTimeout = 6000
                    c.readTimeout = 120000
                    c.doOutput = true
                    if (prefs.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${prefs.token}")
                    c.setRequestProperty("Content-Type", "application/octet-stream")
                    c.outputStream.use { it.write(bytes) }
                    val code = c.responseCode
                    val txt = (if (code in 200..299) c.inputStream else c.errorStream)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    Res(code, txt)
                } catch (e: Exception) {
                    Res(0, "{\"error\":\"${(e.message ?: "upload failed").replace("\"", "'")}\"}")
                }
                main.post {
                    try {
                        cb(r)
                    } catch (e: Exception) {
                        // ignore UI callback errors if activity is finishing/destroyed
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore execution rejection if thread pool is shutting down
        }
    }

    private fun displayName(uri: Uri, cr: ContentResolver): String? =
        cr.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return@use cur.getString(i)
            }
            null
        } ?: uri.lastPathSegment
}
