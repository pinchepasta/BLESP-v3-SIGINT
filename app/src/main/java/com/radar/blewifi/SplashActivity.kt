package com.radar.blewifi

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.radar.blewifi.databinding.ActivitySplashBinding
import java.util.Random

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()

    private var currentTheme: RadarView.Theme = RadarView.Theme.DEFAULT
    private var primaryColor: Int = Color.parseColor(ThemeUtils.LEGACY_GREEN)
    private var accentColor: Int = Color.parseColor(ThemeUtils.LEGACY_PINK)
    private var secondaryColor: Int = Color.parseColor("#003B0F")
    private var bgColor: Int = Color.BLACK

    private val statusMessages = arrayOf(
        "LOADING UI COMPONENTS...",
        "INITIALIZING SCANNER ENGINE...",
        "REQUESTING PERMISSIONS...",
        "CALIBRATING SIGNAL SENSORS...",
        "BUILDING DEVICE DATABASE...",
        "OPTIMIZING RADAR INTERFACE...",
        "STARTING SCANNER...",
        "SYSTEM READY."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen mode for splash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyTheme()
        startGlitchAnimation()
        startLoadingSimulation()

        // Transition to MainActivity after 2 seconds to show off effects
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2500)
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = try { RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name) } catch(e: Exception) { RadarView.Theme.DEFAULT }

        val colors = ThemeUtils.getThemeColors(currentTheme)
        primaryColor = colors.primary
        accentColor = colors.accent
        bgColor = colors.background
        secondaryColor = colors.headerBar

        binding.root.setBackgroundColor(bgColor)
        binding.tvLoadingStatus.setTextColor(primaryColor)
        binding.tvLoadingPercent.setTextColor(primaryColor)
        binding.tvSplashSubTitle.setTextColor(primaryColor)

        binding.pbLoading.progressTintList = ColorStateList.valueOf(primaryColor)
        binding.pbLoading.progressBackgroundTintList = ColorStateList.valueOf(secondaryColor)

        // Logo tinting based on theme
        binding.ivSplashLogo.imageTintList = null // Keep original logo colors
        if (currentTheme != RadarView.Theme.HIGH_CONTRAST && currentTheme != RadarView.Theme.DEFAULT) {
            binding.ivGlitchCyan.imageTintList = ColorStateList.valueOf(accentColor)
            binding.ivGlitchPink.imageTintList = ColorStateList.valueOf(primaryColor)
        } else {
            binding.ivGlitchCyan.imageTintList = ColorStateList.valueOf(Color.CYAN)
            binding.ivGlitchPink.imageTintList = ColorStateList.valueOf(Color.MAGENTA)
        }
    }

    private fun startLoadingSimulation() {
        var progress = 0
        val progressRunnable = object : Runnable {
            override fun run() {
                if (progress <= 100) {
                    binding.pbLoading.progress = progress
                    binding.tvLoadingPercent.text = ""
                    
                    val statusIndex = (progress / (100 / statusMessages.size)).coerceAtMost(statusMessages.size - 1)
                    binding.tvLoadingStatus.text = statusMessages[statusIndex]

                    // Glitch the status text occasionally
                    if (random.nextInt(10) > 8) {
                        binding.tvLoadingStatus.alpha = 0.3f
                        binding.tvLoadingStatus.translationX = (random.nextInt(10) - 5).toFloat()
                    } else {
                        binding.tvLoadingStatus.alpha = 1f
                        binding.tvLoadingStatus.translationX = 0f
                    }

                    progress += random.nextInt(10) + 5
                    handler.postDelayed(this, (random.nextInt(80) + 20).toLong())
                }
            }
        }
        handler.post(progressRunnable)
    }

    private fun startGlitchAnimation() {
        val glitchRunnable = object : Runnable {
            override fun run() {
                // Static effect
                updateStatic()

                // Logo Glitch Logic
                val isCritical = random.nextInt(10) > 7
                if (isCritical) {
                    binding.ivGlitchCyan.visibility = View.VISIBLE
                    binding.ivGlitchPink.visibility = View.VISIBLE
                    
                    binding.ivGlitchCyan.translationX = (random.nextInt(20) - 10).toFloat()
                    binding.ivGlitchPink.translationX = (random.nextInt(20) - 10).toFloat()
                    
                    handler.postDelayed({
                        binding.ivGlitchCyan.visibility = View.INVISIBLE
                        binding.ivGlitchPink.visibility = View.INVISIBLE
                    }, (random.nextInt(50) + 20).toLong())
                }

                val nextDelay = (random.nextInt(400) + 50).toLong()
                handler.postDelayed(this, nextDelay)
            }
        }
        handler.post(glitchRunnable)
    }

    private fun updateStatic() {
        binding.staticContainer.removeAllViews()
        val staticView = object : android.view.View(this) {
            private val paint = android.graphics.Paint()
            override fun onDraw(canvas: android.graphics.Canvas) {
                for (i in 0..500) {
                    val color = if (random.nextBoolean()) primaryColor else accentColor
                    paint.color = color
                    paint.alpha = random.nextInt(50) + 20
                    val x = random.nextFloat() * width
                    val y = random.nextFloat() * height
                    canvas.drawPoint(x, y, paint)
                }
            }
        }
        binding.staticContainer.addView(staticView)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}