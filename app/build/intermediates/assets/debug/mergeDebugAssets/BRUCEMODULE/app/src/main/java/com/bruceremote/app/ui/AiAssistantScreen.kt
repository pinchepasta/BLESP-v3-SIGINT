package com.bruceremote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bruceremote.app.serial.AiEngineType
import com.bruceremote.app.serial.ChatMessage
import com.bruceremote.app.serial.MessageSender
import com.bruceremote.app.ui.theme.BruceBorder
import com.bruceremote.app.ui.theme.BruceMuted
import com.bruceremote.app.ui.theme.BruceSurface
import com.bruceremote.app.ui.theme.BruceSurfaceAlt
import com.bruceremote.app.ui.theme.NodeAccent
import com.bruceremote.app.ui.theme.NodeBg
import com.bruceremote.app.ui.theme.NodeFg
import com.bruceremote.app.ui.theme.NodePrimary

private val defaultSuggestions = listOf(
    "ping 192.168.1.1",
    "wifi scan",
    "ble spam",
    "device info",
    "free memory",
    "reboot"
)

@Composable
fun AiAssistantScreen(
    messages: List<ChatMessage>,
    connected: Boolean,
    aiEngine: AiEngineType,
    lmStudioUrl: String,
    lmStudioApiKey: String,
    lmStudioModelName: String,
    onSendMessage: (String) -> Unit,
    onSetAiEngine: (AiEngineType) -> Unit,
    onSetLmStudioConfig: (serverUrl: String, apiKey: String, modelName: String) -> Unit,
    onFetchLmStudioModels: (onResult: (List<String>) -> Unit) -> Unit,
    onSaveFile: (filename: String, content: String) -> String
) {
    var input by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun triggerSend() {
        if (input.isNotBlank()) {
            val promptToSend = input
            input = ""
            onSendMessage(promptToSend)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // AI Status Banner & Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = BruceSurfaceAlt),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = NodeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (aiEngine == AiEngineType.LMSTUDIO_AGENT) "Model: $lmStudioModelName" else aiEngine.displayName,
                            color = NodeAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            if (aiEngine == AiEngineType.LMSTUDIO_AGENT) "LM Studio Server • $lmStudioUrl" else "On-Device Screen OCR & USB Task Agent",
                            color = BruceMuted,
                            fontSize = 9.5.sp
                        )
                    }
                }
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = NodePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Chat Message Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(BruceSurface, RoundedCornerShape(8.dp))
                .border(1.dp, BruceBorder, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🤖 AI Assistant Ready.\nModel: $lmStudioModelName\nServer: $lmStudioUrl",
                            color = BruceMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg, onSaveFile)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Quick Suggestion Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            items(defaultSuggestions) { suggestion ->
                SuggestionChip(
                    onClick = {
                        input = suggestion
                        triggerSend()
                    },
                    label = { Text(suggestion, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = BruceSurfaceAlt,
                        labelColor = NodePrimary
                    )
                )
            }
        }

        // Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Ask AI Assistant e.g. ping 192.168.1.1…",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NodePrimary,
                    unfocusedBorderColor = BruceBorder
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { triggerSend() }),
                singleLine = true
            )
            Button(
                enabled = input.isNotBlank(),
                onClick = { triggerSend() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NodePrimary,
                    contentColor = NodeBg
                ),
                shape = CircleShape,
                contentPadding = PaddingValues(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }

    if (showSettingsDialog) {
        LmStudioSettingsDialog(
            aiEngine = aiEngine,
            lmStudioUrl = lmStudioUrl,
            lmStudioApiKey = lmStudioApiKey,
            lmStudioModelName = lmStudioModelName,
            onDismiss = { showSettingsDialog = false },
            onSetAiEngine = onSetAiEngine,
            onSaveConfig = onSetLmStudioConfig,
            onFetchModels = onFetchLmStudioModels
        )
    }
}

