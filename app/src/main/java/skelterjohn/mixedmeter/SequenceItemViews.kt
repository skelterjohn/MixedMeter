package skelterjohn.mixedmeter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
private val SequenceEmbossSurfaceDragging = Color(0xFFA8A8A8)
private val SequenceEmbossHighlight = Color(0xFFDADADA)
private val SequenceEmbossShadow = Color(0xFF454545)

@Composable
private fun EmbossedSequenceItemBackground(
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surfaceColor = if (isDragging) SequenceEmbossSurfaceDragging else SequenceEmbossSurface
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
                text = "@${item.bpm.toInt()}",
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
                    text = " @${item.bpm.toInt()}",
                    style = textStyle,
                )
            }
        }
    }
}

@Composable
fun SequenceItemRow(
    item: SequenceItem,
    onDelete: () -> Unit,
    rowDragModifier: Modifier,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(rowDragModifier),
    ) {
        EmbossedSequenceItemBackground(isDragging = isDragging) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                Box(modifier = Modifier.weight(1f)) {
                    SequenceItemLabel(item = item)
                }
            }
        }
    }
}
