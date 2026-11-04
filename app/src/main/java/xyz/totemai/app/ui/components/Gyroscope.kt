package xyz.totemai.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import xyz.totemai.app.ui.theme.Lime
import xyz.totemai.app.ui.theme.Violet

@Composable
fun Gyroscope(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gyro")
    val tilt by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tilt"
    )
    val counter by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter"
    )

    Canvas(modifier) {
        val centerPoint = center
        val radius = size.minDimension / 2f
        val ring = 2.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Lime.copy(alpha = 0.16f), Color.Transparent),
                center = centerPoint,
                radius = radius
            ),
            radius = radius,
            center = centerPoint
        )

        rotate(tilt, centerPoint) {
            drawOval(
                color = Lime.copy(alpha = 0.55f),
                topLeft = Offset(centerPoint.x - radius * 0.96f, centerPoint.y - radius * 0.30f),
                size = Size(radius * 1.92f, radius * 0.60f),
                style = Stroke(ring)
            )
        }

        rotate(counter, centerPoint) {
            drawOval(
                color = Violet.copy(alpha = 0.45f),
                topLeft = Offset(centerPoint.x - radius * 0.34f, centerPoint.y - radius * 0.94f),
                size = Size(radius * 0.68f, radius * 1.88f),
                style = Stroke(ring)
            )
        }

        drawCircle(
            color = Lime.copy(alpha = 0.70f),
            radius = radius * 0.30f,
            center = centerPoint,
            style = Stroke(ring * 0.8f)
        )
    }
}
