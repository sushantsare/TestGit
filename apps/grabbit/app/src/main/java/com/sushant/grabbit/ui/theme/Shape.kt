package com.sushant.grabbit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Rounded shape language: soft, friendly, large radii on containers.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),   // chips, tags, thumbnails in rows
    medium = RoundedCornerShape(16.dp),  // text fields, small cards
    large = RoundedCornerShape(20.dp),   // media thumbnail
    extraLarge = RoundedCornerShape(24.dp), // cards, groups, sheets
)
val PillShape = RoundedCornerShape(50)
