package com.sushant.lekhak.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sushant.lekhak.core.Palette

/** Compose view of the shared [Palette]. */
class UiColors(p: Palette) {
    val isDark = p.isDark
    val background = Color(p.background)
    val card = Color(p.card)
    val input = Color(p.input)
    val divider = Color(p.divider)
    val border = Color(p.border)
    val brand = Color(p.brand)
    val chip = Color(p.chipSelected)
    val chipStroke = Color(p.chipStroke)
    val text = Color(p.text)
    val textSecondary = Color(p.textSecondary)
    val error = Color(p.error)
    val granted = Color(p.granted)
    val notGranted = Color(p.notGranted)
    val banner = Color(p.banner)
    val selectedCard = if (p.isDark) Color(0xFF3E2D5E) else Color(0xFFEDE5FA)
    val cardBorder = if (p.isDark) Color(0xFF34344A) else Color(p.border)
}

val LocalUi = staticCompositionLocalOf { UiColors(Palette.Dark) }

@Composable
fun LekhakTheme(palette: Palette, content: @Composable () -> Unit) {
    val ui = UiColors(palette)
    val scheme = if (palette.isDark) darkColorScheme(
        primary = ui.brand, onPrimary = Color.White, background = ui.background, surface = ui.card,
        onBackground = ui.text, onSurface = ui.text, surfaceTint = Color.Transparent, error = ui.error,
        surfaceContainerHigh = ui.card, outline = Color(0xFF4A4A60),
    ) else lightColorScheme(
        primary = ui.brand, onPrimary = Color.White, background = ui.background, surface = ui.card,
        onBackground = ui.text, onSurface = ui.text, surfaceTint = Color.Transparent, error = ui.error,
        surfaceContainerHigh = ui.card, outline = Color(0xFFB8AECD),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalUi provides ui) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
