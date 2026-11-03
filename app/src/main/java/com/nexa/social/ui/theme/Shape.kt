package com.nexa.social.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Central shape system for the Nexa identity. */
val NexaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object NexaRadius {
    val card = 20.dp
    val sheet = 28.dp
    val pill = 100.dp
    val chip = 14.dp
    val logo = 24.dp
}
