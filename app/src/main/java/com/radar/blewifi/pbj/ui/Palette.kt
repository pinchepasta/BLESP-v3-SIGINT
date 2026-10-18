package com.radar.blewifi.pbj.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class AppTheme(
    val title: String,
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val fg: Color,
    val dim: Color,
    val ok: Color,
    val bad: Color,
    val acc: Color,
    val warn: Color,
    val okBg: Color,
    val okLine: Color,
    val okFg: Color,
    val badBg: Color,
    val badLine: Color,
    val badFg: Color,
    val accBg: Color,
    val accLine: Color,
    val accFg: Color,
    val onBg: Color,
    val onLine: Color,
    val onFg: Color,
    val console: Color
) {
    DEFAULT(
        title = "Stealth Green & Pink (Default)",
        bg = Color(0xFF000000),
        panel = Color(0xFF0A0F0A),
        panel2 = Color(0xFF142016),
        line = Color(0xFF00FF41),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF00AA2A),
        ok = Color(0xFF00FF41),
        bad = Color(0xFFFF0033),
        acc = Color(0xFFFF00FF),
        warn = Color(0xFFFFB300),
        okBg = Color(0xFF00380E),
        okLine = Color(0xFF00FF41),
        okFg = Color(0xFFB3FFC6),
        badBg = Color(0xFF4A000A),
        badLine = Color(0xFFFF0033),
        badFg = Color(0xFFFFB3C2),
        accBg = Color(0xFF4A004A),
        accLine = Color(0xFFFF00FF),
        accFg = Color(0xFFFFC6FF),
        onBg = Color(0xFF004D18),
        onLine = Color(0xFF00FF41),
        onFg = Color(0xFFE6FFE9),
        console = Color(0xFF050A05)
    ),

    HIGH_CONTRAST(
        title = "High Contrast White",
        bg = Color(0xFFFFFFFF),
        panel = Color(0xFFF0F0F0),
        panel2 = Color(0xFFE0E0E0),
        line = Color(0xFF000000),
        fg = Color(0xFF000000),
        dim = Color(0xFF555555),
        ok = Color(0xFF008800),
        bad = Color(0xFFCC0000),
        acc = Color(0xFF000000),
        warn = Color(0xFFCC8800),
        okBg = Color(0xFFD0F0D0),
        okLine = Color(0xFF008800),
        okFg = Color(0xFF004400),
        badBg = Color(0xFFF0D0D0),
        badLine = Color(0xFFCC0000),
        badFg = Color(0xFF660000),
        accBg = Color(0xFFE0E0E0),
        accLine = Color(0xFF000000),
        accFg = Color(0xFF000000),
        onBg = Color(0xFFC0E0C0),
        onLine = Color(0xFF008800),
        onFg = Color(0xFF003300),
        console = Color(0xFFF8F8F8)
    ),

    RED_NIGHT(
        title = "Tactical Red Night",
        bg = Color(0xFF000000),
        panel = Color(0xFF140000),
        panel2 = Color(0xFF240000),
        line = Color(0xFFFF0000),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF990000),
        ok = Color(0xFFFF0000),
        bad = Color(0xFFCC0000),
        acc = Color(0xFFFF0000),
        warn = Color(0xFFFF6600),
        okBg = Color(0xFF380000),
        okLine = Color(0xFFFF0000),
        okFg = Color(0xFFFFB3B3),
        badBg = Color(0xFF4A0000),
        badLine = Color(0xFFCC0000),
        badFg = Color(0xFFFF9999),
        accBg = Color(0xFF550000),
        accLine = Color(0xFFFF0000),
        accFg = Color(0xFFFFCCCC),
        onBg = Color(0xFF4D0000),
        onLine = Color(0xFFFF0000),
        onFg = Color(0xFFFFE6E6),
        console = Color(0xFF0A0000)
    ),

    PINK(
        title = "Cyberpunk Pink",
        bg = Color(0xFF000000),
        panel = Color(0xFF1A001A),
        panel2 = Color(0xFF2A002A),
        line = Color(0xFFFF00FF),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF990099),
        ok = Color(0xFFFF00FF),
        bad = Color(0xFFFF1A53),
        acc = Color(0xFFFF00FF),
        warn = Color(0xFFFF80FF),
        okBg = Color(0xFF380038),
        okLine = Color(0xFFFF00FF),
        okFg = Color(0xFFFFB3FF),
        badBg = Color(0xFF4A0019),
        badLine = Color(0xFFFF1A53),
        badFg = Color(0xFFFFB3C6),
        accBg = Color(0xFF550055),
        accLine = Color(0xFFFF00FF),
        accFg = Color(0xFFFFE6FF),
        onBg = Color(0xFF4D004D),
        onLine = Color(0xFFFF00FF),
        onFg = Color(0xFFFFE6FF),
        console = Color(0xFF0A000A)
    ),

    NEON(
        title = "Tactical Neon Yellow",
        bg = Color(0xFF000000),
        panel = Color(0xFF1A1A00),
        panel2 = Color(0xFF2A2A00),
        line = Color(0xFFE6FB04),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFB3C403),
        ok = Color(0xFFE6FB04),
        bad = Color(0xFFFF3333),
        acc = Color(0xFFE6FB04),
        warn = Color(0xFFFFCC00),
        okBg = Color(0xFF383800),
        okLine = Color(0xFFE6FB04),
        okFg = Color(0xFFFCFFB3),
        badBg = Color(0xFF4A0000),
        badLine = Color(0xFFFF3333),
        badFg = Color(0xFFFFB3B3),
        accBg = Color(0xFF555500),
        accLine = Color(0xFFE6FB04),
        accFg = Color(0xFFFCFFCC),
        onBg = Color(0xFF4D4D00),
        onLine = Color(0xFFE6FB04),
        onFg = Color(0xFFFEFFE6),
        console = Color(0xFF0A0A00)
    ),

    NARANJA(
        title = "Cyber Orange",
        bg = Color(0xFF000000),
        panel = Color(0xFF1A0F00),
        panel2 = Color(0xFF2A1800),
        line = Color(0xFFFF8C00),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF995400),
        ok = Color(0xFF00FFCC),
        bad = Color(0xFFFF3333),
        acc = Color(0xFFFF8C00),
        warn = Color(0xFFFFCC00),
        okBg = Color(0xFF00382E),
        okLine = Color(0xFF00FFCC),
        okFg = Color(0xFF80FAEA),
        badBg = Color(0xFF4A0800),
        badLine = Color(0xFFFF3333),
        badFg = Color(0xFFFFB3A6),
        accBg = Color(0xFF552E00),
        accLine = Color(0xFFFF8C00),
        accFg = Color(0xFFFFDFB3),
        onBg = Color(0xFF4D2900),
        onLine = Color(0xFFFF8C00),
        onFg = Color(0xFFFFEBD6),
        console = Color(0xFF0A0600)
    ),

    BUBBLEGUM(
        title = "Bubblegum Cyber",
        bg = Color(0xFF000000),
        panel = Color(0xFF1A001A),
        panel2 = Color(0xFF2A002A),
        line = Color(0xFFFF00FF),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF990099),
        ok = Color(0xFF00FDFF),
        bad = Color(0xFFFF1A75),
        acc = Color(0xFF00FDFF),
        warn = Color(0xFFFF80BF),
        okBg = Color(0xFF003838),
        okLine = Color(0xFF00FDFF),
        okFg = Color(0xFFB3FFFF),
        badBg = Color(0xFF4A0022),
        badLine = Color(0xFFFF1A75),
        badFg = Color(0xFFFFB3D1),
        accBg = Color(0xFF004A4D),
        accLine = Color(0xFF00FDFF),
        accFg = Color(0xFFCCFFFF),
        onBg = Color(0xFF003D40),
        onLine = Color(0xFF00FDFF),
        onFg = Color(0xFFE6FFFF),
        console = Color(0xFF0A000A)
    ),

    SUMMERTIME(
        title = "Summertime Beach",
        bg = Color(0xFF000000),
        panel = Color(0xFF2A1F1A),
        panel2 = Color(0xFF1A1A1A),
        line = Color(0xFFFF9F6B),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF996BEF),
        ok = Color(0xFF6BEFFF),
        bad = Color(0xFFFF4D4D),
        acc = Color(0xFF6BEFFF),
        warn = Color(0xFFFFB366),
        okBg = Color(0xFF1A3A40),
        okLine = Color(0xFF6BEFFF),
        okFg = Color(0xFFC2F7FF),
        badBg = Color(0xFF4A1A1A),
        badLine = Color(0xFFFF4D4D),
        badFg = Color(0xFFFFC2C2),
        accBg = Color(0xFF1F4850),
        accLine = Color(0xFF6BEFFF),
        accFg = Color(0xFFD6F9FF),
        onBg = Color(0xFF2A4850),
        onLine = Color(0xFF6BEFFF),
        onFg = Color(0xFFE6FAFF),
        console = Color(0xFF0D0B0A)
    ),

    MORIO(
        title = "Morio Gold & Lime",
        bg = Color(0xFF000000),
        panel = Color(0xFF244F48),
        panel2 = Color(0xFF0A1412),
        line = Color(0xFFC3AC3A),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFC8F29E),
        ok = Color(0xFFC8F29E),
        bad = Color(0xFFFF5252),
        acc = Color(0xFFC8F29E),
        warn = Color(0xFFE6C840),
        okBg = Color(0xFF1C3A24),
        okLine = Color(0xFFC8F29E),
        okFg = Color(0xFFE8FCCF),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF5252),
        badFg = Color(0xFFFF8A8A),
        accBg = Color(0xFF2A4A1C),
        accLine = Color(0xFFC8F29E),
        accFg = Color(0xFFEEFDE0),
        onBg = Color(0xFF204238),
        onLine = Color(0xFFC8F29E),
        onFg = Color(0xFFF2FEE6),
        console = Color(0xFF0A1412)
    ),

    FLIPPER(
        title = "Flipper Zero Tactical",
        bg = Color(0xFF000000),
        panel = Color(0xFF18120E),
        panel2 = Color(0xFF261B14),
        line = Color(0xFFFF8C00),
        fg = Color(0xFFFAF0EA),
        dim = Color(0xFF00E5FF),
        ok = Color(0xFF00E5FF),
        bad = Color(0xFFFF3333),
        acc = Color(0xFF00E5FF),
        warn = Color(0xFFFFCC00),
        okBg = Color(0xFF00303A),
        okLine = Color(0xFF00E5FF),
        okFg = Color(0xFF80F4FF),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF3333),
        badFg = Color(0xFFFF9999),
        accBg = Color(0xFF003A47),
        accLine = Color(0xFF00E5FF),
        accFg = Color(0xFFB3F8FF),
        onBg = Color(0xFF004554),
        onLine = Color(0xFF00E5FF),
        onFg = Color(0xFFE6FCFF),
        console = Color(0xFF090605)
    );

    companion object {
        fun fromBlespThemeName(name: String?): AppTheme {
            if (name == null) return DEFAULT
            return runCatching { valueOf(name) }.getOrElse {
                when (name) {
                    "MONO_GREEN", "CLASSIC", "STEALTH_EMERALD" -> DEFAULT
                    "NEON_ORANGE", "STEALTH_AMBER" -> NARANJA
                    "NEON_PINK", "MONO_PINK" -> PINK
                    "COTTON_CANDY", "STEALTH_PURPLE" -> BUBBLEGUM
                    "MONO_RED" -> RED_NIGHT
                    "STEALTH_CYAN", "STEALTH_ORANGE" -> FLIPPER
                    else -> DEFAULT
                }
            }
        }
    }
}

