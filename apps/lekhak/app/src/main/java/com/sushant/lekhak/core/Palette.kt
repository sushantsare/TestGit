package com.sushant.lekhak.core

import android.content.Context
import android.content.res.Configuration

/** Colours shared by the overlay (Views) and the Settings screen (Compose). ARGB ints. */
data class Palette(
    val isDark: Boolean,
    val background: Int,
    val card: Int,
    val input: Int,
    val divider: Int,
    val border: Int,
    val brand: Int,
    val chipSelected: Int,
    val chipStroke: Int,
    val text: Int,
    val textSecondary: Int,
    val handle: Int,
    val error: Int,
    val granted: Int,
    val notGranted: Int,
    val banner: Int,
    val onBrand: Int = 0xFFFFFFFF.toInt(),
) {
    companion object {
        val Dark = Palette(
            isDark = true,
            background = 0xFF1A1A2E.toInt(),
            card = 0xFF2A2A3E.toInt(),
            input = 0xFF1B1B26.toInt(),
            divider = 0xFF2E2E3F.toInt(),
            border = 0xFF252535.toInt(),
            brand = 0xFF7C5CBF.toInt(),
            chipSelected = 0xFF6200EE.toInt(),
            chipStroke = 0xFF8E24AA.toInt(),
            text = 0xFFFFFFFF.toInt(),
            textSecondary = 0xFFB0BEC5.toInt(),
            handle = 0xFF444455.toInt(),
            error = 0xFFFF5252.toInt(),
            granted = 0xFF4CAF50.toInt(),
            notGranted = 0xFFF44336.toInt(),
            banner = 0xFFE57373.toInt(),
        )
        val Light = Palette(
            isDark = false,
            background = 0xFFF6F3FC.toInt(),
            card = 0xFFFFFFFF.toInt(),
            input = 0xFFF0EBF9.toInt(),
            divider = 0xFFE4DCF2.toInt(),
            border = 0xFFDCD2EE.toInt(),
            brand = 0xFF6A47B0.toInt(),
            chipSelected = 0xFF6200EE.toInt(),
            chipStroke = 0xFF8E24AA.toInt(),
            text = 0xFF1C1830.toInt(),
            textSecondary = 0xFF5B5670.toInt(),
            handle = 0xFFC9C0DA.toInt(),
            error = 0xFFC62828.toInt(),
            granted = 0xFF2E7D32.toInt(),
            notGranted = 0xFFC62828.toInt(),
            banner = 0xFFB3261E.toInt(),
        )

        fun resolve(context: Context, themeMode: String): Palette = when (themeMode) {
            LekhakServiceManager.THEME_DARK -> Dark
            LekhakServiceManager.THEME_LIGHT -> Light
            else -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) Dark else Light
            }
        }
    }
}
