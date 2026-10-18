package com.espsdr.jammer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.espsdr.jammer.jammer.JammerViewModel
import com.espsdr.jammer.ui.AppTheme
import com.espsdr.jammer.ui.JammerScreen
import com.espsdr.jammer.ui.LocalAppTheme
import com.espsdr.jammer.ui.P

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { JammerRoot() }
    }
}

@Composable
private fun JammerRoot() {
    val vm: JammerViewModel = viewModel()
    val ui by vm.ui.collectAsState()

    CompositionLocalProvider(LocalAppTheme provides ui.theme) {
        JammerTheme(ui.theme) {
            JammerScreen(vm)
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