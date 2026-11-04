package xyz.totemai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import xyz.totemai.app.ui.theme.Lime

@Composable
fun TotemLogo(
    modifier: Modifier = Modifier,
    ring: Color = Lime,
    mark: Color = Color.White
) {
    Canvas(modifier) {
        val width = size.width
        val stroke = width * 0.055f
        val inset = stroke / 2f

        drawRoundRect(
            color = ring,
            topLeft = Offset(inset, inset),
            size = Size(width - stroke, width - stroke),
            cornerRadius = CornerRadius(width * 0.22f),
            style = Stroke(stroke)
        )
        drawRect(
            color = mark,
            topLeft = Offset(width * 0.300f, width * 0.280f),
            size = Size(width * 0.400f, width * 0.110f)
        )
        drawRect(
            color = mark,
            topLeft = Offset(width * 0.445f, width * 0.390f),
            size = Size(width * 0.110f, width * 0.320f)
        )
        drawRect(
            color = ring,
            topLeft = Offset(width * 0.660f, width * 0.660f),
            size = Size(width * 0.110f, width * 0.110f)
        )
    }
}
