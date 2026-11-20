package com.sushant.grabbit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Grabbit colour system. Seed: tangerine. Every pair below passed the WCAG contrast check
// (design/contrast.md): text >= 4.5:1, outlines >= 3:1, in light, dark and pure-black themes.

val LightColors = lightColorScheme(
    primary = Color(0xFFAD3600),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCD),
    onPrimaryContainer = Color(0xFF390C00),
    inversePrimary = Color(0xFFFFB597),
    secondary = Color(0xFF77574B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE0D4),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF006A63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA2F2E8),
    onTertiaryContainer = Color(0xFF00201D),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED5),
    onSurfaceVariant = Color(0xFF53433D),
    inverseSurface = Color(0xFF392E2A),
    inverseOnSurface = Color(0xFFFFEDE7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFBEAE3),
    surfaceContainerHigh = Color(0xFFF5E4DD),
    surfaceContainerHighest = Color(0xFFEFDED8),
    surfaceTint = Color.Transparent,
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB597),
    onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF4E2312),
    onPrimaryContainer = Color(0xFFFFDBCD),
    inversePrimary = Color(0xFFAD3600),
    secondary = Color(0xFFE7BDAE),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF3A302C),
    onSecondaryContainer = Color(0xFFFFE0D4),
    tertiary = Color(0xFF82D5CC),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF0F3B37),
    onTertiaryContainer = Color(0xFFA2F2E8),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2B2725),
    onSurfaceVariant = Color(0xFFB8AEAA),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF2A2A2A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4D1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A807C),
    outlineVariant = Color(0xFF3A3532),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF343434),
    surfaceTint = Color.Transparent,
    scrim = Color(0xFF000000),
)

/** Pure-black variant of the dark scheme for OLED screens. */
val AmoledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF282828),
)

@Immutable
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
)

val LightSemantic = SemanticColors(
    success = Color(0xFF1B6D35),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA6F4B0),
    onSuccessContainer = Color(0xFF00210B),
)
val DarkSemantic = SemanticColors(
    success = Color(0xFF8BD89A),
    onSuccess = Color(0xFF00391A),
    successContainer = Color(0xFF0E3D1E),
    onSuccessContainer = Color(0xFFA6F4B0),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/** Brand mark gradient (app icon, logo tile). Fixed in both themes; always carries white content. */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFFFF7A3D), Color(0xFFE8470F)))
val OnBrand = Color.White

/** Scrim and labels drawn on top of video thumbnails (always dark, any theme). */
val ImageScrim = Color(0x8C000000)
val OnImage = Color.White
