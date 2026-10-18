package com.espsdr.jammer.ui

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
    MONO_GREEN(
        title = "Terminal Green",
        bg = Color(0xFF000000),
        panel = Color(0xFF001405),
        panel2 = Color(0xFF00280A),
        line = Color(0xFF00FF44),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFF80FF9E),
        ok = Color(0xFF00FF44),
        bad = Color(0xFF00CC33),
        acc = Color(0xFF00FF44),
        warn = Color(0xFF66FF88),
        okBg = Color(0xFF00380E),
        okLine = Color(0xFF00FF44),
        okFg = Color(0xFFB3FFC6),
        badBg = Color(0xFF004A13),
        badLine = Color(0xFF00CC33),
        badFg = Color(0xFFB3FFC6),
        accBg = Color(0xFF005514),
        accLine = Color(0xFF00FF44),
        accFg = Color(0xFFB3FFC6),
        onBg = Color(0xFF004D18),
        onLine = Color(0xFF00FF44),
        onFg = Color(0xFFE6FFE9),
        console = Color(0xFF000A02)
    ),

    NEON_ORANGE(
        title = "Cyber Orange",
        bg = Color(0xFF000000),
        panel = Color(0xFF140800),
        panel2 = Color(0xFF281000),
        line = Color(0xFFFF5500),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFFFB380),
        ok = Color(0xFF00FFCC),
        bad = Color(0xFFFF2200),
        acc = Color(0xFFFF5500),
        warn = Color(0xFFFFCC00),
        okBg = Color(0xFF00382E),
        okLine = Color(0xFF00FFCC),
        okFg = Color(0xFF80FAEA),
        badBg = Color(0xFF4A0800),
        badLine = Color(0xFFFF2200),
        badFg = Color(0xFFFFB3A6),
        accBg = Color(0xFF551A00),
        accLine = Color(0xFFFF5500),
        accFg = Color(0xFFFFD1B3),
        onBg = Color(0xFF004D3D),
        onLine = Color(0xFF00FFCC),
        onFg = Color(0xFFE6FFF9),
        console = Color(0xFF0A0400)
    ),

    NEON_PINK(
        title = "Cyberpunk Pink",
        bg = Color(0xFF000000),
        panel = Color(0xFF14000A),
        panel2 = Color(0xFF280014),
        line = Color(0xFFFF007F),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFFF99CC),
        ok = Color(0xFF00FFCC),
        bad = Color(0xFFFF1A53),
        acc = Color(0xFFFF007F),
        warn = Color(0xFFFFDD00),
        okBg = Color(0xFF00382E),
        okLine = Color(0xFF00FFCC),
        okFg = Color(0xFF80FAEA),
        badBg = Color(0xFF4A0019),
        badLine = Color(0xFFFF1A53),
        badFg = Color(0xFFFFB3C6),
        accBg = Color(0xFF55002A),
        accLine = Color(0xFFFF007F),
        accFg = Color(0xFFFFB3D9),
        onBg = Color(0xFF004D3D),
        onLine = Color(0xFF00FFCC),
        onFg = Color(0xFFE6FFF9),
        console = Color(0xFF0A0005)
    ),

    COTTON_CANDY(
        title = "Cotton Candy",
        bg = Color(0xFF000000),
        panel = Color(0xFF14020F),
        panel2 = Color(0xFF28041E),
        line = Color(0xFFFF3399),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFFFA3D7),
        ok = Color(0xFF00FFCC),
        bad = Color(0xFFFF1A75),
        acc = Color(0xFFFF66C4),
        warn = Color(0xFFFF80BF),
        okBg = Color(0xFF00382E),
        okLine = Color(0xFF00FFCC),
        okFg = Color(0xFF80FAEA),
        badBg = Color(0xFF4A0022),
        badLine = Color(0xFFFF1A75),
        badFg = Color(0xFFFFB3D1),
        accBg = Color(0xFF55003A),
        accLine = Color(0xFFFF66C4),
        accFg = Color(0xFFFFE6F5),
        onBg = Color(0xFF004D3D),
        onLine = Color(0xFF00FFCC),
        onFg = Color(0xFFE6FFF9),
        console = Color(0xFF0A0108)
    ),

    MONO_RED(
        title = "Tactical Red",
        bg = Color(0xFF000000),
        panel = Color(0xFF140005),
        panel2 = Color(0xFF28000A),
        line = Color(0xFFFF0033),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFFF8095),
        ok = Color(0xFFFF0033),
        bad = Color(0xFFFF001A),
        acc = Color(0xFFFF0033),
        warn = Color(0xFFFF4D6A),
        okBg = Color(0xFF38000B),
        okLine = Color(0xFFFF0033),
        okFg = Color(0xFFFFB3C2),
        badBg = Color(0xFF4A000A),
        badLine = Color(0xFFFF001A),
        badFg = Color(0xFFFFB3B8),
        accBg = Color(0xFF550011),
        accLine = Color(0xFFFF0033),
        accFg = Color(0xFFFFB3C2),
        onBg = Color(0xFF4D0014),
        onLine = Color(0xFFFF0033),
        onFg = Color(0xFFFFE6EB),
        console = Color(0xFF0A0002)
    ),

    MONO_PINK(
        title = "Neon Fuchsia",
        bg = Color(0xFF000000),
        panel = Color(0xFF14000D),
        panel2 = Color(0xFF28001A),
        line = Color(0xFFFF1493),
        fg = Color(0xFFFFFFFF),
        dim = Color(0xFFFF99D6),
        ok = Color(0xFFFF1493),
        bad = Color(0xFFFF0066),
        acc = Color(0xFFFF1493),
        warn = Color(0xFFFF66B2),
        okBg = Color(0xFF380021),
        okLine = Color(0xFFFF1493),
        okFg = Color(0xFFFFB3DC),
        badBg = Color(0xFF4A001F),
        badLine = Color(0xFFFF0066),
        badFg = Color(0xFFFFB3D1),
        accBg = Color(0xFF550031),
        accLine = Color(0xFFFF1493),
        accFg = Color(0xFFFFB3DC),
        onBg = Color(0xFF4D002D),
        onLine = Color(0xFFFF1493),
        onFg = Color(0xFFFFE6F5),
        console = Color(0xFF0A0006)
    ),

    CLASSIC(
        title = "Stealth Dark",
        bg = Color(0xFF0B0E13),
        panel = Color(0xFF141A23),
        panel2 = Color(0xFF1B2330),
        line = Color(0xFF26303F),
        fg = Color(0xFFE6EDF6),
        dim = Color(0xFF8B9BB0),
        ok = Color(0xFF4ADE80),
        bad = Color(0xFFF87171),
        acc = Color(0xFF60A5FA),
        warn = Color(0xFFFBBF24),
        okBg = Color(0xFF12271C),
        okLine = Color(0xFF2C5A40),
        okFg = Color(0xFFB7F0CD),
        badBg = Color(0xFF2A1414),
        badLine = Color(0xFF5A2C2C),
        badFg = Color(0xFFF5C2C2),
        accBg = Color(0xFF1D3A2A),
        accLine = Color(0xFF2C5A40),
        accFg = Color(0xFFB7F0CD),
        onBg = Color(0xFF20406B),
        onLine = Color(0xFF356399),
        onFg = Color(0xFFCFE3FF),
        console = Color(0xFF0D1219)
    ),

    STEALTH_AMBER(
        title = "Stealth Amber",
        bg = Color(0xFF0F0D0A),
        panel = Color(0xFF1C1610),
        panel2 = Color(0xFF292018),
        line = Color(0xFF483622),
        fg = Color(0xFFFAF0E6),
        dim = Color(0xFFB89E85),
        ok = Color(0xFFFFB300),
        bad = Color(0xFFFF5252),
        acc = Color(0xFFFFB300),
        warn = Color(0xFFFFD54F),
        okBg = Color(0xFF332400),
        okLine = Color(0xFFFFB300),
        okFg = Color(0xFFFFE082),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF5252),
        badFg = Color(0xFFFF8A8A),
        accBg = Color(0xFF382700),
        accLine = Color(0xFFFFB300),
        accFg = Color(0xFFFFE082),
        onBg = Color(0xFF422E00),
        onLine = Color(0xFFFFB300),
        onFg = Color(0xFFFFF3D0),
        console = Color(0xFF0A0806)
    ),

    STEALTH_CYAN(
        title = "Stealth Cyan",
        bg = Color(0xFF0A0F14),
        panel = Color(0xFF101A24),
        panel2 = Color(0xFF182636),
        line = Color(0xFF25405A),
        fg = Color(0xFFE6FAFF),
        dim = Color(0xFF88B3C2),
        ok = Color(0xFF00E5FF),
        bad = Color(0xFFFF5252),
        acc = Color(0xFF00E5FF),
        warn = Color(0xFF80D8FF),
        okBg = Color(0xFF002A33),
        okLine = Color(0xFF00E5FF),
        okFg = Color(0xFF80F4FF),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF5252),
        badFg = Color(0xFFFF8A8A),
        accBg = Color(0xFF00303A),
        accLine = Color(0xFF00E5FF),
        accFg = Color(0xFF80F4FF),
        onBg = Color(0xFF003A47),
        onLine = Color(0xFF00E5FF),
        onFg = Color(0xFFCCF9FF),
        console = Color(0xFF05080C)
    ),

    STEALTH_EMERALD(
        title = "Stealth Emerald",
        bg = Color(0xFF0A120D),
        panel = Color(0xFF101E16),
        panel2 = Color(0xFF182A1F),
        line = Color(0xFF234A35),
        fg = Color(0xFFE6F7ED),
        dim = Color(0xFF88B89E),
        ok = Color(0xFF00E676),
        bad = Color(0xFFFF5252),
        acc = Color(0xFF00E676),
        warn = Color(0xFF69F0AE),
        okBg = Color(0xFF003018),
        okLine = Color(0xFF00E676),
        okFg = Color(0xFF80F7BB),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF5252),
        badFg = Color(0xFFFF8A8A),
        accBg = Color(0xFF00361B),
        accLine = Color(0xFF00E676),
        accFg = Color(0xFF80F7BB),
        onBg = Color(0xFF004221),
        onLine = Color(0xFF00E676),
        onFg = Color(0xFFCCFCE3),
        console = Color(0xFF050B07)
    ),

    STEALTH_PURPLE(
        title = "Stealth Purple",
        bg = Color(0xFF0F0A14),
        panel = Color(0xFF1B1024),
        panel2 = Color(0xFF271836),
        line = Color(0xFF452A5E),
        fg = Color(0xFFF2E6FF),
        dim = Color(0xFFAA88C2),
        ok = Color(0xFFB388FF),
        bad = Color(0xFFFF5252),
        acc = Color(0xFFB388FF),
        warn = Color(0xFFD1C4E9),
        okBg = Color(0xFF221038),
        okLine = Color(0xFFB388FF),
        okFg = Color(0xFFD1C4E9),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF5252),
        badFg = Color(0xFFFF8A8A),
        accBg = Color(0xFF271240),
        accLine = Color(0xFFB388FF),
        accFg = Color(0xFFD1C4E9),
        onBg = Color(0xFF321652),
        onLine = Color(0xFFB388FF),
        onFg = Color(0xFFE1D5FF),
        console = Color(0xFF08050C)
    ),

    STEALTH_ORANGE(
        title = "Stealth Neon Orange",
        bg = Color(0xFF0D0B0A),
        panel = Color(0xFF18120E),
        panel2 = Color(0xFF261B14),
        line = Color(0xFF422818),
        fg = Color(0xFFFAF0EA),
        dim = Color(0xFFB89B88),
        ok = Color(0xFF00FFCC),
        bad = Color(0xFFFF3333),
        acc = Color(0xFFFF5500),
        warn = Color(0xFFFFCC00),
        okBg = Color(0xFF002E26),
        okLine = Color(0xFF00FFCC),
        okFg = Color(0xFF80FFEC),
        badBg = Color(0xFF381010),
        badLine = Color(0xFFFF3333),
        badFg = Color(0xFFFF9999),
        accBg = Color(0xFF3B1600),
        accLine = Color(0xFFFF5500),
        accFg = Color(0xFFFFC299),
        onBg = Color(0xFF00382E),
        onLine = Color(0xFF00FFCC),
        onFg = Color(0xFF80FFEC),
        console = Color(0xFF090605)
    )
}

val LocalAppTheme = staticCompositionLocalOf { AppTheme.MONO_GREEN }

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