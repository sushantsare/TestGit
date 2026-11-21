package com.sushant.gallery.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Seed: teal #00696B (style "Clean & bright"). Values mirror design/DESIGN_SPEC.md and the prototype CSS tokens.
val LightColors = lightColorScheme(
    primary = Color(0xFF00696B), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EEEC), onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7), onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FBFA), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA), onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E4), onSurfaceVariant = Color(0xFF3F4948),
    surfaceTint = Color(0xFF00696B),
    inverseSurface = Color(0xFF2B3231), inverseOnSurface = Color(0xFFECF2F1), inversePrimary = Color(0xFF80D4D4),
    outline = Color(0xFF6F7979), outlineVariant = Color(0xFFBEC9C8), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4FBFA), surfaceDim = Color(0xFFD5DBDA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEEF5F4),
    surfaceContainer = Color(0xFFE8EFEE), surfaceContainerHigh = Color(0xFFE2E9E8),
    surfaceContainerHighest = Color(0xFFDDE4E3),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D4), onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51), onPrimaryContainer = Color(0xFF9CF1F0),
    secondary = Color(0xFFB0CCCB), onSecondary = Color(0xFF1B3535),
    secondaryContainer = Color(0xFF324B4B), onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8), onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863), onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceTint = Color(0xFF80D4D4),
    inverseSurface = Color(0xFFDDE4E3), inverseOnSurface = Color(0xFF2B3231), inversePrimary = Color(0xFF00696B),
    outline = Color(0xFF889392), outlineVariant = Color(0xFF3F4948), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3A), surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121), surfaceContainerHigh = Color(0xFF242B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
)

/** Fixed colours that do not follow light/dark (photo viewer, filmstrip, accents). */
@Immutable
data class FixedColors(
    val viewerBg: Color = Color(0xFF000000),
    val filmBg: Color = Color(0xFF0D1414),
    val onMedia: Color = Color(0xFFFFFFFF),
    val mediaScrim: Color = Color(0x99000000),
    val trash: Color = Color(0xFFB3261E),
    val sun: Color = Color(0xFFF5B841),
    val stripSelected: Color = Color(0xFFB4EEEC),
    val hintFill: Color = Color(0x1FFFFFFF),
    val hintBorder: Color = Color(0x33FFFFFF),
    val editorPanel: Color = Color(0xFF111111),
    val editorAccent: Color = Color(0xFF80D4D4),
    val editorChipOn: Color = Color(0xFF004F51),
    val editorOnChip: Color = Color(0xFF9CF1F0),
    val editorMuted: Color = Color(0xFFAAAAAA),
)
val LocalFixedColors = staticCompositionLocalOf { FixedColors() }
