#!/usr/bin/env bash
# =============================================================================
#  mostly-a-SDR :: NEON REMOTE  —  one-file Android/Kotlin project generator
# -----------------------------------------------------------------------------
#  Creates a complete Android Studio project that remote-controls
#  https://github.com/pinchepasta/mostly-a-SDR  over its /api/v1 HTTP/JSON API.
#
#  Palette: neon orange primary, teal secondary, black background.
#
#  Usage:
#     ./make-neon-remote.sh                    -> ./mostly-a-sdr-neon-remote
#     ./make-neon-remote.sh ~/Projects/neon    -> custom target dir
#     ./make-neon-remote.sh --zip              -> also make a .zip
#     ./make-neon-remote.sh --no-wrapper       -> skip gradle-wrapper.jar fetch
# =============================================================================
set -euo pipefail

PKG_DIR="de/mostlyasdr/neonremote"
PKG="de.mostlyasdr.neonremote"
TARGET="mostly-a-sdr-neon-remote"
DO_ZIP=0
DO_WRAPPER=1
GRADLE_VER="8.7"

while [ $# -gt 0 ]; do
  case "$1" in
    --zip)        DO_ZIP=1; shift ;;
    --no-wrapper) DO_WRAPPER=0; shift ;;
    -h|--help)    sed -n '2,16p' "$0"; exit 0 ;;
    -*)           echo "unknown option: $1" >&2; exit 1 ;;
    *)            TARGET="$1"; shift ;;
  esac
done

say() { printf '\033[1;33m▓▒░\033[0m %s\n' "$*"; }

[ -e "$TARGET" ] && { say "$TARGET already exists - refreshing files"; }
A="$TARGET/app/src/main"

mkdir -p "$A/java/$PKG_DIR" "$A/res/values" "$A/res/xml" "$A/res/drawable" \
         "$TARGET/gradle/wrapper"

say "writing gradle files"
cat > "$TARGET/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "mostly-a-sdr-neon-remote"
include(":app")
EOF

cat > "$TARGET/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
EOF

cat > "$TARGET/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
EOF

cat > "$TARGET/gradle/wrapper/gradle-wrapper.properties" <<EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

cat > "$TARGET/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.mostlyasdr.neonremote"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.mostlyasdr.neonremote"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
EOF

cat > "$TARGET/app/proguard-rules.pro" <<'EOF'
-dontwarn org.json.**
EOF

say "writing manifest + resources"
cat > "$A/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:roundIcon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.NeonSdr">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > "$A/res/values/colors.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF050506</color>
    <color name="surface">#FF0B0C0F</color>
    <color name="neon_orange">#FFFF7A18</color>
    <color name="neon_teal">#FF00E5C7</color>
    <color name="text">#FFEDEDED</color>
    <color name="dim">#FF7C828C</color>
</resources>
EOF

cat > "$A/res/values/strings.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">SDR Neon Remote</string>
</resources>
EOF

cat > "$A/res/values/themes.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.NeonSdr" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">@color/neon_orange</item>
        <item name="colorOnPrimary">@color/black</item>
        <item name="colorSecondary">@color/neon_teal</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="colorSurface">@color/surface</item>
        <item name="colorOnSurface">@color/text</item>
        <item name="android:colorBackground">@color/black</item>
        <item name="android:windowBackground">@color/black</item>
        <item name="android:statusBarColor">@color/black</item>
        <item name="android:navigationBarColor">@color/black</item>
        <item name="android:textColorPrimary">@color/text</item>
        <item name="android:textColorSecondary">@color/dim</item>
    </style>
</resources>
EOF

cat > "$A/res/xml/network_security_config.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
EOF

cat > "$A/res/drawable/ic_launcher.xml" <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#050506" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#FF7A18"
        android:pathData="M20,64h10v12h-10z M36,52h10v24h-10z M52,40h10v36h-10z M68,26h10v50h-10z" />
    <path android:fillColor="#00E5C7" android:pathData="M20,82h58v6h-58z" />
</vector>
EOF

