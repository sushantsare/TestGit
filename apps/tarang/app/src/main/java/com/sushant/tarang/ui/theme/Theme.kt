@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.sushant.tarang.R
import com.sushant.tarang.data.ThemeMode

/** Google Sans Flex (open licence), bundled. "Title" = weight 650 at 110% width, used for every page title. */
val Gsf = FontFamily(
    Font(R.font.gsf_regular, FontWeight.Normal),
    Font(R.font.gsf_medium, FontWeight.Medium),
    Font(R.font.gsf_semibold, FontWeight.SemiBold),
    Font(R.font.gsf_title, FontWeight.Bold),
)

/** Google Sans Flex with its rounded setting (Pixel style) for titles, buttons and the player. */
val GsfRound = FontFamily(
    Font(R.font.gsf_round_semibold, FontWeight.SemiBold),
    Font(R.font.gsf_round_bold, FontWeight.Bold),
    Font(R.font.gsf_round_bold, FontWeight.ExtraBold),
)
val RoundTitle = TextStyle(fontFamily = GsfRound, fontWeight = FontWeight.Bold)

private val base = Typography()
val TarangType = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Gsf), displayMedium = base.displayMedium.copy(fontFamily = Gsf),
    displaySmall = base.displaySmall.copy(fontFamily = Gsf),
    headlineLarge = base.headlineLarge.copy(fontFamily = GsfRound, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = GsfRound, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = GsfRound, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = GsfRound, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Gsf, fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontFamily = Gsf, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Gsf), bodyMedium = base.bodyMedium.copy(fontFamily = Gsf),
    bodySmall = base.bodySmall.copy(fontFamily = Gsf),
    labelLarge = base.labelLarge.copy(fontFamily = Gsf, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Gsf, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Gsf, fontWeight = FontWeight.Medium),
)

/** The one title style for every top-level page (Home greeting, Folders, Library, Stats, Settings). */
val PageTitle = TextStyle(fontFamily = GsfRound, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp)

val Shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TarangTheme(mode: ThemeMode, pureBlack: Boolean, wallpaper: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val ctx = LocalContext.current
    var scheme = when {
        wallpaper && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> TarangDark
        else -> TarangLight
    }
    if (dark && pureBlack) scheme = scheme.pureBlack()
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), shapes = Shapes, typography = TarangType) {
            SystemBarIcons(light = !dark)
            androidx.compose.material3.Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
        }
    }
}

val LocalIsDark = staticCompositionLocalOf { false }

/** Colours of the player, taken from the song's cover (Material tonal-spot scheme of the cover's main colour). */
@Immutable
data class CoverColors(
    val full: Color,          // full player background
    val onFull: Color,
    val accent: Color,        // play button / seek bar
    val onAccent: Color,
    val chip: Color,          // prev / next / small round buttons
    val onChip: Color,
    val mini: Color,          // mini player background
    val miniFill: Color,      // mini player progress fill
    val onMini: Color,
    val onMiniVariant: Color,
    val panel: Color,         // up next / lyrics panel
    val onPanel: Color,
    val onPanelVariant: Color,
    val outline: Color,
)

fun coverColors(seed: Int?, dark: Boolean, fallback: ColorScheme): CoverColors {
    if (seed == null) return fromScheme(fallback, dark)
    return runCatching {
        val s = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
        val m = MaterialDynamicColors()
        fun c(v: Int) = Color(v)
        val mini = c(m.surfaceContainerHigh().getArgb(s)); val primary = c(m.primary().getArgb(s))
        CoverColors(
            full = c(m.primaryContainer().getArgb(s)), onFull = c(m.onPrimaryContainer().getArgb(s)),
            accent = primary, onAccent = c(m.onPrimary().getArgb(s)),
            chip = c(m.surfaceContainerLowest().getArgb(s)).copy(alpha = 0.7f), onChip = c(m.onSurface().getArgb(s)),
            mini = mini, miniFill = lerp(mini, primary, if (dark) 0.28f else 0.22f),
            onMini = c(m.onSurface().getArgb(s)), onMiniVariant = c(m.onSurfaceVariant().getArgb(s)),
            panel = c(m.surface().getArgb(s)), onPanel = c(m.onSurface().getArgb(s)), onPanelVariant = c(m.onSurfaceVariant().getArgb(s)),
            outline = c(m.outlineVariant().getArgb(s)),
        )
    }.getOrElse { fromScheme(fallback, dark) }
}

private fun fromScheme(s: ColorScheme, dark: Boolean) = CoverColors(
    full = s.primaryContainer, onFull = s.onPrimaryContainer, accent = s.primary, onAccent = s.onPrimary,
    chip = s.surfaceContainerLowest.copy(alpha = 0.7f), onChip = s.onSurface,
    mini = s.surfaceContainerHigh, miniFill = lerp(s.surfaceContainerHigh, s.primary, if (dark) 0.28f else 0.22f),
    onMini = s.onSurface, onMiniVariant = s.onSurfaceVariant, panel = s.surface, onPanel = s.onSurface,
    onPanelVariant = s.onSurfaceVariant, outline = s.outlineVariant,
)

@Suppress("unused") private fun Color.argb() = toArgb()


/** Dark status/navigation bar icons on light backgrounds and light icons on dark ones (follows the app's theme, not the phone's). */
@Composable
fun SystemBarIcons(light: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
        }
    }
}
