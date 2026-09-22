package com.radar.blewifi.qr.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.radar.blewifi.R
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.radar.blewifi.RadarView
import com.radar.blewifi.ThemeUtils

class QrActivity : AppCompatActivity() {

    private var currentThemeColors: ThemeUtils.ThemeColors? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qr_activity_main)

        setupImmersiveMode()

        val pager = findViewById<ViewPager2>(R.id.pager)
        val tabs = findViewById<TabLayout>(R.id.tabs)
        val btnBack = findViewById<MaterialButton>(R.id.btn_back)

        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) SendFragment() else ReceiveFragment()
        }

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_send) else getString(R.string.tab_receive)
        }.attach()

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        val currentTheme = try {
            RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name)
        } catch (e: Exception) {
            RadarView.Theme.DEFAULT
        }

        val colors = ThemeUtils.getThemeColors(currentTheme)
        currentThemeColors = colors

        findViewById<View>(android.R.id.content).setBackgroundColor(colors.background)

        val qrHeader = findViewById<View>(R.id.qr_header)
        qrHeader?.setBackgroundColor(colors.header)

        val btnBack = findViewById<MaterialButton>(R.id.btn_back)
        btnBack?.apply {
            setTextColor(colors.primary)
            typeface = android.graphics.Typeface.MONOSPACE
            rippleColor = ColorStateList.valueOf(colors.primary and 0x33FFFFFF)
        }

        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs?.apply {
            setBackgroundColor(colors.headerBar)
            setSelectedTabIndicatorColor(colors.primary)
            setTabTextColors(colors.dim, colors.primary)
            
            // Apply monospace to tab text if possible
            for (i in 0 until tabCount) {
                val tab = getTabAt(i)
                val tabView = (getChildAt(0) as ViewGroup).getChildAt(i)
                for (j in 0 until (tabView as ViewGroup).childCount) {
                    val tabViewChild = tabView.getChildAt(j)
                    if (tabViewChild is TextView) {
                        tabViewChild.typeface = android.graphics.Typeface.MONOSPACE
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
