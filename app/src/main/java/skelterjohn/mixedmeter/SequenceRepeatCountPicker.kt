package skelterjohn.mixedmeter

import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

private const val REPEAT_COUNT_MAX = 9999

@Composable
fun SequenceRepeatCountPickerDialog(
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selected by remember { mutableIntStateOf(initialValue.coerceAtLeast(1)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFFE0E0E0)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 1
                            maxValue = REPEAT_COUNT_MAX
                            value = selected
                            wrapSelectorWheel = false
                            setOnValueChangedListener { _, _, newValue ->
                                selected = newValue
                            }
                        }
                    },
                    update = { picker ->
                        picker.minValue = 1
                        picker.maxValue = REPEAT_COUNT_MAX
                        picker.value = selected.coerceIn(1, REPEAT_COUNT_MAX)
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Black)
                    }
                    TextButton(onClick = { onConfirm(selected.coerceAtLeast(1)) }) {
                        Text("OK", color = Color.Black)
                    }
                }
            }
        }
    }
}
