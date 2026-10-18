package com.radar.blewifi.pbj.jammer

/** ASCII ramp used by the firmware for spectrum bars, darkest to brightest. */
const val RAMP = " .:-=+*#%@"

private val MON_HEADER = Regex(
    """mon f=(\d+)\.(\d{3}) h=(\d+)\.(\d{3}) b=(\d+) w=(\d+)\.(\d{2}) p=(-?\d+)\.(\d)"""
)
private val BAR_LINE = Regex("""^\s*\|([^|]+)\|\s*$""")

data class SpectrumFrame(
    val lo: Double,
    val half: Double,
    val bins: Int,
    val bw: Double,
    val peak: Double,
    val bars: String
)

/**
 * Stateful line reassembler. The firmware can split the `mon f=` header from
 * the bar line across reads (and can split the header itself), exactly like
 * the web UI's `pending` buffer.
 */
class LineParser {
    private var pending = ""

    sealed interface Event {
        data class Text(val line: String) : Event
        data class Frame(val frame: SpectrumFrame) : Event
    }

    fun feed(raw: String): List<Event> {
        val line = if (pending.isEmpty()) raw else pending + raw

        if (line.contains("mon f=") && !line.contains(" p=")) {
            pending = line
            return emptyList()
        }
        pending = ""

        val out = ArrayList<Event>(2)
        out += Event.Text(line)

        MON_HEADER.find(line)?.let { m ->
            out += Event.Frame(
                SpectrumFrame(
                    lo = m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / 1000.0,
                    half = m.groupValues[3].toDouble() + m.groupValues[4].toDouble() / 1000.0,
                    bins = m.groupValues[5].toInt(),
                    bw = m.groupValues[6].toDouble() + m.groupValues[7].toDouble() / 100.0,
                    peak = (m.groupValues[8] + "." + m.groupValues[9]).toDouble(),
                    bars = ""
                )
            )
            return out
        }

        BAR_LINE.find(line)?.let { m ->
            out += Event.Frame(
                SpectrumFrame(0.0, 0.0, 0, 0.0, 0.0, m.groupValues[1])
            )
        }
        return out
    }
}
