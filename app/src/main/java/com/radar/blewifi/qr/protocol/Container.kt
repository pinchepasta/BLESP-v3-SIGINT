package com.radar.blewifi.qr.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Wraps a file's bytes (plus name/type/hash) into a single self-describing
 * envelope that gets split across fountain-coded QR frames, and unwraps it
 * again on the receive side with an integrity check.
 *
 * Layout: "N8" | version | flags | nameLen(u16) | typeLen(u16) | origSize(u32)
 *         | sha256(32) | name | type | payload
 */
object Container {

    private val MAGIC = byteArrayOf('N'.code.toByte(), '8'.code.toByte())
    private const val VERSION: Int = 1
    private const val FLAG_NONE: Int = 0
    private const val FLAG_GZIP: Int = 1

    data class Parsed(
        val name: String,
        val type: String,
        val bytes: ByteArray,
        val verified: Boolean
    )

    fun build(name: String, type: String, data: ByteArray, compress: Boolean = true): ByteArray {
        val sha = sha256(data)
        val compressed = if (compress) gzip(data) else data
        // Only actually use gzip if it helped; tiny/incompressible payloads can grow.
        val useGzip = compress && compressed.size < data.size
        val flag = if (useGzip) FLAG_GZIP else FLAG_NONE
        val payload = if (useGzip) compressed else data

        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val typeBytes = type.toByteArray(Charsets.UTF_8)

        val buf = ByteArrayOutputStream()
        val dos = DataOutputStream(buf)
        dos.write(MAGIC)
        dos.writeByte(VERSION)
        dos.writeByte(flag)
        dos.writeShort(nameBytes.size)
        dos.writeShort(typeBytes.size)
        dos.writeInt(data.size)
        dos.write(sha)
        dos.write(nameBytes)
        dos.write(typeBytes)
        dos.write(payload)
        return buf.toByteArray()
    }

    fun parse(all: ByteArray): Parsed? {
        return try {
            val dis = DataInputStream(ByteArrayInputStream(all))
            val m0 = dis.readByte()
            val m1 = dis.readByte()
            if (m0 != MAGIC[0] || m1 != MAGIC[1]) return null
            dis.readByte() // version, unused for now
            val flag = dis.readUnsignedByte()
            val nameLen = dis.readUnsignedShort()
            val typeLen = dis.readUnsignedShort()
            val origSize = dis.readInt()
            val sha = ByteArray(32).also { dis.readFully(it) }
            val nameBytes = ByteArray(nameLen).also { dis.readFully(it) }
            val typeBytes = ByteArray(typeLen).also { dis.readFully(it) }
            val data = if (flag == FLAG_GZIP) {
                gunzip(dis.readBytes(), origSize)
            } else {
                val p = ByteArray(origSize)
                dis.readFully(p)
                p
            }
            val ok = data.size == origSize && sha256(data).contentEquals(sha)
            Parsed(String(nameBytes, Charsets.UTF_8), String(typeBytes, Charsets.UTF_8), data, ok)
        } catch (e: Exception) {
            null
        }
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray, expectedSize: Int): ByteArray {
        GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
            val out = ByteArrayOutputStream(expectedSize.coerceAtLeast(64))
            val buf = ByteArray(8192)
            while (true) {
                val n = gis.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
