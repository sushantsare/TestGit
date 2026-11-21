package com.sushant.gallery.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xxs: Dp = 2.dp, val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp, val l: Dp = 16.dp,
    val xl: Dp = 24.dp, val xxl: Dp = 32.dp, val xxxl: Dp = 48.dp,
    val screen: Dp = 16.dp,
    val gridGap: Dp = 2.dp,
    val pinSize: Dp = 58.dp,
    val albumCardRadius: Dp = 16.dp,
    val topBar: Dp = 64.dp,
    val filmCardHeight: Dp = 290.dp,
    val filmCardGap: Dp = 14.dp,
    val filmCardRadius: Dp = 18.dp,
    val stripThumbW: Dp = 32.dp,
    val stripThumbH: Dp = 44.dp,
    val trashSize: Dp = 52.dp,
    val pinKey: Dp = 76.dp,
    val emptyIllustration: Dp = 180.dp,
    val maxContentWidth: Dp = 720.dp,
)
val LocalSpacing = staticCompositionLocalOf { Spacing() }