# -----------------------------------------------------------------------------
say "writing kotlin sources"
cat > "$A/java/$PKG_DIR/Neon.kt" <<'EOF'
package de.mostlyasdr.neonremote

import android.graphics.Color

/** Palette: neon orange primary, teal secondary, black canvas. */
object Neon {
    val BG        = Color.parseColor("#050506")
    val SURFACE   = Color.parseColor("#0B0C0F")
    val SURFACE2  = Color.parseColor("#13151A")
    val ORANGE    = Color.parseColor("#FF7A18")
    val ORANGE_DK = Color.parseColor("#B35410")
    val TEAL      = Color.parseColor("#00E5C7")
    val TEAL_DK   = Color.parseColor("#0C6E62")
    val RED       = Color.parseColor("#FF3B5C")
    val TEXT      = Color.parseColor("#EDEDED")
    val DIM       = Color.parseColor("#7C828C")
}
EOF

cat > "$A/java/$PKG_DIR/Prefs.kt" <<'EOF'
package de.mostlyasdr.neonremote

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("masdr_neon", Context.MODE_PRIVATE)

    var host: String
        get() = sp.getString("host", "") ?: ""
        set(v) = sp.edit().putString("host", v.trim()).apply()

    var port: Int
        get() = sp.getInt("port", 8765)
        set(v) = sp.edit().putInt("port", v).apply()

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()

    var freq: Float
        get() = sp.getFloat("freq", 434.0f)
        set(v) = sp.edit().putFloat("freq", v).apply()

    var autoStop: Int
        get() = sp.getInt("auto_stop", 0)
        set(v) = sp.edit().putInt("auto_stop", v).apply()

    var stopBeforeStart: Boolean
        get() = sp.getBoolean("sbs", true)
        set(v) = sp.edit().putBoolean("sbs", v).apply()

    var verbose: Boolean
        get() = sp.getBoolean("verbose", false)
        set(v) = sp.edit().putBoolean("verbose", v).apply()

    val webUrl: String get() = "http://$host:$port/?token=$token"
}
EOF

cat > "$A/java/$PKG_DIR/Api.kt" <<'EOF'
package de.mostlyasdr.neonremote

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Thin client for the mostly-a-SDR /api/v1 HTTP/JSON API.
 * Bearer-token auth, JSON in / JSON out, everything off the UI thread.
 */
class Api(private val prefs: Prefs) {

    class Res(val code: Int, val text: String) {
        val ok: Boolean get() = code in 200..299
        val json: JSONObject get() = try {
            if (text.trimStart().startsWith("{")) JSONObject(text) else JSONObject()
        } catch (e: Exception) { JSONObject() }
        val error: String get() = json.optString("error",
            if (text.isBlank()) "HTTP $code" else text.take(200))
    }

    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    private fun base() = "http://${prefs.host}:${prefs.port}"

    fun enc(s: String): String = try { URLEncoder.encode(s, "UTF-8") } catch (e: Exception) { s }

    fun shutdown() = pool.shutdownNow()

    private fun open(method: String, path: String, body: JSONObject?): HttpURLConnection {
        val c = URL(base() + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 6000
        c.readTimeout = 40000
        if (prefs.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${prefs.token}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        return c
    }

    fun call(method: String, path: String, body: JSONObject? = null, cb: (Res) -> Unit) {
        pool.execute {
            val r = try {
                val c = open(method, path, body)
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                Res(code, txt)
            } catch (e: Exception) {
                Res(0, "{\"error\":\"${(e.message ?: "network error").replace("\"", "'")}\"}")
            }
            main.post { cb(r) }
        }
    }

    fun get(path: String, cb: (Res) -> Unit) = call("GET", path, null, cb)
    fun post(path: String, body: JSONObject?, cb: (Res) -> Unit) = call("POST", path, body ?: JSONObject(), cb)
    fun delete(path: String, cb: (Res) -> Unit) = call("DELETE", path, null, cb)

