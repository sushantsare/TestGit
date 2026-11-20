package com.sushant.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sushant.gallery.R

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
)

private fun s(size: Int, line: Int, w: FontWeight, track: Double) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = track.sp)

val AppTypography = Typography(
    displayLarge = s(57, 64, FontWeight.Normal, -0.25), displayMedium = s(45, 52, FontWeight.Normal, 0.0),
    displaySmall = s(36, 44, FontWeight.Normal, 0.0),
    headlineLarge = s(32, 40, FontWeight.SemiBold, 0.0), headlineMedium = s(28, 36, FontWeight.Bold, 0.0),
    headlineSmall = s(24, 32, FontWeight.SemiBold, 0.0),
    titleLarge = s(22, 28, FontWeight.SemiBold, 0.0), titleMedium = s(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = s(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = s(16, 24, FontWeight.Normal, 0.3), bodyMedium = s(14, 20, FontWeight.Normal, 0.2),
    bodySmall = s(12, 16, FontWeight.Normal, 0.3),
    labelLarge = s(14, 20, FontWeight.SemiBold, 0.1), labelMedium = s(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = s(11, 16, FontWeight.SemiBold, 0.4),
)
