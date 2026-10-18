package com.radar.blewifi.pbj.jammer

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.radar.blewifi.pbj.serial.UsbSerialController
import com.radar.blewifi.pbj.ui.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class StatusKind { NEUTRAL, OK, BAD }

data class SelectedTarget(val what: String, val widthMhz: Double)

data class UiState(
    val connected: Boolean = false,
    val status: String = "offline",
    val statusKind: StatusKind = StatusKind.NEUTRAL,
    val log: List<String> = emptyList(),
    val search: String = "",
    val selected: Map<Double, SelectedTarget> = emptyMap(),
    val mode: String = "noise",
    val harmMode: Int = 0,
    val running: Boolean = false,
    val span: Int = 39000,
    val dwell: Int = 40,
    val amp: Int = 450,
    val gain: Int = -1,
    val monitorOn: Boolean = false,
    val header: SpectrumFrame? = null,
    val bars: String = "",
    val monitorLabel: String = "no capture yet — the jammer parks while capturing",
    val deviceChooser: List<UsbDevice> = emptyList(),
    val theme: AppTheme = AppTheme.DEFAULT
)

class JammerViewModel(private val app: Application) : AndroidViewModel(app) {

    private val serial = UsbSerialController(app)
    private val parser = LineParser()

    private val prefs by lazy {
        app.getSharedPreferences("pbj3000_prefs", Context.MODE_PRIVATE)
    }

    private val settingsPrefs by lazy {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private val settingsPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "theme_name") {
            val name = prefs.getString("theme_name", "DEFAULT")
            val newTheme = AppTheme.fromBlespThemeName(name)
            _ui.update { it.copy(theme = newTheme) }
        }
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var lineJob: Job? = null
    private var monitorJob: Job? = null
    private var monitorBusy = false

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    // --------------------------------------------------------------- lifecycle

    init {
        val blespThemeName = settingsPrefs.getString("theme_name", null)
        val pbjThemeName = prefs.getString("theme", AppTheme.DEFAULT.name)
        val initialThemeName = blespThemeName ?: pbjThemeName
        val savedTheme = AppTheme.fromBlespThemeName(initialThemeName)

        val savedSpan = prefs.getInt("span", 39000)
        val savedDwell = prefs.getInt("dwell", 40)
        val savedAmp = prefs.getInt("amp", 450)
        val savedGain = prefs.getInt("gain", -1)
        val savedMode = prefs.getString("mode", "noise") ?: "noise"
        val savedHarm = prefs.getInt("harmMode", 0)

        _ui.update {
            it.copy(
                theme = savedTheme,
                span = savedSpan,
                dwell = savedDwell,
                amp = savedAmp,
                gain = savedGain,
                mode = savedMode,
                harmMode = savedHarm
            )
        }

        settingsPrefs.registerOnSharedPreferenceChangeListener(settingsPrefsListener)

        viewModelScope.launch {
            serial.connected.collect { isUp ->
                _ui.update { it.copy(connected = isUp) }
                if (!isUp) {
                    stopMonitor()
                    stopVibration()
                }
            }
        }
    }

    override fun onCleared() {
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(settingsPrefsListener)
        stopVibration()
        serial.dispose()
        super.onCleared()
    }

    // ---------------------------------------------------------------- theme

    fun setTheme(theme: AppTheme) {
        settingsPrefs.edit().putString("theme_name", theme.name).apply()
        prefs.edit().putString("theme", theme.name).apply()
        _ui.update { it.copy(theme = theme) }
    }

    // ---------------------------------------------------------------- vibration

