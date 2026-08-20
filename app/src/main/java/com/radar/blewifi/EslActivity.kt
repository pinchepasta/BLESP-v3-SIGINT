package com.radar.blewifi

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.radar.blewifi.databinding.ActivityEslBinding
import java.util.concurrent.Executors

class EslActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEslBinding
    private var currentTheme: RadarView.Theme = RadarView.Theme.DEFAULT
    private var isScanningBarcode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val themeName = prefs.getString("theme_name", RadarView.Theme.DEFAULT.name)
        currentTheme = try { RadarView.Theme.valueOf(themeName ?: "DEFAULT") } catch(e: Exception) { RadarView.Theme.DEFAULT }
        
        setupImmersiveMode()
        
        binding = ActivityEslBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyTheme()
        setupWebView()
        setupButtons()
    }

    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }

    private fun applyTheme() {
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        val isBubblegum = currentTheme == RadarView.Theme.BUBBLEGUM

        if (isHighContrast) {
            binding.eslRoot.setBackgroundColor(Color.WHITE)
            binding.headerEsl.setBackgroundColor(Color.WHITE)
            binding.btnScanBarcode.setTextColor(Color.BLACK)
            binding.btnScanBarcode.setBackgroundResource(R.drawable.status_box_bg_white)
            binding.btnEslBack.setTextColor(Color.BLACK)
            binding.btnEslBack.setBackgroundResource(R.drawable.status_box_bg_white_pink)
        } else if (isRedNight) {
            binding.eslRoot.setBackgroundColor(Color.BLACK)
            binding.headerEsl.setBackgroundColor(Color.parseColor("#0A0000"))
            binding.btnScanBarcode.setTextColor(Color.RED)
            binding.btnScanBarcode.setBackgroundResource(R.drawable.status_box_bg_red)
            binding.btnEslBack.setTextColor(Color.RED)
            binding.btnEslBack.setBackgroundResource(R.drawable.status_box_bg_red)
        } else if (isBubblegum) {
            binding.eslRoot.setBackgroundColor(Color.BLACK)
            binding.headerEsl.setBackgroundColor(Color.parseColor("#0F0F0F"))
            binding.btnScanBarcode.setTextColor(Color.parseColor("#FF00FF"))
            binding.btnScanBarcode.setBackgroundResource(R.drawable.btn_bg_bubblegum)
            binding.btnEslBack.setTextColor(Color.parseColor("#FF00FF"))
            binding.btnEslBack.setBackgroundResource(R.drawable.btn_bg_bubblegum)
        }
    }

    private fun setupWebView() {
        binding.webViewEsl.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun openScanner() {
                    runOnUiThread { binding.btnScanBarcode.performClick() }
                }
            }, "AndroidHost")
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                    runOnUiThread { request.grant(request.resources) }
                }
            }
            webViewClient = android.webkit.WebViewClient()
            loadUrl("file:///android_asset/esltools.html")
        }
    }

    private fun setupButtons() {
        binding.btnEslBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnScanBarcode.setOnClickListener {
            if (!isScanningBarcode) {
                startScanning()
            } else {
                stopScanning()
            }
        }
    }

    private fun startScanning() {
        isScanningBarcode = true
        binding.cameraPreview.visibility = View.VISIBLE
        binding.scannerOverlay.visibility = View.VISIBLE
        binding.tvScannerHint.visibility = View.VISIBLE
        
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        
        val textColor = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            else -> Color.parseColor("#FF00FF")
        }
        setCyberText(binding.btnScanBarcode, "ABORT", textColor)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = androidx.camera.core.Preview.Builder()
                .build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8
                )
                .build()

            val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                barcode.rawValue?.let { barcodeVal ->
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
                                    } else {
                                        @Suppress("DEPRECATION")
                                        getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    }
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))

                                    runOnUiThread {
                                        binding.webViewEsl.evaluateJavascript("if(window.onBarcodeScanned) { window.onBarcodeScanned('$barcodeVal'); } else { barcodeToPlid('$barcodeVal'); }", null)
                                        stopScanning()
                                    }
                                    imageAnalysis.clearAnalyzer()
                                    return@addOnSuccessListener
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopScanning() {
        isScanningBarcode = false
        binding.cameraPreview.visibility = View.GONE
        binding.scannerOverlay.visibility = View.GONE
        binding.tvScannerHint.visibility = View.GONE
        
        val isHighContrast = currentTheme == RadarView.Theme.HIGH_CONTRAST
        val isRedNight = currentTheme == RadarView.Theme.RED_NIGHT
        
        val green = when {
            isHighContrast -> Color.BLACK
            isRedNight -> Color.RED
            else -> Color.parseColor("#00FF41")
        }
        setCyberText(binding.btnScanBarcode, "SCANNER", green)
        
        ProcessCameraProvider.getInstance(this).get().unbindAll()
    }

    private fun setCyberText(button: android.widget.Button, text: String, color: Int) {
        button.text = text
        button.setTextColor(color)
    }
}
