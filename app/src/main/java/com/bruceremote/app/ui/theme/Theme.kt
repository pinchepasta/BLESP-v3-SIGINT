package com.bruceremote.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.radar.blewifi.RadarView
import com.radar.blewifi.ThemeUtils

@Composable
fun MultipassTheme(
    themeName: String = "DEFAULT",
    content: @Composable () -> Unit
) {
    val theme = try {
        RadarView.Theme.valueOf(themeName)
    } catch (e: Exception) {
        RadarView.Theme.DEFAULT
    }
    val themeColors = ThemeUtils.getThemeColors(theme)

    val primaryColor = Color(themeColors.primary)
    val accentColor = Color(themeColors.accent)
    val backgroundColor = Color(themeColors.background)
    val surfaceColor = Color(themeColors.header)
    val surfaceVariantColor = Color(themeColors.headerBar)
    val dimColor = Color(themeColors.dim)
    val isLight = theme == RadarView.Theme.HIGH_CONTRAST
    val textColor = if (isLight) Color.Black else Color.White

    val colorScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = if (isLight) Color.White else Color.Black,
        primaryContainer = surfaceVariantColor,
        onPrimaryContainer = primaryColor,
        secondary = accentColor,
        onSecondary = if (isLight) Color.White else Color.Black,
        secondaryContainer = surfaceVariantColor,
        onSecondaryContainer = accentColor,
        tertiary = accentColor,
        onTertiary = if (isLight) Color.White else Color.Black,
        tertiaryContainer = accentColor.copy(alpha = 0.3f),
        onTertiaryContainer = textColor,
        background = backgroundColor,
        onBackground = if (isLight) Color.Black else primaryColor,
        surface = surfaceColor,
        onSurface = if (isLight) Color.Black else primaryColor,
        surfaceVariant = surfaceVariantColor,
        onSurfaceVariant = if (isLight) Color.Black.copy(alpha = 0.7f) else accentColor,
        outline = dimColor,
        outlineVariant = surfaceVariantColor,
        error = if (isLight) Color(0xFFCC0000) else Color(0xFFFF0000),
        onError = Color.White,
        errorContainer = if (isLight) Color(0xFFF0D0D0) else Color(0xFF8B0000),
        onErrorContainer = Color.White
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BruceTypography,
        content = content
    )
}

@Composable
fun BruceRemoteTheme(
    themeName: String = "DEFAULT",
    content: @Composable () -> Unit
) = MultipassTheme(themeName = themeName, content = content)
