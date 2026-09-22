package com.radar.blewifi.qr.protocol

import java.util.Random

data class FountainSymbol(
    val transferId: Int,
    val k: Int,
    val blockSize: Int,
    val seed: Long,
    val degree: Int,
    val data: ByteArray
)

/**
 * Deterministically picks `degree` distinct block indices in [0, k) from a
 * seed. Both the encoder and decoder call this with the same seed+degree,
 * so they always agree on which source blocks a symbol XORs together.
 */
fun indicesFor(seed: Long, k: Int, degree: Int): IntArray {
    val d = degree.coerceIn(1, k)
    val arr = IntArray(k) { it }
    val rnd = Random(seed)
    for (i in 0 until d) {
        val j = i + rnd.nextInt(k - i)
        val tmp = arr[i]
        arr[i] = arr[j]
        arr[j] = tmp
    }
    return arr.copyOfRange(0, d)
}

private fun xorInto(dst: ByteArray, src: ByteArray) {
    for (i in dst.indices) dst[i] = (dst[i].toInt() xor src[i].toInt()).toByte()
}

/** Splits a payload into K fixed-size blocks and emits an endless stream of coded symbols. */
class FountainEncoder(payload: ByteArray, val blockSize: Int, val transferId: Int) {

    val k: Int = ((payload.size + blockSize - 1) / blockSize).coerceAtLeast(1)
    private val blocks: Array<ByteArray> = Array(k) { i ->
        val start = i * blockSize
        val end = minOf(start + blockSize, payload.size)
        val block = ByteArray(blockSize)
        if (start < payload.size) System.arraycopy(payload, start, block, 0, end - start)
        block
    }
    private val cumulative = Soliton.cumulativeDistribution(k)
    private val rnd = Random()

    fun nextSymbol(): FountainSymbol {
        val seed = rnd.nextLong() and 0xFFFFFFFFL
        val degree = Soliton.sampleDegree(cumulative, rnd)
        val idx = indicesFor(seed, k, degree)
        val out = ByteArray(blockSize)
        for (i in idx) xorInto(out, blocks[i])
        return FountainSymbol(transferId, k, blockSize, seed, degree, out)
    }
}

/** Peeling decoder: feed it symbols as they arrive; it reconstructs the payload once enough distinct symbols are seen. */
class FountainDecoder(val k: Int, val blockSize: Int) {

    private val known = arrayOfNulls<ByteArray>(k)
    private var knownCount = 0
    private val seenSeeds = HashSet<Long>()
    private data class Pending(val indices: MutableSet<Int>, val data: ByteArray)
    private val pending = mutableListOf<Pending>()

    val isComplete: Boolean get() = knownCount == k
    val recoveredBlocks: Int get() = knownCount

    /** Returns true if this symbol was new (not a duplicate frame already processed). */
    fun addSymbol(sym: FountainSymbol): Boolean {
        if (sym.k != k || sym.blockSize != blockSize) return false
        if (!seenSeeds.add(sym.seed)) return false

        val indices = indicesFor(sym.seed, k, sym.degree).toMutableSet()
        val data = sym.data.copyOf()
        reduceAgainstKnown(indices, data)

        when {
            indices.isEmpty() -> Unit // fully redundant
            indices.size == 1 -> solve(indices.first(), data)
            else -> pending.add(Pending(indices, data))
        }
        return true
    }

    fun assemble(): ByteArray? {
        if (!isComplete) return null
        val out = ByteArray(k * blockSize)
        for (i in 0 until k) System.arraycopy(known[i]!!, 0, out, i * blockSize, blockSize)
        return out
    }

    private fun reduceAgainstKnown(indices: MutableSet<Int>, data: ByteArray) {
        val it = indices.iterator()
        while (it.hasNext()) {
            val i = it.next()
            known[i]?.let { kb ->
                xorInto(data, kb)
                it.remove()
            }
        }
    }

    private fun solve(index: Int, data: ByteArray) {
        if (known[index] != null) return
        known[index] = data
        knownCount++
        val workQueue = ArrayDeque<Int>()
        workQueue.add(index)

        while (workQueue.isNotEmpty()) {
            val solvedIdx = workQueue.removeFirst()
            val solvedData = known[solvedIdx]!!
            val it = pending.iterator()
            while (it.hasNext()) {
                val p = it.next()
                if (p.indices.remove(solvedIdx)) {
                    xorInto(p.data, solvedData)
                    when {
                        p.indices.isEmpty() -> it.remove()
                        p.indices.size == 1 -> {
                            val only = p.indices.first()
                            it.remove()
                            if (known[only] == null) {
                                known[only] = p.data
                                knownCount++
                                workQueue.add(only)
                            }
                        }
                    }
                }
            }
        }
    }
}