    /** Raw-body upload: POST /api/v1/upload?kind=&name= */
    fun upload(kind: String, name: String, uri: Uri, cr: ContentResolver, cb: (Res) -> Unit) {
        pool.execute {
            val r = try {
                val safe = displayName(uri, cr) ?: name
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val c = URL("${base()}/api/v1/upload?kind=$kind&name=${enc(safe)}")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 6000
                c.readTimeout = 120000
                c.doOutput = true
                if (prefs.token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${prefs.token}")
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.outputStream.use { it.write(bytes) }
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                Res(code, txt)
            } catch (e: Exception) {
                Res(0, "{\"error\":\"${(e.message ?: "upload failed").replace("\"", "'")}\"}")
            }
            main.post { cb(r) }
        }
    }

    private fun displayName(uri: Uri, cr: ContentResolver): String? =
        cr.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return@use cur.getString(i)
            }
            null
        } ?: uri.lastPathSegment
}
EOF

say "writing MainActivity.kt"
cat > "$A/java/$PKG_DIR/MainActivity.kt" <<'EOF'
package de.mostlyasdr.neonremote

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote control for the mostly-a-SDR API (/api/v1).
 *
 * The whole UI is built in code: header with live TX status + frequency,
 * a bottom tab bar, and screens generated from the Pi's own mode catalogue,
 * so anything the Pi adds shows up without touching the app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var api: Api
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var content: FrameLayout
    private lateinit var dot: View
    private lateinit var statusText: TextView
    private lateinit var freqField: EditText

    private val navs = LinkedHashMap<String, TextView>()

    private var freq = 434.0
    private var modes: JSONArray? = null
    private var subSpinner: Spinner? = null
    private var fileKind = "sub"
    private var tab = "tx"
    private var polling = false
    private var logAuto = true
    private var logTx = false
    private var logOut: TextView? = null

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        api = Api(prefs)
        freq = prefs.freq.toDouble()
        setContentView(buildRoot())
        if (prefs.host.isBlank()) {
            show("link")
        } else {
            show("tx"); loadModes(); loadSubs()
        }
        startPoll()
    }

    override fun onDestroy() {
        super.onDestroy(); polling = false; api.shutdown()
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

    private fun txt(t: String, color: Int = Neon.TEXT, size: Float = 13.5f, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(color)
        v.textSize = size
        v.typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                     else Typeface.MONOSPACE
        return v
    }

    private fun <T : View> pad(v: T, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): T {
        v.setPadding(dp(l), dp(t), dp(r), dp(b)); return v
    }

    private fun btn(label: String, color: Int = Neon.ORANGE, filled: Boolean = false,
                    onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 12.5f
        b.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        b.setTextColor(if (filled) Neon.BG else color)
        b.background = if (filled) round(color, null, 8) else round(Neon.SURFACE, color, 8)
        b.setPadding(dp(10), dp(6), dp(10), dp(6))
        b.minHeight = 0
        b.minimumHeight = 0
        b.setOnClickListener { onClick() }
        return b
    }

    private fun card(title: String?, accent: Int = Neon.TEAL): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.background = round(Neon.SURFACE, accent, 12)
        l.setPadding(dp(12), dp(12), dp(12), dp(12))
        val p = LinearLayout.LayoutParams(MP, WC); p.bottomMargin = dp(12)
        l.layoutParams = p
        if (title != null) {
            val t = txt(title, accent, 11.5f, true)
            t.letterSpacing = 0.10f
            l.addView(pad(t, 0, 0, 0, 6))
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
        l.setPadding(dp(12), dp(12), dp(12), dp(28))
        return l
    }

    private fun scroll(v: View): ScrollView {
        val s = ScrollView(this)
        s.setBackgroundColor(Neon.BG)
        s.addView(v)
        return s
    }

    private fun field(value: String, numeric: Boolean = false, hint: String = ""): EditText {
        val e = EditText(this)
        if (value.isNotEmpty()) e.setText(value)
        if (hint.isNotEmpty()) e.hint = hint
        e.textSize = 13f
        e.typeface = Typeface.MONOSPACE
        e.setTextColor(Neon.TEXT)
        e.setHintTextColor(Neon.DIM)
        e.background = round(Neon.SURFACE2, Neon.TEAL_DK, 8)
        e.setPadding(dp(10), dp(8), dp(10), dp(8))
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        return e
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun defStr(p: JSONObject): String {
        val d = p.opt("default")
        return if (d == null || d == JSONObject.NULL) "" else d.toString()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ─────────────────────────────── chrome ───────────────────────────────

    private fun buildRoot(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Neon.BG)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(14), dp(14), dp(14), dp(8))

        val titleRow = row()
        titleRow.addView(txt("▓▒░ SDR NEON REMOTE", Neon.ORANGE, 16f, true), lp(1f))
        titleRow.addView(btn("STOP", Neon.RED) { stopTx() })
        head.addView(titleRow)

        val statRow = row()
        dot = View(this)
        dot.background = oval(Neon.DIM)
        val dl = LinearLayout.LayoutParams(dp(10), dp(10)); dl.rightMargin = dp(8)
        statRow.addView(dot, dl)
        statusText = txt("standby", Neon.DIM, 12f)
        statRow.addView(statusText, lp(1f))
        statRow.addView(btn("LINK", Neon.TEAL) { show("link") })
        head.addView(statRow)

        val freqRow = row()
        freqField = field(freq.toString(), numeric = true, hint = "MHz")
        freqRow.addView(freqField, lp(1f))
        freqRow.addView(pad(txt("MHz", Neon.DIM, 11f), 8, 0, 8, 0))
        freqRow.addView(btn("SET", Neon.ORANGE, true) { pushFreq() })
        head.addView(pad(freqRow, 0, 6, 0, 0))
        root.addView(head)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MP, 0, 1f))

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Neon.SURFACE)
        nav.setPadding(dp(4), dp(4), dp(4), dp(4))
        listOf("tx" to "TX", "files" to "FILES", "tools" to "TOOLS",
               "log" to "LOG", "sys" to "SYS").forEach { (k, l) ->
            val t = txt(l, Neon.DIM, 11f, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
            t.setOnClickListener { show(k) }
            navs[k] = t
            nav.addView(t, lp(1f))
        }
        root.addView(nav)
        return root
    }

    private fun show(id: String) {
        tab = id
        navs.forEach { (k, v) -> v.setTextColor(if (k == id) Neon.ORANGE else Neon.DIM) }
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
        col.addView(pad(txt("TRANSMIT", Neon.ORANGE, 12.5f, true).also { it.letterSpacing = 0.12f },
            0, 0, 0, 8))
        col.addView(subCard())
        col.addView(modesCard())
        return scroll(col)
    }

    /** The .sub dropdown: lists captures stored on the Pi and replays one. */
    private fun subCard(): View {
        val c = card("SUB-GHZ REPLAY · .SUB CAPTURES", Neon.ORANGE)
        c.addView(pad(txt("pick a saved capture from the Pi's sub_dir", Neon.DIM, 10.5f), 0, 0, 0, 6))

        val sp = Spinner(this)
        sp.adapter = simpleAdapter(listOf("loading…"))
        sp.background = round(Neon.SURFACE2, Neon.TEAL, 8)
        subSpinner = sp
        c.addView(sp, lpFull())

        val r1 = row()
        r1.addView(btn("REFRESH", Neon.TEAL) { loadSubs() })
        val play = Spinner(this).apply { adapter = simpleAdapter(listOf("once", "loop")) }
        r1.addView(pad(play, 8, 0, 0, 0), lp(1f))
        c.addView(pad(r1, 0, 8, 0, 0))

        val r2 = row()
        r2.addView(txt("repeat", Neon.DIM, 10.5f))
        val rep = field("3", numeric = true)
        r2.addView(pad(rep, 6, 0, 10, 0), lp(1f))
        r2.addView(txt("secs", Neon.DIM, 10.5f))
        val dur = field("0", numeric = true)
        r2.addView(pad(dur, 6, 0, 0, 0), lp(1f))
        c.addView(pad(r2, 0, 6, 0, 0))

        c.addView(pad(btn("▶ TRANSMIT .SUB", Neon.ORANGE, true) {
            val name = subSpinner?.selectedItem?.toString() ?: ""
            if (name.isBlank() || name == "loading…" || name.startsWith("(")) {
                toast("no .sub file selected"); return@btn
            }
            val body = JSONObject()
                .put("mode", "sub")
                .put("params", JSONObject()
                    .put("file", name)
                    .put("playback", play.selectedItem?.toString() ?: "once")
                    .put("repeat", rep.text.toString().toIntOrNull() ?: 3))
                .put("duration", dur.text.toString().toDoubleOrNull() ?: 0.0)
            api.post("/api/v1/tx", body) { r ->
                toast(if (r.ok) "replaying $name" else "error: ${r.error}")
            }
        }, 0, 8, 0, 0))
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
        val c = card("MODES · EVERY FUNCTION THE PI EXPOSES", Neon.TEAL)
        val arr = modes
        if (arr == null) {
            c.addView(txt("loading catalogue…", Neon.DIM, 12f))
            loadModes()
            return c
        }
        var group = ""
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            if (m.optString("group") != group) {
                group = m.optString("group")
                c.addView(pad(txt("── $group", Neon.ORANGE, 11f, true), 0, 10, 0, 4))
            }
            c.addView(modeRow(m))
        }
        c.addView(pad(btn("REFRESH CATALOGUE", Neon.TEAL) { loadModes() }, 0, 8, 0, 0))
        return c
    }

    private fun modeRow(m: JSONObject): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.background = round(Neon.SURFACE2, Neon.TEAL_DK, 8)
        r.setPadding(dp(10), dp(8), dp(10), dp(8))
        val p = LinearLayout.LayoutParams(MP, WC); p.bottomMargin = dp(6)
        r.layoutParams = p
        r.addView(txt(m.optString("label"), Neon.TEXT, 13f, true))
        val help = m.optString("help")
        if (help.isNotBlank()) r.addView(txt(help, Neon.DIM, 10.5f))
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
            val label = p.optString("name") + (if (unit.isNotBlank()) "  [$unit]" else "")
            box.addView(pad(txt(label, Neon.TEAL, 11f, true), 0, 6, 0, 2))

            val v: View = when (type) {
                "bool" -> CheckBox(this).apply { text = "on"; setTextColor(Neon.TEXT) }
                "choice" -> Spinner(this).apply {
                    val ch = p.optJSONArray("choices")
                    val list = ArrayList<String>()
                    for (c in 0 until (ch?.length() ?: 0)) list.add(ch!!.getString(c))
                    adapter = simpleAdapter(list)
                    val idx = list.indexOf(p.optString("default"))
                    if (idx >= 0) setSelection(idx)
                }
                "number" -> field(defStr(p), numeric = true)
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
                else -> field(defStr(p))
            }
            box.addView(v, lpFull())
            val help = p.optString("help")
            if (help.isNotBlank()) box.addView(txt(help, Neon.DIM, 10.5f))
            fields.add(Triple(p, type, v))
        }

        box.addView(pad(txt("duration in seconds (0 = until stopped)", Neon.DIM, 11f), 0, 8, 0, 2))
        val dur = field("0", numeric = true)
        box.addView(dur, lpFull())

        val dlg = AlertDialog.Builder(this)
            .setTitle(mode.optString("label"))
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("TRANSMIT", null)
            .setNeutralButton("STOP", null)
            .setNegativeButton("close", null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
                    .put("duration", dur.text.toString().toDoubleOrNull() ?: 0.0)
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
        col.addView(pad(txt("FILES ON THE PI", Neon.ORANGE, 12.5f, true), 0, 0, 0, 8))

        val tabs = row()
        listOf("sub", "media", "iq").forEach { k ->
            tabs.addView(btn(k.uppercase(),
                if (k == fileKind) Neon.ORANGE else Neon.DIM,
                k == fileKind) { fileKind = k; show("files") }, lp(1f))
        }
        col.addView(pad(tabs, 0, 0, 0, 10))

        val c = card(null, Neon.TEAL)
        c.addView(btn("UPLOAD  →  $fileKind", Neon.TEAL) { pickFile() }, lpFull())
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        c.addView(pad(list, 0, 8, 0, 0))
        col.addView(c)

        api.get("/api/v1/files?kind=$fileKind") { r ->
            val arr = r.json.optJSONArray("files")
            if (arr == null || arr.length() == 0) {
                list.addView(txt("(empty)", Neon.DIM, 12f))
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
        val p = LinearLayout.LayoutParams(MP, WC); p.bottomMargin = dp(4)
        r.layoutParams = p
        r.addView(txt("$name  (${size / 1024} kB)", Neon.TEXT, 11.5f), lp(1f))
        r.addView(btn("TX", Neon.ORANGE) { txFile(name) })
        r.addView(btn("GET", Neon.TEAL) {
            val url = "http://${prefs.host}:${prefs.port}/api/v1/file" +
                    "?kind=$fileKind&name=${api.enc(name)}&token=${api.enc(prefs.token)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        })
        r.addView(btn("DEL", Neon.RED) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE && resultCode == Activity.RESULT_OK) {
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
        col.addView(pad(txt("TOOLS", Neon.ORANGE, 12.5f, true), 0, 0, 0, 8))

        val raw = card("RAW SHELL  ·  POST /raw", Neon.TEAL)
        val cmd = field("testvfo.sh 434e6")
        raw.addView(cmd, lpFull())
        val bgBox = CheckBox(this).apply {
            text = "background (fire and forget)"; setTextColor(Neon.TEXT)
        }
        raw.addView(bgBox)
        val rawOut = txt("", Neon.DIM, 10.5f)
        raw.addView(btn("RUN", Neon.ORANGE, true) {
            api.post("/api/v1/raw", JSONObject()
                .put("cmd", cmd.text.toString())
                .put("background", bgBox.isChecked)) { r -> rawOut.text = r.text.take(4000) }
        })
        raw.addView(rawOut)
        col.addView(raw)

        val sdt = card("sdrtool.py  ·  capture tooling", Neon.TEAL)
        val op = Spinner(this).apply {
            adapter = simpleAdapter(listOf("info", "freq", "sub", "analyze", "fields", "hex", "render"))
        }
        sdt.addView(op, lpFull())
        val cu8 = field("", hint = "capture .cu8 name")
        sdt.addView(cu8, lpFull())
        val outPath = field("", hint = "output path (optional)")
        sdt.addView(outPath, lpFull())
        val sdtOut = txt("", Neon.DIM, 10.5f)
        sdt.addView(btn("RUN sdrtool", Neon.ORANGE, true) {
            api.post("/api/v1/sdrtool", JSONObject()
                .put("op", op.selectedItem?.toString() ?: "info")
                .put("cu8", cu8.text.toString())
                .put("out", outPath.text.toString())) { r -> sdtOut.text = r.text.take(4000) }
        })
        sdt.addView(sdtOut)
        col.addView(sdt)

        val cfg = card("SERVER CONFIG", Neon.TEAL)
        cfg.addView(txt("auto_stop_seconds (0 = off)", Neon.DIM, 10.5f))
        val auto = field(prefs.autoStop.toString(), numeric = true)
        cfg.addView(auto, lpFull())
        val sbs = CheckBox(this).apply {
            text = "stop_before_start"; isChecked = prefs.stopBeforeStart; setTextColor(Neon.TEXT)
        }
        cfg.addView(sbs)
        val vrb = CheckBox(this).apply {
            text = "verbose logging"; isChecked = prefs.verbose; setTextColor(Neon.TEXT)
        }
        cfg.addView(vrb)
        cfg.addView(btn("SAVE CONFIG", Neon.ORANGE, true) {
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

        col.addView(btn("RESTART API SERVICE", Neon.RED) {
            api.post("/api/v1/restart", JSONObject()) { toast("restarting service") }
        })
        col.addView(pad(btn("COPY WEB UI LINK", Neon.TEAL) {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("sdr", prefs.webUrl))
            toast("link copied")
        }, 0, 8, 0, 0))
        return scroll(col)
    }

    // ───────────────────────────── log screen ─────────────────────────────

    private fun logScreen(): View {
        val col = column()
        col.addView(pad(txt("LOG", Neon.ORANGE, 12.5f, true), 0, 0, 0, 8))

        val r = row()
        r.addView(CheckBox(this).apply {
            text = "auto"; isChecked = logAuto; setTextColor(Neon.TEXT)
            setOnCheckedChangeListener { _, b -> logAuto = b }
        })
        r.addView(CheckBox(this).apply {
            text = "tx.log"; isChecked = logTx; setTextColor(Neon.TEXT)
            setOnCheckedChangeListener { _, b -> logTx = b; loadLog() }
        })
        r.addView(btn("REFRESH", Neon.TEAL) { loadLog() })
        col.addView(r)

        val out = txt("", Neon.TEXT, 10.5f)
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
        col.addView(pad(txt("SYSTEM", Neon.ORANGE, 12.5f, true), 0, 0, 0, 8))
        val out = txt("loading…", Neon.TEXT, 10.5f)
        col.addView(btn("REFRESH", Neon.TEAL) { loadSys(out) })
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
        col.addView(pad(txt("LINK TO THE PI", Neon.ORANGE, 12.5f, true), 0, 0, 0, 8))

        val c = card("CONNECTION", Neon.TEAL)
        val h = field(prefs.host, hint = "192.168.1.50  or  mostly-a-sdr.local")
        val p = field(prefs.port.toString(), numeric = true)
        val t = field(prefs.token, hint = "API token printed by the installer")
        c.addView(txt("host", Neon.DIM, 10.5f)); c.addView(h, lpFull())
        c.addView(txt("port", Neon.DIM, 10.5f)); c.addView(p, lpFull())
        c.addView(txt("token", Neon.DIM, 10.5f)); c.addView(t, lpFull())
        c.addView(pad(btn("DISCOVER (mDNS  _masdr._tcp)", Neon.TEAL) { discover(h, p) }, 0, 8, 0, 0))
        c.addView(btn("CONNECT", Neon.ORANGE, true) {
            prefs.host = h.text.toString()
            prefs.port = p.text.toString().toIntOrNull() ?: 8765
            prefs.token = t.text.toString()
            api.get("/api/v1/status") { r ->
                if (r.ok) { toast("connected"); show("tx"); loadModes(); loadSubs() }
                else toast("failed: ${r.error}")
            }
        })
        c.addView(pad(txt("The Pi also serves a web UI at /?token=… . " +
                "mDNS needs both devices on the same LAN.", Neon.DIM, 10.5f), 0, 8, 0, 0))
        col.addView(c)
        return scroll(col)
    }

    private fun discover(hostField: EditText, portField: EditText) {
        val nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
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
        val mhz = freqField.text.toString().toDoubleOrNull()
        if (mhz == null) { toast("bad frequency"); return }
        api.post("/api/v1/freq", JSONObject().put("mhz", mhz)) { r ->
            if (r.ok) { freq = mhz; prefs.freq = mhz.toFloat(); toast("frequency set") }
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
                                dot.background = oval(Neon.RED)
                                statusText.text = "offline · ${r.error.take(60)}"
                                statusText.setTextColor(Neon.RED)
                            }
                            st.optBoolean("active") -> {
                                dot.background = oval(Neon.ORANGE)
                                statusText.text = "TX ${st.optString("mode")} · " +
                                        "${st.optDouble("elapsed")}s · pid ${st.optInt("pid")}"
                                statusText.setTextColor(Neon.ORANGE)
                            }
                            else -> {
                                dot.background = oval(Neon.TEAL)
                                statusText.text = "standby · ${prefs.host}:${prefs.port}"
                                statusText.setTextColor(Neon.DIM)
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
EOF

# -----------------------------------------------------------------------------
say "writing helper scripts + readme"
cat > "$TARGET/build-apk.sh" <<'EOF'
#!/usr/bin/env bash
# Build the debug APK. Needs JDK 17 + Android SDK.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "[!] gradle-wrapper.jar missing - fetching"
  curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
    "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" \
    || { echo "fetch failed; run: gradle wrapper --gradle-version 8.7"; exit 1; }
fi

if [ ! -f local.properties ]; then
  [ -n "${ANDROID_SDK_ROOT:-}" ] && printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
  [ -n "${ANDROID_HOME:-}" ] && printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
fi

./gradlew assembleDebug
mkdir -p dist
cp -f app/build/outputs/apk/debug/app-debug.apk dist/sdr-neon-remote.apk
echo "APK -> $(pwd)/dist/sdr-neon-remote.apk"
EOF
chmod +x "$TARGET/build-apk.sh"

cat > "$TARGET/README.md" <<'EOF'
# SDR Neon Remote

Android remote control for [mostly-a-SDR](https://github.com/pinchepasta/mostly-a-SDR),
talking to its `/api/v1` HTTP/JSON API on the Pi.

Neon orange / teal on black. Kotlin, no third-party libs beyond AndroidX + Material.

## Build

    ./build-apk.sh                 # or: open the folder in Android Studio

## First run

1. On the Pi: `sudo ./mostly-a-sdr.sh` (the API installer) — note the token it prints.
2. In the app: LINK tab -> host (IP or `mostly-a-sdr.local`), port 8765, token -> CONNECT
   (or DISCOVER to find the Pi over mDNS).
3. TX tab: pick a `.sub` from the dropdown and hit TRANSMIT, or open any mode.

## Screens

* **TX** — `.sub` dropdown (playback once/loop, repeat, duration), frequency setter, full mode catalogue
* **FILES** — browse sub / media / iq, upload, download, delete, one-tap transmit
* **TOOLS** — raw shell, sdrtool.py, server config, service restart
* **LOG** — server + tx.log tail
* **SYS** — host, temperature, load, disk, tool availability
* **LINK** — connection settings + mDNS discovery

The mode catalogue is fetched from `GET /api/v1/modes`, so every mode the Pi
exposes appears in the app automatically.
EOF

# -----------------------------------------------------------------------------
if [ "$DO_WRAPPER" -eq 1 ]; then
  if [ ! -f "$TARGET/gradle/wrapper/gradle-wrapper.jar" ]; then
    say "fetching gradle-wrapper.jar (${GRADLE_VER})"
    curl -fsSL -o "$TARGET/gradle/wrapper/gradle-wrapper.jar" \
      "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VER}.0/gradle/wrapper/gradle-wrapper.jar" \
      || { rm -f "$TARGET/gradle/wrapper/gradle-wrapper.jar"; say "fetch failed - run 'gradle wrapper' or open in Android Studio"; }
  fi
fi

cat > "$TARGET/gradlew" <<'EOF'
#!/bin/sh
app_path=$0
while
    APP_HOME=${app_path%"${app_path##*/}"}
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in
      /*)   app_path=$link ;;
      *)    app_path=$APP_HOME$link ;;
    esac
done
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit
MAX_FD=maximum
warn () { echo "$*"; } >&2
die () { echo; echo "$*"; echo; exit 1; } >&2
case "$( uname )" in
  CYGWIN* ) cygwin=true ;; Darwin* ) darwin=true ;; MSYS* | MINGW* ) msys=true ;;
esac
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
set -- "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" \
       org.gradle.wrapper.GradleWrapperMain "$@"
eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'
exec "$JAVACMD" "$@"
EOF
chmod +x "$TARGET/gradlew"

cat > "$TARGET/gradlew.bat" <<'EOF'
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
goto fail
:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
goto fail
:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
:end
if %ERRORLEVEL% equ 0 goto mainEnd
:fail
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%
:mainEnd
if "%OS%"=="Windows_NT" endlocal
:omega
EOF

if [ "$DO_ZIP" -eq 1 ] && command -v zip >/dev/null 2>&1; then
  say "zipping"
  ( cd "$TARGET/.." && zip -qr "$(basename "$TARGET").zip" "$(basename "$TARGET")" -x '*/build/*' -x '*/.gradle/*' )
fi

echo
say "done"
echo "  project : $(cd "$TARGET" && pwd)"
echo "  package : $PKG"
echo
echo "  open it in Android Studio, or:"
echo "     cd $TARGET && ./build-apk.sh"
echo