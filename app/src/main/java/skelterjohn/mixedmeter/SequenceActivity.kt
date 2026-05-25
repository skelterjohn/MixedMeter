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
    var sequencePosition by remember { mutableFloatStateOf(0f) }
    var activeItemIndex by remember { mutableIntStateOf(0) }
    var activeRepeatIndex by remember { mutableStateOf<Int?>(null) }
    var playbackGeneration by remember { mutableIntStateOf(0) }
    var playbackStartSeconds by remember { mutableFloatStateOf(0f) }
    var prerenderToken by remember { mutableIntStateOf(0) }
    var sequencePrerender by remember { mutableStateOf<SequencePrerender?>(null) }
    val playbackHolder = remember { mutableStateOf<SequencePlaybackSlot?>(null) }

    val beatToneSetting by remember {
        context.dataStore.data.map { preferences -> preferences[TONE_KEY] ?: "bip" }
    }.collectAsState(initial = "bip")

    val leadToneSetting by remember {
        context.dataStore.data.map { preferences -> preferences[LEAD_TONE_KEY] ?: "bip" }
    }.collectAsState(initial = "bip")

    val activeSegment by remember {
        derivedStateOf {
            val prerender = sequencePrerender ?: return@derivedStateOf null
            segmentAt(sequencePosition, prerender.segments)
        }
    }

    val displayBpm by remember {
        derivedStateOf {
            activeSegment?.displayBpm
                ?: sequenceItems.getOrNull(activeItemIndex)?.displayBpm()
                ?: sequenceItems.firstOrNull()?.displayBpm()
                ?: 120f
        }
    }

    val beatProgress by remember {
        derivedStateOf {
            val segment = activeSegment ?: return@derivedStateOf 0f
            val positionInMeasure = sequencePosition - segment.startTimeSeconds
            beatBoxProgress(isOn, positionInMeasure, segment.schedule)
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

    fun pausePlayback() {
        playbackHolder.value?.player?.stop()
        playbackHolder.value?.player?.release()
        playbackHolder.value = null
        isOn = false
    }

    fun resolveStartSegment(prerender: SequencePrerender): SequenceSegment? {
        val itemIndex = activeItemIndex.coerceIn(sequenceItems.indices)
        val repeat = activeRepeatIndex
        return if (repeat != null) {
            segmentForRepeat(itemIndex, repeat, prerender.segments)
        } else {
            null
        }
            ?: prerender.segments.firstOrNull { it.itemIndex == itemIndex }
            ?: prerender.segments.firstOrNull()
    }

    fun beginPlayback(prerender: SequencePrerender) {
        if (sequenceItems.isEmpty()) return
        val startSegment = resolveStartSegment(prerender) ?: return
        activeItemIndex = startSegment.itemIndex
        activeRepeatIndex = startSegment.repeatIndex
        playbackStartSeconds = startSegment.startTimeSeconds
        playbackGeneration++
        sequencePosition = playbackStartSeconds
        isOn = true
    }

    val togglePlayback: () -> Unit = {
        if (isOn) {
            pausePlayback()
        } else if (sequenceItems.isNotEmpty()) {
            scope.launch {
                var prerender = sequencePrerender
                if (prerender == null) {
                    prerender = withContext(Dispatchers.Default) {
                        renderSequence(
                            items = sequenceItems,
                            useBeepBeatTone = beatToneSetting == "beep",
                            useBeepLeadTone = leadToneSetting == "beep",
                        )
                    }
                    sequencePrerender = prerender
                }
                prerender?.let { built ->
                    sequencePrerender = built
                    beginPlayback(built)
                }
            }
        }
    }

    LaunchedEffect(sequenceItems, beatToneSetting, leadToneSetting) {
        sequencePrerender = null
        prerenderToken++
        if (isOn) {
            pausePlayback()
        }
    }

    LaunchedEffect(prerenderToken, sequenceItems, beatToneSetting, leadToneSetting) {
        if (sequenceItems.isEmpty()) {
            sequencePrerender = null
            return@LaunchedEffect
        }
        sequencePrerender = withContext(Dispatchers.Default) {
            renderSequence(
                items = sequenceItems,
                useBeepBeatTone = beatToneSetting == "beep",
                useBeepLeadTone = leadToneSetting == "beep",
            )
        }
    }

    LaunchedEffect(sequenceItems) {
        if (sequenceItems.isEmpty()) {
            pausePlayback()
            activeRepeatIndex = null
            return@LaunchedEffect
        }
        if (activeItemIndex >= sequenceItems.size) {
            activeItemIndex = 0
            activeRepeatIndex = null
        } else {
            val item = sequenceItems[activeItemIndex]
            val repeat = activeRepeatIndex
            if (repeat != null && repeat >= item.repeatCount) {
                activeRepeatIndex = null
            }
        }
    }

    LaunchedEffect(
        isOn,
        playbackGeneration,
        beatToneSetting,
        leadToneSetting,
        loopEnabled,
    ) {
        if (!isOn) {
            playbackHolder.value?.player?.stop()
            playbackHolder.value?.player?.release()
            playbackHolder.value = null
            return@LaunchedEffect
        }

        val prerender = sequencePrerender ?: return@LaunchedEffect
        val oldPlayer = playbackHolder.value?.player
        val loop = MetronomeLoopRenderer.MetronomeLoop(
            samples = prerender.samples,
            cycleFrameCount = prerender.totalFrameCount,
            cycleDurationSeconds = prerender.durationSeconds,
        )
        val newPlayer = withContext(Dispatchers.Default) {
            if (loopEnabled) {
                MetronomeLoopPlayer.create(context, loop)
            } else {
                MetronomeLoopPlayer.createOneShot(context, loop)
            }
        }
        oldPlayer?.release()
        playbackHolder.value = SequencePlaybackSlot(newPlayer, prerender)
        val startAt = playbackStartSeconds.coerceIn(0f, prerender.durationSeconds)
        sequencePosition = startAt
        newPlayer.start(startAt)
    }

    LaunchedEffect(isOn, loopEnabled) {
        if (!isOn) return@LaunchedEffect

        var attempts = 0
        while (playbackHolder.value == null && isActive && attempts < 500) {
            delay(10)
            attempts++
        }

        while (isActive && isOn) {
            withFrameNanos {
                val slot = playbackHolder.value ?: return@withFrameNanos
                val player = slot.player
                val prerender = slot.prerender

                val position = player.cyclePositionSeconds()
                sequencePosition = position

                segmentAt(position, prerender.segments)?.let { segment ->
                    activeItemIndex = segment.itemIndex
                    activeRepeatIndex = segment.repeatIndex
                }

                if (loopEnabled) return@withFrameNanos

                val finished = position >= prerender.durationSeconds - 0.02f ||
                    (!player.isPlaying() && position > 0.05f)
                if (finished) {
                    pausePlayback()
                }
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
                val rowActiveRepeatIndex = if (index == activeItemIndex) activeRepeatIndex else null
                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                    SequenceItemRow(
                        item = item,
                        isDragging = isDragging,
                        isActive = index == activeItemIndex,
                        activeRepeatIndex = rowActiveRepeatIndex,
                        onSelect = {
                            if (index != activeItemIndex) {
                                activeRepeatIndex = null
                            }
                            activeItemIndex = index
                            if (isOn) {
                                val prerender = sequencePrerender
                                val segment = prerender?.let { resolveStartSegment(it) }
                                if (prerender != null && segment != null) {
                                    playbackHolder.value?.player?.seekToSeconds(segment.startTimeSeconds)
                                    sequencePosition = segment.startTimeSeconds
                                    activeRepeatIndex = segment.repeatIndex
                                    playbackStartSeconds = segment.startTimeSeconds
                                }
                            }
                        },
                        onRepeatClick = if (!isOn) {
                            { repeatIndex ->
                                activeItemIndex = index
                                activeRepeatIndex = repeatIndex
                                sequencePrerender?.let { prerender ->
                                    segmentForRepeat(index, repeatIndex, prerender.segments)
                                        ?.let { segment ->
                                            playbackStartSeconds = segment.startTimeSeconds
                                            sequencePosition = segment.startTimeSeconds
                                        }
                                }
                            }
                        } else {
                            null
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
                pausePlayback()
                activeRepeatIndex = null
                sequencePosition = 0f
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
