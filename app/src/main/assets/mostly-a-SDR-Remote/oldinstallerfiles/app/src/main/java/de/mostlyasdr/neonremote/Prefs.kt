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
