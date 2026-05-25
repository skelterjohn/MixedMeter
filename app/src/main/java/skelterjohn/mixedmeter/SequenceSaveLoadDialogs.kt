package skelterjohn.mixedmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val DialogSurfaceColor = Color(0xFFE0E0E0)
private val SequenceNameFieldSurface = Color(0xFFAEAEAE)

@Composable
fun SequenceNameField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showUntitled: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val displayValue = if (showUntitled) "" else name
    val placeholderAlpha = 0.45f

    BasicTextField(
        value = displayValue,
        onValueChange = { onNameChange(it.replace('\n', ' ').take(80)) },
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(Color.Black),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .background(SequenceNameFieldSurface, RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (displayValue.isEmpty()) {
                    Text(
                        text = "untitled",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black.copy(alpha = placeholderAlpha),
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
fun SequenceSaveDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current
    val canSave = name.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = DialogSurfaceColor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Save sequence",
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.replace('\n', ' ').take(80) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    ),
                    cursorBrush = SolidColor(Color.Black),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Black)
                    }
                    TextButton(
                        onClick = { onConfirm(name.trim()) },
                        enabled = canSave,
                    ) {
                        Text(
                            "Save",
                            color = if (canSave) Color.Black else Color.Black.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SequenceLoadDialog(
    savedSequences: List<SavedSequence>,
    onDismiss: () -> Unit,
    onSelect: (SavedSequence) -> Unit,
    onDelete: (SavedSequence) -> Unit,
) {
    val sorted = remember(savedSequences) {
        savedSequences.sortedBy { it.name.lowercase() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = DialogSurfaceColor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Load sequence",
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (sorted.isEmpty()) {
                    Text(
                        text = "No saved sequences",
                        color = Color.Black.copy(alpha = 0.6f),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(sorted, key = { it.id }) { saved ->
                            SavedSequenceListRow(
                                saved = saved,
                                onSelect = { onSelect(saved) },
                                onDelete = { onDelete(saved) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSequenceListRow(
    saved: SavedSequence,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete saved sequence",
                tint = Color.Black,
            )
        }
        Text(
            text = saved.name,
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelect)
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
