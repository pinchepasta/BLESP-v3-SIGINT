package com.node8.transfer.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader

/**
 * Decodes every QR code visible in each camera frame (supports the grid
 * layouts the sender can use) and hands the raw payload bytes back.
 */
class QrAnalyzer(private val onPayloads: (List<ByteArray>) -> Unit) : ImageAnalysis.Analyzer {

    private val baseReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "ISO-8859-1",
                DecodeHintType.TRY_HARDER to true
            )
        )
    }
    private val multiReader = GenericMultipleBarcodeReader(baseReader)

    override fun analyze(image: ImageProxy) {
        try {
            val data = yPlaneToBytes(image)
            val source = PlanarYUVLuminanceSource(
                data, image.width, image.height, 0, 0, image.width, image.height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val results = try {
                multiReader.decodeMultiple(bitmap)
            } catch (e: NotFoundException) {
                null
            }
            if (!results.isNullOrEmpty()) {
                val payloads = results.map { it.text.toByteArray(Charsets.ISO_8859_1) }
                onPayloads(payloads)
            }
        } catch (e: Exception) {
            // Decode failures are routine (blur/motion) - just skip this frame.
        } finally {
            image.close()
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
