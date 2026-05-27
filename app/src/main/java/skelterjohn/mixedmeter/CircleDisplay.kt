package skelterjohn.mixedmeter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val CircleDisplaySize = 200.dp

const val BpmDialMinBpm = 30f
const val BpmDialMaxBpm = 220f
const val BpmDialStartAngle = 120f
const val BpmDialSweepAngle = 300f

/** Compose degrees: 30° clockwise from down — min BPM label and dial start. */
const val BpmDialMinLabelAngle = 120f

/** Compose degrees: 30° counterclockwise from down — max BPM label and dial end. */
const val BpmDialMaxLabelAngle = 60f

const val BpmDialLabelScale = 1.1f

const val BpmDialRangeTickStartScale = 0.9f

const val BpmDialRangeTickEndScale = 1f

const val PercentDialMin = 25
const val PercentDialMax = 200
const val PercentDialMid = 100

/** ± this many points around [PercentDialMid] resolve to exactly 100%. */
const val PercentDialSnapToMidBuffer = 1

/** Compose degrees: straight up — [PercentDialMid] on the sequence percent dial. */
const val PercentDialStraightUpAngle = 270f

private const val PercentDialLowSweep =
    PercentDialStraightUpAngle - BpmDialStartAngle

private const val PercentDialHighSweep = 150f

/** Matches [drawLine] end inset in [CircleDisplay] canvas (`y = -2.dp`). */
private val BpmDialLineEndInset = 2.dp

private val BpmDialRangeTickStroke = 3.dp

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

fun resolvedBpm(bpm: Float): Int =
    bpm.roundToInt()

fun resolvedSequencePercent(percent: Int): Int {
    val clamped = percent.coerceIn(PercentDialMin, PercentDialMax)
    val low = PercentDialMid - PercentDialSnapToMidBuffer
    val high = PercentDialMid + PercentDialSnapToMidBuffer
    return if (clamped in low..high) PercentDialMid else clamped
}

fun resolvedSequencePercent(percent: Float): Int =
    resolvedSequencePercent(percent.roundToInt())

fun percentToDialAngle(percent: Int): Float {
    val p = resolvedSequencePercent(percent)
    return if (p <= PercentDialMid) {
        val ratio = (p - PercentDialMin).toFloat() / (PercentDialMid - PercentDialMin).toFloat()
        BpmDialStartAngle + ratio * PercentDialLowSweep
    } else {
        val ratio = (p - PercentDialMid).toFloat() / (PercentDialMax - PercentDialMid).toFloat()
        var angle = PercentDialStraightUpAngle + ratio * PercentDialHighSweep
        if (angle >= 360f) angle -= 360f
        angle
    }
}

fun percentChangeForAngleDelta(angleDeltaDegrees: Float, currentPercent: Int): Float {
    val degreesPerPercent = when {
        currentPercent < PercentDialMid ||
            (currentPercent == PercentDialMid && angleDeltaDegrees < 0f) ->
            PercentDialLowSweep / (PercentDialMid - PercentDialMin).toFloat()
        else ->
            PercentDialHighSweep / (PercentDialMax - PercentDialMid).toFloat()
    }
    return angleDeltaDegrees / degreesPerPercent
}

/** Compose degrees: straight down — center of the bottom half label. */
const val CircleBottomHalfLabelAngle = 90f

/** Radial distance from circle center as a fraction of half the circle diameter. */
const val CircleBottomHalfLabelRadialScale = 0.45f

/** Label center in circle-local px: lower semicircle along [CircleBottomHalfLabelAngle]. */
fun circleBottomHalfLabelOffsetPx(circleSizePx: Float): Offset {
    val centerPx = circleSizePx / 2f
    val labelRadiusPx = CircleBottomHalfLabelRadialScale * centerPx
    val rad = Math.toRadians(CircleBottomHalfLabelAngle.toDouble())
    return Offset(
        centerPx + labelRadiusPx * cos(rad).toFloat(),
        centerPx + labelRadiusPx * sin(rad).toFloat(),
    )
}

/** Label center in circle-local px: radial from canvas center at [angleDegrees], 1.1× the dial line length. */
fun bpmDialRangeLabelOffsetPx(
    circleSizePx: Float,
    angleDegrees: Float,
    lineEndGapPx: Float,
): Offset {
    val centerPx = circleSizePx / 2f
    val lineLengthPx = centerPx + lineEndGapPx
    val labelRadiusPx = BpmDialLabelScale * lineLengthPx
    val rad = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        centerPx + labelRadiusPx * cos(rad).toFloat(),
        centerPx + labelRadiusPx * sin(rad).toFloat(),
    )
}

