package com.node8.transfer.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Binary framing for a single QR code's payload:
 * magic(1) | transferId(u16) | k(u16) | blockSize(u16) | seed(u32) | degree(u8) | data(blockSize)
 */
object FrameCodec {

    private const val MAGIC: Int = 0xD8
    const val HEADER_SIZE = 12

    fun pack(sym: FountainSymbol): ByteArray {
        val buf = ByteArrayOutputStream(HEADER_SIZE + sym.data.size)
        val dos = DataOutputStream(buf)
        dos.writeByte(MAGIC)
        dos.writeShort(sym.transferId and 0xFFFF)
        dos.writeShort(sym.k and 0xFFFF)
        dos.writeShort(sym.blockSize and 0xFFFF)
        dos.writeInt((sym.seed and 0xFFFFFFFFL).toInt())
        dos.writeByte(sym.degree and 0xFF)
        dos.write(sym.data)
        return buf.toByteArray()
    }

    fun unpack(bytes: ByteArray): FountainSymbol? {
        if (bytes.size < HEADER_SIZE) return null
        return try {
            val dis = DataInputStream(ByteArrayInputStream(bytes))
            val magic = dis.readUnsignedByte()
            if (magic != MAGIC) return null
            val transferId = dis.readUnsignedShort()
            val k = dis.readUnsignedShort()
            val blockSize = dis.readUnsignedShort()
            val seedInt = dis.readInt()
            val seed = seedInt.toLong() and 0xFFFFFFFFL
            val degree = dis.readUnsignedByte()
            val expectedDataLen = bytes.size - HEADER_SIZE
            if (expectedDataLen != blockSize) return null
            val data = ByteArray(blockSize)
            dis.readFully(data)
            FountainSymbol(transferId, k, blockSize, seed, degree, data)
        } catch (e: Exception) {
            null
        }
    }
}
