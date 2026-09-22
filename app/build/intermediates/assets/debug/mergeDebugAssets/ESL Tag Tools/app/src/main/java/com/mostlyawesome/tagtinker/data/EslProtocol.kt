package com.mostlyawesome.tagtinker.data

/**
 * ESL (Electronic Shelf Label) protocol implementation.
 * Based on furrtek's reverse engineering of Hanshow/Newton ESL tags.
 * IR carrier: 1.255 MHz | Modulation: 38 kHz | Encoding: Manchester/NRZ
 */
object EslProtocol {

    // ── Commands ────────────────────────────────────────────────────────────
    const val CMD_TEXT    = 0x10
    const val CMD_IMAGE   = 0x20
    const val CMD_TEST    = 0x30
    const val CMD_LED     = 0x40
    const val CMD_CLEAR   = 0x50

    // ── PLID Encoding ───────────────────────────────────────────────────────
    /**
     * Convert a barcode string to a 7-byte PLID.
     * Strips non-numeric, takes last 13 digits, BCD-encodes MSB first.
     */
    fun barcodeToPlid(barcode: String): ByteArray {
        val numeric = barcode.filter { it.isDigit() }
        var digits  = numeric.takeLast(13).padStart(13, '0')
        val plid    = ByteArray(7)
        for (i in 0 until 7) {
            val hi = (digits.getOrNull(i * 2)?.digitToInt() ?: 0)
            val lo = (digits.getOrNull(i * 2 + 1)?.digitToInt() ?: 0)
            plid[i] = ((hi shl 4) or lo).toByte()
        }
        return plid
    }

    /** XOR CRC over all packet bytes */
    fun calcCrc(data: ByteArray, length: Int = data.size): Byte {
        var crc = 0
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
        }
        return (crc and 0xFF).toByte()
    }

    fun plidHex(plid: ByteArray): String =
        plid.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    fun crcHex(plid: ByteArray, command: Int): String {
        val dummy = plid.toMutableList().apply {
            add(command.toByte()); add(0x00)
        }.toByteArray()
        return "0x%02X".format(calcCrc(dummy).toInt() and 0xFF)
    }

    // ── IR File Builder ─────────────────────────────────────────────────────
    /**
     * Build a Flipper/MULTIPASS-compatible .ir file for a given ESL command.
     * Packet = [PLID 7B] [CMD 1B] [PAYLOAD NB] [CRC-8]
     * Timing: 9ms sync, 4.5ms gap, then NEC-like bit timings
     */
    fun buildIrFile(barcode: String, command: Int, payload: ByteArray): String {
        val plid = barcodeToPlid(barcode)
        val packetSize = 7 + 1 + payload.size
        val packet = ByteArray(packetSize + 1) // +1 for CRC
        
        System.arraycopy(plid, 0, packet, 0, 7)
        packet[7] = command.toByte()
        System.arraycopy(payload, 0, packet, 8, payload.size)
        packet[packetSize] = calcCrc(packet, packetSize)

        return buildString {
            appendLine("Filetype: IR signals file")
            appendLine("Version: 1")
            appendLine("#")
            appendLine("name: ESL_CMD")
            appendLine("type: raw")
            appendLine("frequency: 1255000")
            appendLine("duty_cycle: 0.33")
            append("data:")
            
            // Header timings
            append(" 9000 4500")
            
            // Packet timings
            for (b in packet) {
                val bv = b.toInt() and 0xFF
                for (bit in 0 until 8) {
                    if (bv and (1 shl bit) != 0) {
                        append(" 560 1690")
                    } else {
                        append(" 560 560")
                    }
                }
            }
            
            // Stop bit
            append(" 560")
            appendLine()
        }
    }

    /** Build payload for text command */
    fun textPayload(text: String, invert: Boolean): ByteArray {
        val truncated = text.take(32)
        return ByteArray(2 + truncated.length).also {
            it[0] = if (invert) 0x01 else 0x00
            it[1] = (truncated.length and 0xFF).toByte()
            truncated.forEachIndexed { i, c -> it[2 + i] = c.code.toByte() }
        }
    }

    /** Build payload for LED command */
    fun ledPayload(mode: Int): ByteArray = byteArrayOf(mode.toByte())

    /** Build payload for test pattern command */
    fun testPayload(): ByteArray = byteArrayOf(0x01)

    /** Build payload for clear command */
    fun clearPayload(colorClear: Boolean): ByteArray =
        byteArrayOf(if (colorClear) 0x01 else 0x00)
}

// ── Data models ─────────────────────────────────────────────────────────────
data class EslTarget(
    val barcode: String = "N4611470825513274",
    val widthPx: Int    = 200,
    val heightPx: Int   = 80,
    val txRepeats: Int  = 200,
    val delayMs: Int    = 15,
)

data class IrConfig(
    val commandType: Int       = EslProtocol.CMD_TEXT,
    val textContent: String    = "ESL Tools",
    val invertText: Boolean    = false,
    val colorClear: Boolean    = false,
    val ledMode: Int           = 0,
)

data class ImageConfig(
    val threshold: Int      = 128,
    val ditherMode: String  = "floyd",   // none | floyd | bayer
    val invert: Boolean     = false,
    val fitToEsl: Boolean   = true,
)

enum class LedMode(val label: String, val value: Int) {
    OFF("Off", 0), RED("Red", 1), GREEN("Green", 2), ORANGE("Orange", 3)
}

enum class IrCommand(val label: String, val value: Int) {
    TEXT("Text (0x10)", 0x10),
    IMAGE("Image (0x20)", 0x20),
    TEST("Test Pattern (0x30)", 0x30),
    LED("LED Control (0x40)", 0x40),
    CLEAR("Clear Display (0x50)", 0x50),
}
