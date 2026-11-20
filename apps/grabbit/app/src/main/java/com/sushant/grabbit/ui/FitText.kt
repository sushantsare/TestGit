package com.sushant.grabbit.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * One-line label that shrinks step by step until it fits its space (big system font, narrow phone).
 * Never goes below [min]; if it still doesn't fit there, it wraps to two lines instead of clipping.
 * Only for tight controls (segmented buttons, chips) — normal text keeps the user's font size.
 */
@Composable
fun FitText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, min: TextUnit = 12.sp) {
    val base = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    // The floor is a real on-screen size: sp grows with the system font, so undo that for the minimum
    val floor = (min.value / LocalDensity.current.fontScale).sp
    var size by remember(text, base) { mutableStateOf(base) }
    var ready by remember(text, base) { mutableStateOf(false) }
    val atMin = size.value <= floor.value
    Text(
        text,
        modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(fontSize = size, lineHeight = if (atMin) (size.value * 1.25f).sp else style.lineHeight),
        textAlign = TextAlign.Center,
        maxLines = if (atMin) 2 else 1,
        softWrap = atMin,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && !atMin) size = (size.value - 0.5f).coerceAtLeast(floor.value).sp
            else ready = true
        },
    )
}
