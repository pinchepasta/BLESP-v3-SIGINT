package com.mostlyawesome.tagtinker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Brand colors ──────────────────────────────────────────────────────────────
val Orange     = Color(0xFFE8621A)
val OrangeBg   = Color(0x1AE8621A)
val Green      = Color(0xFF15803D)
val GreenBg    = Color(0x1715803D)
val DarkBg     = Color(0xFF0E0E0E)
val DarkSurface= Color(0xFF171717)
val DarkSurface2= Color(0xFF222222)
val DarkBorder = Color(0xFF2E2E2E)
val DarkText   = Color(0xFFF0F0EE)
val DarkText2  = Color(0xFFA0A09E)
val DarkText3  = Color(0xFF606060)
val LightBg    = Color(0xFFFFFFFF)
val LightSurface= Color(0xFFF5F5F3)
val LightSurface2=Color(0xFFEBEBEA)
val LightBorder= Color(0xFFD8D8D6)
val LightText  = Color(0xFF0A0A0A)
val LightText2 = Color(0xFF5A5A58)
val LightText3 = Color(0xFF9A9A98)

private val DarkColorScheme = darkColorScheme(
    primary        = DarkText,
    onPrimary      = DarkBg,
    primaryContainer = DarkSurface2,
    secondary      = Orange,
    onSecondary    = Color.White,
    background     = DarkBg,
    surface        = DarkSurface,
    onBackground   = DarkText,
    onSurface      = DarkText,
    outline        = DarkBorder,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkText2,
)

private val LightColorScheme = lightColorScheme(
    primary        = LightText,
    onPrimary      = LightBg,
    primaryContainer = LightSurface2,
    secondary      = Orange,
    onSecondary    = Color.White,
    background     = LightBg,
    surface        = LightSurface,
    onBackground   = LightText,
    onSurface      = LightText,
    outline        = LightBorder,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightText2,
)

@Composable
fun TagTinkerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize   = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
