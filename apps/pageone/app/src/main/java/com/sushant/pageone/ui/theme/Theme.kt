package com.sushant.pageone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
val LocalIsDark = staticCompositionLocalOf { false }

/**
 * PageOne theme. Pure Material 3 Expressive (expressive motion scheme, Expressive shapes in components).
 * Wallpaper colours on by default (Android 12+); Sage is the fallback. Pure black only applies in dark mode.
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
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = PageOneTypography,
            shapes = PageOneShapes,
        ) {
            // text outside a Surface must follow the theme, not default to black
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onBackground) {
                val view = androidx.compose.ui.platform.LocalView.current
                if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
                    (view.context as? android.app.Activity)?.window?.let { w ->
                        androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                        }
                    }
                }
                content()
            }
        }
    }
}
