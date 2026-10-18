package com.espsdr.jammer.jammer

import java.util.Locale

/** Every shell verb jammer.html speaks. */
object Cmd {
    const val INFO = "jam info"
    const val STATUS = "jam status"
    const val TEST = "jam test"
    const val START = "jam start"
    const val STOP = "jam stop"

    fun harm(v: Int) = if (v == 0) "jam harm auto" else "jam harm $v"

    fun lo(freqs: List<Double>) = buildString {
        append("jam lo ")
        freqs.forEachIndexed { i, f ->
            if (i > 0) append(' ')
            append(String.format(Locale.US, "%.3f", f))
        }
    }

    fun span(khz: Int) = "jam span $khz"
    fun dwell(ms: Int) = "jam dwell $ms"
    fun amp(v: Int) = "jam amp $v"
    fun gain(v: Int) = "jam gain $v"
    fun mode(m: String) = "jam mode $m"
    fun mon(bins: Int) = "jam mon $bins"
}