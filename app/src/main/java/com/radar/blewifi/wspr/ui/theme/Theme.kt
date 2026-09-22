package com.radar.blewifi.wspr.ui.theme

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
import com.radar.blewifi.RadarView
import com.radar.blewifi.ThemeUtils

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

    val colorScheme = WSPRColorScheme.copy(
        primary = primaryColor,
        onPrimary = if (theme == RadarView.Theme.HIGH_CONTRAST) Color.White else Color.Black,
        secondary = accentColor,
        onSecondary = if (theme == RadarView.Theme.HIGH_CONTRAST) Color.White else Color.Black,
        tertiary = accentColor,
        background = backgroundColor,
        onBackground = primaryColor,
        surface = surfaceColor,
        onSurface = primaryColor,
        surfaceVariant = surfaceVariantColor,
        onSurfaceVariant = accentColor
    )
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.toArgb()
            val isLight = theme == RadarView.Theme.HIGH_CONTRAST
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
