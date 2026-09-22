package com.mostlyawesome.tagtinker.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.mostlyawesome.tagtinker.ui.theme.*

// ── Section Card ────────────────────────────────────────────────────────────
@Composable
fun TtCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier       = modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(16.dp),
        color          = colors.surface,
        border         = BorderStroke(1.dp, colors.outline),
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

// ── Section Header ───────────────────────────────────────────────────────────
@Composable
fun SectionHeader(num: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 14.dp)
    ) {
        Text(
            text  = num,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text  = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            fontSize = 11.sp,
        )
    }
}

// ── Warning Bar ──────────────────────────────────────────────────────────────
@Composable
fun WarningBar(title: String, body: String) {
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = OrangeBg,
        border = BorderStroke(1.dp, Orange.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Orange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(2.dp))
            Text(body, color = Orange, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp)
        }
    }
}

// ── Info Box ─────────────────────────────────────────────────────────────────
@Composable
fun InfoBox(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Spacer(Modifier.width(4.dp))
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MonoCode(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

// ── Status Box ───────────────────────────────────────────────────────────────
@Composable
fun StatusBox(ok: Boolean, message: String, modifier: Modifier = Modifier) {
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = if (ok) GreenBg else OrangeBg,
        border = BorderStroke(1.dp, if (ok) Green.copy(alpha = 0.35f) else Orange.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text   = message,
            color  = if (ok) Green else Orange,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.padding(10.dp)
        )
    }
}

// ── Grid Info ────────────────────────────────────────────────────────────────
@Composable
fun GridInfo(items: List<Pair<String, String>>) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (key, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(8.dp),
                color    = colors.background,
                border   = BorderStroke(1.dp, colors.outline),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text  = value,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = colors.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text  = key,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 9.sp,
                        color      = colors.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

// ── Buttons ──────────────────────────────────────────────────────────────────
@Composable
fun TtButtonPrimary(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
        colors   = ButtonDefaults.buttonColors(
            containerColor = colors.onBackground,
            contentColor   = colors.background,
            disabledContainerColor = colors.onBackground.copy(alpha = 0.5f),
            disabledContentColor   = colors.background.copy(alpha = 0.5f),
        ),
        shape    = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TtButtonAccent(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
        colors   = ButtonDefaults.buttonColors(
            containerColor = Orange,
            contentColor   = Color.White,
            disabledContainerColor = Orange.copy(alpha = 0.5f),
            disabledContentColor   = Color.White.copy(alpha = 0.5f),
        ),
        shape    = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TtButtonGhost(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = modifier,
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.onSurfaceVariant,
            disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        shape    = RoundedCornerShape(8.dp),
        border   = BorderStroke(1.dp, if (enabled) colors.outline else colors.outline.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Field Label ───────────────────────────────────────────────────────────────
@Composable
fun FieldLabel(text: String) {
    Text(
        text       = text,
        fontSize   = 12.sp,
        fontWeight = FontWeight.Medium,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier   = Modifier.padding(bottom = 6.dp),
    )
}

// ── Section divider ───────────────────────────────────────────────────────────
@Composable
fun SectionDivider() {
    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
}

// ── Mono output box ───────────────────────────────────────────────────────────
@Composable
fun MonoOutputBox(text: String, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val isEmpty = text.isBlank()

    // For very large text (like image payloads), the Compose Text component can crash 
    // due to internal texture/measurement limits if the line is too long.
    // We truncate the display text for safety, as the full content is still available for Copy/Save.
    val displayText = remember(text) {
        if (text.length > 8000) {
            text.take(8000) + "\n\n[... content truncated for display — use Copy or Save to get full file ...]"
        } else text
    }

    Surface(
        shape   = RoundedCornerShape(8.dp),
        color   = colors.surfaceVariant,
        border  = BorderStroke(1.dp, colors.outline),
        modifier= modifier.fillMaxWidth()
    ) {
        Text(
            text  = if (isEmpty) placeholder else displayText,
            color = if (isEmpty) colors.onSurfaceVariant.copy(alpha = 0.45f) else colors.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize   = 10.sp,
            lineHeight = 15.sp,
            fontStyle  = if (isEmpty) androidx.compose.ui.text.font.FontStyle.Italic
                         else androidx.compose.ui.text.font.FontStyle.Normal,
            modifier   = Modifier
                .padding(12.dp)
                .then(if (text.length <= 8000) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        )
    }
}

// ── Dropdown (replaces <select>) ─────────────────────────────────────────────
@Composable
fun <T> TtDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors   = MaterialTheme.colorScheme

    Column(modifier) {
        FieldLabel(label)
        Box {
            Surface(
                shape  = RoundedCornerShape(8.dp),
                color  = colors.background,
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth().clickable { expanded = true }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(labelFor(selected), fontSize = 13.sp, color = colors.onSurface,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text    = { Text(labelFor(opt), fontSize = 13.sp) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
}

// ── Slider row with value ─────────────────────────────────────────────────────
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value         = value,
                onValueChange = onValueChange,
                valueRange    = valueRange,
                modifier      = Modifier.weight(1f),
                colors        = SliderDefaults.colors(
                    thumbColor       = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                )
            )
            Spacer(Modifier.width(10.dp))
            Text(displayValue, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 36.dp))
        }
    }
}
