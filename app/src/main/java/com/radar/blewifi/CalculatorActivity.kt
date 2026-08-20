package com.radar.blewifi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.radar.blewifi.databinding.ActivityCalculatorBinding
import net.objecthunter.exp4j.ExpressionBuilder

class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private var lastNumeric: Boolean = false
    private var stateError: Boolean = false
    private var lastDot: Boolean = false
    private var pinEntered: Boolean = false
    private var isChangingPin: Boolean = false
    private var currentTheme: RadarView.Theme = RadarView.Theme.DEFAULT
    private var primaryColor: Int = Color.parseColor("#00FF41")
    private var accentColor: Int = Color.parseColor("#FF00FF")
    private var secondaryColor: Int = Color.parseColor("#990000")
    private var neutralColor: Int = Color.WHITE
    private var bgColor: Int = Color.BLACK
    private var pulsateAnimator: android.animation.ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val proceedRunnable = Runnable {
        if (!isChangingPin) {
            proceedToSplash()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyTheme()

        setupButtons()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = try { RadarView.Theme.valueOf(themeName ?: RadarView.Theme.DEFAULT.name) } catch(e: Exception) { RadarView.Theme.DEFAULT }

        bgColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.WHITE
            else -> Color.BLACK
        }
        
        primaryColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#00FDFF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#ff9f6b")
            RadarView.Theme.MORIO -> Color.parseColor("#c3ac3a")
            else -> Color.parseColor("#00FF41")
        }

        accentColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.BLACK
            RadarView.Theme.RED_NIGHT -> Color.RED
            RadarView.Theme.PINK -> Color.parseColor("#FF00FF")
            RadarView.Theme.NEON -> Color.parseColor("#E6FB04")
            RadarView.Theme.NARANJA -> Color.parseColor("#FF8C00")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
            RadarView.Theme.MORIO -> Color.parseColor("#c8f29e")
            else -> Color.parseColor("#FF00FF")
        }

        secondaryColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.LTGRAY
            RadarView.Theme.RED_NIGHT -> Color.parseColor("#990000")
            RadarView.Theme.SUMMERTIME -> Color.parseColor("#6befff")
            RadarView.Theme.BUBBLEGUM -> Color.parseColor("#FF00FF")
            RadarView.Theme.MORIO -> Color.parseColor("#244f48")
            else -> primaryColor
        }

        neutralColor = when (currentTheme) {
            RadarView.Theme.HIGH_CONTRAST -> Color.DKGRAY
            else -> accentColor
        }

        binding.root.setBackgroundColor(bgColor)
        binding.tvDisplay.setTextColor(accentColor)
        binding.tvChangePin.setTextColor(accentColor)

        // Update display box border color based on theme
        val displayBox = binding.tvDisplay.background as? android.graphics.drawable.GradientDrawable
        displayBox?.let {
            it.setStroke(4, primaryColor)
            it.setColor(Color.argb(20, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)))
        }
    }

    private fun setupButtons() {
        val numericButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )
        for (button in numericButtons) {
            setCyberText(button, button.text.toString(), primaryColor)
            button.setOnClickListener { onDigit((it as Button).text.toString()) }
        }

        val operatorButtons = listOf(
            binding.btnPlus, binding.btnMinus, binding.btnMultiply, binding.btnDivide, binding.btnPercent
        )
        for (button in operatorButtons) {
            setCyberText(button, button.text.toString(), primaryColor)
            button.setOnClickListener { onOperator((it as Button).text.toString()) }
        }

        setCyberText(binding.btnDot, ".", primaryColor)
        binding.btnDot.setOnClickListener { onDecimalPoint() }

        setCyberText(binding.btnAC, "AC", accentColor)
        binding.btnAC.setOnClickListener { onClear() }

        setCyberText(binding.btnDelete, "DEL", primaryColor)
        binding.btnDelete.setOnClickListener { onDelete() }

        setCyberText(binding.btnEqual, "=", accentColor)
        binding.btnEqual.setOnClickListener { onEqual() }
    }

    private fun setCyberText(button: Button, text: String, contentColor: Int) {
        // Apply background tint based on color
        val tint = if (contentColor == accentColor) {
            Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        } else {
            Color.argb(25, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        }
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(tint)
        
        // Add a subtle border if possible (MaterialButton)
        if (button is com.google.android.material.button.MaterialButton) {
            button.strokeColor = android.content.res.ColorStateList.valueOf(contentColor)
            button.strokeWidth = 2
        }

        val spannable = SpannableString(text)
        
        spannable.setSpan(ForegroundColorSpan(contentColor), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        // Bold if it's the accent color and it's different from primary
        val isAccent = contentColor == accentColor && accentColor != primaryColor
        
        if (isAccent) {
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        button.text = spannable
    }

    private fun onDigit(digit: String) {
        if (stateError) {
            binding.tvDisplay.text = digit
            stateError = false
        } else {
            val currentText = binding.tvDisplay.text.toString()
            if (currentText == "0") {
                binding.tvDisplay.text = digit
            } else {
                binding.tvDisplay.append(digit)
            }
        }
        lastNumeric = true
        checkPin()
    }

    private fun onDecimalPoint() {
        if (lastNumeric && !stateError && !lastDot) {
            binding.tvDisplay.append(".")
            lastNumeric = false
            lastDot = true
        }
    }

    private fun onOperator(op: String) {
        val currentText = binding.tvDisplay.text.toString()
        if ((lastNumeric || currentText.endsWith("%")) && !stateError) {
            binding.tvDisplay.append(op)
            lastNumeric = false
            lastDot = false
        }
    }

    private fun onClear() {
        binding.tvDisplay.text = "0"
        lastNumeric = false
        stateError = false
        lastDot = false
    }

    private fun onDelete() {
        val text = binding.tvDisplay.text.toString()
        if (text.isNotEmpty() && text != "0") {
            binding.tvDisplay.text = text.substring(0, text.length - 1)
            if (binding.tvDisplay.text.isEmpty()) {
                binding.tvDisplay.text = "0"
            }
            val newText = binding.tvDisplay.text.toString()
            if (newText.isNotEmpty()) {
                lastNumeric = newText.last().isDigit()
                lastDot = newText.contains(".")
            } else {
                lastNumeric = false
                lastDot = false
            }
        }
    }

    private fun onEqual() {
        val currentText = binding.tvDisplay.text.toString()
        if ((lastNumeric || currentText.endsWith("%")) && !stateError) {
            try {
                // Replace % with /100.0 for evaluation
                val expressionStr = currentText.replace("%", "/100.0")
                val expression = ExpressionBuilder(expressionStr).build()
                val result = expression.evaluate()
                binding.tvDisplay.text = if (result % 1 == 0.0) result.toLong().toString() else result.toString()
                lastDot = binding.tvDisplay.text.contains(".")
                lastNumeric = true
            } catch (ex: Exception) {
                binding.tvDisplay.text = "ERROR"
                stateError = true
                lastNumeric = false
            }
        }
        checkPin()
    }

    private fun checkPin() {
        if (pinEntered || isChangingPin) return
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val secretPin = prefs.getString("secret_pin", "123456") ?: "123456"
        
        if (binding.tvDisplay.text.toString() == secretPin) {
            pinEntered = true
            
            // Show USER AUTHORIZED instead of the PIN
            binding.tvDisplay.gravity = android.view.Gravity.CENTER
            binding.tvDisplay.textSize = 28f
            binding.tvDisplay.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.tvDisplay.text = "USER AUTHORIZED"

            // Flash Green, Vibrate Once & Glitch
            flashAndVibrateWithGlitch()

            binding.tvChangePin.visibility = View.VISIBLE
            startPulsateAnimation()
            binding.tvChangePin.setOnClickListener {
                isChangingPin = true
                stopPulsateAnimation()
                handler.removeCallbacks(proceedRunnable)
                showChangePinDialog()
            }
            
            handler.postDelayed(proceedRunnable, 1500)
        }
    }

    private fun startPulsateAnimation() {
        pulsateAnimator?.cancel()
        pulsateAnimator = android.animation.ValueAnimator.ofFloat(0.4f, 1.0f).apply {
            duration = 600
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                binding.tvChangePin.alpha = alpha
                val scale = 0.95f + (alpha * 0.1f)
                binding.tvChangePin.scaleX = scale
                binding.tvChangePin.scaleY = scale
            }
            start()
        }
    }

    private fun stopPulsateAnimation() {
        pulsateAnimator?.cancel()
        binding.tvChangePin.alpha = 1.0f
        binding.tvChangePin.scaleX = 1.0f
        binding.tvChangePin.scaleY = 1.0f
    }

    private fun flashAndVibrateWithGlitch() {
        val flashColor = if (currentTheme == RadarView.Theme.HIGH_CONTRAST) Color.LTGRAY else primaryColor

        // Flash screen
        binding.root.setBackgroundColor(flashColor)
        handler.postDelayed({
            binding.root.setBackgroundColor(bgColor)
        }, 150)

        // Vibrate once
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))

        // Short Glitch Animation
        startGlitchEffect()
    }

    private fun startGlitchEffect() {
        val root = binding.root
        val random = java.util.Random()
        val frames = 15
        val frameInterval = 40L

        for (i in 0 until frames) {
            handler.postDelayed({
                if (i == frames - 1) {
                    root.translationX = 0f
                    root.translationY = 0f
                    root.alpha = 1f
                } else {
                    // Digital tearing: jerky horizontal shifts
                    root.translationX = (random.nextFloat() - 0.5f) * 120f
                    
                    // Minor vertical jitter
                    root.translationY = (random.nextFloat() - 0.5f) * 30f
                    
                    // Alpha flicker (bad signal)
                    root.alpha = if (random.nextFloat() > 0.8f) 0.3f else 1.0f

                    // Add digital block slices to the overlay
                    val sliceColor = when(random.nextInt(3)) {
                        0 -> primaryColor
                        1 -> secondaryColor
                        else -> neutralColor
                    }
                    
                    val slice = android.graphics.drawable.ColorDrawable(sliceColor)
                    val h = (random.nextFloat() * 150 + 20).toInt()
                    val w = root.width
                    val y = random.nextInt(root.height.coerceAtLeast(1))
                    
                    slice.setBounds(0, y, w, y + h)
                    slice.alpha = (random.nextFloat() * 180 + 40).toInt()
                    root.overlay.add(slice)
                    
                    // Add random "pixel block" artifacts
                    val pixelBlockColor = if (random.nextBoolean()) accentColor else sliceColor
                    val pixelBlock = android.graphics.drawable.ColorDrawable(pixelBlockColor)
                    val bw = random.nextInt(300) + 50
                    val bh = random.nextInt(150) + 30
                    val bx = random.nextInt(root.width.coerceAtLeast(1))
                    val by = random.nextInt(root.height.coerceAtLeast(1))
                    
                    pixelBlock.setBounds(bx, by, bx + bw, by + bh)
                    pixelBlock.alpha = 100
                    root.overlay.add(pixelBlock)

                    // Remove these artifacts before the next frame
                    handler.postDelayed({
                        root.overlay.remove(slice)
                        root.overlay.remove(pixelBlock)
                    }, frameInterval)
                }
            }, i * frameInterval)
        }
    }

    private fun proceedToSplash() {
        val intent = Intent(this, SplashActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showChangePinDialog() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar)
        
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(60, 60, 60, 60)
            gravity = android.view.Gravity.CENTER
        }

        val title = android.widget.TextView(this).apply {
            text = " ACCESS CONTROL // PIN "
            setTextColor(accentColor)
            textSize = 20f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        root.addView(title)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentPin = prefs.getString("secret_pin", "123456") ?: "123456"

        val pinInput = android.widget.EditText(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setText(currentPin)
            setTextColor(if (currentTheme == RadarView.Theme.HIGH_CONTRAST) Color.BLACK else neutralColor)
            setBackgroundColor(if (currentTheme == RadarView.Theme.HIGH_CONTRAST) Color.parseColor("#EEEEEE") else Color.parseColor("#111111"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 24f
            setPadding(20, 20, 20, 20)
            gravity = android.view.Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        }
        root.addView(pinInput)

        val saveBtn = android.widget.Button(this).apply {
            text = "UPDATE ACCESS CODE"
            setTextColor(if (currentTheme == RadarView.Theme.HIGH_CONTRAST) Color.WHITE else Color.BLACK)
            setBackgroundColor(primaryColor)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setPadding(40, 30, 40, 30)
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 40, 0, 0)
            layoutParams = params
        }
        root.addView(saveBtn)

        saveBtn.setOnClickListener {
            val newPin = pinInput.text.toString().trim()
            if (newPin.isNotEmpty()) {
                prefs.edit().putString("secret_pin", newPin).apply()
                onClear()
                pinEntered = false
                isChangingPin = false
                binding.tvChangePin.visibility = View.GONE
                dialog.dismiss()
            }
        }

        dialog.setOnCancelListener {
            isChangingPin = false
            proceedToSplash()
        }

        dialog.setContentView(root)
        dialog.show()
    }
}
