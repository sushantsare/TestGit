package com.sushant.inknote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.inknote.R
import com.sushant.inknote.data.ThemeMode

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)
val InterDisplay = FontFamily(Font(R.font.inter_display_bold, FontWeight.Bold))

/** Extra colours not in the Material scheme. */
data class InkColors(
    val bg: Color, val card: Color, val surf2: Color, val divider: Color, val muted: Color,
    val paperLine: Color, val highlight: Color, val danger: Color, val pdfBadge: Color, val dark: Boolean,
)

val LocalInk = staticCompositionLocalOf { LightInk }

val LightInk = InkColors(Color(0xFFFBF8F3), Color.White, Color(0xFFF4F1EC), Color(0xFFE4DED4), Color(0xFF625D55),
    Color(0xFFDCE6F7), Color(0xFFFFE066), Color(0xFFB3261E), Color(0xFFD93025), false)
val DarkInk = InkColors(Color(0xFF141518), Color(0xFF1E2024), Color(0xFF232529), Color(0xFF34373D), Color(0xFFABA59B),
    Color(0xFF2D3A52), Color(0xFF7A6A1E), Color(0xFFF2B8B5), Color(0xFFD93025), true)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2B5BD7), onPrimary = Color.White, primaryContainer = Color(0xFFDCE5FB), onPrimaryContainer = Color(0xFF0F2A73),
    secondaryContainer = Color(0xFFDCE5FB), onSecondaryContainer = Color(0xFF0F2A73),
    background = Color(0xFFFBF8F3), onBackground = Color(0xFF1C1B1F), surface = Color(0xFFFBF8F3), onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFECE8E1), onSurfaceVariant = Color(0xFF625D55), surfaceContainer = Color(0xFFF4F1EC),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFECE8E1),
    surfaceContainerLowest = Color.White, outline = Color(0xFF8A8478), outlineVariant = Color(0xFFE4DED4), error = Color(0xFFB3261E),
    surfaceTint = Color.Transparent, inverseSurface = Color(0xFF2F3033), inverseOnSurface = Color(0xFFF2EFEA), inversePrimary = Color(0xFFA9C1FF),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB6FF), onPrimary = Color(0xFF0B1D4D), primaryContainer = Color(0xFF243A73), onPrimaryContainer = Color(0xFFDCE5FB),
    secondaryContainer = Color(0xFF243A73), onSecondaryContainer = Color(0xFFDCE5FB),
    background = Color(0xFF141518), onBackground = Color(0xFFECE8E1), surface = Color(0xFF141518), onSurface = Color(0xFFECE8E1),
    surfaceVariant = Color(0xFF2A2C31), onSurfaceVariant = Color(0xFFABA59B), surfaceContainer = Color(0xFF232529),
    surfaceContainerLow = Color(0xFF1E2024), surfaceContainerHigh = Color(0xFF1E2024), surfaceContainerHighest = Color(0xFF2A2C31),
    surfaceContainerLowest = Color(0xFF141518), outline = Color(0xFF8C877E), outlineVariant = Color(0xFF34373D), error = Color(0xFFF2B8B5),
    surfaceTint = Color.Transparent, inverseSurface = Color(0xFFECE8E1), inverseOnSurface = Color(0xFF2F3033), inversePrimary = Color(0xFF2B5BD7),
)

private val Base = Typography()
private fun TextStyle.inter() = copy(fontFamily = Inter)
val InkTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = Base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.inter(), bodyMedium = Base.bodyMedium.inter(), bodySmall = Base.bodySmall.inter(),
    labelLarge = Base.labelLarge.inter().copy(fontWeight = FontWeight.SemiBold), labelMedium = Base.labelMedium.inter(), labelSmall = Base.labelSmall.inter(),
)

val InkShapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp))

@Composable
fun isDark(mode: Int): Boolean = when (mode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; else -> isSystemInDarkTheme() }

@Composable
fun InkTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInk provides if (dark) DarkInk else LightInk) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = InkTypography, shapes = InkShapes, content = content)
    }
}

val FolderColors = listOf(0xFF2B5BD7, 0xFF0E8A6B, 0xFFB4561F, 0xFF8A3FB8, 0xFFC0392B, 0xFFB7860B, 0xFF00838F, 0xFF5E6B7A).map { it.toInt() }
