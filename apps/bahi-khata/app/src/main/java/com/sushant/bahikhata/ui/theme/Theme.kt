package com.sushant.bahikhata.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.bahikhata.R
import com.sushant.bahikhata.data.ThemeMode

/* Warm ledger identity (approved style 2): cream paper, deep indigo ink, saffron accent. */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C3E8C), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FA), onPrimaryContainer = Color(0xFF14215A),
    secondary = Color(0xFF8A5A1E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE3C2), onSecondaryContainer = Color(0xFF5A3300),
    tertiary = Color(0xFF1F7A45), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF3E5), onTertiaryContainer = Color(0xFF14532D),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFFFE0D6), onErrorContainer = Color(0xFF7A1A12),
    background = Color(0xFFFBF6EE), onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFBF6EE), onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFF3EADB), onSurfaceVariant = Color(0xFF6A6155),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFDF8), surfaceContainer = Color(0xFFFFFDF8),
    surfaceContainerHigh = Color(0xFFF3EADB), surfaceContainerHighest = Color(0xFFEDE2D0),
    surfaceBright = Color(0xFFFFFDF8), surfaceDim = Color(0xFFEDE4D6),
    outline = Color(0xFF8C8273), outlineVariant = Color(0xFFE6DACA),
    inverseSurface = Color(0xFF322F2A), inverseOnSurface = Color(0xFFF5EFE4), inversePrimary = Color(0xFFB9C4FF),
    scrim = Color.Black, surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C4FF), onPrimary = Color(0xFF0F1E5C),
    primaryContainer = Color(0xFF26304F), onPrimaryContainer = Color(0xFFDDE3FA),
    secondary = Color(0xFFFFB869), onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF5A3A12), onSecondaryContainer = Color(0xFFFFDDB5),
    tertiary = Color(0xFF7DD9A0), onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF1E3A2A), onTertiaryContainer = Color(0xFF9EE6B8),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF4A2A20), onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF16130F), onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF16130F), onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF2C2720), onSurfaceVariant = Color(0xFFB5AA9A),
    surfaceContainerLowest = Color(0xFF110E0B), surfaceContainerLow = Color(0xFF211D18), surfaceContainer = Color(0xFF211D18),
    surfaceContainerHigh = Color(0xFF2C2720), surfaceContainerHighest = Color(0xFF37312A),
    surfaceBright = Color(0xFF3A342C), surfaceDim = Color(0xFF16130F),
    outline = Color(0xFF8F8576), outlineVariant = Color(0xFF3A342C),
    inverseSurface = Color(0xFFEDE6DA), inverseOnSurface = Color(0xFF322F2A), inversePrimary = Color(0xFF2C3E8C),
    scrim = Color.Black, surfaceTint = Color.Transparent,
)

@Immutable
data class BkColors(
    val income: Color, val expense: Color, val saffron: Color, val onSaffron: Color,
    val card: Color, val cardBorder: Color, val info: Color, val onInfo: Color,
    val incomeBg: Color, val expenseBg: Color, val warn: Color,
    val cat: List<Pair<Color, Color>>,   // container, content
    val cardFaces: List<Color>,
)

private val lightBk = BkColors(
    income = Color(0xFF1F7A45), expense = Color(0xFFB3261E), saffron = Color(0xFFE68A1E), onSaffron = Color(0xFF1E1B16),
    card = Color(0xFFFFFDF8), cardBorder = Color(0xFFE6DACA), info = Color(0xFFE3E7F7), onInfo = Color(0xFF2C3E8C),
    incomeBg = Color(0xFFDDF3E5), expenseBg = Color(0xFFFFE0D6), warn = Color(0xFF92400E),
    cat = listOf(
        Color(0xFFFFE0D6) to Color(0xFF9A3412), Color(0xFFD6F5E3) to Color(0xFF14532D), Color(0xFFD9ECFF) to Color(0xFF0B3D73),
        Color(0xFFFFEFC2) to Color(0xFF7A4A00), Color(0xFFE6DEFF) to Color(0xFF3B2A87), Color(0xFFFCE3C2) to Color(0xFF5A3300),
        Color(0xFFFFDDE8) to Color(0xFF8A1C46), Color(0xFFD5F2F0) to Color(0xFF0E5A55), Color(0xFFE8EDD2) to Color(0xFF465214),
        Color(0xFFF0DDF7) to Color(0xFF63237A),
    ),
    cardFaces = listOf(Color(0xFF2B2B2B), Color(0xFF2C3E8C), Color(0xFF8A1C1C), Color(0xFF14532D), Color(0xFF5A3300), Color(0xFF4A2A6B), Color(0xFF0E5A55), Color(0xFF6B4E16)),
)

private val darkBk = BkColors(
    income = Color(0xFF7DD9A0), expense = Color(0xFFFFB4AB), saffron = Color(0xFFE68A1E), onSaffron = Color(0xFF1E1B16),
    card = Color(0xFF211D18), cardBorder = Color(0xFF3A342C), info = Color(0xFF26304F), onInfo = Color(0xFFC7D0FF),
    incomeBg = Color(0xFF1E3A2A), expenseBg = Color(0xFF4A2A20), warn = Color(0xFFFBBF24),
    cat = listOf(
        Color(0xFF4A2A20) to Color(0xFFFFB59A), Color(0xFF1E3A2A) to Color(0xFF9EE6B8), Color(0xFF1D3247) to Color(0xFFA8D0FF),
        Color(0xFF463A16) to Color(0xFFF5CF7A), Color(0xFF2F2852) to Color(0xFFCBBEFF), Color(0xFF5A3A12) to Color(0xFFFFDDB5),
        Color(0xFF4A1F30) to Color(0xFFFFB1CB), Color(0xFF143B39) to Color(0xFF8FE3DC), Color(0xFF34391A) to Color(0xFFD3DE9B),
        Color(0xFF3D2347) to Color(0xFFE7B7F7),
    ),
    cardFaces = lightBk.cardFaces,
)

val LocalBk = staticCompositionLocalOf { lightBk }

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold), Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun t(size: Int, line: Int, w: FontWeight, ls: Double = 0.0) =
    TextStyle(fontFamily = Manrope, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = ls.sp)

val AppTypography = Typography(
    displayLarge = t(57, 64, FontWeight.ExtraBold, -0.5), displayMedium = t(45, 52, FontWeight.ExtraBold, -0.5),
    displaySmall = t(36, 44, FontWeight.ExtraBold, -0.5), headlineLarge = t(32, 40, FontWeight.ExtraBold, -0.5),
    headlineMedium = t(28, 36, FontWeight.ExtraBold, -0.3), headlineSmall = t(24, 32, FontWeight.ExtraBold, -0.2),
    titleLarge = t(21, 28, FontWeight.ExtraBold, -0.2), titleMedium = t(16, 24, FontWeight.Bold, 0.1),
    titleSmall = t(14, 20, FontWeight.Bold, 0.1), bodyLarge = t(16, 24, FontWeight.Medium, 0.2),
    bodyMedium = t(14, 20, FontWeight.Medium, 0.2), bodySmall = t(12, 16, FontWeight.Medium, 0.3),
    labelLarge = t(14, 20, FontWeight.Bold, 0.1), labelMedium = t(12, 16, FontWeight.Bold, 0.4),
    labelSmall = t(11, 16, FontWeight.Bold, 0.5),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class Spacing(val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp, val l: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val screen: Dp = 16.dp)
val LocalSpacing = staticCompositionLocalOf { Spacing() }

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }

@Composable
fun BahiTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    CompositionLocalProvider(LocalBk provides if (dark) darkBk else lightBk, LocalSpacing provides Spacing()) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
