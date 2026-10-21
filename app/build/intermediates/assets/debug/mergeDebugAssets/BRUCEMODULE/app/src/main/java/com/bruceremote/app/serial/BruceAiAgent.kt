package com.bruceremote.app.serial

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

data class AiAnalysisResult(
    val explanation: String,
    val commandsToExecute: List<String>
)

class BruceAiAgent(private val fileManager: LocalFileToolsManager? = null) {

    private val ipPattern = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    fun getBaseUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return "http://192.168.1.100:1234"
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "http://$trimmed"
        }
        val uri = try { URI(trimmed) } catch (_: Exception) { null }
        return if (uri != null && uri.host != null) {
            val scheme = uri.scheme ?: "http"
            val host = uri.host
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$scheme://$host$port"
        } else {
            trimmed.substringBefore("/v1").substringBefore("/api").trimEnd('/')
        }
    }

    /**
     * AI Intent, Smalltalk, Code Generation, and File Tools Processor.
     * Supports LM Studio / Tailscale Agent Tools, Qwen LLM, Local Fast Engine, and Gemini.
     */
    fun processPrompt(
        prompt: String,
        engineType: AiEngineType = AiEngineType.LMSTUDIO_AGENT,
        apiKey: String = "",
        localServerUrl: String = "",
        qwenModelTag: String = "local-model"
    ): AiAnalysisResult {
        return when (engineType) {
            AiEngineType.LMSTUDIO_AGENT -> queryLmStudioAgentWithTools(prompt, localServerUrl, apiKey, qwenModelTag)
            AiEngineType.QWEN_LLM -> queryQwenLlm(prompt, localServerUrl, qwenModelTag)
            AiEngineType.GEMINI_LLM -> queryGeminiLlm(prompt, apiKey)
            AiEngineType.LOCAL_QUICK -> processLocalHeuristic(prompt)
        }
    }

    /**
     * Dynamically queries GET /v1/models from LM Studio to fetch active loaded models.
     */
    fun fetchLmStudioLoadedModels(hostUrl: String, apiKey: String = ""): List<String> {
        val baseUrl = getBaseUrl(hostUrl)
        val endpoints = listOf(
            "$baseUrl/v1/models",
            "$baseUrl/models",
            "$baseUrl/api/v0/models"
        )

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
                    val rawStream = if (isGzip) GZIPInputStream(conn.inputStream) else conn.inputStream
                    val responseText = rawStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                    if (responseText.trim().startsWith("{")) {
                        val root = JSONObject(responseText)
                        val data = root.optJSONArray("data")
                        if (data != null && data.length() > 0) {
                            val modelsList = mutableListOf<String>()
                            for (i in 0 until data.length()) {
                                val modelObj = data.getJSONObject(i)
                                val modelId = modelObj.optString("id", "")
                                if (modelId.isNotBlank()) modelsList.add(modelId)
                            }
                            if (modelsList.isNotEmpty()) return modelsList
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun queryLmStudioAgentWithTools(
        prompt: String,
        hostUrl: String,
        apiKey: String,
        modelName: String
    ): AiAnalysisResult {
        val baseUrl = getBaseUrl(hostUrl)
        val endpoint = "$baseUrl/v1/chat/completions"

        var effectiveModelTag = modelName.trim()
        if (effectiveModelTag.isBlank() || effectiveModelTag.equals("local-model", ignoreCase = true) || effectiveModelTag.contains("(")) {
            val loadedModels = fetchLmStudioLoadedModels(hostUrl, apiKey)
            if (loadedModels.isNotEmpty()) {
                effectiveModelTag = loadedModels[0]
            }
        }

        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000

            val systemContent = """
Answer the user directly and concisely. Do not output intro phrases or repeat system prompt rules.

USB SERIAL COMMANDS (Append on new line if executing device tasks):
- WiFi: CMD: wifi scan | CMD: wifi ap | CMD: wifi deauth | CMD: wifi beacon | CMD: wifi stop
- BLE: CMD: ble scan | CMD: ble spam | CMD: ble sourapple | CMD: ble swiftpair | CMD: ble stop
- Ping: CMD: ping <ip_or_domain> (e.g. CMD: ping 192.168.1.1)
- System: CMD: info | CMD: uptime | CMD: free | CMD: i2c | CMD: settings | CMD: help
- Power: CMD: power reboot | CMD: power sleep | CMD: power off
- Navigation: CMD: nav sel | CMD: nav esc | CMD: nav up | CMD: nav down | CMD: nav menu

LOCAL FILE TOOLS (Append on new line if managing local files):
- FILE_WRITE: <filename> | <content>
- FILE_READ: <filename>
- FILE_LIST
- FILE_APPEND: <filename> | <content>
- FILE_DELETE: <filename>
""".trimIndent()

            val jsonBody = JSONObject().apply {
                if (effectiveModelTag.isNotBlank() && !effectiveModelTag.equals("local-model", ignoreCase = true)) {
                    put("model", effectiveModelTag)
                }
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemContent)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("stream", false)
                put("temperature", 0.3)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
                val rawStream = if (isGzip) GZIPInputStream(conn.inputStream) else conn.inputStream
                val responseText = rawStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                val rawAiContent = parseLmStudioResponse(responseText)
                val aiContent = sanitizeAiResponse(rawAiContent)

                var finalExplanation = aiContent
                val extractedCmds = extractCmdsFromText(aiContent, prompt)
                val fileToolResults = mutableListOf<String>()

                val lines = aiContent.split("\n")
                for (line in lines) {
                    val lineTrimmed = line.trim()
                    if (fileManager != null) {
                        if (lineTrimmed.contains("FILE_WRITE:", ignoreCase = true)) {
                            val parts = lineTrimmed.substringAfter("FILE_WRITE:").split("|", limit = 2)
                            val fname = parts.getOrNull(0)?.trim() ?: "notes.txt"
                            val fcontent = parts.getOrNull(1)?.trim() ?: ""
                            val res = fileManager.writeFile(fname, fcontent)
                            fileToolResults.add("📄 $res")
                        } else if (lineTrimmed.contains("FILE_READ:", ignoreCase = true)) {
                            val fname = lineTrimmed.substringAfter("FILE_READ:").trim()
                            val res = fileManager.readFile(fname)
                            fileToolResults.add("📖 **File '$fname' Content**:\n```\n$res\n```")
                        } else if (lineTrimmed.contains("FILE_LIST", ignoreCase = true)) {
                            val res = fileManager.listFiles()
                            fileToolResults.add(res)
                        } else if (lineTrimmed.contains("FILE_APPEND:", ignoreCase = true)) {
                            val parts = lineTrimmed.substringAfter("FILE_APPEND:").split("|", limit = 2)
                            val fname = parts.getOrNull(0)?.trim() ?: "notes.txt"
                            val fcontent = parts.getOrNull(1)?.trim() ?: ""
                            val res = fileManager.appendFile(fname, fcontent)
                            fileToolResults.add("📄 $res")
                        } else if (lineTrimmed.contains("FILE_DELETE:", ignoreCase = true)) {
                            val fname = lineTrimmed.substringAfter("FILE_DELETE:").trim()
                            val res = fileManager.deleteFile(fname)
                            fileToolResults.add("🗑️ $res")
                        }
                    }
                }

                if (fileToolResults.isNotEmpty()) {
                    finalExplanation = finalExplanation + "\n\n" + fileToolResults.joinToString("\n")
                }

                return AiAnalysisResult(explanation = finalExplanation, commandsToExecute = extractedCmds)
            } else {
                val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
                val errStream = conn.errorStream
                val rawErrStream = if (isGzip && errStream != null) GZIPInputStream(errStream) else errStream
                val errText = rawErrStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
                return AiAnalysisResult(
                    explanation = "LM Studio Server Error (HTTP ${conn.responseCode}): $errText",
                    commandsToExecute = emptyList()
                )
            }
        } catch (e: Exception) {
            return AiAnalysisResult(
                explanation = "LM Studio Connection Error: ${e.message}",
                commandsToExecute = emptyList()
            )
        }
    }

    private fun sanitizeAiResponse(rawText: String): String {
        var text = rawText.trim()
        val unwantedIntros = listOf(
            "You are an AI Agent",
            "You are an AI assistant",
            "I am an AI Agent",
            "I am an AI assistant",
            "I'm an AI Agent",
            "I'm an AI assistant",
            "As an AI Agent",
            "As an AI assistant",
            "As a Bruce MULTiPASS",
            "I am powered by a Bruce",
            "I'm powered by a Bruce"
        )

        for (intro in unwantedIntros) {
            if (text.startsWith(intro, ignoreCase = true)) {
                val firstDot = text.indexOf('.')
                val firstLine = text.indexOf('\n')
                val cutIndex = when {
                    firstDot != -1 && (firstLine == -1 || firstDot < firstLine) -> firstDot + 1
                    firstLine != -1 -> firstLine + 1
                    else -> 0
                }
                if (cutIndex > 0 && cutIndex < text.length) {
                    text = text.substring(cutIndex).trim()
                }
            }
        }
        return text
    }

    /**
     * Parses JSON object or SSE event streams returned by LM Studio / OpenAI endpoints.
     */
    private fun parseLmStudioResponse(rawResponseText: String): String {
        val trimmed = rawResponseText.trim()
        if (trimmed.isEmpty()) return ""

        // Case A: Standard Single JSON Object
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val root = JSONObject(trimmed)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.optJSONObject("message")
                    if (message != null) {
                        val content = message.optString("content", "")
                        if (content.isNotBlank()) return content
                    }
                    val text = firstChoice.optString("text", "")
                    if (text.isNotBlank()) return text
                }
                val directResponse = root.optString("response", "")
                if (directResponse.isNotBlank()) return directResponse
            } catch (_: Exception) {}
        }

        // Case B: SSE Event Stream lines
        val sb = StringBuilder()
        val lines = trimmed.split("\n")
        for (line in lines) {
            val lineTrimmed = line.trim()
            if (lineTrimmed.startsWith("data:") && !lineTrimmed.contains("[DONE]")) {
                val jsonStr = lineTrimmed.substringAfter("data:").trim()
                if (jsonStr.startsWith("{")) {
                    try {
                        val root = JSONObject(jsonStr)
                        val choices = root.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val delta = firstChoice.optJSONObject("delta")
                            if (delta != null) {
                                val content = delta.optString("content", "")
                                if (content.isNotBlank()) sb.append(content)
                            }
                            val message = firstChoice.optJSONObject("message")
                            if (message != null) {
                                val content = message.optString("content", "")
                                if (content.isNotBlank()) sb.append(content)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        val streamText = sb.toString()
        if (streamText.isNotBlank()) return streamText

        return trimmed
    }

    private fun queryQwenLlm(prompt: String, hostUrl: String, modelTag: String): AiAnalysisResult {
        val baseUrl = getBaseUrl(hostUrl)
        val endpoint = "$baseUrl/api/generate"
        val modelName = if (modelTag.isBlank()) "qwen2.5-coder-1.5b-instruct" else modelTag

        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 15000

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("prompt", prompt)
                put("stream", false)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val text = root.optString("response", "")
                if (text.isNotBlank()) {
                    val extractedCmds = extractCmdsFromText(text, prompt)
                    return AiAnalysisResult(explanation = text, commandsToExecute = extractedCmds)
                }
            }
        } catch (_: Exception) {}

        return processLocalHeuristic(prompt)
    }

    private fun queryGeminiLlm(prompt: String, apiKey: String): AiAnalysisResult {
        if (apiKey.isBlank()) {
            val localResult = processLocalHeuristic(prompt)
            return localResult.copy(
                explanation = "⚠️ **Gemini API Key Required**\nPlease configure your Gemini API key in AI Model Settings. Executing via Local Engine:\n\n" + localResult.explanation
            )
        }
        try {
            val systemPrompt = "You answer queries, write code, and control the Bruce device over USB serial."

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 12000

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", systemPrompt) })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val text = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val extractedCmds = extractCmdsFromText(text, prompt)
                return AiAnalysisResult(explanation = text, commandsToExecute = extractedCmds)
            }
        } catch (_: Exception) {}

        return processLocalHeuristic(prompt)
    }

    private fun extractCmdsFromText(text: String, originalPrompt: String = ""): List<String> {
        val cmds = mutableListOf<String>()

        // Pattern 1: Explicit CMD: / Command: / Executing: prefixes
        val lines = text.split("\n")
        for (line in lines) {
            val lineTrimmed = line.trim()
            val lower = lineTrimmed.lowercase()

            if (lower.contains("cmd:") || lower.contains("command:") || lower.contains("executing:") || lower.contains("running:")) {
                val rawCmd = when {
                    lower.contains("cmd:") -> lineTrimmed.substringAfter("CMD:")
                    lower.contains("command:") -> lineTrimmed.substringAfter("command:")
                    lower.contains("executing:") -> lineTrimmed.substringAfter("executing:")
                    else -> lineTrimmed.substringAfter("running:")
                }.trim().removeSurrounding("`").removeSurrounding("\"").removeSurrounding("'")

                if (rawCmd.isNotBlank()) {
                    cmds.add(rawCmd)
                }
            }
        }

        // Pattern 2: Code blocks containing valid serial commands (e.g. ```sh\nping 192.168.1.1\n```)
        val codeBlockRegex = Regex("```(?:sh|bash|cli)?\\n?([\\s\\S]*?)```")
        codeBlockRegex.findAll(text).forEach { match ->
            val snippet = match.groupValues[1].trim()
            snippet.split("\n").forEach { codeLine ->
                val cleanLine = codeLine.trim().removePrefix("$ ").removePrefix("> ")
                if (isValidBruceCommand(cleanLine)) {
                    cmds.add(cleanLine)
                }
            }
        }

        // Pattern 3: Direct Bruce command recognition in text or original prompt fallback
        if (cmds.isEmpty()) {
            val combined = (text + "\n" + originalPrompt).lowercase()
            val recognizedCmd = findBruceCommandInText(combined, originalPrompt)
            if (recognizedCmd != null) {
                cmds.add(recognizedCmd)
            }
        }

        return cmds.distinct()
    }

    private fun isValidBruceCommand(cmd: String): Boolean {
        val lower = cmd.lowercase().trim()
        return lower.startsWith("ping ") ||
                lower.startsWith("wifi ") ||
                lower.startsWith("ble ") ||
                lower.startsWith("power ") ||
                lower.startsWith("nav ") ||
                lower in listOf("info", "uptime", "free", "i2c", "settings", "help", "display dump", "display start", "display stop")
    }

    private fun findBruceCommandInText(text: String, rawPrompt: String): String? {
        val lowerText = text.lowercase()
        val lowerPrompt = rawPrompt.lowercase()

        // Check for ping IP
        val ipMatch = ipPattern.matcher(rawPrompt)
        if (ipMatch.find() || lowerPrompt.contains("ping")) {
            val target = if (ipMatch.find()) ipMatch.group() else rawPrompt.substringAfter("ping").trim().takeWhile { !it.isWhitespace() }
            if (target.isNotBlank()) return "ping $target"
        }

        // Check for WiFi actions
        if (lowerPrompt.contains("wifi") || lowerText.contains("wifi")) {
            return when {
                lowerPrompt.contains("deauth") || lowerText.contains("deauth") -> "wifi deauth"
                lowerPrompt.contains("ap") || lowerText.contains("ap") -> "wifi ap"
                lowerPrompt.contains("beacon") || lowerText.contains("beacon") -> "wifi beacon"
                lowerPrompt.contains("probe") || lowerText.contains("probe") -> "wifi probe"
                lowerPrompt.contains("stop") || lowerText.contains("stop") -> "wifi stop"
                else -> "wifi scan"
            }
        }

        // Check for BLE actions
        if (lowerPrompt.contains("ble") || lowerPrompt.contains("bluetooth") || lowerText.contains("ble") || lowerText.contains("bluetooth")) {
            return when {
                lowerPrompt.contains("apple") || lowerPrompt.contains("sourapple") -> "ble sourapple"
                lowerPrompt.contains("swift") || lowerPrompt.contains("windows") -> "ble swiftpair"
                lowerPrompt.contains("android") -> "ble android"
                lowerPrompt.contains("samsung") -> "ble samsung"
                lowerPrompt.contains("stop") -> "ble stop"
                lowerPrompt.contains("spam") -> "ble spam"
                else -> "ble scan"
            }
        }

        // Check for system status / power commands
        if (lowerPrompt.contains("info") || lowerPrompt.contains("status")) return "info"
        if (lowerPrompt.contains("uptime")) return "uptime"
        if (lowerPrompt.contains("free") || lowerPrompt.contains("memory") || lowerPrompt.contains("heap")) return "free"
        if (lowerPrompt.contains("i2c")) return "i2c"
        if (lowerPrompt.contains("reboot") || lowerPrompt.contains("restart")) return "power reboot"
        if (lowerPrompt.contains("sleep")) return "power sleep"

        return null
    }

    private fun processLocalHeuristic(prompt: String): AiAnalysisResult {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase()

        // Local File tools check for heuristic engine
        if (fileManager != null) {
            if (lower.startsWith("save ") || lower.startsWith("write file") || lower.contains("save to file")) {
                val fname = "notes.txt"
                val res = fileManager.writeFile(fname, trimmed)
                return AiAnalysisResult(explanation = "📄 $res", commandsToExecute = emptyList())
            }
            if (lower.contains("list files") || lower.contains("show files")) {
                val res = fileManager.listFiles()
                return AiAnalysisResult(explanation = res, commandsToExecute = emptyList())
            }
        }

        // 1. IP / Domain Ping Detection
        val cmds = mutableListOf<String>()
        if (lower.contains("ping")) {
            val ipMatch = ipPattern.matcher(trimmed)
            val target = if (ipMatch.find()) ipMatch.group()
            else trimmed.substringAfter("ping").trim().ifBlank { "192.168.1.1" }
            val cmd = "ping $target"
            cmds.add(cmd)
            return AiAnalysisResult(
                explanation = "Executing '$cmd' over USB serial.",
                commandsToExecute = cmds
            )
        }

        // 2. WiFi Tasks
        if (lower.contains("wifi")) {
            val cmd = when {
                lower.contains("deauth") -> "wifi deauth"
                lower.contains("ap") -> "wifi ap"
                lower.contains("beacon") -> "wifi beacon"
                lower.contains("stop") -> "wifi stop"
                else -> "wifi scan"
            }
            cmds.add(cmd)
            return AiAnalysisResult(
                explanation = "Executing '$cmd' via USB serial.",
                commandsToExecute = cmds
            )
        }

        // 3. BLE Tasks
        if (lower.contains("ble") || lower.contains("bluetooth") || lower.contains("spam")) {
            val cmd = when {
                lower.contains("apple") || lower.contains("sourapple") -> "ble sourapple"
                lower.contains("swift") || lower.contains("windows") -> "ble swiftpair"
                lower.contains("stop") -> "ble stop"
                else -> "ble spam"
            }
            cmds.add(cmd)
            return AiAnalysisResult(
                explanation = "Executing '$cmd' over USB serial.",
                commandsToExecute = cmds
            )
        }

        // 4. System Commands
        if (lower.contains("info") || lower.contains("memory") || lower.contains("reboot") || lower.contains("free") || lower.contains("uptime")) {
            val cmd = when {
                lower.contains("reboot") -> "power reboot"
                lower.contains("free") || lower.contains("memory") -> "free"
                lower.contains("uptime") -> "uptime"
                else -> "info"
            }
            cmds.add(cmd)
            return AiAnalysisResult(
                explanation = "Executing '$cmd' on device.",
                commandsToExecute = cmds
            )
        }

        // 5. Conversational Fallback
        return AiAnalysisResult(
            explanation = trimmed,
            commandsToExecute = emptyList()
        )
    }
}
