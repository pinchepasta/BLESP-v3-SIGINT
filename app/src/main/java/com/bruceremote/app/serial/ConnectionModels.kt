package com.bruceremote.app.serial

import java.util.UUID

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object RequestingPermission : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

enum class LogDirection { RX, TX, SYSTEM }

data class LogLine(
    val direction: LogDirection,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/** Navigation directions the device's remote-display mode understands, matching `nav <dir>`. */
enum class NavDirection(val wireValue: String, val label: String) {
    UP("up", "▲"),
    DOWN("down", "▼"),
    PREV("prev", "◀"),
    NEXT("next", "▶"),
    SELECT("sel", "●"),
    ESCAPE("esc", "⟲"),
    MENU("menu", "M"),
    NEXT_PAGE("nextpage", "Pg▲"),
    PREV_PAGE("prevpage", "Pg▼")
}

enum class ActionCategory(val displayName: String) {
    ALL("All"),
    WIFI("WiFi"),
    BLE("BLE"),
    SYSTEM("System"),
    CUSTOM("Custom")
}

data class QuickAction(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val category: ActionCategory = ActionCategory.CUSTOM,
    val isCustom: Boolean = false
)

enum class MessageSender { USER, AI }

enum class AiEngineType(val displayName: String) {
    LMSTUDIO_AGENT("LM Studio / Tailscale Agent"),
    QWEN_LLM("Qwen 1.5B - 3B Local / Server LLM"),
    LOCAL_QUICK("Local OpenClaw Quick Engine"),
    GEMINI_LLM("Google Gemini 1.5 LLM")
}

data class VisibleScreenText(
    val text: String,
    val x: Float,
    val y: Float,
    val isSelected: Boolean = false
)

enum class TaskStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

data class AgentTaskStep(
    val title: String,
    val details: String,
    var isDone: Boolean = false
)

data class ActiveAgentTask(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val recognizedScreenSummary: String,
    val steps: List<AgentTaskStep>,
    val commandsExecuted: List<String>,
    val status: TaskStatus = TaskStatus.COMPLETED
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val task: ActiveAgentTask? = null,
    val commandExecuted: String? = null,
    val commandResponse: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
