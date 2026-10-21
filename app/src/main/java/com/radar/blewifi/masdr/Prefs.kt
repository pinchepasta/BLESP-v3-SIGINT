package com.radar.blewifi.masdr

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

    var activeTab: String
        get() = sp.getString("active_tab", "tx") ?: "tx"
        set(v) = sp.edit().putString("active_tab", v).apply()

    var fileKind: String
        get() = sp.getString("file_kind", "sub") ?: "sub"
        set(v) = sp.edit().putString("file_kind", v).apply()

    var lastPlayback: String
        get() = sp.getString("last_playback", "once") ?: "once"
        set(v) = sp.edit().putString("last_playback", v).apply()

    var lastRepeat: Int
        get() = sp.getInt("last_repeat", 3)
        set(v) = sp.edit().putInt("last_repeat", v).apply()

    var lastDuration: Double
        get() = sp.getFloat("last_duration", 0f).toDouble()
        set(v) = sp.edit().putFloat("last_duration", v.toFloat()).apply()

    var lastRawCmd: String
        get() = sp.getString("last_raw_cmd", "testvfo.sh 434e6") ?: "testvfo.sh 434e6"
        set(v) = sp.edit().putString("last_raw_cmd", v).apply()

    var lastSdrToolCu8: String
        get() = sp.getString("last_sdrtool_cu8", "") ?: ""
        set(v) = sp.edit().putString("last_sdrtool_cu8", v).apply()

    var lastSdrToolOut: String
        get() = sp.getString("last_sdrtool_out", "") ?: ""
        set(v) = sp.edit().putString("last_sdrtool_out", v).apply()

    val webUrl: String get() = "http://$host:$port/?token=$token"
}
