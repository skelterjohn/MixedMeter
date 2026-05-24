package skelterjohn.mixedmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var loopEnabled by remember { mutableStateOf(false) }
    var isOn by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableFloatStateOf(0f) }
    var activeItemIndex by remember { mutableIntStateOf(0) }
    var repeatsRemaining by remember { mutableIntStateOf(0) }
    var playbackGeneration by remember { mutableIntStateOf(0) }
    var prevCyclePosition by remember { mutableFloatStateOf(0f) }
    val loopPlayerHolder = remember { mutableStateOf<LoopPlayerSlot?>(null) }

    val beatToneSetting by remember {
        context.dataStore.data.map { preferences -> preferences[TONE_KEY] ?: "bip" }
    }.collectAsState(initial = "bip")

    val leadToneSetting by remember {
        context.dataStore.data.map { preferences -> preferences[LEAD_TONE_KEY] ?: "bip" }
    }.collectAsState(initial = "bip")

    val activeSchedule by remember {
        derivedStateOf {
            sequenceItems.getOrNull(activeItemIndex)?.metronomeSchedule()
        }
    }

    val displayBpm by remember {
        derivedStateOf {
            sequenceItems.getOrNull(activeItemIndex)?.displayBpm()
                ?: sequenceItems.firstOrNull()?.displayBpm()
                ?: 120f
        }
    }

    val beatProgress by remember {
        derivedStateOf {
            val schedule = activeSchedule ?: return@derivedStateOf 0f
            beatBoxProgress(isOn, playbackPosition, schedule)
        }
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val reordered = sequenceItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        scope.launch {
            context.setSequenceItems(reordered)
        }
    }

    fun stopPlayback() {
        loopPlayerHolder.value?.player?.stop()
        loopPlayerHolder.value?.player?.release()
        loopPlayerHolder.value = null
        playbackPosition = 0f
        prevCyclePosition = 0f
        isOn = false
    }

    fun beginPlaybackFromStart() {
        if (sequenceItems.isEmpty()) return
        val index = activeItemIndex.coerceIn(sequenceItems.indices)
        activeItemIndex = index
        repeatsRemaining = sequenceItems[index].repeatCount
        playbackGeneration++
        playbackPosition = 0f
        prevCyclePosition = 0f
        isOn = true
    }

    val togglePlayback = {
        if (isOn) {
            stopPlayback()
        } else {
            beginPlaybackFromStart()
        }
    }

    LaunchedEffect(sequenceItems) {
        if (sequenceItems.isEmpty()) {
            stopPlayback()
            return@LaunchedEffect
        }
        if (activeItemIndex >= sequenceItems.size) {
            activeItemIndex = 0
            repeatsRemaining = sequenceItems.first().repeatCount
            if (isOn) playbackGeneration++
        }
    }

    LaunchedEffect(isOn, activeItemIndex, playbackGeneration, beatToneSetting, leadToneSetting) {
        if (!isOn) {
            loopPlayerHolder.value?.player?.stop()
            loopPlayerHolder.value?.player?.release()
            loopPlayerHolder.value = null
            return@LaunchedEffect
        }

        val item = sequenceItems.getOrNull(activeItemIndex) ?: run {
            stopPlayback()
            return@LaunchedEffect
        }

        val schedule = item.metronomeSchedule()
        val oldPlayer = loopPlayerHolder.value?.player
        val newPlayer = withContext(Dispatchers.Default) {
            val loop = MetronomeLoopRenderer.render(
                schedule = schedule,
                useBeepBeatTone = beatToneSetting == "beep",
                useBeepLeadTone = leadToneSetting == "beep",
            )
            MetronomeLoopPlayer.create(context, loop)
        }
        oldPlayer?.release()
        loopPlayerHolder.value = LoopPlayerSlot(newPlayer, schedule)
        playbackPosition = 0f
        prevCyclePosition = 0f
        newPlayer.start()
    }

    LaunchedEffect(isOn) {
        if (!isOn) return@LaunchedEffect

        var attempts = 0
        while (loopPlayerHolder.value == null && isActive && attempts < 500) {
            delay(10)
            attempts++
        }

        while (isActive && isOn) {
            withFrameNanos {
                val slot = loopPlayerHolder.value ?: return@withFrameNanos
                val player = slot.player
                if (!player.isPlaying()) return@withFrameNanos

                val position = player.cyclePositionSeconds()
                playbackPosition = position

                val cycleDuration = loopCycleSeconds(slot.schedule)
                if (cycleDuration <= 0f) return@withFrameNanos

                val wrappedPrev = prevCyclePosition % cycleDuration
                val wrappedNow = position % cycleDuration
                val completedCycle = wrappedPrev > cycleDuration * 0.5f && wrappedNow < cycleDuration * 0.15f
                prevCyclePosition = position

                if (!completedCycle) return@withFrameNanos

                repeatsRemaining--
                if (repeatsRemaining > 0) return@withFrameNanos

                val nextIndex = activeItemIndex + 1
                if (nextIndex >= sequenceItems.size) {
                    if (loopEnabled) {
                        activeItemIndex = 0
                        repeatsRemaining = sequenceItems.first().repeatCount
                        playbackGeneration++
                    } else {
                        stopPlayback()
                    }
                    return@withFrameNanos
                }

                activeItemIndex = nextIndex
                repeatsRemaining = sequenceItems[nextIndex].repeatCount
                playbackGeneration++
            }
        }
    }

    val listBottomPadding = CircleDisplaySize + BottomNavEdgePadding * 2

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
                bottom = listBottomPadding,
            ),
        ) {
            itemsIndexed(sequenceItems, key = { _, item -> item.id }) { index, item ->
                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                    SequenceItemRow(
                        item = item,
                        isDragging = isDragging,
                        isActive = index == activeItemIndex,
                        onSelect = {
                            activeItemIndex = index
                            if (isOn) {
                                repeatsRemaining = item.repeatCount
                                playbackGeneration++
                            }
                        },
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
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = loopEnabled,
                        onCheckedChange = { loopEnabled = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Black,
                            uncheckedColor = Color.Black,
                            checkmarkColor = Color.White,
                        ),
                    )
                    Text(
                        text = "loop",
                        color = Color.Black,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(BottomNavEdgePadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            BottomNavIconButton(onClick = {
                stopPlayback()
                onBack()
            }) {
                ArrowDropDownNavIcon()
            }
            CircleDisplay(
                bpm = displayBpm,
                isOn = isOn,
                beatProgress = beatProgress,
                onToggle = togglePlayback,
            )
        }
    }
}