    private fun startVibration() {
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(800, 200) // 200ms pulse every 1s
                val amplitudes = intArrayOf(0, 45)  // medium-low distinct amplitude
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(800, 200), 0)
            }
        } catch (e: Exception) {
            log("# vibration: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ logging

    private fun log(line: String) = _ui.update {
        val next = if (it.log.size > 500) it.log.drop(it.log.size - 500) + line else it.log + line
        it.copy(log = next)
    }

    private fun setStatus(text: String, kind: StatusKind = StatusKind.NEUTRAL) =
        _ui.update { it.copy(status = text, statusKind = kind) }

    // ------------------------------------------------------------------ connect

    fun connectPressed() {
        if (_ui.value.connected) { disconnect(); return }
        val devices = serial.listDevices()
        if (devices.isEmpty()) {
            log("# no USB serial device found — plug in the ESP32-S3 and allow USB access")
            return
        }
        if (devices.size == 1) openDevice(devices.first())
        else _ui.update { it.copy(deviceChooser = devices) }
    }

    fun chooseDevice(device: UsbDevice) {
        _ui.update { it.copy(deviceChooser = emptyList()) }
        openDevice(device)
    }

    fun dismissChooser() = _ui.update { it.copy(deviceChooser = emptyList()) }

    private fun openDevice(device: UsbDevice) {
        viewModelScope.launch {
            setStatus("connecting…")
            serial.connect(device)
                .onSuccess {
                    setStatus("connected", StatusKind.OK)
                    _ui.update { it.copy(connected = true) }
                    startCollecting()
                    send("")
                    delay(200)
                    send(Cmd.INFO)
                    send(Cmd.STATUS)
                }
                .onFailure {
                    setStatus("failed", StatusKind.BAD)
                    _ui.update { it.copy(connected = false) }
                    log("connect: ${it.message ?: it}")
                }
        }
    }

    private fun disconnect() {
        stopMonitor()
        stopVibration()
        serial.disconnect()
        _ui.update { it.copy(connected = false) }
        setStatus("offline")
    }

    private fun startCollecting() {
        lineJob?.cancel()
        lineJob = viewModelScope.launch {
            serial.lines.collect { raw ->
                for (event in parser.feed(raw)) {
                    when (event) {
                        is LineParser.Event.Text -> log(event.line)
                        is LineParser.Event.Frame -> applyFrame(event.frame)
                    }
                }
            }
        }
    }

    private fun applyFrame(frame: SpectrumFrame) {
        if (frame.bars.isNotEmpty()) {
            _ui.update { it.copy(bars = frame.bars) }
            monitorBusy = false
        } else {
            _ui.update {
                it.copy(
                    header = frame,
                    monitorLabel = "LO ${fmt3(frame.lo)} MHz  ·  ±${fmt3(frame.half)} MHz  ·  " +
                        "${frame.bins} bins @ ${fmt2(frame.bw)} MHz  ·  peak ${"%.1f".format(frame.peak)} dBFS"
                )
            }
        }
    }

    // ------------------------------------------------------------------- target

    fun setSearch(q: String) = _ui.update { it.copy(search = q) }

    /** Selecting a target replaces any previous selection so user doesn't need to press Clear. */
    fun selectTarget(mhz: Double, what: String, width: Double) {
        _ui.update { state ->
            val isAlreadySelected = state.selected.containsKey(mhz) && state.selected.size == 1
            val sel = if (isAlreadySelected) {
                emptyMap()
            } else {
                mapOf(mhz to SelectedTarget(what, width))
            }
            val span = if (sel.isNotEmpty()) {
                minOf(40000, maxOf(200, (Math.ceil((width / 2) / 100.0) * 100).toInt()))
            } else state.span
            state.copy(selected = sel, span = span)
        }
        send(Cmd.span(_ui.value.span))
    }

    fun toggleTarget(mhz: Double, what: String, width: Double) {
        _ui.update { state ->
            val sel = state.selected.toMutableMap()
            if (sel.containsKey(mhz)) sel.remove(mhz) else sel[mhz] = SelectedTarget(what, width)
            val span = if (sel.size == 1) {
                minOf(40000, maxOf(200, (Math.ceil((width / 2) / 100.0) * 100).toInt()))
            } else state.span
            state.copy(selected = sel, span = span)
        }
        send(Cmd.span(_ui.value.span))
    }

    fun addManual(text: String) {
        val v = text.trim().toDoubleOrNull()
        if (v == null || v < 100 || v > 6000) { log("# enter 100–6000 MHz"); return }
        selectTarget(Math.round(v * 1000) / 1000.0, "manual", 20.0)
    }

    fun clearTargets() = _ui.update { it.copy(selected = emptyMap()) }

    fun planSummary(): String {
        val s = _ui.value
        if (s.selected.isEmpty()) return "no target selected"
        val fr = s.selected.keys.sorted().map { fmt3(it) }
        return "${fr.size} target(s): ${fr.joinToString(", ")} MHz  ·  span ±${s.span} kHz each"
    }

    // ------------------------------------------------------------------ actions

    fun setMode(mode: String) {
        prefs.edit().putString("mode", mode).apply()
        _ui.update { it.copy(mode = mode) }
        send(Cmd.mode(mode))
    }

    fun setHarm(harm: Int) {
        prefs.edit().putInt("harmMode", harm).apply()
        _ui.update { it.copy(harmMode = harm) }
        send(Cmd.harm(harm))
    }

    fun setSpan(v: Int) {
        prefs.edit().putInt("span", v).apply()
        _ui.update { it.copy(span = v) }
    }

    fun setDwell(v: Int) {
        prefs.edit().putInt("dwell", v).apply()
        _ui.update { it.copy(dwell = v) }
    }

    fun setAmp(v: Int) {
        prefs.edit().putInt("amp", v).apply()
        _ui.update { it.copy(amp = v) }
    }

    fun setGain(v: Int) {
        prefs.edit().putInt("gain", v).apply()
        _ui.update { it.copy(gain = v) }
    }

    fun commitSpan() = send(Cmd.span(_ui.value.span))
    fun commitDwell() = send(Cmd.dwell(_ui.value.dwell))
    fun commitAmp() = send(Cmd.amp(_ui.value.amp))
    fun commitGain() = send(Cmd.gain(_ui.value.gain))

    fun testCarrier() = send(Cmd.TEST)
    fun readStatus() = send(Cmd.STATUS)
    fun readPlan() = send(Cmd.INFO)
    fun sendRaw(cmd: String) { if (cmd.isNotBlank()) send(cmd.trim()) }

    fun startJammingApplyingOnly() = viewModelScope.launch {
        applyPlan()
    }

    fun startJamming() = viewModelScope.launch {
        applyPlan()
        send(Cmd.START)
        _ui.update { it.copy(running = true) }
        setStatus("jamming", StatusKind.OK)
        send(Cmd.STATUS)
        startVibration()
    }

    fun stopJamming() = viewModelScope.launch {
        send(Cmd.STOP)
        _ui.update { it.copy(running = false) }
        setStatus("connected", StatusKind.OK)
        stopVibration()
    }

    private suspend fun applyPlan() {
        val s = _ui.value
        if (s.selected.isEmpty()) {
            log("# nothing selected, leaving the radio as it is")
        } else {
            val freqs = s.selected.keys
                .filter { reach(it).harm != 0 || s.harmMode == 1 }
                .sorted()
            if (freqs.isEmpty()) { log("# all selected targets are outside the LO window"); return }
            if (s.running) send(Cmd.STOP)
            send(Cmd.harm(s.harmMode))
            send(Cmd.lo(freqs))
        }
        if (s.running) send(Cmd.STOP)
        send(Cmd.span(s.span))
        send(Cmd.mode(s.mode))
        send(Cmd.amp(s.amp))
        send(Cmd.dwell(s.dwell))
        send(Cmd.gain(s.gain))
        if (s.running) send(Cmd.START) else send(Cmd.STATUS)
    }

    // ------------------------------------------------------------------ monitor

    fun toggleMonitor() {
        if (_ui.value.monitorOn) stopMonitor() else startMonitor()
    }

    private fun startMonitor() {
        _ui.update { it.copy(monitorOn = true) }
        monitorJob = viewModelScope.launch {
            while (true) {
                if (serial.connected.value && !monitorBusy) {
                    monitorBusy = true
                    send(Cmd.mon(2048))
                    launch { delay(2500); monitorBusy = false }
                }
                delay(1200)
            }
        }
    }

    private fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        monitorBusy = false
        _ui.update { it.copy(monitorOn = false) }
    }

    // -------------------------------------------------------------------- util

    private fun send(cmd: String) {
        if (!_ui.value.connected && cmd.isNotEmpty()) { /* still queue */ }
        serial.send(cmd)
    }

    private fun fmt3(v: Double) = String.format(Locale.US, "%.3f", v)
    private fun fmt2(v: Double) = String.format(Locale.US, "%.2f", v)
}
