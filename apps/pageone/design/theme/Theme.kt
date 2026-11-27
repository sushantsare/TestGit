package com.sushant.pageone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/**
 * PageOne theme. Pure Material 3 Expressive.
 * - dynamicColor (default ON): wallpaper colours on Android 12+, Sage fallback otherwise.
 * - pureBlack: only applies in dark mode; background/surfaces to #000000, containers slightly lifted.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageOneTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> SageDark
        else -> SageLight
    }
    if (dark && pureBlack) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0E0E0E), surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerHigh = Color(0xFF1A1A1A), surfaceVariant = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424),
            surfaceBright = Color(0xFF2A2A2A), outlineVariant = Color(0xFF2A2A2A),
        )
    }
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalSemanticColors provides if (dark) DarkSemantic else LightSemantic,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = PageOneTypography,
            shapes = PageOneShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
