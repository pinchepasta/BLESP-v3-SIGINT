package com.radar.blewifi

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt

import java.util.EnumMap

object ThemeUtils {
    const val LEGACY_GREEN = "#00FF41"
    const val LEGACY_PINK = "#FF00FF"
    const val LEGACY_CYAN = "#00FFFF"
    const val LEGACY_AMBER = "#FFB300"

    private val themeColorsMap: EnumMap<RadarView.Theme, ThemeColors> by lazy {
        EnumMap<RadarView.Theme, ThemeColors>(RadarView.Theme::class.java).apply {
            for (theme in RadarView.Theme.entries) {
                put(theme, createThemeColors(theme))
            }
        }
    }

    fun getThemeColors(theme: RadarView.Theme): ThemeColors {
        return themeColorsMap[theme] ?: themeColorsMap[RadarView.Theme.DEFAULT]!!
    }

    private fun createThemeColors(theme: RadarView.Theme): ThemeColors {
        return when (theme) {
            RadarView.Theme.HIGH_CONTRAST -> ThemeColors(
                primary = Color.BLACK,
                accent = Color.BLACK,
                background = Color.WHITE,
                dim = Color.LTGRAY,
                header = "#F0F0F0".toColorInt(),
                headerBar = Color.LTGRAY,
                statusBoxRes = R.drawable.status_box_bg_white_pink,
                buttonRes = R.drawable.btn_bg_white,
                buttonDimRes = R.drawable.btn_bg_white_dim
            )
            RadarView.Theme.RED_NIGHT -> ThemeColors(
                primary = Color.RED,
                accent = Color.RED,
                background = Color.BLACK,
                dim = "#990000".toColorInt(),
                header = "#0A0000".toColorInt(),
                headerBar = "#1A0000".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_red,
                buttonRes = R.drawable.btn_bg_red,
                buttonDimRes = R.drawable.btn_bg_red
            )
            RadarView.Theme.PINK -> ThemeColors(
                primary = "#FF00FF".toColorInt(),
                accent = "#FF00FF".toColorInt(),
                background = Color.BLACK,
                dim = "#990099".toColorInt(),
                header = "#2A002A".toColorInt(),
                headerBar = "#330033".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_pink,
                buttonRes = R.drawable.btn_bg_pink,
                buttonDimRes = R.drawable.btn_bg_dim
            )
            RadarView.Theme.NEON -> ThemeColors(
                primary = "#E6FB04".toColorInt(),
                accent = "#E6FB04".toColorInt(),
                background = Color.BLACK,
                dim = "#B3C403".toColorInt(),
                header = "#1A1A00".toColorInt(),
                headerBar = "#333300".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_neon,
                buttonRes = R.drawable.btn_bg_neon,
                buttonDimRes = R.drawable.btn_bg_dim
            )
            RadarView.Theme.NARANJA -> ThemeColors(
                primary = "#FF8C00".toColorInt(),
                accent = "#FF8C00".toColorInt(),
                background = Color.BLACK,
                dim = "#995400".toColorInt(),
                header = "#1A0F00".toColorInt(),
                headerBar = "#331E00".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_naranja,
                buttonRes = R.drawable.btn_bg_naranja,
                buttonDimRes = R.drawable.btn_bg_dim
            )
            RadarView.Theme.BUBBLEGUM -> ThemeColors(
                primary = "#FF00FF".toColorInt(),
                accent = "#00FDFF".toColorInt(),
                background = Color.BLACK,
                dim = "#330033".toColorInt(),
                header = "#1A001A".toColorInt(),
                headerBar = "#2A002A".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_bubblegum,
                buttonRes = R.drawable.btn_bg_bubblegum,
                buttonDimRes = R.drawable.btn_bg_dim
            )
            RadarView.Theme.SUMMERTIME -> ThemeColors(
                primary = "#ff9f6b".toColorInt(),
                accent = "#6befff".toColorInt(),
                background = Color.BLACK,
                dim = "#996befff".toColorInt(),
                header = "#2A1F1A".toColorInt(),
                headerBar = "#1A1A1A".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_summertime,
                buttonRes = R.drawable.btn_bg_summertime,
                buttonDimRes = R.drawable.btn_bg_summertime_dim
            )
            RadarView.Theme.MORIO -> ThemeColors(
                primary = "#c3ac3a".toColorInt(),
                accent = "#c8f29e".toColorInt(),
                background = Color.BLACK,
                dim = "#99c8f29e".toColorInt(),
                header = "#244f48".toColorInt(),
                headerBar = "#0A1412".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg_morio,
                buttonRes = R.drawable.btn_bg_morio,
                buttonDimRes = R.drawable.btn_bg_morio_dim
            )
            RadarView.Theme.FLIPPER -> ThemeColors(
                primary = "#FF8C00".toColorInt(),
                accent = "#00E5FF".toColorInt(),
                background = Color.BLACK,
                dim = "#00E5FF".toColorInt(),
                header = Color.BLACK,
                headerBar = Color.BLACK,
                statusBoxRes = R.drawable.status_box_bg_flipper,
                buttonRes = R.drawable.btn_bg_flipper,
                buttonDimRes = R.drawable.btn_bg_flipper_dim
            )
            RadarView.Theme.DEFAULT -> ThemeColors(
                primary = LEGACY_GREEN.toColorInt(),
                accent = LEGACY_PINK.toColorInt(),
                background = Color.BLACK,
                dim = "#00AA2A".toColorInt(),
                header = "#0A0A0A".toColorInt(),
                headerBar = "#0F0F0F".toColorInt(),
                statusBoxRes = R.drawable.status_box_bg,
                buttonRes = R.drawable.btn_bg,
                buttonDimRes = R.drawable.btn_bg_dim
            )
        }
    }

    data class ThemeColors(
        val primary: Int,
        val accent: Int,
        val background: Int,
        val dim: Int,
        val header: Int,
        val headerBar: Int,
        val statusBoxRes: Int,
        val buttonRes: Int,
        val buttonDimRes: Int
    )
}
