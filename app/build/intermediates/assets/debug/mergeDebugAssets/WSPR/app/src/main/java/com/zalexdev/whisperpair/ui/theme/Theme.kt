package com.zalexdev.whisperpair.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val WSPRColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    primaryContainer = HackerGreenDark,
    onPrimaryContainer = HackerGreenLight,
    secondary = HackerPink,
    onSecondary = Color.Black,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = HackerPink,
    tertiary = HackerPink,
    onTertiary = Color.Black,
    tertiaryContainer = HackerPink.copy(alpha = 0.5f),
    onTertiaryContainer = Color.White,
    background = BlackBackground,
    onBackground = NeonGreen,
    surface = BlackSurface,
    onSurface = NeonGreen,
    surfaceVariant = BlackSurfaceVariant,
    onSurfaceVariant = HackerPink,
    outline = TextTertiary,
    outlineVariant = DarkCard,
    error = VulnerableRed,
    onError = Color.White,
    errorContainer = VulnerableRedDark,
    onErrorContainer = Color.White
)

@Composable
fun WSPRTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = WSPRColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
