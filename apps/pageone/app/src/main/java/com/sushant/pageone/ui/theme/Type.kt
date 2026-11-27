@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.sushant.pageone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sushant.pageone.R

/**
 * Roboto Flex (OFL), bundled as res/font/roboto_flex.ttf (variable font, from github.com/googlefonts/roboto-flex).
 * One family for everything. Weights used: 400, 500, 560 (large titles), 600.
 */
private fun flex(weight: Int) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val RobotoFlex = FontFamily(flex(400), flex(500), flex(560), flex(600), flex(700))

private fun s(size: Int, line: Int, w: Int, track: Double = 0.0) = TextStyle(
    fontFamily = RobotoFlex, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(w), letterSpacing = track.sp,
)

val PageOneTypography = Typography(
    displayLarge = s(57, 64, 400, -0.25),
    displayMedium = s(45, 52, 400),
    displaySmall = s(36, 44, 400),
    headlineLarge = s(40, 46, 560, -0.5),   // "PageOne", "Recent", "Starred" large titles; setup step titles
    headlineMedium = s(28, 36, 400),
    headlineSmall = s(24, 32, 400),          // dialog titles
    titleLarge = s(22, 28, 500),             // small top bar titles, empty-state titles
    titleMedium = s(16, 24, 600, 0.15),      // list row titles
    titleSmall = s(14, 20, 600, 0.1),        // section labels (primary colour)
    bodyLarge = s(16, 24, 400, 0.5),
    bodyMedium = s(14, 20, 400, 0.25),
    bodySmall = s(13, 18, 400, 0.3),         // row meta "418 pages · 38 MB · 12 Nov"
    labelLarge = s(15, 20, 600, 0.1),        // buttons
    labelMedium = s(13, 16, 600, 0.4),       // nav labels, connected-button labels
    labelSmall = s(12, 16, 500, 0.5),
)
