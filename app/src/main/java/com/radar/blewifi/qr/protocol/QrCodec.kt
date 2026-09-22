package com.radar.blewifi.qr.protocol

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Raw bytes (0..255) are round-tripped through QR "byte mode" losslessly by
 * treating them as an ISO-8859-1 string on both the encode and decode side.
 */
object QrCodec {

    fun encode(bytes: ByteArray, ecLevel: ErrorCorrectionLevel, size: Int): Bitmap {
        val text = String(bytes, Charsets.ISO_8859_1)
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            EncodeHintType.ERROR_CORRECTION to ecLevel,
            EncodeHintType.MARGIN to 1
        )
        val matrix: BitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) BLACK else WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    private const val BLACK = -0x1000000
    private const val WHITE = -0x1
}
