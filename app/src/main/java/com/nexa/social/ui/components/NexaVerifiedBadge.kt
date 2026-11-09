package com.nexa.social.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact Instagram-style verification seal: blue scalloped rosette with a white check.
 * The badge is decorative; verification state must come from the trusted Supabase owner flow.
 */
@Composable
fun NexaVerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val diameter = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outerRadius = diameter * 0.49f
        val innerRadius = diameter * 0.435f
        val count = 24
        val points = List(count) { index ->
            val angle = -PI / 2.0 + (2.0 * PI * index / count)
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            Offset(
                center.x + (cos(angle) * radius).toFloat(),
                center.y + (sin(angle) * radius).toFloat()
            )
        }
        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
        path.close()
        drawPath(path = path, color = Color(0xFF0095F6))

        val checkStrokeWidth = diameter * 0.105f
        drawLine(
            color = Color.White,
            start = Offset(center.x - diameter * 0.19f, center.y + diameter * 0.015f),
            end = Offset(center.x - diameter * 0.045f, center.y + diameter * 0.16f),
            strokeWidth = checkStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(center.x - diameter * 0.045f, center.y + diameter * 0.16f),
            end = Offset(center.x + diameter * 0.235f, center.y - diameter * 0.17f),
            strokeWidth = checkStroke.width,
            cap = StrokeCap.Round
        )
    }
}
