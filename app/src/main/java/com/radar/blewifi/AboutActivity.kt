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
        val colors = ThemeUtils.getThemeColors(currentTheme)

        val bgColor = colors.background
        val headerColor = colors.header
        val textColor = colors.primary
        val accentColor = colors.accent

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

        binding.btnAboutBack.setTextColor(accentColor)
        binding.btnAboutBack.setBackgroundResource(colors.statusBoxRes)
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

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
