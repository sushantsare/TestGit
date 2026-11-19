package com.sushant.grabbit.ui

import androidx.compose.foundation.isSystemInDarkTheme
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

private val Dark = darkColorScheme(
    primary = Color(0xFFB9A3FF),
    onPrimary = Color(0xFF250A63),
    primaryContainer = Color(0xFF4626A8),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFFF8DB2),
    onSecondary = Color(0xFF4F0024),
    secondaryContainer = Color(0xFF6E1D41),
    onSecondaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF0F0D17),
    onBackground = Color(0xFFE7E1F0),
    surface = Color(0xFF0F0D17),
    onSurface = Color(0xFFE7E1F0),
    surfaceVariant = Color(0xFF2B2738),
    onSurfaceVariant = Color(0xFFCAC3D8),
    surfaceContainer = Color(0xFF1A1724),
    surfaceContainerHigh = Color(0xFF221E2E),
    surfaceContainerLow = Color(0xFF16131F),
    outline = Color(0xFF948DA3),
    outlineVariant = Color(0xFF3D3849),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
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

@Composable
fun GrabbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDarkTheme()) Dark else Light, content = content)
}
