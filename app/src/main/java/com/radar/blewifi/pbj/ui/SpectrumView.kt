package com.radar.blewifi.pbj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radar.blewifi.pbj.jammer.RAMP
import java.util.Locale

/**
 * Digital Spectrum Analyzer Component with dBFS grid, LO center marker,
 * and high-contrast neon signal bars.
 */
@Composable
fun SpectrumView(bars: String, lo: Double, half: Double, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val consoleBg = P.Console
    val lineCol = P.Line
    val badCol = P.Bad
    val warnCol = P.Warn
    val okCol = P.Ok
    val dimCol = P.Dim
    val accCol = P.Acc

    Canvas(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(consoleBg)
            .border(1.dp, lineCol, RoundedCornerShape(10.dp))
    ) {
        val w = size.width
        val h = size.height
        val base = h - 18.dp.toPx()

        // Horizontal dBFS gridlines
        val gridLevels = listOf("0 dB", "-20", "-40", "-60")
        for (i in 0..3) {
            val y = base * i / 3f
            drawLine(
                color = lineCol.copy(alpha = 0.4f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            )
            val layout = measurer.measure(gridLevels[i], TextStyle(color = dimCol.copy(alpha = 0.6f), fontSize = 8.sp))
            drawText(layout, topLeft = Offset(4f, (y - 12f).coerceAtLeast(2f)))
        }

        // Vertical frequency gridlines
        for (g in 0..4) {
            val x = (w * g / 4f).let { if (it > 0f) it - 0.5f else it }
            drawLine(lineCol, Offset(x, 0f), Offset(x, base), strokeWidth = 1f)
        }

        // Center LO Marker line
        val centerX = w / 2f
        drawLine(
            color = accCol,
            start = Offset(centerX, 0f),
            end = Offset(centerX, base),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f)
        )

        // Spectrum signal bars
        if (bars.isNotEmpty()) {
            val n = bars.length
            val bw = w / n
            for (i in 0 until n) {
                val v = RAMP.indexOf(bars[i]).coerceAtLeast(0).toFloat() / (RAMP.length - 1)
                val bh = maxOf(1f, v * (base - 2f))
                val color = when {
                    v > 0.72f -> badCol
                    v > 0.45f -> warnCol
                    else -> okCol
                }
                drawRect(
                    color = color,
                    topLeft = Offset(i * bw, base - bh),
                    size = Size(maxOf(1f, bw - 0.5f), bh)
                )
            }
        }

        // Frequency ruler labels
        if (half > 0.0) {
            val f0 = lo - half
            val f1 = lo + half
            for (g in 0..4) {
                val label = String.format(Locale.US, "%.1f", f0 + (f1 - f0) * g / 4.0)
                val layout = measurer.measure(label, TextStyle(color = dimCol, fontSize = 9.sp))
                val x = (w * g / 4f - layout.size.width / 2f).coerceIn(2f, w - layout.size.width - 2f)
                drawText(layout, topLeft = Offset(x, base + 3f))
            }
        }
    }
}
