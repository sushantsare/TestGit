package com.sushant.pageone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PageOneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Grouped-list (segmented) corners: outer 24dp, inner 6dp. Used by every list/settings group. */
object GroupShapes {
    val first = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    val middle = RoundedCornerShape(6.dp)
    val last = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val only = RoundedCornerShape(24.dp)
    fun of(index: Int, count: Int) = when {
        count == 1 -> only
        index == 0 -> first
        index == count - 1 -> last
        else -> middle
    }
}

/** Continue-reading card 32dp, home FAB 24dp (80dp size), reader pen FAB 20dp (64dp). */
object PageOneCorners {
    val heroCard = RoundedCornerShape(32.dp)
    val homeFab = RoundedCornerShape(24.dp)
    val penFab = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val splitLeft = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    val splitRight = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 26.dp, bottomEnd = 26.dp)
}

/**
 * Expressive icon containers: use androidx.graphics.shapes via MaterialShapes
 * (MaterialShapes.Cookie9Sided, Clover4Leaf, Sunny, Flower, Burst, Circle) — rotate through them
 * by stable item index for folder rows; Cookie9Sided is the brand shape (icon, lock, loading).
 */
