package com.bruceremote.app.ui.theme

import androidx.compose.ui.graphics.Color

// MULTiPASS Theme Colors: Neon Orange (Main), Neon Teal (Accent), Deep Black (Background)
val NodeBg = Color(0xFF000000)
val NodeSurface = Color(0xFF0A0A0A)
val NodeSurfaceAlt = Color(0xFF141414)
val NodeBorder = Color(0xFF262626)
val NodeFg = Color(0xFFEEEEEE)
val NodeMuted = Color(0xFF888888)

val NodePrimary = Color(0xFFFF5F00) // Neon Orange
val NodeAccent = Color(0xFF00F5D4)  // Neon Teal

val NodeOk = Color(0xFF00F5D4)      // Neon Teal
val NodeWarn = Color(0xFFFFB300)    // Neon Amber
val NodeErr = Color(0xFFFF3366)     // Neon Red

enum class AppTheme(val label: String, val primary: Color, val accent: Color) {
    NEON_ORANGE("Neon Orange & Teal", Color(0xFFFF5F00), Color(0xFF00F5D4)),
    CYBERPUNK("Cyberpunk Red & Yellow", Color(0xFFFF0055), Color(0xFFFFE600)),
    MATRIX("Matrix Green", Color(0xFF00FF66), Color(0xFF66FF00)),
    SYNTHWAVE("Synthwave Cyan & Magenta", Color(0xFF00F0FF), Color(0xFFFF007F)),
    AMBER("Monochrome Amber", Color(0xFFFFB300), Color(0xFFFFCC00))
}

// Backward compatibility aliases
val BruceBg = NodeBg
val BruceSurface = NodeSurface
val BruceSurfaceAlt = NodeSurfaceAlt
val BruceBorder = NodeBorder
val BruceFg = NodeFg
val BruceMuted = NodeMuted
val BruceAccent = NodeAccent
val BruceOk = NodeOk
val BruceWarn = NodeWarn
val BruceErr = NodeErr
val MultipassBg = NodeBg
val MultipassSurface = NodeSurface
val MultipassSurfaceAlt = NodeSurfaceAlt
val MultipassBorder = NodeBorder
val MultipassFg = NodeFg
val MultipassMuted = NodeMuted
val MultipassAccent = NodeAccent
val MultipassOk = NodeOk
val MultipassWarn = NodeWarn
val MultipassErr = NodeErr