private fun DrawScope.drawBpmDialRangeTick(
    angleDegrees: Float,
    lineLengthPx: Float,
    strokeWidthPx: Float,
) {
    val rad = Math.toRadians(angleDegrees.toDouble())
    val dirX = cos(rad).toFloat()
    val dirY = sin(rad).toFloat()
    val startR = BpmDialRangeTickStartScale * lineLengthPx
    val endR = BpmDialRangeTickEndScale * lineLengthPx
    drawLine(
        color = Color.White,
        start = Offset(center.x + startR * dirX, center.y + startR * dirY),
        end = Offset(center.x + endR * dirX, center.y + endR * dirY),
        strokeWidth = strokeWidthPx,
    )
}

private fun Modifier.centeredAt(positionPx: Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, 0) {
        placeable.place(
            (positionPx.x - placeable.width / 2f).roundToInt(),
            (positionPx.y - placeable.height / 2f).roundToInt(),
        )
    }
}

@Composable
private fun CircleOverlayLabel(
    text: String,
    positionPx: Offset,
    modifier: Modifier = Modifier,
) {
    val theme = currentAppTheme()
    Text(
        text = text,
        color = theme.text,
        fontSize = 11.sp,
        modifier = modifier.centeredAt(positionPx),
    )
}

@Composable
fun CircleDisplay(
    bpm: Float,
    isOn: Boolean,
    beatProgress: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showBpmDial: Boolean = true,
    showBpmRangeLabels: Boolean = false,
    showDialRangeTicks: Boolean = false,
    bottomHalfLabel: String? = null,
    dialAngleDegrees: Float? = null,
) {
    val currentAngle = dialAngleDegrees ?: bpmToDialAngle(bpm)
    val density = LocalDensity.current
    val lineEndGapPx = with(density) { BpmDialLineEndInset.toPx() }
    val clickInteractionSource = remember { MutableInteractionSource() }

    // Shrink uniformly when horizontal space is tight (e.g. sequence bottom row); never stretch.
    BoxWithConstraints(modifier = modifier) {
        val side: Dp = minOf(CircleDisplaySize, maxWidth, maxHeight)
        val circleSizePx = with(density) { side.toPx() }

        Box(
            modifier = Modifier
                .size(side)
                .clickable(
                    onClick = onToggle,
                    indication = null,
                    interactionSource = clickInteractionSource,
                ),
        ) {
        if (showBpmDial && showBpmRangeLabels) {
            CircleOverlayLabel(
                text = BpmDialMinBpm.toInt().toString(),
                positionPx = bpmDialRangeLabelOffsetPx(
                    circleSizePx,
                    BpmDialMinLabelAngle,
                    lineEndGapPx,
                ),
            )
            CircleOverlayLabel(
                text = BpmDialMaxBpm.toInt().toString(),
                positionPx = bpmDialRangeLabelOffsetPx(
                    circleSizePx,
                    BpmDialMaxLabelAngle,
                    lineEndGapPx,
                ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 2.dp, color = Color.White, shape = CircleShape)
                    .padding(2.dp)
                    .border(width = 8.dp, color = Color.Black, shape = CircleShape),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val diameter = minOf(size.width, size.height)
                val innerRadius = diameter / 2f - 12.dp.toPx()
                val lineEndY = with(density) { -BpmDialLineEndInset.toPx() }

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
                        size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
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
                        topLeft = Offset(
                            (size.width - diameter) / 2f + 12.dp.toPx(),
                            (size.height - diameter) / 2f + 12.dp.toPx(),
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            diameter - 24.dp.toPx(),
                            diameter - 24.dp.toPx(),
                        ),
                    )
                    rotate(degrees = currentAngle - 270f) {
                        drawLine(
                            color = Color.White,
                            start = center,
                            end = Offset(x = center.x, y = lineEndY),
                            strokeWidth = 8.dp.toPx(),
                        )
                    }
                    if (showDialRangeTicks || showBpmRangeLabels) {
                        val lineLengthPx = center.x + BpmDialLineEndInset.toPx()
                        val tickStrokePx = BpmDialRangeTickStroke.toPx()
                        drawBpmDialRangeTick(BpmDialMinLabelAngle, lineLengthPx, tickStrokePx)
                        drawBpmDialRangeTick(BpmDialMaxLabelAngle, lineLengthPx, tickStrokePx)
                    }
                }
            }
        }
        bottomHalfLabel?.let { label ->
            CircleOverlayLabel(
                text = label,
                positionPx = circleBottomHalfLabelOffsetPx(circleSizePx),
            )
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
