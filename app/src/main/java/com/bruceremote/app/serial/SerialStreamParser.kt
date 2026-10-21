package com.bruceremote.app.serial

/**
 * Splits the raw byte stream coming from Bruce over USB-serial into:
 *  - binary screen-draw packets (framed as 0xAA <size> <fnId> <payload>)
 *  - plain text (CLI command echoes / command output)
 */
class SerialStreamParser(
    private val onBinaryPacket: (ByteArray) -> Unit,
    private val onTextChunk: (String) -> Unit
) {
    private var rxBuffer: ByteArray = ByteArray(0)
    private var dumpCapture: StringBuilder? = null

    /** Call this once before sending "display dump" so the following text is captured, not logged. */
    fun beginDumpCapture() {
        dumpCapture = StringBuilder()
    }

    fun feed(data: ByteArray) {
        rxBuffer = concat(rxBuffer, data)

        while (rxBuffer.isNotEmpty()) {
            if (rxBuffer[0] == AA) {
                if (rxBuffer.size < 2) return // need at least size byte
                val size = rxBuffer[1].toInt() and 0xFF
                if (size < 3) {
                    // Invalid size header for 0xAA packet; drop this byte and continue searching
                    rxBuffer = rxBuffer.copyOfRange(1, rxBuffer.size)
                    continue
                }
                if (rxBuffer.size < size) return // wait for complete packet
                val packet = rxBuffer.copyOfRange(0, size)
                rxBuffer = rxBuffer.copyOfRange(size, rxBuffer.size)
                onBinaryPacket(packet)
                continue
            }

            val nextIdx = rxBuffer.indexOfFirst { it == AA }
            val textBytes: ByteArray
            if (nextIdx == -1) {
                textBytes = rxBuffer
                rxBuffer = ByteArray(0)
            } else {
                textBytes = rxBuffer.copyOfRange(0, nextIdx)
                rxBuffer = rxBuffer.copyOfRange(nextIdx, rxBuffer.size)
            }

            val text = String(textBytes, Charsets.UTF_8)
            if (text.isNotEmpty()) {
                onTextChunk(text)
                val capture = dumpCapture
                if (capture != null) {
                    capture.append(text)
                    if (capture.contains(END_OF_DUMP, ignoreCase = true) || capture.contains("End of Dump", ignoreCase = true)) {
                        val full = capture.toString()
                        dumpCapture = null
                        parseDumpToBytes(full)?.let { feedDecodedDump(it) }
                    }
                }
            }

            if (nextIdx == -1) break
        }
    }

    /** The decoded dump is itself a concatenation of 0xAA packets; split and replay them. */
    private fun feedDecodedDump(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            while (offset < bytes.size && bytes[offset] != AA) {
                offset++
            }
            if (offset + 1 >= bytes.size) break
            val size = bytes[offset + 1].toInt() and 0xFF
            if (size < 3 || offset + size > bytes.size) {
                offset++
                continue
            }
            onBinaryPacket(bytes.copyOfRange(offset, offset + size))
            offset += size
        }
    }

    private fun parseDumpToBytes(text: String): ByteArray? {
        val startIdx = text.indexOf("AA", ignoreCase = true)
        var cleaned = if (startIdx >= 0) text.substring(startIdx) else text
        val endMarkerIdx = cleaned.indexOf("End of Dump", ignoreCase = true)
        if (endMarkerIdx >= 0) cleaned = cleaned.substring(0, endMarkerIdx)
        val hex = cleaned.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
        if (hex.length < 2) return null
        val evenLen = hex.length - (hex.length % 2)
        if (evenLen == 0) return null
        val out = ByteArray(evenLen / 2)
        for (i in out.indices) {
            out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    companion object {
        private const val AA: Byte = 0xAA.toByte()
        private const val END_OF_DUMP = "[End of Dump]"
    }
}
