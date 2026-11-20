package com.sushant.grabbit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sushant.grabbit.R

/** Plus Jakarta Sans (OFL), bundled in res/font so it works offline. One family for the whole app. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun s(size: Int, line: Int, weight: FontWeight, tracking: Double) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

// Material 3 scale; headlines are heavier for Grabbit's bold, confident identity.
val AppTypography = Typography(
    displayLarge = s(57, 64, FontWeight.ExtraBold, -0.25),
    displayMedium = s(45, 52, FontWeight.ExtraBold, -0.5),
    displaySmall = s(36, 44, FontWeight.ExtraBold, -0.5),
    headlineLarge = s(32, 40, FontWeight.ExtraBold, -0.6),
    headlineMedium = s(28, 36, FontWeight.ExtraBold, -0.5),
    headlineSmall = s(24, 32, FontWeight.Bold, -0.3),
    titleLarge = s(22, 28, FontWeight.Bold, -0.2),
    titleMedium = s(16, 24, FontWeight.Bold, 0.0),
    titleSmall = s(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = s(16, 24, FontWeight.Normal, 0.15),
    bodyMedium = s(14, 20, FontWeight.Normal, 0.15),
    bodySmall = s(12, 16, FontWeight.Normal, 0.3),
    labelLarge = s(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = s(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = s(11, 16, FontWeight.SemiBold, 0.5),
)

/** Small all-caps section overline ("IN PROGRESS · 1"). */
val Overline = s(12, 16, FontWeight.Bold, 0.0).copy(letterSpacing = 0.06.em)

/** Tabular digits so progress numbers don't jump. */
const val TabularNums = "tnum"
