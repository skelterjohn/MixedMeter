package skelterjohn.mixedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun StackedTimeSignature(
    timeSignature: TimeSignature,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        Text(
            text = timeSignature.numerator.toString(),
            style = textStyle.copy(textAlign = TextAlign.Center),
        )
        Text(
            text = if (timeSignature.denominator == 0) {
                "4"
            } else {
                timeSignature.denominator.toString()
            },
            style = textStyle.copy(textAlign = TextAlign.Center),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(rowDragModifier)
            .shadow(if (isDragging) 6.dp else 0.dp, RoundedCornerShape(8.dp))
            .background(
                color = if (isDragging) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 8.dp),
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
