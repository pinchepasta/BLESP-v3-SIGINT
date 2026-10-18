package com.radar.blewifi.pbj

import android.R
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radar.blewifi.MainActivity
import com.radar.blewifi.pbj.jammer.JammerViewModel
import com.radar.blewifi.pbj.ui.AppTheme
import com.radar.blewifi.pbj.ui.JammerScreen
import com.radar.blewifi.pbj.ui.LocalAppTheme

class PbjActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        applyImmersiveMode()

        setContent {
            val settingsPrefs = remember { getSharedPreferences("settings", MODE_PRIVATE) }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "immersive_mode") {
                        applyImmersiveMode()
                    }
                }
                settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    settingsPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            JammerRoot(
                onBack = { navigateBackToRadarMap() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    private fun navigateBackToRadarMap() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun applyImmersiveMode() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("immersive_mode", true)) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun JammerRoot(onBack: () -> Unit) {
    val vm: JammerViewModel = viewModel()
    val ui by vm.ui.collectAsState()

    CompositionLocalProvider(LocalAppTheme provides ui.theme) {
        JammerTheme(ui.theme) {
            JammerScreen(vm, onBack = onBack)
        }
    }
}

@Composable
private fun JammerTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = theme.bg,
            surface = theme.panel,
            primary = theme.acc,
            onPrimary = theme.fg,
            onBackground = theme.fg,
            onSurface = theme.fg
        ),
        content = content
    )
}
