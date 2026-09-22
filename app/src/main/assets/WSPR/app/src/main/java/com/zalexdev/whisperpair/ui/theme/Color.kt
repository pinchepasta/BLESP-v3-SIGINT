package com.zalexdev.whisperpair.ui.theme

import androidx.compose.ui.graphics.Color

// 90s Hacker Theme Colors
val NeonGreen = Color(0xFF00FF00)
val HackerPink = Color(0xFFFF00FF)
val HackerGreenLight = Color(0xFF39FF14)
val HackerGreenDark = Color(0xFF003300)

// Standard Hacker Backgrounds
val BlackBackground = Color(0xFF000000)
val BlackSurface = Color(0xFF0A0A0A)
val BlackSurfaceVariant = Color(0xFF1A1A1A)
val HackerCard = Color(0xFF0F0F0F)

// Text colors - Green primary for that terminal look
val TextPrimary = Color(0xFF00FF00)
val TextSecondary = Color(0xFFFF00FF)
val TextTertiary = Color(0xFF008800)

// Status colors - keeping them distinct but matching the vibe
val VulnerableRed = Color(0xFFFF0000)
val VulnerableRedDark = Color(0xFF8B0000)
val PatchedGreen = Color(0xFF00FF00)
val PatchedGreenDark = Color(0xFF006400)
val TestingBlue = Color(0xFF00FFFF)
val WarningOrange = Color(0xFFFFA500)

// Accent gradients
val GradientStart = Color(0xFF00FF00)
val GradientEnd = Color(0xFFFF00FF)

// Signal strength colors
val SignalStrong = Color(0xFF00FF00)
val SignalMedium = Color(0xFFFFFF00)
val SignalWeak = Color(0xFFFF0000)

// Legacy mappings to keep the project compiling if they are used by name elsewhere
val CyanPrimary = NeonGreen
val CyanDark = HackerGreenDark
val CyanLight = HackerGreenLight
val DarkBackground = BlackBackground
val DarkSurface = BlackSurface
val DarkSurfaceVariant = BlackSurfaceVariant
val DarkCard = HackerCard
