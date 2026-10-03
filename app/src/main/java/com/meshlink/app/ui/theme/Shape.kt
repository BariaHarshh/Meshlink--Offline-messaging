package com.meshlink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MeshLinkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

object MeshLinkRadius {
    val Card        = 24.dp
    val CardLarge   = 28.dp
    val Button      = 28.dp
    val Pill        = 50.dp
    val AvatarPill  = 50.dp
    val Bubble      = 20.dp
    val BubbleTail  = 6.dp
}

