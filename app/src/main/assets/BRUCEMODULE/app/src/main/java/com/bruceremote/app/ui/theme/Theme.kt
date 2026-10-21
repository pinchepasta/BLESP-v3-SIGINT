package com.bruceremote.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MultipassTheme(
    appTheme: AppTheme = AppTheme.NEON_ORANGE,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        background = NodeBg,
        surface = NodeSurface,
        surfaceVariant = NodeSurfaceAlt,
        primary = appTheme.primary,
        onPrimary = NodeBg,
        secondary = appTheme.accent,
        onSecondary = NodeBg,
        tertiary = appTheme.accent,
        onBackground = NodeFg,
        onSurface = NodeFg,
        error = NodeErr,
        outline = NodeBorder
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BruceTypography,
        content = content
    )
}

@Composable
fun BruceRemoteTheme(
    appTheme: AppTheme = AppTheme.NEON_ORANGE,
    content: @Composable () -> Unit
) = MultipassTheme(appTheme = appTheme, content = content)
