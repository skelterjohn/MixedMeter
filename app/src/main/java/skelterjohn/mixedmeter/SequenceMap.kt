package skelterjohn.mixedmeter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SequenceItemMap(
    itemCount: Int,
    activeIndex: Int,
    onItemScroll: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (itemCount <= 0) return

    val edgePaddingPx = 12.dp
    val lineWidthPx = 2.dp
    val dashHalfWidthPx = 10.dp
    val activeDashHalfWidthPx = 14.dp
    val dashStrokePx = 3.dp
    val activeDashStrokePx = 5.dp
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
            .pointerInput(itemCount, activeIndex) {
                detectTapGestures { offset ->
                    val index = dashIndexAtY(
                        y = offset.y,
                        height = size.height.toFloat(),
                        itemCount = itemCount,
                        edgePadding = edgePaddingPx.toPx(),
                    )
                    if (index in 0 until itemCount) {
                        onItemScroll(index)
                    }
                }
            },
    ) {
        val lineX = size.width / 2f
        val top = edgePaddingPx.toPx()
        val bottom = size.height - edgePaddingPx.toPx()
        val span = (bottom - top).coerceAtLeast(1f)

        drawLine(
            color = Color.Black,
            start = Offset(lineX, top),
            end = Offset(lineX, bottom),
            strokeWidth = lineWidthPx.toPx(),
            cap = StrokeCap.Round,
        )

        for (index in 0 until itemCount) {
            val y = top + span * (index + 0.5f) / itemCount
            val isActive = index == activeIndex
            drawLine(
                color = Color.Black,
                start = Offset(lineX - if (isActive) activeDashHalfWidthPx.toPx() else dashHalfWidthPx.toPx(), y),
                end = Offset(lineX + if (isActive) activeDashHalfWidthPx.toPx() else dashHalfWidthPx.toPx(), y),
                strokeWidth = if (isActive) activeDashStrokePx.toPx() else dashStrokePx.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun dashIndexAtY(
    y: Float,
    height: Float,
    itemCount: Int,
    edgePadding: Float,
): Int {
    if (itemCount <= 0 || height <= edgePadding * 2f) return -1
    val top = edgePadding
    val span = height - edgePadding * 2f
    val slot = ((y - top) / span * itemCount).toInt().coerceIn(0, itemCount - 1)
    val slotCenter = top + span * (slot + 0.5f) / itemCount
    val halfSlot = span / itemCount / 2f + edgePadding * 0.25f
    return if (kotlin.math.abs(y - slotCenter) <= halfSlot) slot else -1
}
