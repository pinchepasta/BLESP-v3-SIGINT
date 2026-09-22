package com.mostlyawesome.tagtinker.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import androidx.core.graphics.set
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

object ImageProcessor {

    data class ProcessedImage(
        val bitmap: Bitmap,
        val pixels: IntArray, // 0 = black, 255 = white
        val width: Int,
        val height: Int,
    )

    /** Convert source bitmap to monochrome using specified method */
    fun processImage(
        source: Bitmap,
        targetW: Int,
        targetH: Int,
        threshold: Int,
        ditherMode: String, // "none" | "floyd" | "bayer"
        invert: Boolean,
        fitToEsl: Boolean,
    ): ProcessedImage {
        // Scale
        val (tW, tH) = if (fitToEsl) {
            targetW to targetH
        } else {
            val scale = minOf(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
            (source.width * scale).toInt() to (source.height * scale).toInt()
        }

        val scaled = source.scale(tW, tH, true)

        // Extract grayscale
        val gray = FloatArray(tW * tH)
        for (i in 0 until tW * tH) {
            val x = i % tW; val y = i / tW
            val px = scaled[x, y]
            gray[i] = (0.299f * Color.red(px) + 0.587f * Color.green(px) + 0.114f * Color.blue(px))
        }

        // Apply dithering
        val out = IntArray(tW * tH)
        when (ditherMode) {
            "floyd" -> {
                val g = gray.copyOf()
                for (y in 0 until tH) {
                    for (x in 0 until tW) {
                        val idx = y * tW + x
                        val old = g[idx]
                        val nv  = if (old < threshold) 0f else 255f
                        val err = old - nv
                        g[idx] = nv; out[idx] = nv.toInt()
                        if (x + 1 < tW) g[idx + 1] += err * 7f / 16f
                        if (y + 1 < tH) {
                            if (x - 1 >= 0) g[(y + 1) * tW + (x - 1)] += err * 3f / 16f
                            g[(y + 1) * tW + x]     += err * 5f / 16f
                            if (x + 1 < tW) g[(y + 1) * tW + (x + 1)] += err * 1f / 16f
                        }
                    }
                }
            }
            "bayer" -> {
                val bm = arrayOf(
                    intArrayOf(0, 8, 2, 10), intArrayOf(12, 4, 14, 6),
                    intArrayOf(3, 11, 1, 9), intArrayOf(15, 7, 13, 5)
                )
                for (i in 0 until tW * tH) {
                    val x = i % tW; val y = i / tW
                    out[i] = if (gray[i] + (bm[y % 4][x % 4] / 16f - 0.5f) * 64f > threshold) 255 else 0
                }
            }
            else -> { // "none"
                for (i in 0 until tW * tH) out[i] = if (gray[i] < threshold) 0 else 255
            }
        }

        // Build result bitmap
        val result = createBitmap(tW, tH, Bitmap.Config.ARGB_8888)
        for (i in 0 until tW * tH) {
            val x = i % tW; val y = i / tW
            val v = if (invert) (if (out[i] == 0) 255 else 0) else out[i]
            result[x, y] = Color.rgb(v, v, v)
        }

        return ProcessedImage(result, out, tW, tH)
    }

    /** Export to XBM format string */
    fun exportXbm(processed: ProcessedImage, invert: Boolean): String {
        val (_, pixels, W, H) = processed
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i < W * H) {
            var b = 0
            for (bit in 0 until 8) {
                val pix = if (i + bit < W * H) pixels[i + bit] else 255
                if (if (invert) pix != 0 else pix == 0) b = b or (1 shl bit)
            }
            bytes.add(b)
            i += 8
        }
        val hexBytes = bytes.joinToString(", ") { "0x%02x".format(it) }
        val varName  = "img_${W}x${H}"
        return buildString {
            appendLine("#define ${varName}_width $W")
            appendLine("#define ${varName}_height $H")
            appendLine("static unsigned char ${varName}_bits[] = {")
            append("  ")
            appendLine(hexBytes)
            append("};")
        }
    }

    /** Build MULTIPASS JS snippet for XBM usage */
    fun buildJsSnippet(processed: ProcessedImage, xbmData: String): String {
        val (_, _, W, H) = processed
        // Extract hex bytes from XBM
        val hexLine = xbmData.lines().firstOrNull { it.startsWith("  0x") }?.trim() ?: ""
        return buildString {
            appendLine("// img_${W}x${H} — ${W}×${H} XBM")
            appendLine("var IMG_W = $W, IMG_H = $H;")
            appendLine("var imgBytes = new Uint8Array([")
            appendLine("  $hexLine")
            appendLine("]);")
            appendLine()
            appendLine("// Usage:")
            appendLine("var C_FG = display.color(0, 0, 0);")
            appendLine("var C_BG = display.color(255, 255, 255);")
            append("display.drawXBitmap(0, 0, imgBytes.buffer, IMG_W, IMG_H, C_FG, C_BG);")
        }
    }

    /** Build image IR payload from processed pixels */
    fun imageToIrPayload(processed: ProcessedImage, invert: Boolean): ByteArray {
        val (_, pixels, W, H) = processed
        val byteCount = 4 + (W * H + 7) / 8
        val bytes = ByteArray(byteCount)

        // header: width (2B LE), height (2B LE)
        bytes[0] = (W and 0xFF).toByte()
        bytes[1] = ((W shr 8) and 0xFF).toByte()
        bytes[2] = (H and 0xFF).toByte()
        bytes[3] = ((H shr 8) and 0xFF).toByte()

        // pixel data packed
        var byteIdx = 4
        var bitIdx = 0
        while (bitIdx < W * H) {
            var b = 0
            for (bit in 0 until 8) {
                if (bitIdx + bit < W * H) {
                    val pix = pixels[bitIdx + bit]
                    if (if (invert) pix != 0 else pix == 0) {
                        b = b or (1 shl bit)
                    }
                }
            }
            bytes[byteIdx++] = b.toByte()
            bitIdx += 8
        }
        return bytes
    }
}
