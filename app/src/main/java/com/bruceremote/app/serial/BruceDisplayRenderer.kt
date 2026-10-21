package com.bruceremote.app.serial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.res.ResourcesCompat
import com.radar.blewifi.R
import java.util.Collections
import kotlin.math.abs

/**
 * Re-draws Bruce's device screen from the binary packets its tftLogger streams over serial.
 */
class BruceDisplayRenderer(
    private val context: Context? = null,
    initialWidth: Int = 320,
    initialHeight: Int = 240
) {

    var bitmap: Bitmap = Bitmap.createBitmap(initialWidth, initialHeight, Bitmap.Config.ARGB_8888)
        private set

    // Bumped on every applied draw op; Compose reads this to know when to redraw the bitmap.
    var tick by mutableIntStateOf(0)
        private set

    var renderIcons: Boolean = true
    var primaryColor: Int = Color.parseColor("#FF5F00")
    var accentColor: Int = Color.parseColor("#00F5D4")

    private val activeTextList = Collections.synchronizedList(mutableListOf<VisibleScreenText>())

    fun getRecognizedScreenText(): List<VisibleScreenText> {
        synchronized(activeTextList) {
            return activeTextList.toList()
        }
    }

    // Pixel-exact fills & strokes without anti-aliasing artifacts
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (context != null) {
            try {
                ResourcesCompat.getFont(context, R.font.pixelfont) ?: Typeface.MONOSPACE
            } catch (_: Exception) {
                Typeface.MONOSPACE
            }
        } else {
            Typeface.MONOSPACE
        }
    }

    private var canvas = Canvas(bitmap)

    init {
        drawInitialFrame()
    }

    fun drawInitialFrame() {
        canvas.drawColor(Color.BLACK)
        activeTextList.clear()

        textPaint.color = accentColor
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MULTiPASS DISPLAY READY", bitmap.width / 2f, bitmap.height / 2f, textPaint)

        textPaint.color = Color.parseColor("#888888")
        textPaint.textSize = 9f
        canvas.drawText("Tap Stream / Refresh or press a button", bitmap.width / 2f, bitmap.height / 2f + 20f, textPaint)
    }

    fun reset(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        drawInitialFrame()
        tick++
    }

    /** Feed one already-framed 0xAA packet (as produced by [SerialStreamParser]). */
    fun handlePacket(packet: ByteArray) {
        if (packet.size < 3 || packet[0] != 0xAA.toByte()) return
        val size = packet[1].toInt() and 0xFF
        val fn = packet[2].toInt() and 0xFF
        val payloadLen = (size - 3).coerceAtLeast(0)
        val fields = parseFields(fn, packet, payloadLen)
        applyDrawOp(fn, fields)
    }

    // ---- field layout, matches `keysMap` in esp32_serial_navigator.html ----
    private val keysMap: Map<Int, List<String>> = mapOf(
        0 to listOf("fg"),
        1 to listOf("x", "y", "w", "h", "fg"),
        2 to listOf("x", "y", "w", "h", "fg"),
        3 to listOf("x", "y", "w", "h", "r", "fg"),
        4 to listOf("x", "y", "w", "h", "r", "fg"),
        5 to listOf("x", "y", "r", "fg"),
        6 to listOf("x", "y", "r", "fg"),
        7 to listOf("x", "y", "x2", "y2", "x3", "y3", "fg"),
        8 to listOf("x", "y", "x2", "y2", "x3", "y3", "fg"),
        9 to listOf("x", "y", "rx", "ry", "fg"),
        10 to listOf("x", "y", "rx", "ry", "fg"),
        11 to listOf("x", "y", "x1", "y1", "fg"),
        12 to listOf("x", "y", "r", "ir", "startAngle", "endAngle", "fg", "bg"),
        13 to listOf("x", "y", "bx", "by", "wd", "fg", "bg"),
        14 to listOf("x", "y", "size", "fg", "bg", "txt"),
        15 to listOf("x", "y", "size", "fg", "bg", "txt"),
        16 to listOf("x", "y", "size", "fg", "bg", "txt"),
        17 to listOf("x", "y", "size", "fg", "bg", "txt"),
        18 to listOf("x", "y", "center", "ms", "fs", "file"),
        20 to listOf("x", "y", "h", "fg"),
        21 to listOf("x", "y", "w", "fg"),
        99 to listOf("w", "h", "rotation")
    )

    private fun parseFields(fn: Int, data: ByteArray, payloadLen: Int): Map<String, Any> {
        val keys = keysMap[fn] ?: emptyList()
        var pos = 3
        var left = payloadLen
        val out = HashMap<String, Any>()
        for (key in keys) {
            when (key) {
                "txt", "file" -> {
                    val len = left.coerceAtLeast(0)
                    val end = (pos + len).coerceAtMost(data.size)
                    out[key] = if (pos < end) String(data, pos, end - pos, Charsets.UTF_8) else ""
                    pos += len
                    left = 0
                }
                "rotation", "fs" -> {
                    if (pos >= data.size) continue
                    val v = data[pos].toInt() and 0xFF
                    pos += 1; left -= 1
                    out[key] = if (key == "fs") (if (v == 0) "SD" else "FS") else v
                }
                else -> {
                    if (pos + 1 >= data.size) continue
                    val b1 = data[pos].toInt() and 0xFF
                    val b2 = data[pos + 1].toInt() and 0xFF
                    val raw = (b1 shl 8) or b2
                    out[key] = if (key == "fg" || key == "bg") raw else raw.toShort().toInt()
                    pos += 2; left -= 2
                }
            }
        }
        return out
    }

    private fun applyDrawOp(fn: Int, f: Map<String, Any>) {
        fun i(key: String): Int = (f[key] as? Int) ?: 0
        fun s(key: String): String = (f[key] as? String) ?: ""
        fun argb(key: String): Int = color565ToArgb(i(key))

        // Option to suppress non-essential icon/graphic shape drawing calls
        if (!renderIcons && fn in listOf(3, 4, 5, 6, 7, 8, 9, 10, 12, 18)) {
            return
        }

        when (fn) {
            99 -> reset(i("w"), i("h"))

            0 -> { // fill whole screen
                activeTextList.clear()
                fillPaint.color = argb("fg")
                canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), fillPaint)
            }

            1 -> { // stroke rect
                strokePaint.color = argb("fg")
                canvas.drawRect(
                    i("x").toFloat(), i("y").toFloat(),
                    (i("x") + i("w")).toFloat(), (i("y") + i("h")).toFloat(), strokePaint
                )
            }

            2 -> { // fill rect
                fillPaint.color = argb("fg")
                canvas.drawRect(
                    i("x").toFloat(), i("y").toFloat(),
                    (i("x") + i("w")).toFloat(), (i("y") + i("h")).toFloat(), fillPaint
                )
            }

            3, 4 -> { // round rect, stroke / fill
                val paint = if (fn == 3) strokePaint else fillPaint
                paint.color = argb("fg")
                val x = i("x").toFloat(); val y = i("y").toFloat()
                val r = i("r").toFloat()
                canvas.drawRoundRect(x, y, x + i("w"), y + i("h"), r, r, paint)
            }

            5, 6 -> { // circle, stroke / fill
                val paint = if (fn == 5) strokePaint else fillPaint
                paint.color = argb("fg")
                canvas.drawCircle(i("x").toFloat(), i("y").toFloat(), i("r").toFloat(), paint)
            }

            7, 8 -> { // triangle, stroke / fill
                val paint = if (fn == 7) strokePaint else fillPaint
                paint.color = argb("fg")
                val path = Path().apply {
                    moveTo(i("x").toFloat(), i("y").toFloat())
                    lineTo(i("x2").toFloat(), i("y2").toFloat())
                    lineTo(i("x3").toFloat(), i("y3").toFloat())
                    close()
                }
                canvas.drawPath(path, paint)
            }

            9, 10 -> { // ellipse, stroke / fill
                val paint = if (fn == 9) strokePaint else fillPaint
                paint.color = argb("fg")
                val cx = i("x").toFloat(); val cy = i("y").toFloat()
                val rx = i("rx").toFloat(); val ry = i("ry").toFloat()
                canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
            }

            11 -> { // thin line
                strokePaint.color = argb("fg")
                strokePaint.strokeWidth = 1f
                canvas.drawLine(i("x").toFloat(), i("y").toFloat(), i("x1").toFloat(), i("y1").toFloat(), strokePaint)
            }

            12 -> { // arc (gauge-style)
                val r = i("r"); val ir = i("ir")
                strokePaint.color = argb("fg")
                strokePaint.strokeWidth = abs(r - ir).toFloat().coerceAtLeast(1f)
                val sa = (i("startAngle") + 90).toFloat()
                val ea = (i("endAngle") + 90).toFloat()
                val radius = ((r + ir) / 2).toFloat()
                val cx = i("x").toFloat(); val cy = i("y").toFloat()
                canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), sa, ea - sa, false, strokePaint)
                strokePaint.strokeWidth = 1f
            }

            13 -> { // thick line
                strokePaint.color = argb("fg")
                strokePaint.strokeWidth = i("wd").toFloat().coerceAtLeast(1f)
                canvas.drawLine(i("x").toFloat(), i("y").toFloat(), i("bx").toFloat(), i("by").toFloat(), strokePaint)
                strokePaint.strokeWidth = 1f
            }

            14, 15, 16, 17 -> { // text: center / right / left
                val bg = i("bg")
                val fg = i("fg")
                val size = i("size").coerceAtLeast(1)
                val txt = s("txt").replace("\r", "").replace("\n", "")
                if (txt.isEmpty()) return

                val fontH = (size * 5f).coerceIn(8f, 15f)
                textPaint.textSize = fontH
                textPaint.color = color565ToArgb(fg)
                textPaint.textAlign = when (fn) {
                    14 -> Paint.Align.CENTER
                    15 -> Paint.Align.RIGHT
                    else -> Paint.Align.LEFT
                }

                val x = i("x").toFloat()
                val y = i("y").toFloat()

                val isSel = (bg != fg && bg != 0)
                activeTextList.add(VisibleScreenText(text = txt, x = x, y = y, isSelected = isSel))

                val hasBg = (bg != 0 && bg != fg)
                if (hasBg) {
                    fillPaint.color = color565ToArgb(bg)
                    val textW = textPaint.measureText(txt)
                    val isFullRow = (x < 15f && (x + textW) > bitmap.width * 0.65f)
                    val rectLeft = when (fn) {
                        14 -> (x - textW / 2f - 2f).coerceAtLeast(0f)
                        15 -> (x - textW - 2f).coerceAtLeast(0f)
                        else -> if (isFullRow) 0f else x
                    }
                    val rectRight = when (fn) {
                        14 -> (x + textW / 2f + 2f).coerceAtMost(bitmap.width.toFloat())
                        15 -> x.coerceAtMost(bitmap.width.toFloat())
                        else -> if (isFullRow) bitmap.width.toFloat() else (x + textW + 2f).coerceAtMost(bitmap.width.toFloat())
                    }
                    val rectTop = y
                    val rectBottom = y + fontH
                    canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, fillPaint)
                }

                val baselineY = y - textPaint.ascent()
                canvas.drawText(txt, x, baselineY, textPaint)
            }

            18 -> { /* inline images: not supported, matches the reference web tool */ }

            19 -> { /* single pixel: protocol doesn't encode coordinates for this op */ }

            20 -> { // vertical 1px line
                fillPaint.color = argb("fg")
                canvas.drawRect(i("x").toFloat(), i("y").toFloat(), i("x") + 1f, i("y") + i("h").toFloat(), fillPaint)
            }

            21 -> { // horizontal 1px line
                fillPaint.color = argb("fg")
                canvas.drawRect(i("x").toFloat(), i("y").toFloat(), i("x") + i("w").toFloat(), i("y") + 1f, fillPaint)
            }
        }
        tick++
    }

    private fun color565ToArgb(c: Int): Int {
        val r = (((c shr 11) and 0x1F) * 255) / 31
        val g = (((c shr 5) and 0x3F) * 255) / 63
        val b = ((c and 0x1F) * 255) / 31
        return Color.rgb(r, g, b)
    }
}
