package com.sushant.saveo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = Color(0xFF187C51), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEDB), onPrimaryContainer = Color(0xFF0B3D26),
    secondary = Color(0xFF4E6356), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4EC), onSecondaryContainer = Color(0xFF1B6E48),
    tertiary = Color(0xFF3D6373), onTertiary = Color.White,
    background = Color.White, onBackground = Color(0xFF17211B),
    surface = Color.White, onSurface = Color(0xFF17211B),
    surfaceVariant = Color(0xFFEEF4F0), onSurfaceVariant = Color(0xFF5B6B61),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEEF4F0), surfaceContainerHigh = Color(0xFFE6ECE8),
    surfaceContainerHighest = Color(0xFFDEE5E0),
    outline = Color(0xFF8A988F), outlineVariant = Color(0xFFE2E8E4),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFEDEA), onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2B332F), inverseOnSurface = Color.White, inversePrimary = Color(0xFF7EE2AE),
    surfaceTint = Color.Transparent, scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD49A), onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF1F3A2C), onPrimaryContainer = Color(0xFF9BE8BF),
    secondary = Color(0xFFB3CCBD), onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF1F3A2C), onSecondaryContainer = Color(0xFF9BE8BF),
    tertiary = Color(0xFFA5CDDF), onTertiary = Color(0xFF063543),
    background = Color(0xFF0F1512), onBackground = Color(0xFFE3EAE5),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFE3EAE5),
    surfaceVariant = Color(0xFF222C27), onSurfaceVariant = Color(0xFF9AABA1),
    surfaceContainerLowest = Color(0xFF0A0F0C), surfaceContainerLow = Color(0xFF141B17),
    surfaceContainer = Color(0xFF1B231F), surfaceContainerHigh = Color(0xFF222C27),
    surfaceContainerHighest = Color(0xFF2B3631),
    outline = Color(0xFF6E7D74), outlineVariant = Color(0xFF2A342F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1A17), onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE3EAE5), inverseOnSurface = Color(0xFF17211B), inversePrimary = Color(0xFF187C51),
    surfaceTint = Color.Transparent, scrim = Color.Black,
)

/** Fixed Saveo brand extras that stay the same even with wallpaper colours on. */
@Immutable
data class SaveoExtras(
    val glossTop: Color,
    val glossBottom: Color,
    val onGloss: Color,
    val saveBadgeGreen: Color,
    val snackBg: Color,
    val snackAction: Color,
    val isDark: Boolean,
) {
    val glossBrush: Brush get() = Brush.verticalGradient(listOf(glossTop, glossBottom))
}

private val LightExtras = SaveoExtras(Color(0xFF1C8655), Color(0xFF14704A), Color.White, Color(0xFF14804F), Color(0xFF2B332F), Color(0xFF7EE2AE), false)
private val DarkExtras = SaveoExtras(Color(0xFF6EDBA5), Color(0xFF45C287), Color(0xFF003920), Color(0xFF14804F), Color(0xFF2B332F), Color(0xFF7EE2AE), true)

val LocalSaveo = staticCompositionLocalOf { LightExtras }

@Immutable
data class Spacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp,
    val l: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val xxxl: Dp = 48.dp,
)
val LocalSpacing = staticCompositionLocalOf { Spacing() }

val SaveoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Base = Typography()
val SaveoType = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SaveoTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalSaveo provides if (dark) DarkExtras else LightExtras,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = SaveoType, shapes = SaveoShapes, content = content)
    }
}
