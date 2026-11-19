package com.sushant.gymmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.gymmanager.data.local.ThemeMode

// Brand palette
val Amber600 = Color(0xFFEA580C)
val Amber400 = Color(0xFFFB923C)
val Amber700 = Color(0xFFC2410C)
val Slate700 = Color(0xFF334155)
val Slate400 = Color(0xFF94A3B8)
val Teal600 = Color(0xFF0D9488)
val Teal400 = Color(0xFF2DD4BF)

val WhatsAppGreen = Color(0xFF25D366)

/** Status colours: darker in light mode and lighter in dark mode so text and pills stay at least 4.5:1. */
val StatusGreen: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFF4ADE80) else Color(0xFF166534)
val StatusAmber: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
val StatusRed: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFFF87171) else Color(0xFFB91C1C)
val StatusBlue: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
val StatusGray: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFFA8A8A8) else Color(0xFF475569)
val StatusTeal: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
/** Text/icon colour on a solid status-coloured background. */
val OnStatus: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFF0F0F0F) else Color.White
/** WhatsApp action text/icon colour. */
val WhatsAppInk: Color @Composable @ReadOnlyComposable get() = if (LocalExtraColors.current.isDark) Color(0xFF4ADE80) else Color(0xFF0F7A3E)

private val LightColors = lightColorScheme(
    primary = Amber700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Slate700,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Teal600,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF134E4A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF8FAFC),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

// Dark theme: neutral charcoal (no blue tint) with a warm orange accent.
// Layers get slightly lighter as they rise: background < surface < containers.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF2A1200),
    primaryContainer = Color(0xFF3A2416),
    onPrimaryContainer = Color(0xFFFFD9C2),
    secondary = Color(0xFFD6D3D1),
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = Color(0xFF2B2826),
    onSecondaryContainer = Color(0xFFE7E5E4),
    tertiary = Color(0xFF4FD1C5),
    onTertiary = Color(0xFF00201D),
    tertiaryContainer = Color(0xFF12302D),
    onTertiaryContainer = Color(0xFFB4F1EA),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF0B0B0B),
    surfaceContainerLow = Color(0xFF161616),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF1A1A1A),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF2C2C2C),
    scrim = Color(0xFF000000),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF3B1717),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Immutable
data class ExtraColors(val isDark: Boolean, val cardBorder: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(false, Color(0xFFE2E8F0)) }

private val base = Typography()
private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, lineHeight = 14.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isAppInDarkTheme(themeMode: String): Boolean = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun GymManagerTheme(themeMode: String = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(themeMode)
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalExtraColors provides ExtraColors(dark, if (dark) Color(0xFF2A2A2A) else Color(0xFFE2E8F0))
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
