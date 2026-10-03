package com.meshlink.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlink.app.ui.theme.PrimaryAccent
import com.meshlink.app.ui.theme.PrimaryText
import com.meshlink.app.ui.theme.WarmWhite

@Composable
fun MeshLinkLogo(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    animated: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val pulseAlpha by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue  = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // Calculate equilateral triangle centered in canvas
            val topNode   = Offset(w * 0.50f, h * 0.28f)
            val leftNode  = Offset(w * 0.30f, h * 0.68f)
            val rightNode = Offset(w * 0.70f, h * 0.68f)

            val strokeWidth = w * 0.048f
            val nodeRadius = w * 0.088f
            val innerRadius = nodeRadius * 0.52f

            // Connecting lines
            val lineColor = PrimaryAccent.copy(alpha = if (animated) pulseAlpha else 0.95f)
            drawLine(
                color = lineColor,
                start = topNode,
                end   = leftNode,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = lineColor,
                start = topNode,
                end   = rightNode,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = lineColor,
                start = leftNode,
                end   = rightNode,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Top Node (Charcoal)
            drawCircle(color = PrimaryText, radius = nodeRadius, center = topNode)
            drawCircle(color = WarmWhite, radius = innerRadius, center = topNode)

            // Bottom-Left Node (Charcoal)
            drawCircle(color = PrimaryText, radius = nodeRadius, center = leftNode)
            drawCircle(color = WarmWhite, radius = innerRadius, center = leftNode)

            // Bottom-Right Node (Dusty Rose Accent)
            drawCircle(color = PrimaryAccent, radius = nodeRadius, center = rightNode)
            drawCircle(color = WarmWhite, radius = innerRadius, center = rightNode)
        }
    }
}