@Composable
private fun LmStudioSettingsDialog(
    aiEngine: AiEngineType,
    lmStudioUrl: String,
    lmStudioApiKey: String,
    lmStudioModelName: String,
    onDismiss: () -> Unit,
    onSetAiEngine: (AiEngineType) -> Unit,
    onSaveConfig: (serverUrl: String, apiKey: String, modelName: String) -> Unit,
    onFetchModels: (onResult: (List<String>) -> Unit) -> Unit
) {
    var selectedEngine by remember { mutableStateOf(aiEngine) }
    var serverUrl by remember { mutableStateOf(lmStudioUrl) }
    var apiKey by remember { mutableStateOf(lmStudioApiKey) }
    var modelName by remember { mutableStateOf(lmStudioModelName) }
    var isFetching by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LM Studio & Agent Settings", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select AI Agent Engine:", fontSize = 11.sp, color = BruceMuted)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AiEngineType.values()) { engine ->
                        FilterChip(
                            selected = selectedEngine == engine,
                            onClick = {
                                selectedEngine = engine
                                onSetAiEngine(engine)
                            },
                            label = { Text(engine.displayName, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NodePrimary,
                                selectedLabelColor = NodeBg,
                                containerColor = BruceSurface,
                                labelColor = NodePrimary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("LM Studio / Tailscale Server URL", fontSize = 11.sp) },
                    placeholder = { Text("http://100.x.y.z:1234/v1 or http://192.168.1.100:1234", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NodePrimary,
                        unfocusedBorderColor = BruceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Bearer Token (Optional)", fontSize = 11.sp) },
                    placeholder = { Text("Bearer token if required", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NodePrimary,
                        unfocusedBorderColor = BruceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Active Loaded Model", fontSize = 11.sp) },
                        placeholder = { Text("e.g. qwen2.5-coder-1.5b-instruct or local-model", fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NodePrimary,
                            unfocusedBorderColor = BruceBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            isFetching = true
                            onFetchModels { fetchedList ->
                                isFetching = false
                                if (fetchedList.isNotEmpty()) {
                                    modelName = fetchedList[0]
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NodeAccent, contentColor = NodeBg),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (isFetching) "Fetching…" else "Fetch Models", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveConfig(serverUrl, apiKey, modelName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NodePrimary, contentColor = NodeBg)
            ) {
                Text("Save", fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 11.sp) }
        }
    )
}

private data class MessagePart(
    val isCode: Boolean,
    val text: String,
    val language: String = ""
)

private fun parseMessageParts(fullText: String): List<MessagePart> {
    if (!fullText.contains("```")) {
        return listOf(MessagePart(isCode = false, text = fullText))
    }
    val parts = mutableListOf<MessagePart>()
    try {
        val regex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")
        var lastIndex = 0

        regex.findAll(fullText).forEach { match ->
            val startIndex = match.range.first
            val endIndex = match.range.first + match.value.length

            if (startIndex > lastIndex) {
                val normalText = fullText.substring(lastIndex, startIndex).trim()
                if (normalText.isNotEmpty()) {
                    parts.add(MessagePart(isCode = false, text = normalText))
                }
            }

            val lang = match.groupValues[1].ifBlank { "code" }
            val codeText = match.groupValues[2].trim()
            if (codeText.isNotEmpty()) {
                parts.add(MessagePart(isCode = true, text = codeText, language = lang.uppercase()))
            }

            lastIndex = endIndex
        }

        if (lastIndex < fullText.length) {
            val remainingText = fullText.substring(lastIndex).trim()
            if (remainingText.isNotEmpty()) {
                parts.add(MessagePart(isCode = false, text = remainingText))
            }
        }
    } catch (_: Exception) {
        return listOf(MessagePart(isCode = false, text = fullText))
    }

    if (parts.isEmpty()) {
        parts.add(MessagePart(isCode = false, text = fullText))
    }

    return parts
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onSaveFile: (filename: String, content: String) -> String
) {
    val context = LocalContext.current
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) NodePrimary else BruceSurfaceAlt
    val textColor = if (isUser) NodeBg else NodeAccent

    var showSaveDialog by remember { mutableStateOf(false) }

    fun copyToClipboard(textToCopy: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Output", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val parts = remember(message.text) { parseMessageParts(message.text) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "AI Agent",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isUser) NodeBg.copy(alpha = 0.7f) else NodePrimary
                    )

                    if (!isUser) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(
                                onClick = { copyToClipboard(message.text) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("📋 Copy", fontSize = 8.5.sp, color = NodePrimary, fontFamily = FontFamily.Monospace)
                            }
                            TextButton(
                                onClick = { showSaveDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("💾 Save", fontSize = 8.5.sp, color = NodeAccent, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                parts.forEach { part ->
                    if (part.isCode) {
                        CodeBlockCard(
                            codeText = part.text,
                            language = part.language,
                            onCopy = { copyToClipboard(it) },
                            onSave = { fname, content ->
                                val result = onSaveFile(fname, content)
                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        Text(
                            text = part.text,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveFileDialog(
            initialFilename = "ai_output_${System.currentTimeMillis() / 1000}.txt",
            contentToSave = message.text,
            onDismiss = { showSaveDialog = false },
            onConfirmSave = { fname ->
                val result = onSaveFile(fname, message.text)
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun CodeBlockCard(
    codeText: String,
    language: String,
    onCopy: (String) -> Unit,
    onSave: (String, String) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    val ext = when (language.lowercase()) {
        "python", "py" -> "py"
        "cpp", "c++", "c" -> "cpp"
        "bash", "sh", "shell" -> "sh"
        "json" -> "json"
        "kotlin", "kt" -> "kt"
        "html" -> "html"
        else -> "txt"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NodeBg),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, BruceBorder, RoundedCornerShape(6.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BruceSurfaceAlt)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💻 $language",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = NodeAccent
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = { onCopy(codeText) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("📋 Copy", fontSize = 8.5.sp, color = NodePrimary, fontFamily = FontFamily.Monospace)
                    }
                    TextButton(
                        onClick = { showSaveDialog = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("💾 Save File", fontSize = 8.5.sp, color = NodeAccent, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Text(
                text = codeText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = NodeFg,
                modifier = Modifier.padding(8.dp)
            )
        }
    }

    if (showSaveDialog) {
        SaveFileDialog(
            initialFilename = "script.$ext",
            contentToSave = codeText,
            onDismiss = { showSaveDialog = false },
            onConfirmSave = { fname ->
                onSave(fname, codeText)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun SaveFileDialog(
    initialFilename: String,
    contentToSave: String,
    onDismiss: () -> Unit,
    onConfirmSave: (String) -> Unit
) {
    var filename by remember { mutableStateOf(initialFilename) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save File to Download/bruce_remote", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Saves directly to public phone storage at:\nDownload/bruce_remote/",
                    fontSize = 10.sp,
                    color = BruceMuted
                )
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("Filename", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NodePrimary,
                        unfocusedBorderColor = BruceBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSave(filename) },
                colors = ButtonDefaults.buttonColors(containerColor = NodePrimary, contentColor = NodeBg)
            ) {
                Text("Save File", fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontSize = 11.sp) }
        }
    )
}
