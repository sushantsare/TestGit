package com.sushant.grabbit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val screen: Dp = 16.dp,
)

@Immutable
data class Sizes(
    val button: Dp = 52.dp,
    val buttonSmall: Dp = 40.dp,
    val field: Dp = 56.dp,
    val search: Dp = 48.dp,
    val icon: Dp = 24.dp,
    val iconSmall: Dp = 18.dp,
    val logo: Dp = 36.dp,
    val rowThumbW: Dp = 104.dp,
    val rowThumbH: Dp = 58.dp,
    val stepBadge: Dp = 36.dp,
    val progress: Dp = 6.dp,
    val avatar: Dp = 40.dp,
    val illustration: Dp = 180.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalSizes = staticCompositionLocalOf { Sizes() }
