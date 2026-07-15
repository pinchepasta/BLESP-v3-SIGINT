package com.radar.blewifi

import android.content.Intent
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

        startGlitchAnimation()
        startLoadingSimulation()

        // Transition to MainActivity after 2 seconds to show off effects
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2500)
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
                // Randomly show/hide glitch layers and offset them
                val glitchType = random.nextInt(20)
                
                when {
                    glitchType > 17 -> { // Heavy glitch
                        binding.ivGlitchCyan.visibility = View.VISIBLE
                        binding.ivGlitchPink.visibility = View.VISIBLE
                        binding.ivGlitchCyan.translationX = (random.nextInt(40) - 20).toFloat()
                        binding.ivGlitchPink.translationX = (random.nextInt(40) - 20).toFloat()
                        binding.ivSplashLogo.scaleX = 1.1f
                        binding.ivSplashLogo.alpha = 0.5f
                    }
                    glitchType > 14 -> { // Soft glitch
                        binding.ivGlitchCyan.visibility = View.VISIBLE
                        binding.ivGlitchPink.visibility = View.INVISIBLE
                        binding.ivGlitchCyan.translationX = (random.nextInt(10) - 5).toFloat()
                        binding.ivSplashLogo.scaleX = 1.0f
                        binding.ivSplashLogo.alpha = 0.9f
                    }
                    else -> {
                        binding.ivGlitchCyan.visibility = View.INVISIBLE
                        binding.ivGlitchPink.visibility = View.INVISIBLE
                        binding.ivSplashLogo.translationX = 0f
                        binding.ivSplashLogo.translationY = 0f
                        binding.ivSplashLogo.scaleX = 1.0f
                        binding.ivSplashLogo.alpha = 1.0f
                    }
                }
                
                // Static effect
                updateStatic()
                
                val nextDelay = if (glitchType > 17) 40L else (random.nextInt(400) + 50).toLong()
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
                    val colorStr = if (random.nextBoolean()) "#FFFFFF" else "#FF00FF"
                    paint.color = android.graphics.Color.parseColor(colorStr)
                    paint.alpha = random.nextInt(50) + 20
                    val x = random.nextFloat() * width
                    val y = random.nextFloat() * height
                    canvas.drawPoint(x, y, paint)
                }
            }
        }
        binding.staticContainer.addView(staticView)
    }
}