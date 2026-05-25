package skelterjohn.mixedmeter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme

val CircleDisplaySize = 200.dp

@Composable
fun CircleDisplay(
    bpm: Float,
    isOn: Boolean,
    beatProgress: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showBpmDial: Boolean = true,
) {
    val minBpm = 30f
    val maxBpm = 220f
    val startAngle = 120f
    val sweepAngle = 300f
    val ratio = ((bpm - minBpm) / (maxBpm - minBpm)).coerceIn(0f, 1f)
    val currentAngle = startAngle + ratio * sweepAngle

    Box(
        modifier = modifier
            .size(CircleDisplaySize)
            .clip(CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = 2.dp, color = Color.White, shape = CircleShape)
                .padding(2.dp)
                .border(width = 8.dp, color = Color.Black, shape = CircleShape),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val innerRadius = size.width / 2 - 12.dp.toPx()

            if (isOn) {
                drawCircle(
                    color = Color.White,
                    radius = innerRadius,
                    center = center,
                )
                drawArc(
                    color = Color.Gray,
                    startAngle = currentAngle,
                    sweepAngle = 360f * beatProgress,
                    useCenter = true,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                )
            } else {
                drawCircle(
                    color = Color.Gray,
                    radius = innerRadius,
                    center = center,
                )
            }

            if (showBpmDial) {
                drawArc(
                    color = Color.White.copy(alpha = 0.1f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx()),
                    topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                    size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx()),
                )
                rotate(degrees = currentAngle - 270f) {
                    drawLine(
                        color = Color.White,
                        start = center,
                        end = Offset(x = size.width / 2, y = -2.dp.toPx()),
                        strokeWidth = 8.dp.toPx(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CircleDisplayPreview() {
    MixedMeterTheme {
        CircleDisplay(bpm = 120f, isOn = false, beatProgress = 0f, onToggle = {})
    }
}
