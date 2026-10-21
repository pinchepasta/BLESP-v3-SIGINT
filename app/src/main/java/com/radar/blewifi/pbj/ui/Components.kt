package com.radar.blewifi.pbj.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BtnKind { DEFAULT, ACCENT, STOP, TEST, ON, PLAIN }

@Composable
fun AppCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    isGlowing: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_trans")

    // Slower base neon tube breathing (1800ms)
    val baseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "base_alpha"
    )

    // Micro-flicker simulating high-voltage gas fluctuations in a real neon tube
    val flickerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                0.85f at 120
                1.0f at 280
                0.78f at 420
                0.96f at 580
                0.82f at 740
                1.0f at 950
                0.88f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "flicker_alpha"
    )

    val glowAlpha = if (isGlowing) (baseAlpha * flickerAlpha).coerceIn(0.4f, 1.0f) else 1.0f

    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius"
    )

    val shape = RoundedCornerShape(12.dp)
    val accColor = P.Acc

    val cardModifier = if (isGlowing) {
        modifier
            .fillMaxWidth()
            .drawWithContent {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = accColor.copy(alpha = 0.65f * glowAlpha)
                        asFrameworkPaint().maskFilter = BlurMaskFilter(glowRadius.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        12.dp.toPx(), 12.dp.toPx(), paint
                    )
                }
                drawContent()
            }
            .clip(shape)
            .background(P.Panel)
            .background(accColor.copy(alpha = 0.14f * glowAlpha))
            .border(2.5.dp, accColor.copy(alpha = glowAlpha), shape)
            .border(1.dp, Color.White.copy(alpha = 0.85f * glowAlpha), shape)
    } else {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.Panel)
            .border(1.dp, P.Line, shape)
    }

    Column(
        cardModifier.padding(14.dp)
    ) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isGlowing) accColor.copy(alpha = glowAlpha) else P.Acc)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    color = if (isGlowing) accColor else P.Fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
        }
        content()
    }
}

@Composable
fun ConnectionLed(
    connected: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "led_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val accColor = P.Acc

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (connected) {
            // Glowing Radial Aura
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accColor.copy(alpha = 0.35f * glowAlpha))
            )
            // 100% Saturated Core LED
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accColor.copy(alpha = glowAlpha))
                    .border(1.5.dp, Color.White, CircleShape)
            )
        } else {
            // Dark Unlit Bezel
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(P.Panel2)
                    .border(1.5.dp, P.Line, CircleShape)
            )
        }
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.DEFAULT,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val (bg, line, fg) = when (kind) {
        BtnKind.DEFAULT -> Triple(P.Panel2, P.Line, P.Fg)
        BtnKind.ACCENT -> Triple(P.AccBg, P.AccLine, P.AccFg)
        BtnKind.STOP -> Triple(P.BadBg, P.BadLine, P.BadFg)
        BtnKind.TEST -> Triple(P.Panel2, P.Line.copy(alpha = 0.7f), P.Fg)
        BtnKind.ON -> Triple(P.OnBg, P.OnLine, P.OnFg)
        BtnKind.PLAIN -> Triple(Color.Transparent, Color.Transparent, P.Fg)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.3f))
            .border(1.dp, if (enabled) line else line.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) fg else fg.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

enum class StatusKindUi { NEUTRAL, OK, BAD }

@Composable
fun StatusPill(text: String, kind: StatusKindUi) {
    val (bg, line, fg) = when (kind) {
        StatusKindUi.NEUTRAL -> Triple(P.Panel2, P.Line, P.Dim)
        StatusKindUi.OK -> Triple(P.OkBg, P.OkLine, P.OkFg)
        StatusKindUi.BAD -> Triple(P.BadBg, P.BadLine, P.BadFg)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "led_pulse")
    val ledAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led"
    )

    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, line, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(fg.copy(alpha = if (kind != StatusKindUi.NEUTRAL) ledAlpha else 0.5f))
        )
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
    onCommit: () -> Unit
) {
    val stepSize = (range.endInclusive - range.start) / (if (steps > 0) steps + 1 else 100)

    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = P.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(P.Panel2)
                    .border(1.dp, P.Line, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(valueText, color = P.Acc, fontSize = 12.sp, fontFamily = P.Mono, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(P.Panel2)
                    .border(1.dp, P.Line, RoundedCornerShape(6.dp))
                    .clickable {
                        val next = (value - stepSize).coerceIn(range.start, range.endInclusive)
                        onChange(next.toInt())
                        onCommit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = P.Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = value,
                onValueChange = { onChange(it.toInt()) },
                onValueChangeFinished = onCommit,
                valueRange = range,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = P.Acc,
                    activeTrackColor = P.Acc,
                    inactiveTrackColor = P.Line
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(P.Panel2)
                    .border(1.dp, P.Line, RoundedCornerShape(6.dp))
                    .clickable {
                        val next = (value + stepSize).coerceIn(range.start, range.endInclusive)
                        onChange(next.toInt())
                        onCommit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = P.Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SegmentedControl(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, P.Line, RoundedCornerShape(8.dp))
            .background(P.Panel2)
    ) {
        options.forEachIndexed { index, (label, _) ->
            val on = selected == options[index].second
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) P.AccBg else Color.Transparent)
                    .border(
                        if (on) 1.dp else 0.dp,
                        if (on) P.AccLine else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(options[index].second) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.uppercase(),
                    color = if (on) P.AccFg else P.Fg,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
