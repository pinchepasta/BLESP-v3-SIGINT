package com.bruceremote.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.toArgb
import com.bruceremote.app.serial.ActionCategory
import com.bruceremote.app.serial.AiEngineType
import com.bruceremote.app.serial.BruceAiAgent
import com.bruceremote.app.serial.BruceDisplayRenderer
import com.bruceremote.app.serial.BruceTaskAgent
import com.bruceremote.app.serial.ChatMessage
import com.bruceremote.app.serial.ConnectionState
import com.bruceremote.app.serial.LocalQwenEngine
import com.bruceremote.app.serial.LogDirection
import com.bruceremote.app.serial.LogLine
import com.bruceremote.app.serial.MessageSender
import com.bruceremote.app.serial.NavDirection
import com.bruceremote.app.serial.QuickAction
import com.bruceremote.app.serial.SerialStreamParser
import com.bruceremote.app.serial.UsbSerialManager
import com.bruceremote.app.ui.theme.AppTheme
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.bruceremote.app.serial.LocalFileToolsManager
import com.radar.blewifi.RadarView
import com.radar.blewifi.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BruceViewModel(application: Application) : AndroidViewModel(application) {

    val fileManager = LocalFileToolsManager(application)
    val display = BruceDisplayRenderer(application)
    val localQwenEngine = LocalQwenEngine(application)
    private val aiAgent = BruceAiAgent(fileManager)
    private val taskAgent = BruceTaskAgent(aiAgent)

    private val _currentTheme = MutableStateFlow(AppTheme.NEON_ORANGE)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _showIcons = MutableStateFlow(true)
    val showIcons: StateFlow<Boolean> = _showIcons.asStateFlow()

    private val _quickActions = MutableStateFlow<List<QuickAction>>(emptyList())
    val quickActions: StateFlow<List<QuickAction>> = _quickActions.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<ChatMessage>> = _aiChatMessages.asStateFlow()

    private val _aiEngine = MutableStateFlow(AiEngineType.LMSTUDIO_AGENT)
    val aiEngine: StateFlow<AiEngineType> = _aiEngine.asStateFlow()

    private val _lmStudioUrl = MutableStateFlow("http://192.168.1.100:1234")
    val lmStudioUrl: StateFlow<String> = _lmStudioUrl.asStateFlow()

    private val _lmStudioApiKey = MutableStateFlow("")
    val lmStudioApiKey: StateFlow<String> = _lmStudioApiKey.asStateFlow()

    private val _lmStudioModelName = MutableStateFlow("local-model")
    val lmStudioModelName: StateFlow<String> = _lmStudioModelName.asStateFlow()

    private val _qwenModelTag = MutableStateFlow("Qwen2.5-Coder-1.5B-Instruct (Q4_K_M)")
    val qwenModelTag: StateFlow<String> = _qwenModelTag.asStateFlow()

    private val _qwenServerUrl = MutableStateFlow("http://192.168.1.100:11434")
    val qwenServerUrl: StateFlow<String> = _qwenServerUrl.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val settingsPrefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        loadQuickActions()
        loadAiSettings()
        applyThemeColors()
        settingsPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_name") applyThemeColors()
        }
    }

    private fun applyThemeColors() {
        val themeName = settingsPrefs.getString("theme_name", "DEFAULT") ?: "DEFAULT"
        val theme = try { RadarView.Theme.valueOf(themeName) } catch (_: Exception) { RadarView.Theme.DEFAULT }
        val colors = ThemeUtils.getThemeColors(theme)
        display.primaryColor = colors.primary
        display.accentColor = colors.accent
        display.drawInitialFrame()
    }

    private fun loadAiSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        val engineStr = prefs.getString("ai_engine_type", AiEngineType.LMSTUDIO_AGENT.name)
        val engine = try { AiEngineType.valueOf(engineStr!!) } catch (_: Exception) { AiEngineType.LMSTUDIO_AGENT }
        _aiEngine.value = engine
        _lmStudioUrl.value = prefs.getString("lm_studio_url", "http://192.168.1.100:1234") ?: "http://192.168.1.100:1234"
        _lmStudioApiKey.value = prefs.getString("lm_studio_api_key", "") ?: ""
        _lmStudioModelName.value = prefs.getString("lm_studio_model", "local-model") ?: "local-model"
        _qwenModelTag.value = prefs.getString("qwen_model_tag", "Qwen2.5-Coder-1.5B-Instruct (Q4_K_M)") ?: "Qwen2.5-Coder-1.5B-Instruct (Q4_K_M)"
        _qwenServerUrl.value = prefs.getString("qwen_server_url", "http://192.168.1.100:11434") ?: "http://192.168.1.100:11434"
        _geminiApiKey.value = prefs.getString("gemini_api_key", "") ?: ""
    }

    fun setAiEngine(engine: AiEngineType) {
        _aiEngine.value = engine
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ai_engine_type", engine.name).apply()
    }

    fun setLmStudioConfig(serverUrl: String, apiKey: String, modelName: String) {
        _lmStudioUrl.value = serverUrl.trim()
        _lmStudioApiKey.value = apiKey.trim()
        _lmStudioModelName.value = modelName.trim()
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("lm_studio_url", serverUrl.trim())
            .putString("lm_studio_api_key", apiKey.trim())
            .putString("lm_studio_model", modelName.trim())
            .apply()
    }

    fun saveContentToFile(filename: String, content: String): String {
        return fileManager.writeFile(filename, content)
    }

    fun fetchLmStudioModels(onResult: (List<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val models = aiAgent.fetchLmStudioLoadedModels(_lmStudioUrl.value, _lmStudioApiKey.value)
            viewModelScope.launch(Dispatchers.Main) {
                if (models.isNotEmpty()) {
                    _lmStudioModelName.value = models[0]
                }
                onResult(models)
            }
        }
    }

    fun setQwenConfig(modelTag: String, serverUrl: String) {
        _qwenModelTag.value = modelTag.trim()
        _qwenServerUrl.value = serverUrl.trim()
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("qwen_model_tag", modelTag.trim())
            .putString("qwen_server_url", serverUrl.trim())
            .apply()
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key.trim()
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        display.primaryColor = theme.primary.toArgb()
        display.accentColor = theme.accent.toArgb()
        display.drawInitialFrame()
    }

    fun setShowIcons(show: Boolean) {
        _showIcons.value = show
        display.renderIcons = show
        if (connectionState.value is ConnectionState.Connected) {
            requestDump()
        }
    }

    fun sendAiPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val userMsg = ChatMessage(sender = MessageSender.USER, text = prompt)
        _aiChatMessages.value = _aiChatMessages.value + userMsg

        viewModelScope.launch {
            val screenTexts = display.getRecognizedScreenText()

            val effectiveUrl = if (_aiEngine.value == AiEngineType.LMSTUDIO_AGENT) _lmStudioUrl.value else _qwenServerUrl.value
            val effectiveKey = if (_aiEngine.value == AiEngineType.LMSTUDIO_AGENT) _lmStudioApiKey.value else _geminiApiKey.value
            val effectiveTag = if (_aiEngine.value == AiEngineType.LMSTUDIO_AGENT) _lmStudioModelName.value else _qwenModelTag.value

            val taskResult = withContext(Dispatchers.IO) {
                taskAgent.processTaskRequest(
                    prompt = prompt,
                    screenTexts = screenTexts,
                    engineType = _aiEngine.value,
                    apiKey = effectiveKey,
                    localServerUrl = effectiveUrl,
                    qwenModelTag = effectiveTag,
                    localQwenEngine = localQwenEngine
                )
            }
            var responseOutput = ""

            if (connectionState.value is ConnectionState.Connected) {
                taskResult.commandsToSend.forEach { cmd ->
                    sendCommand(cmd)
                    delay(300)
                }

                val recentLogs = _logLines.value.takeLast(10).filter { it.direction == LogDirection.RX }
                if (recentLogs.isNotEmpty()) {
                    responseOutput = recentLogs.joinToString("\n") { it.text }
                }
            } else {
                responseOutput = "USB Device Disconnected. Serial commands will execute automatically once connected."
            }

            val aiMsg = ChatMessage(
                sender = MessageSender.AI,
                text = taskResult.explanationText,
                task = taskResult.task,
                commandExecuted = if (taskResult.commandsToSend.isNotEmpty()) taskResult.commandsToSend.joinToString(", ") else null,
                commandResponse = responseOutput
            )
            _aiChatMessages.value = _aiChatMessages.value + aiMsg
        }
    }

    private fun loadQuickActions() {
        val custom = loadCustomActionsFromPrefs()
        _quickActions.value = DEFAULT_QUICK_ACTIONS + custom
    }

    fun addQuickAction(label: String, command: String, category: ActionCategory) {
        val newAction = QuickAction(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            command = command.trim(),
            category = category,
            isCustom = true
        )
        val updatedCustom = loadCustomActionsFromPrefs() + newAction
        saveCustomActionsToPrefs(updatedCustom)
        _quickActions.value = DEFAULT_QUICK_ACTIONS + updatedCustom
    }

    fun editQuickAction(id: String, label: String, command: String, category: ActionCategory) {
        val currentCustom = loadCustomActionsFromPrefs()
        val updatedCustom = currentCustom.map {
            if (it.id == id) it.copy(label = label.trim(), command = command.trim(), category = category)
            else it
        }
        saveCustomActionsToPrefs(updatedCustom)
        _quickActions.value = DEFAULT_QUICK_ACTIONS + updatedCustom
    }

    fun deleteQuickAction(id: String) {
        val currentCustom = loadCustomActionsFromPrefs()
        val updatedCustom = currentCustom.filterNot { it.id == id }
        saveCustomActionsToPrefs(updatedCustom)
        _quickActions.value = DEFAULT_QUICK_ACTIONS + updatedCustom
    }

    private fun saveCustomActionsToPrefs(customActions: List<QuickAction>) {
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        val array = JSONArray()
        customActions.forEach { action ->
            val obj = JSONObject().apply {
                put("id", action.id)
                put("label", action.label)
                put("command", action.command)
                put("category", action.category.name)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_quick_actions", array.toString()).apply()
    }

    private fun loadCustomActionsFromPrefs(): List<QuickAction> {
        val prefs = getApplication<Application>().getSharedPreferences("bruce_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_quick_actions", null) ?: return emptyList()
        val list = mutableListOf<QuickAction>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val label = obj.optString("label", "")
                val command = obj.optString("command", "")
                val catName = obj.optString("category", ActionCategory.CUSTOM.name)
                val category = try { ActionCategory.valueOf(catName) } catch (_: Exception) { ActionCategory.CUSTOM }
                if (label.isNotBlank() && command.isNotBlank()) {
                    list.add(QuickAction(id = id, label = label, command = command, category = category, isCustom = true))
                }
            }
        } catch (_: Exception) { }
        return list
    }

    private val parser = SerialStreamParser(
        onBinaryPacket = { packet -> display.handlePacket(packet) },
        onTextChunk = { text -> appendLog(LogDirection.RX, text) }
    )

    private val usb = UsbSerialManager(application) { bytes -> parser.feed(bytes) }

    val connectionState: StateFlow<ConnectionState> = usb.state

    private val _logLines = MutableStateFlow<List<LogLine>>(emptyList())
    val logLines: StateFlow<List<LogLine>> = _logLines.asStateFlow()

    private val _navigatorOpen = MutableStateFlow(false)
    val navigatorOpen: StateFlow<Boolean> = _navigatorOpen.asStateFlow()

    fun availableDevices(): List<UsbSerialDriver> = usb.availableDevices()

    fun connect(driver: UsbSerialDriver, portIndex: Int = 0) {
        usb.connect(driver, portIndex = portIndex, baudRate = 115200)
    }

    fun disconnect() {
        closeNavigator()
        usb.disconnect()
    }

    /** Puts the device into remote-display streaming mode (`display start`) and dumps screen. */
    fun openNavigator() {
        _navigatorOpen.value = true
        viewModelScope.launch {
            sendLine("display start")
            delay(150)
            requestDump()
        }
    }

    fun closeNavigator() {
        if (!_navigatorOpen.value) return
        _navigatorOpen.value = false
        sendLine("display stop")
    }

    fun sendNav(direction: NavDirection) {
        sendLine("nav ${direction.wireValue}")
    }

    /** One-shot full-screen refresh via the text-safe hex dump command. */
    fun requestDump() {
        parser.beginDumpCapture()
        sendLine("display dump")
    }

    fun sendCommand(text: String) {
        if (text.isBlank()) return
        val trimmed = text.trim()

        if (navigatorOpen.value && !trimmed.startsWith("nav ", ignoreCase = true) && !trimmed.startsWith("display ", ignoreCase = true)) {
            viewModelScope.launch {
                sendLine("display stop")
                delay(100)
                sendLine(trimmed)
                delay(200)
                sendLine("display start")
                delay(100)
                requestDump()
            }
        } else {
            sendLine(trimmed)
        }
    }

    private fun sendLine(text: String) {
        appendLog(LogDirection.TX, text)
        usb.sendLine(text)
    }

    private fun appendLog(direction: LogDirection, text: String) {
        if (text.isEmpty()) return
        viewModelScope.launch {
            val current = _logLines.value
            val updated = (current + LogLine(direction, text)).takeLast(500)
            _logLines.value = updated
        }
    }

    override fun onCleared() {
        super.onCleared()
        usb.teardown()
    }

    companion object {
        val DEFAULT_QUICK_ACTIONS = listOf(
            // Navigation Shortcuts
            QuickAction(id = "nav_sel", label = "Select (●)", command = "nav sel", category = ActionCategory.SYSTEM),
            QuickAction(id = "nav_esc", label = "Back (⟲)", command = "nav esc", category = ActionCategory.SYSTEM),
            QuickAction(id = "nav_menu", label = "Menu (M)", command = "nav menu", category = ActionCategory.SYSTEM),
            QuickAction(id = "nav_up", label = "Up (▲)", command = "nav up", category = ActionCategory.SYSTEM),
            QuickAction(id = "nav_down", label = "Down (▼)", command = "nav down", category = ActionCategory.SYSTEM),
            QuickAction(id = "nav_nextpage", label = "Page Down", command = "nav nextpage", category = ActionCategory.SYSTEM),
            QuickAction(id = "nav_prevpage", label = "Page Up", command = "nav prevpage", category = ActionCategory.SYSTEM),

            // System Commands
            QuickAction(id = "sys_info", label = "Info", command = "info", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_uptime", label = "Uptime", command = "uptime", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_free", label = "Free Mem", command = "free", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_i2c", label = "I2C Scan", command = "i2c", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_settings", label = "Settings", command = "settings", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_help", label = "Help", command = "help", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_reboot", label = "Reboot", command = "power reboot", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_sleep", label = "Sleep", command = "power sleep", category = ActionCategory.SYSTEM),
            QuickAction(id = "sys_poweroff", label = "Power Off", command = "power off", category = ActionCategory.SYSTEM),

            // Display Controls
            QuickAction(id = "disp_dump", label = "Display Dump", command = "display dump", category = ActionCategory.SYSTEM),
            QuickAction(id = "disp_start", label = "Display Start", command = "display start", category = ActionCategory.SYSTEM),
            QuickAction(id = "disp_stop", label = "Display Stop", command = "display stop", category = ActionCategory.SYSTEM)
        )
    }
}
