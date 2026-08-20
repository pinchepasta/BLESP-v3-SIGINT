package com.radar.blewifi

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.view.ViewGroup
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import com.radar.blewifi.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTheme()

        binding.btnSettingsBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        setupThemeSpinner()

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        binding.swImmersive.isChecked = prefs.getBoolean("immersive_mode", true)
        binding.swImmersive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("immersive_mode", isChecked).apply()
            if (isChecked) {
                enterImmersiveMode()
            } else {
                exitImmersiveMode()
            }
        }
    }

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun exitImmersiveMode() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private fun setThemeMode(theme: RadarView.Theme) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("theme_name", theme.name)
            .apply()
        setupTheme()
        // Re-setup spinner to update colors if needed, but avoid infinite loop
        // setupThemeSpinner() 
    }

    private fun setupThemeSpinner() {
        val themes = RadarView.Theme.values()
        val themeNames = themes.map { it.name.replace("_", " ") }
        
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, themeNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.typeface = Typeface.MONOSPACE
                v.setTextColor(getCurrentThemeTextColor())
                v.textSize = 14f
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.typeface = Typeface.MONOSPACE
                v.setTextColor(getCurrentThemeTextColor())
                v.setBackgroundColor(getCurrentThemeBgColor())
                v.setPadding(32, 32, 32, 32)
                return v
            }
        }
        
        binding.spTheme.adapter = adapter
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentThemeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        val currentTheme = RadarView.Theme.valueOf(currentThemeName ?: RadarView.Theme.DEFAULT.name)
        binding.spTheme.setSelection(themes.indexOf(currentTheme))

        binding.spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedTheme = themes[position]
                if (selectedTheme != currentTheme) {
                    setThemeMode(selectedTheme)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun getCurrentThemeTextColor(): Int {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        val theme = RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name)
        return when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            RadarView.Theme.MORIO -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }
    }

    private fun getCurrentThemeBgColor(): Int {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        val theme = RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name)
        return when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.WHITE
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#0A0000")
            RadarView.Theme.PINK -> Color.parseColor("#1A001A")
            RadarView.Theme.NEON -> Color.parseColor("#1A1A00")
            RadarView.Theme.NARANJA -> Color.parseColor("#1A0F00")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#1A1A1A")
            RadarView.Theme.MORIO -> Color.parseColor("#0A0A0A")
            else -> Color.BLACK
        }
    }

    private fun setupTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        val currentTheme = try { RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name) } catch(e: Exception) { RadarView.Theme.DEFAULT }

        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isPink = currentTheme == RadarView.Theme.PINK
        val isNeon = currentTheme == RadarView.Theme.NEON
        val isNaranja = currentTheme == RadarView.Theme.NARANJA
        val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM
        val isSummertime = currentTheme == RadarView.Theme.SUMMERTIME
        val isMorio = currentTheme == RadarView.Theme.MORIO

        val bgColor = when {
            isHighContrast -> Color.WHITE
            isRedNight || isPink || isNeon || isNaranja || isBubblegum || isSummertime || isMorio -> Color.parseColor("#0A0A0A")
            else -> Color.BLACK
        }

        val headerColor = when {
            isHighContrast -> Color.parseColor("#EEEEEE")
            isRedNight -> Color.parseColor("#1A0000")
            isPink -> Color.parseColor("#2A002A")
            isNeon -> Color.parseColor("#333300")
            isNaranja -> Color.parseColor("#331E00")
            isBubblegum -> Color.parseColor("#0F0F0F")
            isSummertime -> Color.parseColor("#2A1F1A")
            isMorio -> Color.parseColor("#0D1A18")
            else -> Color.parseColor("#0F0F0F")
        }

        val textColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            isPink -> Color.parseColor("#FF00FF")
            isNeon -> Color.parseColor("#E6FB04")
            isNaranja -> Color.parseColor("#FF8C00")
            isBubblegum -> Color.parseColor("#FF00FF")
            isSummertime -> Color.parseColor("#ff9f6b")
            isMorio -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }

        val accentColor = when {
            isSummertime -> Color.parseColor("#6befff")
            isMorio -> Color.parseColor("#c8f29e")
            else -> textColor
        }

        binding.settingsRoot.setBackgroundColor(bgColor)
        binding.headerSettings.setBackgroundColor(headerColor)

        binding.tvSettingsTitle.setTextColor(textColor)
        binding.tvLabelTheme.setTextColor(textColor)
        binding.tvLabelHud.setTextColor(textColor)
        binding.tvImmersiveText.setTextColor(textColor)
        
        binding.swImmersive.thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.swImmersive.trackTintList = android.content.res.ColorStateList.valueOf(accentColor).withAlpha(80)

        binding.btnSettingsBack.setTextColor(if (isPink || isBubblegum) Color.parseColor("#FF00FF") else if(isSummertime || isMorio) accentColor else textColor)
        binding.btnSettingsBack.setBackgroundResource(when {
            isHighContrast -> R.drawable.status_box_bg_white
            isRedNight -> R.drawable.status_box_bg_red
            isPink -> R.drawable.status_box_bg_pink
            isNeon -> R.drawable.status_box_bg_neon
            isNaranja -> R.drawable.status_box_bg_naranja
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.status_box_bg_summertime
            isMorio -> R.drawable.status_box_bg_morio
            else -> R.drawable.status_box_bg
        })
        
        // Update spinner background
        binding.spTheme.setBackgroundResource(when {
            isHighContrast -> R.drawable.status_box_bg_white
            isRedNight -> R.drawable.status_box_bg_red
            isPink -> R.drawable.status_box_bg_pink
            isNeon -> R.drawable.status_box_bg_neon
            isNaranja -> R.drawable.status_box_bg_naranja
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.status_box_bg_summertime
            isMorio -> R.drawable.status_box_bg_morio
            else -> R.drawable.status_box_bg
        })
    }
    
    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && getSharedPreferences("settings", MODE_PRIVATE).getBoolean("immersive_mode", true)) {
            enterImmersiveMode()
        }
    }
}
