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
import kotlin.math.atan2

val CircleDisplaySize = 200.dp

const val BpmDialMinBpm = 30f
const val BpmDialMaxBpm = 220f
const val BpmDialStartAngle = 120f
const val BpmDialSweepAngle = 300f

fun bpmToDialAngle(bpm: Float): Float {
    val ratio = ((bpm - BpmDialMinBpm) / (BpmDialMaxBpm - BpmDialMinBpm)).coerceIn(0f, 1f)
    return BpmDialStartAngle + ratio * BpmDialSweepAngle
}

fun pointerAngleDegrees(center: Offset, position: Offset): Float {
    val dx = position.x - center.x
    val dy = position.y - center.y
    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (degrees < 0f) degrees += 360f
    return degrees
}

fun shortestAngleDelta(fromDegrees: Float, toDegrees: Float): Float {
    var delta = toDegrees - fromDegrees
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

fun bpmChangeForAngleDelta(angleDeltaDegrees: Float): Float {
    return angleDeltaDegrees / BpmDialSweepAngle * (BpmDialMaxBpm - BpmDialMinBpm)
}

@Composable
fun CircleDisplay(
    bpm: Float,
    isOn: Boolean,
    beatProgress: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showBpmDial: Boolean = true,
) {
    val currentAngle = bpmToDialAngle(bpm)

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
                    startAngle = BpmDialStartAngle,
                    sweepAngle = BpmDialSweepAngle,
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
