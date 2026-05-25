package skelterjohn.mixedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SequenceEmbossSurface = Color(0xFF969696)
private val SequenceEmbossSurfaceActive = Color(0xFFB8B8B8)
private val SequenceRepeatCountButtonSurface = Color(0xFFAEAEAE)
private val SequenceEmbossSurfaceDragging = Color(0xFFA8A8A8)
private val SequenceEmbossHighlight = Color(0xFFDADADA)
private val SequenceEmbossShadow = Color(0xFF454545)

@Composable
private fun EmbossedSequenceItemBackground(
    isDragging: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surfaceColor = when {
        isDragging -> SequenceEmbossSurfaceDragging
        isActive -> SequenceEmbossSurfaceActive
        else -> SequenceEmbossSurface
    }
    val bevelWidth = 3.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDragging) {
                    Modifier.shadow(4.dp)
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val bevel = bevelWidth.toPx()
                drawRect(color = surfaceColor)
                drawLine(
                    color = SequenceEmbossHighlight,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = bevel,
                )
                drawLine(
                    color = SequenceEmbossHighlight,
                    start = Offset.Zero,
                    end = Offset(0f, size.height),
                    strokeWidth = bevel,
                )
                drawLine(
                    color = SequenceEmbossShadow,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = bevel,
                )
                drawLine(
                    color = SequenceEmbossShadow,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = bevel,
                )
            },
    ) {
        content()
    }
}

@Composable
private fun StackedTimeSignature(
    timeSignature: TimeSignature,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val stackedStyle = textStyle.copy(
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-4).dp),
    ) {
        Text(
            text = timeSignature.numerator.toString(),
            style = stackedStyle,
        )
        Text(
            text = if (timeSignature.denominator == 0) {
                "4"
            } else {
                timeSignature.denominator.toString()
            },
            style = stackedStyle,
        )
    }
}

@Composable
fun SequenceItemLabel(
    item: SequenceItem,
    tempoPercent: Float,
    modifier: Modifier = Modifier,
) {
    val textStyle = TextStyle(
        color = Color.Black,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
    )
    when (item) {
        is SequenceItem.PlainBpm -> {
            Text(
                text = "@${item.displayBpmAtPercent(tempoPercent)}",
                style = textStyle,
                modifier = modifier,
            )
        }

        is SequenceItem.MeterPattern -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                item.timeSignatures.forEachIndexed { index, timeSignature ->
                    if (index > 0) {
                        Text(text = " + ", style = textStyle)
                    }
                    StackedTimeSignature(
                        timeSignature = timeSignature,
                        textStyle = textStyle,
                    )
                }
                Text(
                    text = " ${item.displayBpmAtPercent(tempoPercent)} = ${item.selectedNote}",
                    style = textStyle,
                )
            }
        }
    }
}

@Composable
fun SequenceRepeatCountSelector(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Text(
        text = count.coerceAtLeast(1).toString(),
        color = Color.Black,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clickable { showPicker = true }
            .background(SequenceRepeatCountButtonSurface, RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
    if (showPicker) {
        SequenceRepeatCountPickerDialog(
            initialValue = count.coerceAtLeast(1),
            onDismiss = { showPicker = false },
            onConfirm = { newCount ->
                onCountChange(newCount.coerceAtLeast(1))
                showPicker = false
            },
        )
    }
}

@Composable
private fun SequenceBeatBoxGrid(
    count: Int,
    activeRepeatIndex: Int?,
    onRepeatClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val boxCount = count.coerceAtLeast(1)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(boxCount) { index ->
            val isActiveRepeat = activeRepeatIndex != null && index == activeRepeatIndex
            Box(
                modifier = Modifier
                    .size(SequenceBeatBoxSize)
                    .background(if (isActiveRepeat) Color.White else Color.Black)
                    .border(1.dp, Color.White)
                    .then(
                        if (onRepeatClick != null) {
                            Modifier.clickable { onRepeatClick(index) }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

private val SequenceBeatBoxSize = 20.dp

@Composable
fun SequenceItemRow(
    item: SequenceItem,
    tempoPercent: Float,
    onDelete: () -> Unit,
    onRepeatCountChange: (Int) -> Unit,
    onSelect: () -> Unit,
    onRepeatClick: ((Int) -> Unit)?,
    rowDragModifier: Modifier,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    isActive: Boolean = false,
    activeRepeatIndex: Int? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(rowDragModifier),
    ) {
        EmbossedSequenceItemBackground(isDragging = isDragging, isActive = isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelect)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove from sequence",
                            tint = Color.Black,
                        )
                    }
                    SequenceRepeatCountSelector(
                        count = item.repeatCount,
                        onCountChange = onRepeatCountChange,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                ) {
                    SequenceItemLabel(item = item, tempoPercent = tempoPercent)
                    SequenceBeatBoxGrid(
                        count = item.repeatCount,
                        activeRepeatIndex = activeRepeatIndex,
                        onRepeatClick = onRepeatClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
