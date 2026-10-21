package com.radar.blewifi.masdr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.radar.blewifi.R
import com.radar.blewifi.RadarView
import com.radar.blewifi.ThemeUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Integrated MULT1PASS / mostly-a-SDR Remote Control for BLESP.
 * Dynamic theme colors, persistent settings, and tactical UI.
 */
class SdrRemoteActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var api: Api
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var content: FrameLayout
    private lateinit var dot: View
    private lateinit var statusText: TextView
    private lateinit var freqField: EditText

    private val navs = LinkedHashMap<String, TextView>()

    private var currentTheme: RadarView.Theme = RadarView.Theme.DEFAULT
    private lateinit var themeColors: ThemeUtils.ThemeColors

    // Dynamic Theme Colors
    private var colorBg = Color.BLACK
    private var colorSurface = Color.parseColor("#0B0C0F")
    private var colorSurface2 = Color.parseColor("#13151A")
    private var colorPrimary = Color.parseColor("#FF7A18")
    private var colorAccent = Color.parseColor("#00E5C7")
    private var colorDim = Color.parseColor("#7C828C")
    private var colorText = Color.parseColor("#EDEDED")
    private var colorRed = Color.parseColor("#FF3B5C")

    private var freq = 434.0
    private var modes: JSONArray? = null
    private var subSpinner: Spinner? = null
    private var fileKind = "sub"
    private var tab = "tx"
    private var polling = false
    private var logAuto = true
    private var logTx = false
    private var logOut: TextView? = null

    private val otherFreqFields = CopyOnWriteArraySet<EditText>()
    private var updatingFreqFields = false

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        loadThemeColors()
        setupFullscreen()

        prefs = Prefs(this)
        api = Api(prefs)
        freq = prefs.freq.toDouble()
        tab = prefs.activeTab
        fileKind = prefs.fileKind

        val root = buildRoot()
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else sysBarInsets.bottom
            root.setPadding(dp(12), sysBarInsets.top + dp(12), dp(12), bottomInset + dp(12))

            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (!isKeyboardVisible) {
                setupFullscreen()
            }
            insets
        }

        if (prefs.host.isBlank()) {
            show("link")
        } else {
            show(tab)
            loadModes()
            loadSubs()
        }
        startPoll()
    }

    private fun loadThemeColors() {
        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = settingsPrefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = try { RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name) } catch (e: Exception) { RadarView.Theme.DEFAULT }
        themeColors = ThemeUtils.getThemeColors(currentTheme)

        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        colorBg = themeColors.background
        colorSurface = if (isHighContrast) Color.parseColor("#F5F5F5") else themeColors.header
        colorSurface2 = if (isHighContrast) Color.parseColor("#E8E8E8") else themeColors.headerBar
        colorPrimary = themeColors.primary
        colorAccent = themeColors.accent
        colorDim = themeColors.dim
        colorText = if (isHighContrast) Color.BLACK else Color.WHITE
    }

    private fun setupFullscreen() {
        window?.let { w ->
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                w.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            WindowCompat.setDecorFitsSystemWindows(w, false)
            val controller = WindowCompat.getInsetsController(w, w.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            val isLight = currentTheme == RadarView.Theme.HIGH_CONTRAST
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        polling = false
        api.shutdown()
    }

    // ─────────────────────────── styling helpers ───────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(weight: Float) = LinearLayout.LayoutParams(0, WC, weight)
    private fun lpFull() = LinearLayout.LayoutParams(MP, WC)

    private fun round(fill: Int, stroke: Int? = null, radius: Int = 10): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radius).toFloat()
        g.setColor(fill)
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }

    private fun oval(c: Int): GradientDrawable {
        val g = GradientDrawable(); g.shape = GradientDrawable.OVAL; g.setColor(c); return g
    }

    private fun txt(t: String, color: Int = colorText, size: Float = 13f, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(color)
        v.textSize = size
        v.includeFontPadding = false
        v.typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                     else Typeface.MONOSPACE
        return v
    }

    private fun <T : View> pad(v: T, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): T {
        v.setPadding(dp(l), dp(t), dp(r), dp(b)); return v
    }

    private fun btn(label: String, color: Int = colorPrimary, filled: Boolean = false,
                    onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 12f
        b.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        b.setTextColor(if (filled) colorBg else color)
        b.background = if (filled) round(color, null, 6) else round(colorSurface, color, 6)
        b.setPadding(dp(14), dp(10), dp(14), dp(10))
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.includeFontPadding = false
        b.gravity = Gravity.CENTER
        b.setOnClickListener { onClick() }
        return b
    }

    private fun card(title: String?, accent: Int = colorAccent): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.background = round(colorSurface, accent, 12)
        l.setPadding(dp(16), dp(16), dp(16), dp(16))
        val p = LinearLayout.LayoutParams(MP, WC); p.bottomMargin = dp(18)
        l.layoutParams = p
        if (title != null) {
            val t = txt(title, accent, 12f, true)
            t.letterSpacing = 0.10f
            l.addView(pad(t, 0, 0, 0, 10))
        }
        return l
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        return r
    }

    private fun column(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(16), dp(16), dp(36))
        return l
    }

    private fun scroll(v: View): ScrollView {
        val s = ScrollView(this)
        s.setBackgroundColor(colorBg)
        s.isFillViewport = true
        s.addView(v)
        return s
    }

    private fun field(value: String, numeric: Boolean = false, hint: String = ""): EditText {
        val e = EditText(this)
        if (value.isNotEmpty()) e.setText(value)
        if (hint.isNotEmpty()) e.hint = hint
        e.textSize = 13f
        e.typeface = Typeface.MONOSPACE
        e.setTextColor(colorText)
        e.setHintTextColor(colorDim)
        e.background = round(colorSurface2, colorAccent, 8)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        e.minHeight = 0
        e.minimumHeight = 0
        e.minWidth = 0
        e.minimumWidth = 0
        e.includeFontPadding = false
        e.setSingleLine(true)
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        return e
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = (super.getView(position, convertView, parent) as TextView)
                v.setTextColor(colorText)
                v.textSize = 12.5f
                v.typeface = Typeface.MONOSPACE
                v.includeFontPadding = false
                v.setPadding(dp(8), dp(6), dp(8), dp(6))
                v.setSingleLine(true)
                v.ellipsize = TextUtils.TruncateAt.END
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = (super.getDropDownView(position, convertView, parent) as TextView)
                v.setTextColor(colorText)
                v.setBackgroundColor(colorSurface)
                v.textSize = 12.5f
                v.typeface = Typeface.MONOSPACE
                v.includeFontPadding = false
                v.setPadding(dp(12), dp(10), dp(12), dp(10))
                return v
            }
        }.also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun defStr(p: JSONObject): String {
        val d = p.opt("default")
        return if (d == null || d == JSONObject.NULL) "" else d.toString()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ──────────────────────────── frequency syncing ────────────────────────────

    private fun registerFreqField(e: EditText) {
        otherFreqFields.add(e)
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingFreqFields) return
                val text = s?.toString() ?: ""
                val parsed = text.toDoubleOrNull()
                if (parsed != null && parsed > 0) {
                    freq = parsed
                    prefs.freq = parsed.toFloat()
                    syncTopFreqField(text)
                }
            }
        })
    }

    private fun unregisterFreqField(e: EditText) {
        otherFreqFields.remove(e)
    }

    private fun syncTopFreqField(text: String) {
        if (updatingFreqFields) return
        updatingFreqFields = true
        if (::freqField.isInitialized && freqField.text.toString() != text) {
            freqField.setText(text)
            freqField.setSelection(freqField.text.length)
        }
        updateAllOtherFreqFields(text, source = null)
        updatingFreqFields = false
    }

    private fun updateAllOtherFreqFields(text: String, source: EditText? = null) {
        val iterator = otherFreqFields.iterator()
        while (iterator.hasNext()) {
            val f = iterator.next()
            if (f != source && f.text.toString() != text) {
                f.setText(text)
            }
        }
    }

    // ─────────────────────────────── chrome ───────────────────────────────

    private fun buildRoot(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(colorBg)
        root.fitsSystemWindows = true

        // Top Navigation Bar
        val topBar = row().apply {
            setBackgroundColor(colorSurface)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val backBtn = btn("← BACK", colorPrimary) { finish() }
        topBar.addView(backBtn)

        val titleView = txt("", colorPrimary, 14f, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        topBar.addView(titleView, lp(1f))

        val linkBtn = btn("LINK", colorAccent) { show("link") }
        topBar.addView(linkBtn)

        root.addView(topBar)

        // Header Panel
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.background = round(colorSurface, colorAccent, 10)
        head.setPadding(dp(12), dp(10), dp(12), dp(10))
        val hp = LinearLayout.LayoutParams(MP, WC)
        hp.setMargins(dp(10), dp(6), dp(10), dp(6))
        head.layoutParams = hp

        // Row 1: Status Indicator + Status Text
        val statRow = row()
        dot = View(this)
        dot.background = oval(colorDim)
        val dl = LinearLayout.LayoutParams(dp(10), dp(10)); dl.rightMargin = dp(8)
        statRow.addView(dot, dl)
        statusText = txt("standby", colorDim, 12f, true)
        statusText.maxLines = 1
        statusText.ellipsize = TextUtils.TruncateAt.END
        statRow.addView(statusText, lp(1f))
        head.addView(statRow)

        // Row 2: Frequency Field + MHz + SET Button + STOP Button
        val freqRow = row()
        freqField = field(freq.toString(), numeric = true, hint = "MHz")
        freqField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingFreqFields) return
                updatingFreqFields = true
                val text = s?.toString() ?: ""
                val parsed = text.toDoubleOrNull()
                if (parsed != null && parsed > 0) {
                    freq = parsed
                    prefs.freq = parsed.toFloat()
                    updateAllOtherFreqFields(text, source = freqField)
                }
                updatingFreqFields = false
            }
        })

        freqRow.addView(freqField, lp(1f))
        freqRow.addView(pad(txt("MHz", colorDim, 11f, true), 6, 0, 8, 0))
        val setBtn = btn("SET", colorPrimary, true) { pushFreq() }
        val stopBtn = btn("STOP", colorRed, true) { stopTx() }
        freqRow.addView(setBtn)
        freqRow.addView(pad(stopBtn, 6, 0, 0, 0))
        head.addView(pad(freqRow, 0, 8, 0, 0))
        root.addView(head)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MP, 0, 1f))

        // Bottom Tab Navigation
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(colorSurface)
        nav.setPadding(dp(4), dp(6), dp(4), dp(6))
        listOf("tx" to "TX", "files" to "FILES", "tools" to "TOOLS",
               "log" to "LOG", "sys" to "SYS").forEach { (k, l) ->
            val t = txt(l, colorDim, 12f, true)
            t.gravity = Gravity.CENTER
            t.includeFontPadding = false
            t.setSingleLine(true)
            t.ellipsize = TextUtils.TruncateAt.END
            t.setPadding(dp(2), dp(10), dp(2), dp(10))
            t.setOnClickListener { show(k) }
            navs[k] = t
            nav.addView(t, lp(1f))
        }
        root.addView(nav)
        return root
    }

    private fun show(id: String) {
        tab = id
        prefs.activeTab = id
        navs.forEach { (k, v) -> v.setTextColor(if (k == id) colorPrimary else colorDim) }
        val v = when (id) {
            "tx" -> txScreen()
            "files" -> filesScreen()
            "tools" -> toolsScreen()
            "log" -> logScreen()
            "sys" -> sysScreen()
            else -> linkScreen()
        }
        content.removeAllViews()
        content.addView(v, FrameLayout.LayoutParams(MP, MP))
    }

    // ───────────────────────────── TX screen ─────────────────────────────

    private fun txScreen(): View {
        val col = column()
        col.addView(pad(txt("TRANSMIT CONTROL", colorPrimary, 13f, true).also { it.letterSpacing = 0.12f },
            0, 0, 0, 14))
        col.addView(subCard())
        col.addView(modesCard())
        return scroll(col)
    }

    /** The .sub dropdown: lists captures stored on the Pi and replays one. */
    private fun subCard(): View {
        val c = card("SUB-GHZ REPLAY · .SUB CAPTURES", colorPrimary)
        c.addView(pad(txt("pick a saved capture from the Pi's sub_dir", colorDim, 11f), 0, 0, 0, 10))

        val sp = Spinner(this)
        sp.adapter = simpleAdapter(listOf("loading…"))
        sp.background = round(colorSurface2, colorAccent, 8)
        subSpinner = sp
        c.addView(sp, lpFull())

        val r1 = row().apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dp(12)
            }
        }
        r1.addView(btn("REFRESH", colorAccent) { loadSubs() })
        val playList = listOf("once", "loop")
        val play = Spinner(this).apply {
            adapter = simpleAdapter(playList)
            val idx = playList.indexOf(prefs.lastPlayback)
            if (idx >= 0) setSelection(idx)
            background = round(colorSurface2, colorAccent, 8)
        }
        r1.addView(pad(play, 12, 0, 0, 0), lp(1f))
        c.addView(r1)

        val r2 = row().apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dp(12)
            }
        }
        r2.addView(txt("repeat", colorDim, 11f))
        val rep = field(prefs.lastRepeat.toString(), numeric = true)
        r2.addView(pad(rep, 8, 0, 14, 0), lp(1f))
        r2.addView(txt("secs", colorDim, 11f))
        val dur = field(if (prefs.lastDuration > 0) prefs.lastDuration.toString() else "0", numeric = true)
        r2.addView(pad(dur, 8, 0, 0, 0), lp(1f))
        c.addView(r2)

        val txBtn = btn("TRANSMIT", colorPrimary, true) {
            val name = subSpinner?.selectedItem?.toString() ?: ""
            if (name.isBlank() || name == "loading…" || name.startsWith("(")) {
                toast("no .sub file selected"); return@btn
            }
            val playbackVal = play.selectedItem?.toString() ?: "once"
            val repeatVal = rep.text.toString().toIntOrNull() ?: 3
            val durationVal = dur.text.toString().toDoubleOrNull() ?: 0.0

            prefs.lastPlayback = playbackVal
            prefs.lastRepeat = repeatVal
            prefs.lastDuration = durationVal

            val body = JSONObject()
                .put("mode", "sub")
                .put("params", JSONObject()
                    .put("file", name)
                    .put("playback", playbackVal)
                    .put("repeat", repeatVal))
                .put("duration", durationVal)
            api.post("/api/v1/tx", body) { r ->
                toast(if (r.ok) "replaying $name" else "error: ${r.error}")
            }
        }.apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                topMargin = dp(18)
                bottomMargin = dp(4)
            }
        }

        c.addView(txBtn)
        return c
    }

    private fun loadSubs() {
        val sp = subSpinner ?: return
        api.get("/api/v1/files?kind=sub&ext=.sub") { r ->
            val arr = r.json.optJSONArray("files")
            val names = ArrayList<String>()
            if (arr != null) for (i in 0 until arr.length()) names.add(arr.getJSONObject(i).optString("name"))
            if (names.isEmpty()) names.add("(no .sub files on the Pi)")
            sp.adapter = simpleAdapter(names)
            sp.setSelection(0)
        }
    }

    private fun modesCard(): View {
        val c = card("MODES · SDR FUNCTIONS", colorAccent)
        val arr = modes
        if (arr == null) {
            c.addView(txt("loading catalogue…", colorDim, 12f))
            loadModes()
            return c
        }
        var group = ""
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            if (m.optString("group") != group) {
                group = m.optString("group")
                c.addView(pad(txt("── $group", colorPrimary, 11.5f, true), 0, 10, 0, 4))
            }
            c.addView(modeRow(m))
        }
        c.addView(pad(btn("REFRESH CATALOGUE", colorAccent) { loadModes() }, 0, 8, 0, 0))
        return c
    }

    private fun modeRow(m: JSONObject): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.background = round(colorSurface2, colorAccent, 8)
        r.setPadding(dp(14), dp(12), dp(14), dp(12))
        val p = LinearLayout.LayoutParams(MP, WC); p.bottomMargin = dp(10)
        r.layoutParams = p
        r.addView(txt(m.optString("label"), colorText, 13f, true))
        val help = m.optString("help")
        if (help.isNotBlank()) r.addView(txt(help, colorDim, 11f))
        r.setOnClickListener { openMode(m) }
        return r
    }

    private fun loadModes() {
        api.get("/api/v1/modes") { r ->
            val arr = r.json.optJSONArray("modes")
            if (arr != null) {
                modes = arr
                if (tab == "tx") show("tx")
            }
        }
    }

    /** Builds a form from the mode's param schema and POSTs /api/v1/tx. */
    private fun openMode(mode: JSONObject) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(16), dp(16), dp(16))

        val params = mode.optJSONArray("params") ?: JSONArray()
        val fields = ArrayList<Triple<JSONObject, String, View>>()

        for (i in 0 until params.length()) {
            val p = params.getJSONObject(i)
            val type = p.optString("type")
            val unit = p.optString("unit")
            val name = p.optString("name")
            val label = name + (if (unit.isNotBlank()) "  [$unit]" else "")
            box.addView(pad(txt(label, colorAccent, 11.5f, true), 0, 6, 0, 2))

            val isFreqParam = name.contains("freq", ignoreCase = true) ||
                    unit.equals("MHz", ignoreCase = true) ||
                    unit.equals("Hz", ignoreCase = true)

            val v: View = when (type) {
                "bool" -> CheckBox(this).apply { text = "on"; setTextColor(colorText) }
                "choice" -> Spinner(this).apply {
                    val ch = p.optJSONArray("choices")
                    val list = ArrayList<String>()
                    for (c in 0 until (ch?.length() ?: 0)) list.add(ch!!.getString(c))
                    adapter = simpleAdapter(list)
                    val idx = list.indexOf(p.optString("default"))
                    if (idx >= 0) setSelection(idx)
                }
                "number" -> {
                    val defaultVal = if (isFreqParam) freqField.text.toString().ifBlank { freq.toString() } else defStr(p)
                    field(defaultVal, numeric = true).also {
                        if (isFreqParam) registerFreqField(it)
                    }
                }
                "file" -> Spinner(this).apply {
                    adapter = simpleAdapter(listOf("loading…"))
                    val kind = p.optString("kind", "sub")
                    val exts = p.optJSONArray("exts")
                    val ex = if (exts != null)
                        (0 until exts.length()).joinToString(",") { exts.getString(it) } else ""
                    api.get("/api/v1/files?kind=$kind" + if (ex.isNotBlank()) "&ext=$ex" else "") { r ->
                        val arr = r.json.optJSONArray("files")
                        val list = ArrayList<String>()
                        if (arr != null) for (k in 0 until arr.length())
                            list.add(arr.getJSONObject(k).optString("name"))
                        if (list.isEmpty()) list.add("")
                        adapter = simpleAdapter(list)
                    }
                }
                else -> {
                    val defaultVal = if (isFreqParam) freqField.text.toString().ifBlank { freq.toString() } else defStr(p)
                    field(defaultVal).also {
                        if (isFreqParam) registerFreqField(it)
                    }
                }
            }
            box.addView(v, lpFull())
            val help = p.optString("help")
            if (help.isNotBlank()) box.addView(txt(help, colorDim, 10.5f))
            fields.add(Triple(p, type, v))
        }

        box.addView(pad(txt("duration in seconds (0 = until stopped)", colorDim, 11f), 0, 8, 0, 2))
        val dur = field(if (prefs.lastDuration > 0) prefs.lastDuration.toString() else "0", numeric = true)
        box.addView(dur, lpFull())

        val dlg = AlertDialog.Builder(this)
            .setTitle(mode.optString("label"))
            .setView(ScrollView(this).apply { isFillViewport = true; addView(box) })
            .setPositiveButton("TRANSMIT", null)
            .setNeutralButton("STOP", null)
            .setNegativeButton("CLOSE", null)
            .create()

        dlg.setOnDismissListener {
            for ((_, _, v) in fields) {
                if (v is EditText) unregisterFreqField(v)
            }
        }

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val durationVal = dur.text.toString().toDoubleOrNull() ?: 0.0
                prefs.lastDuration = durationVal

                val pj = JSONObject()
                for ((p, type, v) in fields) {
                    val n = p.optString("name")
                    when (type) {
                        "bool" -> if ((v as CheckBox).isChecked) pj.put(n, true)
                        "choice" -> pj.put(n, (v as Spinner).selectedItem?.toString() ?: "")
                        "number" -> (v as EditText).text.toString().toDoubleOrNull()?.let { x -> pj.put(n, x) }
                        "file" -> {
                            val s = (v as Spinner).selectedItem?.toString() ?: ""
                            if (s.isNotBlank()) pj.put(n, s)
                        }
                        else -> {
                            val s = (v as EditText).text.toString()
                            if (s.isNotBlank()) pj.put(n, s)
                        }
                    }
                }
                val body = JSONObject()
                    .put("mode", mode.optString("id"))
                    .put("params", pj)
                    .put("duration", durationVal)
                api.post("/api/v1/tx", body) { r ->
                    toast(if (r.ok) "TX ${mode.optString("id")} started" else "error: ${r.error}")
                }
                dlg.dismiss()
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { stopTx() }
        }
        dlg.show()
    }

    // ──────────────────────────── files screen ────────────────────────────

    private fun filesScreen(): View {
        val col = column()
        col.addView(pad(txt("FILES ON THE PI", colorPrimary, 13f, true), 0, 0, 0, 8))

        val tabs = row()
        listOf("sub", "media", "iq").forEach { k ->
            tabs.addView(btn(k.uppercase(),
                if (k == fileKind) colorPrimary else colorDim,
                k == fileKind) {
                    fileKind = k
                    prefs.fileKind = k
                    show("files")
                }, lp(1f))
        }
        col.addView(pad(tabs, 0, 0, 0, 10))

        val c = card(null, colorAccent)
        c.addView(btn("UPLOAD  →  $fileKind", colorAccent) { pickFile() }, lpFull())
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        c.addView(pad(list, 0, 8, 0, 0))
        col.addView(c)

        api.get("/api/v1/files?kind=$fileKind") { r ->
            val arr = r.json.optJSONArray("files")
            if (arr == null || arr.length() == 0) {
                list.addView(txt("(empty)", colorDim, 12f))
                return@get
            }
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                list.addView(fileRow(f.optString("name"), f.optLong("size")))
            }
        }
        return scroll(col)
    }

    private fun fileRow(name: String, size: Long): View {
        val r = row()
        val p = LinearLayout.LayoutParams(MP, WC); p.bottomMargin = dp(6)
        r.layoutParams = p
        val label = txt("$name  (${size / 1024} kB)", colorText, 11.5f)
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.MIDDLE
        r.addView(pad(label, 0, 0, 6, 0), lp(1f))
        r.addView(pad(btn("TX", colorPrimary) { txFile(name) }, 0, 0, 4, 0))
        r.addView(pad(btn("GET", colorAccent) {
            val url = "http://${prefs.host}:${prefs.port}/api/v1/file" +
                    "?kind=$fileKind&name=${api.enc(name)}&token=${api.enc(prefs.token)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }, 0, 0, 4, 0))
        r.addView(btn("DEL", colorRed) {
            api.delete("/api/v1/file?kind=$fileKind&name=${api.enc(name)}") { show("files") }
        })
        return r
    }

    /** Sensible default mode for a tapped file. */
    private fun txFile(name: String) {
        val choices = when {
            name.endsWith(".sub") -> arrayOf("sub replay (once x3)", "sub replay (loop)")
            name.endsWith(".iq") || name.endsWith(".cu8") -> arrayOf("iq replay")
            name.endsWith(".jpg") -> arrayOf("SSTV picture", "spectrum painting")
            name.endsWith(".rf") -> arrayOf("FreeDV")
            else -> arrayOf("WFM + RDS", "NFM voice", "AM", "SSB voice")
        }
        AlertDialog.Builder(this).setTitle("transmit $name")
            .setItems(choices) { _, which ->
                val params = JSONObject().put("file", name)
                val mode = when (choices[which]) {
                    "sub replay (once x3)" -> { params.put("playback", "once"); params.put("repeat", 3); "sub" }
                    "sub replay (loop)" -> { params.put("playback", "loop"); "sub" }
                    "iq replay" -> { params.put("freq", freq); "play" }
                    "SSTV picture" -> "sstv"
                    "spectrum painting" -> "spectrum"
                    "FreeDV" -> "freedv"
                    "WFM + RDS" -> { params.put("playback", "loop"); "wfm" }
                    "NFM voice" -> { params.put("playback", "loop"); "nfm" }
                    "AM" -> { params.put("playback", "loop"); "am" }
                    else -> { params.put("playback", "loop"); "ssb" }
                }
                api.post("/api/v1/tx", JSONObject().put("mode", mode).put("params", params)) { r ->
                    toast(if (r.ok) "TX $mode" else "error: ${r.error}")
                }
            }.show()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.type = "*/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i, PICK_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            api.upload(fileKind, "upload.bin", uri, contentResolver) { r ->
                toast(if (r.ok) "uploaded ${r.json.optString("name")}" else "error: ${r.error}")
                if (r.ok) show("files")
            }
        }
    }

    // ──────────────────────────── tools screen ────────────────────────────

    private fun toolsScreen(): View {
        val col = column()
        col.addView(pad(txt("TOOLS", colorPrimary, 13f, true), 0, 0, 0, 8))

        val raw = card("RAW SHELL  ·  POST /raw", colorAccent)
        val cmd = field(prefs.lastRawCmd)
        raw.addView(cmd, lpFull())
        val bgBox = CheckBox(this).apply {
            text = "background (fire and forget)"; setTextColor(colorText)
        }
        raw.addView(bgBox)
        val rawOut = txt("", colorDim, 11f)
        raw.addView(btn("RUN", colorPrimary, true) {
            val cmdStr = cmd.text.toString()
            prefs.lastRawCmd = cmdStr
            api.post("/api/v1/raw", JSONObject()
                .put("cmd", cmdStr)
                .put("background", bgBox.isChecked)) { r -> rawOut.text = r.text.take(4000) }
        })
        raw.addView(rawOut)
        col.addView(raw)

        val sdt = card("sdrtool.py  ·  capture tooling", colorAccent)
        val op = Spinner(this).apply {
            adapter = simpleAdapter(listOf("info", "freq", "sub", "analyze", "fields", "hex", "render"))
        }
        sdt.addView(op, lpFull())
        val cu8 = field(prefs.lastSdrToolCu8, hint = "capture .cu8 name")
        sdt.addView(cu8, lpFull())
        val outPath = field(prefs.lastSdrToolOut, hint = "output path (optional)")
        sdt.addView(outPath, lpFull())
        val sdtOut = txt("", colorDim, 11f)
        sdt.addView(btn("RUN sdrtool", colorPrimary, true) {
            val cu8Str = cu8.text.toString()
            val outStr = outPath.text.toString()
            prefs.lastSdrToolCu8 = cu8Str
            prefs.lastSdrToolOut = outStr
            api.post("/api/v1/sdrtool", JSONObject()
                .put("op", op.selectedItem?.toString() ?: "info")
                .put("cu8", cu8Str)
                .put("out", outStr)) { r -> sdtOut.text = r.text.take(4000) }
        })
        sdt.addView(sdtOut)
        col.addView(sdt)

        val cfg = card("SERVER CONFIG", colorAccent)
        cfg.addView(txt("auto_stop_seconds (0 = off)", colorDim, 11f))
        val auto = field(prefs.autoStop.toString(), numeric = true)
        cfg.addView(auto, lpFull())
        val sbs = CheckBox(this).apply {
            text = "stop_before_start"; isChecked = prefs.stopBeforeStart; setTextColor(colorText)
        }
        cfg.addView(sbs)
        val vrb = CheckBox(this).apply {
            text = "verbose logging"; isChecked = prefs.verbose; setTextColor(colorText)
        }
        cfg.addView(vrb)
        cfg.addView(btn("SAVE CONFIG", colorPrimary, true) {
            val b = JSONObject()
                .put("auto_stop_seconds", auto.text.toString().toIntOrNull() ?: 0)
                .put("stop_before_start", sbs.isChecked)
                .put("verbose", vrb.isChecked)
            api.post("/api/v1/config", b) { r ->
                if (r.ok) {
                    prefs.autoStop = auto.text.toString().toIntOrNull() ?: 0
                    prefs.stopBeforeStart = sbs.isChecked
                    prefs.verbose = vrb.isChecked
                }
                toast(if (r.ok) "config saved" else "error: ${r.error}")
            }
        })
        col.addView(cfg)

        col.addView(btn("RESTART API SERVICE", colorRed) {
            api.post("/api/v1/restart", JSONObject()) { toast("restarting service") }
        })
        col.addView(pad(btn("COPY WEB UI LINK", colorAccent) {
            val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("sdr", prefs.webUrl))
            toast("link copied")
        }, 0, 8, 0, 0))
        return scroll(col)
    }

    // ───────────────────────────── log screen ─────────────────────────────

    private fun logScreen(): View {
        val col = column()
        col.addView(pad(txt("LOG", colorPrimary, 13f, true), 0, 0, 0, 8))

        val r = row()
        r.addView(CheckBox(this).apply {
            text = "auto"; isChecked = logAuto; setTextColor(colorText)
            setOnCheckedChangeListener { _, b -> logAuto = b }
        })
        r.addView(CheckBox(this).apply {
            text = "tx.log"; isChecked = logTx; setTextColor(colorText)
            setOnCheckedChangeListener { _, b -> logTx = b; loadLog() }
        })
        r.addView(btn("REFRESH", colorAccent) { loadLog() })
        col.addView(r)

        val out = txt("", colorText, 11f)
        logOut = out
        col.addView(out)
        loadLog()
        return scroll(col)
    }

    private fun loadLog() {
        val out = logOut ?: return
        api.get("/api/v1/log?lines=400" + if (logTx) "&file=tx" else "") { r ->
            val lines = r.json.optJSONArray("lines")
            val sb = StringBuilder()
            if (lines != null) for (i in 0 until lines.length()) sb.append(lines.getString(i)).append('\n')
            out.text = if (sb.isEmpty()) "(no log)" else sb.toString()
        }
    }

    // ──────────────────────────── system screen ───────────────────────────

    private fun sysScreen(): View {
        val col = column()
        col.addView(pad(txt("SYSTEM", colorPrimary, 13f, true), 0, 0, 0, 8))
        val out = txt("loading…", colorText, 11f)
        col.addView(btn("REFRESH", colorAccent) { loadSys(out) })
        col.addView(out)
        loadSys(out)
        return scroll(col)
    }

    private fun loadSys(out: TextView) {
        api.get("/api/v1/system") { r ->
            out.text = try {
                r.json.optJSONObject("system")?.toString(2) ?: r.text
            } catch (e: Exception) { r.text }
        }
    }

    // ──────────────────────────── link screen ─────────────────────────────

    private fun linkScreen(): View {
        val col = column()
        col.addView(pad(txt("LINK TO THE PI", colorPrimary, 13f, true), 0, 0, 0, 8))

        val c = card("CONNECTION SETTINGS", colorAccent)
        val h = field(prefs.host, hint = "192.168.1.50 or mostly-a-sdr.local")
        val p = field(prefs.port.toString(), numeric = true)
        val t = field(prefs.token, hint = "API token printed by installer")
        c.addView(txt("host", colorDim, 11f)); c.addView(h, lpFull())
        c.addView(txt("port", colorDim, 11f)); c.addView(p, lpFull())
        c.addView(txt("token", colorDim, 11f)); c.addView(t, lpFull())
        c.addView(pad(btn("DISCOVER (mDNS _masdr._tcp)", colorAccent) { discover(h, p) }, 0, 8, 0, 0))
        c.addView(btn("CONNECT", colorPrimary, true) {
            prefs.host = h.text.toString()
            prefs.port = p.text.toString().toIntOrNull() ?: 8765
            prefs.token = t.text.toString()
            api.get("/api/v1/status") { r ->
                if (r.ok) { toast("connected"); show("tx"); loadModes(); loadSubs() }
                else toast("failed: ${r.error}")
            }
        })
        c.addView(pad(txt("The Pi also serves a web UI at /?token=… . " +
                "mDNS needs both devices on the same LAN.", colorDim, 11f), 0, 8, 0, 0))
        col.addView(c)
        return scroll(col)
    }

    private fun discover(hostField: EditText, portField: EditText) {
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        nsd.discoverServices("_masdr._tcp", NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(s: String) { toast("searching…") }
                override fun onServiceFound(info: NsdServiceInfo) {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(i: NsdServiceInfo) {
                            ui.post {
                                hostField.setText(i.host?.hostAddress ?: "")
                                portField.setText(i.port.toString())
                                toast("found ${i.serviceName}")
                            }
                        }
                    })
                }
                override fun onServiceLost(i: NsdServiceInfo) {}
                override fun onDiscoveryStopped(s: String) {}
                override fun onStartDiscoveryFailed(s: String, e: Int) {}
                override fun onStopDiscoveryFailed(s: String, e: Int) {}
            })
    }

    // ───────────────────────────── status loop ────────────────────────────

    private fun pushFreq() {
        val text = freqField.text.toString()
        val mhz = text.toDoubleOrNull()
        if (mhz == null) { toast("bad frequency"); return }
        freq = mhz
        prefs.freq = mhz.toFloat()
        updateAllOtherFreqFields(text, source = freqField)
        api.post("/api/v1/freq", JSONObject().put("mhz", mhz)) { r ->
            if (r.ok) toast("frequency set")
            else toast("error: ${r.error}")
        }
    }

    private fun stopTx() = api.post("/api/v1/stop", JSONObject()) { toast("stopped") }

    private fun startPoll() {
        if (polling) return
        polling = true
        val tick = object : Runnable {
            override fun run() {
                if (prefs.host.isNotBlank()) {
                    api.get("/api/v1/status") { r ->
                        val st = r.json.optJSONObject("status")
                        when {
                            !r.ok || st == null -> {
                                dot.background = oval(colorRed)
                                statusText.text = "offline · ${r.error.take(60)}"
                                statusText.setTextColor(colorRed)
                            }
                            st.optBoolean("active") -> {
                                dot.background = oval(colorPrimary)
                                statusText.text = "TX ${st.optString("mode")} · " +
                                        "${st.optDouble("elapsed")}s · pid ${st.optInt("pid")}"
                                statusText.setTextColor(colorPrimary)
                            }
                            else -> {
                                dot.background = oval(colorAccent)
                                statusText.text = "standby · ${prefs.host}:${prefs.port}"
                                statusText.setTextColor(colorDim)
                            }
                        }
                        if (tab == "log" && logAuto) loadLog()
                    }
                }
                ui.postDelayed(this, 2000)
            }
        }
        ui.post(tick)
    }

    companion object { private const val PICK_FILE = 4711 }
}
