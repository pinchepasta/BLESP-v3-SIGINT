package com.radar.blewifi.qr.protocol

import java.util.Random
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Robust soliton degree distribution for LT (fountain) coding.
 * Produces a cumulative distribution table over degrees 1..k that both the
 * encoder (to pick a degree) and, indirectly, the whole scheme rely on for
 * good peeling-decoder recovery behaviour.
 */
object Soliton {

    fun cumulativeDistribution(k: Int, c: Double = 0.05, delta: Double = 0.5): DoubleArray {
        if (k <= 1) return doubleArrayOf(0.0, 1.0)

        val rho = DoubleArray(k + 1)
        rho[1] = 1.0 / k
        for (i in 2..k) rho[i] = 1.0 / (i * (i - 1.0))

        val s = c * ln(k / delta) * sqrt(k.toDouble())
        val threshold = (k / s.coerceAtLeast(1.0)).toInt().coerceIn(1, k)
        val tau = DoubleArray(k + 1)
        for (i in 1 until threshold) tau[i] = s / (i * k)
        tau[threshold] = s * ln(s / delta) / k

        val mu = DoubleArray(k + 1)
        var sum = 0.0
        for (i in 1..k) {
            mu[i] = rho[i] + tau[i]
            sum += mu[i]
        }

        val cumulative = DoubleArray(k + 1)
        var running = 0.0
        for (i in 1..k) {
            running += mu[i] / sum
            cumulative[i] = running
        }
        // Guard against floating point drift so the last bucket is reachable.
        cumulative[k] = 1.0
        return cumulative
    }

    fun sampleDegree(cumulative: DoubleArray, rnd: Random): Int {
        val x = rnd.nextDouble()
        for (i in 1 until cumulative.size) {
            if (x <= cumulative[i]) return i
        }
        return cumulative.size - 1
    }
}
