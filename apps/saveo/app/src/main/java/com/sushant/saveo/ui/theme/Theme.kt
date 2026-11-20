package com.sushant.saveo.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sushant.saveo.R

// Saveo 2.0 — Grabbit colour identity: tangerine seed, warm cream surfaces, neutral charcoal dark.
// Every text/background pair below is checked in design/DESIGN_SPEC.md (text >= 4.5:1, icons >= 3:1).

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = Color(0xFFAD3600), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCD), onPrimaryContainer = Color(0xFF390C00),
    inversePrimary = Color(0xFFFFB597),
    secondary = Color(0xFF77574B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0D4), onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF006A63), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA2F2E8), onTertiaryContainer = Color(0xFF00201D),
    background = Color(0xFFFFF8F5), onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFF8F5), onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED5), onSurfaceVariant = Color(0xFF53433D),
    inverseSurface = Color(0xFF392E2A), inverseOnSurface = Color(0xFFFFEDE7),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736C), outlineVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFBEAE3), surfaceContainerHigh = Color(0xFFF5E4DD),
    surfaceContainerHighest = Color(0xFFEFDED8),
    surfaceTint = Color.Transparent, scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB597), onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF4E2312), onPrimaryContainer = Color(0xFFFFDBCD),
    inversePrimary = Color(0xFFAD3600),
    secondary = Color(0xFFE7BDAE), onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF3A302C), onSecondaryContainer = Color(0xFFFFE0D4),
    tertiary = Color(0xFF82D5CC), onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF0F3B37), onTertiaryContainer = Color(0xFFA2F2E8),
    background = Color(0xFF121212), onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212), onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2B2725), onSurfaceVariant = Color(0xFFB8AEAA),
    inverseSurface = Color(0xFFEDEDED), inverseOnSurface = Color(0xFF2A2A2A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF4D1A16), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A807C), outlineVariant = Color(0xFF3A3532),
    surfaceContainerLowest = Color(0xFF0D0D0D), surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1F1F1F), surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF343434),
    surfaceTint = Color.Transparent, scrim = Color.Black,
)

/** Pure-black dark mode for OLED screens (Settings › Pure black). */
private val AmoledColors = DarkColors.copy(
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF141414), surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF282828),
)

/** Fixed colours that stay the same with wallpaper colours on. */
@Immutable
data class SaveoExtras(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val isDark: Boolean,
) {
    /** Badges drawn ON photos/videos are identical in every theme (photo colours don't change with the theme). */
    val imageScrim: Color get() = Color(0x8C000000)
    val onImage: Color get() = Color.White
    /** Round buttons on thumbnails: light chip with brand-orange icon (5.2:1) … */
    val chipOnImage: Color get() = Color.White
    val brandOnImage: Color get() = Color(0xFFC2410C)
    /** … and the "done" states: green (saved) and orange (visible in Gallery), both with white icons. */
    val savedOnImage: Color get() = Color(0xFF1B6D35)
    val activeOnImage: Color get() = Color(0xFFC2410C)
    val snackBg: Color get() = Color(0xFF392E2A)
    val snackAction: Color get() = Color(0xFFFFB597)
}

private val LightExtras = SaveoExtras(Color(0xFF1B6D35), Color.White, Color(0xFFA6F4B0), Color(0xFF00210B), false)
private val DarkExtras = SaveoExtras(Color(0xFF8BD89A), Color(0xFF00391A), Color(0xFF0E3D1E), Color(0xFFA6F4B0), true)

val LocalSaveo = staticCompositionLocalOf { LightExtras }

// ---------------------------------------------------------------- spacing & sizes
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp,
    val l: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val xxxl: Dp = 48.dp,
    val screen: Dp = 16.dp,
)
val LocalSpacing = staticCompositionLocalOf { Spacing() }

// ---------------------------------------------------------------- shapes
val SaveoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),      // chips, small badges
    medium = RoundedCornerShape(16.dp),     // small cards, banners
    large = RoundedCornerShape(20.dp),      // media tiles
    extraLarge = RoundedCornerShape(24.dp), // cards, groups, sheets
)
val PillShape = RoundedCornerShape(50)

// ---------------------------------------------------------------- type (Plus Jakarta Sans, bundled)
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun s(size: Int, line: Int, weight: FontWeight, tracking: Double) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

val SaveoType = Typography(
    displayLarge = s(57, 64, FontWeight.ExtraBold, -0.25),
    displayMedium = s(45, 52, FontWeight.ExtraBold, -0.5),
    displaySmall = s(36, 44, FontWeight.ExtraBold, -0.5),
    headlineLarge = s(32, 40, FontWeight.ExtraBold, -0.6),
    headlineMedium = s(28, 36, FontWeight.ExtraBold, -0.5),
    headlineSmall = s(24, 32, FontWeight.Bold, -0.3),
    titleLarge = s(22, 28, FontWeight.Bold, -0.2),
    titleMedium = s(16, 24, FontWeight.Bold, 0.0),
    titleSmall = s(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = s(16, 24, FontWeight.Normal, 0.15),
    bodyMedium = s(14, 20, FontWeight.Normal, 0.15),
    bodySmall = s(12, 16, FontWeight.Normal, 0.3),
    labelLarge = s(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = s(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = s(11, 16, FontWeight.SemiBold, 0.5),
)

/** Small all-caps section overline ("TODAY · 6"). */
val Overline = s(12, 16, FontWeight.Bold, 0.0).copy(letterSpacing = 0.06.em)

val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

// ---------------------------------------------------------------- motion
object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 350
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

@Composable
fun SaveoTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, pureBlack: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    var scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> if (pureBlack) AmoledColors else DarkColors
        else -> LightColors
    }
    if (dark && pureBlack && dynamicColor) scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    CompositionLocalProvider(
        LocalSaveo provides if (dark) DarkExtras else LightExtras,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = SaveoType, shapes = SaveoShapes, content = content)
    }
}