val LocalAppTheme = staticCompositionLocalOf { AppTheme.DEFAULT }

/** Convenience dynamic accessor for theme properties in composables. */
object P {
    val theme: AppTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val Bg: Color @Composable @ReadOnlyComposable get() = theme.bg
    val Panel: Color @Composable @ReadOnlyComposable get() = theme.panel
    val Panel2: Color @Composable @ReadOnlyComposable get() = theme.panel2
    val Line: Color @Composable @ReadOnlyComposable get() = theme.line
    val Fg: Color @Composable @ReadOnlyComposable get() = theme.fg
    val Dim: Color @Composable @ReadOnlyComposable get() = theme.dim
    val Ok: Color @Composable @ReadOnlyComposable get() = theme.ok
    val Bad: Color @Composable @ReadOnlyComposable get() = theme.bad
    val Acc: Color @Composable @ReadOnlyComposable get() = theme.acc
    val Warn: Color @Composable @ReadOnlyComposable get() = theme.warn

    val OkBg: Color @Composable @ReadOnlyComposable get() = theme.okBg
    val OkLine: Color @Composable @ReadOnlyComposable get() = theme.okLine
    val OkFg: Color @Composable @ReadOnlyComposable get() = theme.okFg

    val BadBg: Color @Composable @ReadOnlyComposable get() = theme.badBg
    val BadLine: Color @Composable @ReadOnlyComposable get() = theme.badLine
    val BadFg: Color @Composable @ReadOnlyComposable get() = theme.badFg

    val AccBg: Color @Composable @ReadOnlyComposable get() = theme.accBg
    val AccLine: Color @Composable @ReadOnlyComposable get() = theme.accLine
    val AccFg: Color @Composable @ReadOnlyComposable get() = theme.accFg

    val OnBg: Color @Composable @ReadOnlyComposable get() = theme.onBg
    val OnLine: Color @Composable @ReadOnlyComposable get() = theme.onLine
    val OnFg: Color @Composable @ReadOnlyComposable get() = theme.onFg

    val Console: Color @Composable @ReadOnlyComposable get() = theme.console
    val Mono = FontFamily.Monospace
}
