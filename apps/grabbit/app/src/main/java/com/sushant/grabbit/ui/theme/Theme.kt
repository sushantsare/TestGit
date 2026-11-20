package com.sushant.grabbit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sushant.grabbit.data.Prefs

/** Theme mode from Settings: 0 = system, 1 = light, 2 = dark. */
@Composable
fun isDarkTheme(): Boolean {
    val mode by Prefs.themeFlow.collectAsState()
    return when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun GrabbitTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val amoled by Prefs.amoledFlow.collectAsState()
    val dynamic by Prefs.dynamicFlow.collectAsState()
    val ctx = LocalContext.current
    var scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && amoled) scheme = scheme.copy(
        background = AmoledColors.background, surface = AmoledColors.surface,
        surfaceContainerLowest = AmoledColors.surfaceContainerLowest, surfaceContainerLow = AmoledColors.surfaceContainerLow,
        surfaceContainer = AmoledColors.surfaceContainer, surfaceContainerHigh = AmoledColors.surfaceContainerHigh,
        surfaceContainerHighest = AmoledColors.surfaceContainerHighest,
    )
    scheme = scheme.copy(surfaceTint = Color.Transparent)
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
        LocalSemanticColors provides if (dark) DarkSemantic else LightSemantic,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
