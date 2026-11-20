package com.sushant.inknote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons for the writing tools (same drawings as the approved mockups). */
object InkIcons {
    private fun line(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) b.addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        return b.build()
    }
    val Pen by lazy { line("pen", "M4 20l1-5L16 4l4 4L9 19z", "M14 6l4 4") }
    val Highlighter by lazy { line("highlighter", "M6 15l9-9 4 4-9 9H6z", "M4 21h9") }
    val Eraser by lazy { line("eraser", "M8 20h12", "M4 15l9-9 6 6-7 7H8z", "M8.5 10.5l6 6") }
    val Lasso by lazy { line("lasso", "M4 9a8 5 0 1 0 16 0a8 5 0 1 0 -16 0", "M8 13c-1 3 1 6 3 7") }
    val Shapes by lazy { line("shapes", "M3.8 8a4.2 4.2 0 1 0 8.4 0a4.2 4.2 0 1 0 -8.4 0", "M11.5 11.5h9v9h-9z") }
}
