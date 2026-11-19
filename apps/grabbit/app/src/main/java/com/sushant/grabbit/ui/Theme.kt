package com.sushant.grabbit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sushant.grabbit.data.Prefs

val Purple = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF4D8D)
val BrandGradient = Brush.linearGradient(listOf(Purple, Pink))

// Dark theme: neutral charcoal (no purple/navy tint), brand colours only as accents
private val Dark = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF1E0B4D),
    primaryContainer = Color(0xFF3A2D63),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFFF8AB3),
    onSecondary = Color(0xFF4A0F27),
    // Selected chips, nav indicator and segmented buttons: quiet grey instead of maroon
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFF2F2F2),
    tertiary = Color(0xFF8AD8C8),
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF1A1A1A),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2C2C2C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0A0A),
    errorContainer = Color(0xFF3B1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Light = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF23005C),
    secondary = Color(0xFFD81B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF3E001D),
    background = Color(0xFFFCF8FF),
    surface = Color(0xFFFCF8FF),
    surfaceVariant = Color(0xFFE8E0F3),
    surfaceContainer = Color(0xFFF2ECFA),
    surfaceContainerHigh = Color(0xFFECE6F4),
    surfaceContainerLow = Color(0xFFF7F2FD),
)

@Composable
fun isDarkTheme(): Boolean {
    val mode by Prefs.themeFlow.collectAsState()
    return when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
}

/** Brand banner gradient; deeper in dark mode so it doesn't glare. */
@Composable
fun brandGradient(): Brush =
    if (isDarkTheme()) Brush.linearGradient(listOf(Color(0xFF5B3FC4), Color(0xFFB8336A))) else BrandGradient

/** Subtle 1dp card outline in dark mode (cards would otherwise blur into the background). */
@Composable
fun cardBorder(): BorderStroke? = if (isDarkTheme()) BorderStroke(1.dp, Color(0xFF2A2A2A)) else null

@Composable
fun GrabbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDarkTheme()) Dark else Light, content = content)
}
