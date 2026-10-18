package com.espsdr.jammer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.espsdr.jammer.R
import com.espsdr.jammer.jammer.JammerViewModel
import com.espsdr.jammer.jammer.MODES
import com.espsdr.jammer.jammer.REF_TABLE
import com.espsdr.jammer.jammer.Reach
import com.espsdr.jammer.jammer.StatusKind
import com.espsdr.jammer.jammer.reach
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JammerScreen(vm: JammerViewModel) {
    val ui by vm.ui.collectAsState()
    val outer = rememberScrollState()

    val dimAlpha by animateFloatAsState(
        targetValue = if (ui.running) 0.20f else 1.0f,
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        ),
        label = "dim_alpha"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(P.Bg)
            .verticalScroll(outer)
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 28.dp)
    ) {
        // ------------------------------------------------------------- header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            Column(Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = R.drawable.pbjlogo),
                    contentDescription = "PBJ Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .height(32.dp)
                )
            }
            ConnectionLed(connected = ui.connected, onClick = vm::connectPressed)
            Spacer(Modifier.width(8.dp))
            PillButton(if (ui.connected) "Disconnect" else "Connect") { vm.connectPressed() }
        }

        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------- Theme Selector Dropdown
        AppCard(
            title = "Theme Configuration",
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            var expanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(P.Panel2)
                        .border(1.dp, P.Line, RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE COLORWAY",
                            color = P.Dim,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = ui.theme.title,
                            color = P.Acc,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("▼", color = P.Acc, fontSize = 12.sp)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(P.Panel)
                        .border(1.dp, P.Line, RoundedCornerShape(8.dp))
                ) {
                    AppTheme.entries.forEach { t ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = t.title,
                                    color = if (ui.theme == t) P.Acc else P.Fg,
                                    fontSize = 14.sp,
                                    fontWeight = if (ui.theme == t) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                vm.setTheme(t)
                                expanded = false
                            },
                            modifier = Modifier.background(if (ui.theme == t) P.Panel2 else Color.Transparent)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------- 0 · Transmitter Diagnostics
        AppCard(
            title = "0 · Transmitter Diagnostics",
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Test Carrier (3 s)", Modifier.weight(1f), BtnKind.TEST) { vm.testCarrier() }
                PillButton("Read Status", Modifier.weight(1f)) { vm.readStatus() }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Diagnostic sweep: test carrier outputs a 2441.1 MHz CW signal for 3 s " +
                    "and logs all radio status return codes.",
                color = P.Dim, fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // -------------------------------------------------- 1 · Target Frequency Selector
        AppCard(
            title = "1 · Target Frequency Selector",
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            var categoryFilter by remember { mutableStateOf("ALL") }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                listOf("ALL", "2.4G", "5G", "SUB-G", "ISM").forEach { cat ->
                    val on = categoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) P.AccBg else P.Panel2)
                            .border(1.dp, if (on) P.AccLine else P.Line, RoundedCornerShape(6.dp))
                            .clickable { categoryFilter = cat }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cat,
                            color = if (on) P.AccFg else P.Fg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = ui.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Filter targets: wifi, ble, 5180, dfs…", color = P.Dim, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = P.Console,
                    unfocusedContainerColor = P.Console,
                    focusedBorderColor = P.Acc,
                    unfocusedBorderColor = P.Line,
                    focusedTextColor = P.Fg,
                    unfocusedTextColor = P.Fg,
                    cursorColor = P.Acc
                )
            )

            Spacer(Modifier.height(9.dp))

            val filtered = remember(ui.search, categoryFilter) {
                val q = ui.search.trim().lowercase()
                REF_TABLE.filter { item ->
                    val matchesCategory = when (categoryFilter) {
                        "2.4G" -> item.group.contains("2.4") || item.group.contains("Bluetooth") || item.group.contains("Zigbee")
                        "5G" -> item.group.contains("5 GHz") || item.group.contains("6 GHz")
                        "SUB-G" -> item.group.contains("Sub") || item.group.contains("Below")
                        "ISM" -> item.group.contains("ISM")
                        else -> true
                    }
                    val matchesSearch = if (q.isEmpty()) true else "${item.group} ${item.mhz} ${item.what}".lowercase().contains(q)
                    matchesCategory && matchesSearch
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(P.Console)
                    .border(1.dp, P.Line, RoundedCornerShape(8.dp))
            ) {
                LazyColumn {
                    filtered.groupBy { it.group }.forEach { (group, rows) ->
                        stickyHeader {
                            Text(
                                group.uppercase(),
                                color = P.Dim, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(P.Panel2)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        items(rows, key = { "${it.mhz}-${it.what}" }) { row ->
                            RefListRow(
                                row.mhz, row.what, row.widthMhz,
                                selected = ui.selected.containsKey(row.mhz),
                                harmMode = ui.harmMode,
                                onClick = { vm.selectTarget(row.mhz, row.what, row.widthMhz) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(9.dp))

            var manual by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    placeholder = { Text("Exact frequency MHz, e.g. 2412.5", color = P.Dim, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        vm.addManual(manual); manual = ""
                    }),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = P.Console,
                        unfocusedContainerColor = P.Console,
                        focusedBorderColor = P.Acc,
                        unfocusedBorderColor = P.Line,
                        focusedTextColor = P.Fg,
                        unfocusedTextColor = P.Fg,
                        cursorColor = P.Acc
                    )
                )
                PillButton("Add Target") { vm.addManual(manual); manual = "" }
            }

            if (ui.selected.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                FlowChips(ui.selected.keys.sorted(), ui.harmMode) { mhz ->
                    ui.selected[mhz]?.let { vm.toggleTarget(mhz, it.what, it.widthMhz) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Apply To Radio", Modifier.weight(1f), BtnKind.ACCENT) {
                    vm.startJammingApplyingOnly()
                }
                PillButton("Clear All") { vm.clearTargets() }
            }
            Spacer(Modifier.height(7.dp))
            Text(vm.planSummary(), color = P.Dim, fontSize = 11.sp, fontFamily = P.Mono)
        }

        Spacer(Modifier.height(10.dp))

        // -------------------------------------------------------- 2 · Signal Generator (ALWAYS BRIGHT & GLOWING WHEN ACTIVE)
        AppCard("2 · Signal Generator", isGlowing = ui.running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MODES.forEach { m ->
                    PillButton(
                        m.uppercase(),
                        Modifier.weight(1f),
                        if (ui.mode == m) BtnKind.ON else BtnKind.DEFAULT
                    ) { vm.setMode(m) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("START", Modifier.weight(1f), BtnKind.ACCENT) { vm.startJamming() }
                PillButton("STOP", Modifier.weight(1f), BtnKind.STOP) { vm.stopJamming() }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --------------------------------------------------------- 3 · RF Parameters
        AppCard(
            title = "3 · RF Tuning & Parameters",
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            SliderRow(
                "Half Width Span", "${ui.span} kHz",
                ui.span.toFloat(), 100f..40000f, 398,
                onChange = vm::setSpan, onCommit = vm::commitSpan
            )
            SliderRow(
                "Dwell Time / Target", "${ui.dwell} ms",
                ui.dwell.toFloat(), 5f..1000f, 198,
                onChange = vm::setDwell, onCommit = vm::commitDwell
            )
            SliderRow(
                "Digital Amplitude", "${ui.amp}",
                ui.amp.toFloat(), 10f..511f, 500,
                onChange = vm::setAmp, onCommit = vm::commitAmp
            )
            SliderRow(
                "TX Power Gain  (−1 = MAX)", "${ui.gain}",
                ui.gain.toFloat(), -1f..31f, 31,
                onChange = vm::setGain, onCommit = vm::commitGain
            )
            Spacer(Modifier.height(6.dp))
            Text("HARMONIC MULTIPLIER", color = P.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            SegmentedControl(
                options = listOf("AUTO" to 0, "1ST" to 1, "2ND" to 2, "3RD" to 3),
                selected = ui.harmMode,
                onSelect = vm::setHarm
            )
        }

        Spacer(Modifier.height(10.dp))

        // -------------------------------------------------------- 4 · Digital Spectrum
        AppCard(
            title = "4 · Digital Spectrum Analyzer",
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            val header = ui.header
            SpectrumView(
                bars = ui.bars,
                lo = header?.lo ?: 0.0,
                half = header?.half ?: 0.0
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    if (ui.monitorOn) "Stop Spectrum Monitor" else "Start Spectrum Monitor",
                    kind = if (ui.monitorOn) BtnKind.ON else BtnKind.DEFAULT,
                    modifier = Modifier.weight(1f)
                ) { vm.toggleMonitor() }
                PillButton("Read Plan") { vm.readPlan() }
            }
            Spacer(Modifier.height(7.dp))
            Text(ui.monitorLabel, color = P.Dim, fontSize = 11.sp, fontFamily = P.Mono)
        }

        Spacer(Modifier.height(10.dp))

        // ----------------------------------------------------------- 5 · Serial Console
        AppCard(
            title = "5 · Serial Console",
            modifier = Modifier.graphicsLayer { alpha = dimAlpha }
        ) {
            val logState = rememberScrollState()
            LaunchedEffect(ui.log.size) { logState.animateScrollTo(logState.maxValue) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(P.Console)
                    .border(1.dp, P.Line, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(logState)
            ) {
                Text(
                    ui.log.joinToString("\n"),
                    color = P.Fg,
                    fontFamily = P.Mono,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.height(9.dp))
            var raw by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    placeholder = { Text("Enter command (e.g. jam ref)", color = P.Dim, fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        vm.sendRaw(raw); raw = ""
                    }),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = P.Console,
                        unfocusedContainerColor = P.Console,
                        focusedBorderColor = P.Acc,
                        unfocusedBorderColor = P.Line,
                        focusedTextColor = P.Fg,
                        unfocusedTextColor = P.Fg,
                        cursorColor = P.Acc
                    )
                )
                PillButton("Send") { vm.sendRaw(raw); raw = "" }
            }
        }
    }

    if (ui.deviceChooser.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = vm::dismissChooser,
            containerColor = P.Panel,
            title = { Text("Select USB SDR Device", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ui.deviceChooser.forEach { d ->
                        Text(
                            "${d.productName ?: "USB Serial Device"}  (${d.deviceName})\n" +
                                "VID 0x%04X  PID 0x%04X".format(d.vendorId, d.productId),
                            color = P.Fg, fontFamily = P.Mono, fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.chooseDevice(d) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                PillButton("Cancel") { vm.dismissChooser() }
            }
        )
    }
}

@Composable
private fun RefListRow(
    mhz: Double,
    what: String,
    width: Double,
    selected: Boolean,
    harmMode: Int,
    onClick: () -> Unit
) {
    val r: Reach = reach(mhz)
    val out = r.harm == 0
    val (badgeColor, badgeText) = when {
        harmMode == 1 && r.harm != 0 -> P.OkFg to "direct"
        r.harm == 0 -> P.Dim to r.label
        r.harm == 1 -> P.OkFg to r.label
        r.harm == 2 -> P.Warn to r.label
        else -> P.BadFg to r.label
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) P.OnBg else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            String.format(Locale.US, "%.2f", mhz),
            color = P.Acc, fontFamily = P.Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp)
        )
        Text(
            what,
            color = if (out) P.Dim else P.Fg,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (width >= 1) {
            Text("${width.toInt()} MHz", color = P.Dim, fontSize = 11.sp, fontFamily = P.Mono)
            Spacer(Modifier.width(6.dp))
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(P.Panel2)
                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FlowChips(freqs: List<Double>, harmMode: Int, onRemove: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        freqs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { mhz ->
                    val r = reach(mhz)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(P.Panel2)
                            .border(1.dp, P.AccLine, RoundedCornerShape(999.dp))
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            String.format(Locale.US, "%.3f", mhz),
                            color = P.AccFg, fontFamily = P.Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (r.harm == 0) "out"
                            else "LO " + String.format(Locale.US, "%.3f", mhz / r.harm) +
                                if (r.harm == 1) "" else " ×${r.harm}",
                            color = P.Dim, fontSize = 11.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "×",
                            color = P.Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onRemove(mhz) }
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}