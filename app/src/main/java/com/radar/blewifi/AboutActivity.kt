package com.radar.blewifi

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.radar.blewifi.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTheme()

        binding.btnAboutBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.tvDevValue.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("http://www.mostlyawesome.de"))
            startActivity(intent)
        }
        
        setupImmersiveMode()
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

        val bgColor = when {
            isHighContrast -> Color.WHITE
            isRedNight || isPink || isNeon || isNaranja || isBubblegum || isSummertime -> Color.parseColor("#0A0A0A")
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
            else -> Color.parseColor("#00FF41")
        }

        val accentColor = when {
            isSummertime -> Color.parseColor("#6befff")
            isBubblegum -> Color.parseColor("#00FDFF")
            else -> textColor
        }

        binding.aboutRoot.setBackgroundColor(bgColor)
        binding.headerAbout.setBackgroundColor(headerColor)

        binding.tvAboutTitle.setTextColor(accentColor)
        binding.tvLabelVersion.setTextColor(accentColor)
        binding.tvVersionValue.setTextColor(textColor)
        binding.tvLabelAuthor.setTextColor(accentColor)
        binding.tvAuthorValue.setTextColor(textColor)
        binding.tvLabelDev.setTextColor(accentColor)
        binding.tvDevValue.setTextColor(textColor)
        binding.tvAboutDescription.setTextColor(textColor)
        binding.tvAboutDescription.alpha = 0.7f

        binding.btnAboutBack.setTextColor(if (isPink || isBubblegum) Color.parseColor("#FF00FF") else if(isSummertime) accentColor else textColor)
        binding.btnAboutBack.setBackgroundResource(when {
            isHighContrast -> R.drawable.status_box_bg_white
            isRedNight -> R.drawable.status_box_bg_red
            isPink -> R.drawable.status_box_bg_pink
            isNeon -> R.drawable.status_box_bg_neon
            isNaranja -> R.drawable.status_box_bg_naranja
            isBubblegum -> R.drawable.btn_bg_bubblegum
            isSummertime -> R.drawable.status_box_bg_summertime
            else -> R.drawable.status_box_bg
        })
    }

    private fun setupImmersiveMode() {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("immersive_mode", true)) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
            return
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }
}
