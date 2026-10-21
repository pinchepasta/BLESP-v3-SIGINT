package com.bruceremote.app.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bruceremote.app.serial.BruceDisplayRenderer
import com.bruceremote.app.serial.NavDirection
import com.bruceremote.app.ui.theme.BruceBorder
import com.bruceremote.app.ui.theme.BruceMuted
import com.bruceremote.app.ui.theme.BruceSurface
import com.bruceremote.app.ui.theme.BruceSurfaceAlt
import com.bruceremote.app.ui.theme.NodeAccent
import com.bruceremote.app.ui.theme.NodePrimary

@Composable
fun NavigatorScreen(
    display: BruceDisplayRenderer,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onNav: (NavDirection) -> Unit,
    onDump: () -> Unit,
    connected: Boolean
) {
    LaunchedEffect(isOpen, connected) {
        if (connected && isOpen) onOpen()
    }

    val bmp = display.bitmap

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!connected) {
            Spacer(Modifier.height(48.dp))
            Text(
                "Connect to a MULTiPASS device to mirror its screen.",
                color = BruceMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, NodePrimary, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                @Suppress("UNUSED_VARIABLE")
                val currentTick = display.tick
                val roundedPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, size.width, size.height),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    )
                }
                clipPath(roundedPath) {
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        display.bitmap,
                        null,
                        RectF(0f, 0f, size.width, size.height),
                        null
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Arrows navigate • ● selects • ⟲ goes back • long-press M",
                color = BruceMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Stream", fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDump,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Refresh", fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        DPad(onNav = onNav)
    }
}

@Composable
private fun DPad(onNav: (NavDirection) -> Unit) {
    Column(
        modifier = Modifier
            .background(BruceSurface, RoundedCornerShape(12.dp))
            .border(1.dp, BruceBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton("Pg▲", NavDirection.PREV_PAGE, onNav)
            NavButton("▲", NavDirection.PREV, onNav)
            NavButton("M", NavDirection.MENU, onNav)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton("◀", NavDirection.UP, onNav)
            NavButton("●", NavDirection.SELECT, onNav, accent = true)
            NavButton("▶", NavDirection.DOWN, onNav)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavButton("Pg▼", NavDirection.NEXT_PAGE, onNav)
            NavButton("▼", NavDirection.NEXT, onNav)
            NavButton("⟲", NavDirection.ESCAPE, onNav)
        }
    }
}

@Composable
private fun NavButton(
    label: String,
    direction: NavDirection,
    onNav: (NavDirection) -> Unit,
    accent: Boolean = false
) {
    Button(
        onClick = { onNav(direction) },
        shape = CircleShape,
        colors = if (accent) {
            ButtonDefaults.buttonColors(containerColor = NodeAccent, contentColor = Color.Black)
        } else {
            ButtonDefaults.buttonColors(containerColor = BruceSurfaceAlt, contentColor = NodePrimary)
        },
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(48.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = if (label.length > 2) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}
