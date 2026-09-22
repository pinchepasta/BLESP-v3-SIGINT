package com.radar.blewifi.qr.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import java.io.ByteArrayOutputStream
import java.util.EnumMap

/**
 * Decodes every QR code visible in each camera frame (supports the grid
 * layouts the sender can use) and hands the raw payload bytes back.
 */
class QrAnalyzer(private val onPayloads: (List<ByteArray>) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = QRCodeMultiReader()
    private val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)

    init {
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        hints[DecodeHintType.CHARACTER_SET] = "ISO-8859-1"
        hints[DecodeHintType.TRY_HARDER] = true
    }

    override fun analyze(image: ImageProxy) {
        try {
            val data = yPlaneToBytes(image)
            val source = PlanarYUVLuminanceSource(
                data, image.width, image.height, 0, 0, image.width, image.height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            
            val results = try {
                reader.decodeMultiple(bitmap, hints)
            } catch (e: Exception) {
                null
            }

            if (!results.isNullOrEmpty()) {
                val payloads = results.mapNotNull { result ->
                    extractBytes(result)
                }
                if (payloads.isNotEmpty()) {
                    onPayloads(payloads)
                }
            }
        } catch (e: Exception) {
            // Decode failures are routine (blur/motion) - just skip this frame.
        } finally {
            image.close()
        }
    }

    private fun extractBytes(result: Result): ByteArray? {
        // Prefer raw byte segments if present (standard for Byte mode QR)
        val metadata = result.resultMetadata
        if (metadata != null && metadata.containsKey(ResultMetadataType.BYTE_SEGMENTS)) {
            val segments = metadata[ResultMetadataType.BYTE_SEGMENTS] as? List<*>
            if (segments != null) {
                val bos = ByteArrayOutputStream()
                for (segment in segments) {
                    if (segment is ByteArray) {
                        bos.write(segment)
                    }
                }
                if (bos.size() > 0) return bos.toByteArray()
            }
        }
        // Fallback to text using ISO-8859-1 to preserve byte values
        return try {
            result.text.toByteArray(Charsets.ISO_8859_1)
        } catch (e: Exception) {
            null
        }
    }


    /** Copies the Y (luma) plane into a tightly packed byte array, handling row padding. */
    private fun yPlaneToBytes(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        if (rowStride == width) {
            val out = ByteArray(buffer.remaining())
            buffer.get(out)
            return out
        }

        val out = ByteArray(width * height)
        val rowBuf = ByteArray(rowStride)
        var outPos = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            val available = buffer.remaining().coerceAtMost(rowStride)
            buffer.get(rowBuf, 0, available)
            System.arraycopy(rowBuf, 0, out, outPos, width)
            outPos += width
        }
        return out
    }
}
