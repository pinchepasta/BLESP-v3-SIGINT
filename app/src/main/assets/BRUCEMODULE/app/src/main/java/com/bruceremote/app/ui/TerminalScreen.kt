package com.bruceremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bruceremote.app.serial.LogDirection
import com.bruceremote.app.serial.LogLine
import com.bruceremote.app.ui.theme.BruceBorder
import com.bruceremote.app.ui.theme.BruceSurface
import com.bruceremote.app.ui.theme.NodeAccent
import com.bruceremote.app.ui.theme.NodeBg
import com.bruceremote.app.ui.theme.NodeErr
import com.bruceremote.app.ui.theme.NodePrimary

@Composable
fun TerminalScreen(
    lines: List<LogLine>,
    connected: Boolean,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(BruceSurface, RoundedCornerShape(8.dp))
                .border(1.dp, BruceBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(lines) { line -> LogRow(line) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Command… e.g. info, help, reboot",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NodePrimary,
                    unfocusedBorderColor = BruceBorder
                ),
                singleLine = true
            )
            Button(
                enabled = connected && input.isNotBlank(),
                onClick = {
                    onSend(input)
                    input = ""
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NodePrimary,
                    contentColor = NodeBg
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val (prefix, color) = when (line.direction) {
        LogDirection.TX -> "→ " to NodePrimary
        LogDirection.RX -> "← " to NodeAccent
        LogDirection.SYSTEM -> "• " to NodeErr
    }
    Text(
        text = prefix + line.text,
        color = if (line.direction == LogDirection.RX) NodeAccent else color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
    )
}
