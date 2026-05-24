package skelterjohn.mixedmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme

class SequenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWithSlideDown()
                }
            },
        )
        setContent {
            MixedMeterTheme {
                SequenceScreen(onBack = { finishWithSlideDown() })
            }
        }
    }
}

@Composable
private fun SequenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sequenceItems by context.sequenceItemsFlow().collectAsState(initial = emptyList())
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val reordered = sequenceItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        scope.launch {
            context.setSequenceItems(reordered)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = PaddingValues(
                top = statusBarTop + 32.dp,
                bottom = BottomNavButtonSize + BottomNavEdgePadding * 2,
            ),
        ) {
            items(sequenceItems, key = { it.id }) { item ->
                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                    SequenceItemRow(
                        item = item,
                        isDragging = isDragging,
                        onDelete = {
                            scope.launch {
                                context.removeSequenceItemById(item.id)
                            }
                        },
                        onRepeatCountChange = { repeatCount ->
                            scope.launch {
                                context.updateSequenceItemRepeatCount(item.id, repeatCount)
                            }
                        },
                        rowDragModifier = Modifier.draggableHandle(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(BottomNavEdgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavIconButton(onClick = onBack) {
                ArrowDropDownNavIcon()
            }
        }
    }
}
