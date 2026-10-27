package com.eitan.airtracker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.eitan.airtracker.ui.theme.FindRed

/** Keeps an angle continuous so the arrow always turns the short way round. */
@Composable
private fun rememberContinuousAngle(target: Float?): Float {
    val holder = remember { floatArrayOf(target ?: 0f) }
    if (target != null) {
        val delta = ((target - holder[0]) % 360f + 540f) % 360f - 180f
        holder[0] += delta
    }
    return holder[0]
}

/**
 * Big Find-My-style arrow.
 * @param arrowAngle degrees clockwise from the top of the phone to the target, or null if unknown
 * @param northAngle degrees clockwise from the top of the phone to north, or null if unknown
 * @param pulsing draw a pulse ring (AirPods currently detected)
 */
@Composable
fun NavigationArrow(
    arrowAngle: Float?,
    northAngle: Float?,
    pulsing: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val ring = MaterialTheme.colorScheme.outline
    val tick = MaterialTheme.colorScheme.onSurfaceVariant

    val arrowTarget = rememberContinuousAngle(arrowAngle)
    val northTarget = rememberContinuousAngle(northAngle)
    val arrow by animateFloatAsState(
        arrowTarget,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "arrow",
    )
    val north by animateFloatAsState(
        northTarget,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "north",
    )
    val arrowAlpha by animateFloatAsState(if (arrowAngle == null) 0.2f else 1f, label = "alpha")
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "pulse",
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center

        if (pulsing) {
            drawCircle(
                color = secondary.copy(alpha = (1f - pulse) * 0.35f),
                radius = r * (0.55f + 0.45f * pulse),
                center = c,
            )
        }

        drawCircle(ring, radius = r * 0.97f, center = c, style = Stroke(width = 1.5.dp.toPx()))

        if (northAngle != null) {
            rotate(north, c) {
                for (i in 0 until 72) {
                    val len = when {
                        i % 18 == 0 -> 14.dp.toPx()
                        i % 6 == 0 -> 9.dp.toPx()
                        else -> 5.dp.toPx()
                    }
                    rotate(i * 5f, c) {
                        drawLine(
                            color = tick.copy(alpha = if (i % 6 == 0) 0.9f else 0.45f),
                            start = Offset(c.x, c.y - r * 0.93f),
                            end = Offset(c.x, c.y - r * 0.93f + len),
                            strokeWidth = (if (i % 18 == 0) 2.5.dp else 1.dp).toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                val n = Path().apply {
                    moveTo(c.x, c.y - r * 0.995f)
                    lineTo(c.x + 7.dp.toPx(), c.y - r * 0.995f + 12.dp.toPx())
                    lineTo(c.x - 7.dp.toPx(), c.y - r * 0.995f + 12.dp.toPx())
                    close()
                }
                drawPath(n, FindRed)
            }
        }

        rotate(arrow, c) {
            val top = c.y - r * 0.6f
            val bottom = c.y + r * 0.42f
            val path = Path().apply {
                moveTo(c.x, top)
                lineTo(c.x + r * 0.34f, bottom)
                lineTo(c.x, c.y + r * 0.2f)
                lineTo(c.x - r * 0.34f, bottom)
                close()
            }
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    listOf(primary, primary.copy(alpha = 0.65f)),
                    startY = top,
                    endY = bottom,
                ),
                alpha = arrowAlpha,
            )
        }
    }
}
