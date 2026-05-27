package skelterjohn.mixedmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private data class WorkspaceBaseline(
    val name: String,
    val itemsKey: String,
)

private suspend fun LazyListState.scrollSequenceItemToCenter(
    index: Int,
    fallbackItemHeightPx: Int,
) {
    var attempts = 0
    while (layoutInfo.viewportSize.height == 0 && attempts < 50) {
        delay(16)
        attempts++
    }
    val layoutInfo = layoutInfo
    val viewportHeight = layoutInfo.viewportSize.height
    if (viewportHeight <= 0) return
    val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size
        ?: fallbackItemHeightPx
    val scrollOffset = -((viewportHeight - itemHeight) / 2).coerceAtLeast(0)
    scrollToItem(index = index, scrollOffset = scrollOffset)
}

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
            val context = LocalContext.current
            val themeSetting by remember {
                context.dataStore.data
                    .map { preferences -> preferences[THEME_KEY] ?: DEFAULT_THEME }
            }.collectAsState(initial = DEFAULT_THEME)

            MixedMeterTheme(themeName = themeSetting) {
                SequenceScreen(onBack = { finishWithSlideDown() })
            }
        }
    }
}

@Composable
private fun SequenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = currentAppTheme()
    val scope = rememberCoroutineScope()
    val sequenceItems by context.sequenceItemsFlow().collectAsState(initial = emptyList())
    val savedSequences by context.savedSequencesFlow().collectAsState(initial = emptyList())
    val hasSavedSequence = savedSequences.isNotEmpty()
    val storedSequenceName by context.sequenceNameFlow().collectAsState(initial = "")
    var sequenceName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var workspaceBaseline by remember { mutableStateOf<WorkspaceBaseline?>(null) }
    var wasItemsDirty by remember { mutableStateOf(false) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()
    var loopEnabled by remember { mutableStateOf(false) }
    var isOn by remember { mutableStateOf(false) }
    var sequencePosition by remember { mutableFloatStateOf(0f) }
    var activeItemIndex by remember { mutableIntStateOf(0) }
    var activeRepeatIndex by remember { mutableStateOf<Int?>(null) }
    var playbackGeneration by remember { mutableIntStateOf(0) }
    var sequencePercent by remember { mutableFloatStateOf(PercentDialMid) }
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    var circleDragCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var circleRadiusPx by remember(density) {
        mutableFloatStateOf(with(density) { (CircleDisplaySize / 2).toPx() })
    }
    var playbackStartSeconds by remember { mutableFloatStateOf(0f) }
    var prerenderToken by remember { mutableIntStateOf(0) }
    var sequencePrerender by remember { mutableStateOf<SequencePrerender?>(null) }
    val playbackHolder = remember { mutableStateOf<SequencePlaybackSlot?>(null) }

    LaunchedEffect(storedSequenceName, sequenceItems) {
        if (workspaceBaseline == null) {
            workspaceBaseline = WorkspaceBaseline(
                name = storedSequenceName,
                itemsKey = sequenceItemsContentKey(sequenceItems),
            )
        }
    }

    LaunchedEffect(storedSequenceName) {
        if (!wasItemsDirty) {
            sequenceName = storedSequenceName
        }
    }

    val itemsDirty by remember {
        derivedStateOf {
            val baseline = workspaceBaseline ?: return@derivedStateOf false
            sequenceItemsContentKey(sequenceItems) != baseline.itemsKey
        }
    }

    val showUntitled by remember {
        derivedStateOf {
            itemsDirty &&
                sequenceName.isEmpty() &&
                workspaceBaseline?.name?.isNotEmpty() == true
        }
    }

    LaunchedEffect(itemsDirty, workspaceBaseline) {
        val baseline = workspaceBaseline ?: return@LaunchedEffect
        if (itemsDirty && !wasItemsDirty && baseline.name.isNotEmpty()) {
            sequenceName = ""
            context.setSequenceName("")
        } else if (!itemsDirty && sequenceName.isEmpty() && baseline.name.isNotEmpty()) {
            sequenceName = baseline.name
            context.setSequenceName(baseline.name)
        }
        wasItemsDirty = itemsDirty
    }

    LaunchedEffect(sequenceName, itemsDirty) {
        if (itemsDirty) return@LaunchedEffect
        if (sequenceName == storedSequenceName) return@LaunchedEffect
        delay(400)
        if (!itemsDirty && sequenceName != storedSequenceName) {
            context.setSequenceName(sequenceName)
            workspaceBaseline = workspaceBaseline?.copy(name = sequenceName)
        }
    }

    val beatToneSetting by remember {
        context.dataStore.data.map { preferences -> preferences[TONE_KEY] ?: DEFAULT_TONE }
    }.collectAsState(initial = DEFAULT_TONE)

    val leadToneSetting by remember {
        context.dataStore.data.map { preferences -> preferences[LEAD_TONE_KEY] ?: DEFAULT_TONE }
    }.collectAsState(initial = DEFAULT_TONE)

    val activeSegment by remember {
        derivedStateOf {
            val prerender = sequencePrerender ?: return@derivedStateOf null
            segmentAt(sequencePosition, prerender.segments)
        }
    }

    val displayBpm by remember {
        derivedStateOf {
            activeSegment?.displayBpm
                ?: sequenceItems.getOrNull(activeItemIndex)?.displayBpmAtPercent(sequencePercent)?.toFloat()
                ?: sequenceItems.firstOrNull()?.displayBpmAtPercent(sequencePercent)?.toFloat()
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

    fun pausePlayback() {
        playbackHolder.value?.player?.stop()
        playbackHolder.value?.player?.release()
        playbackHolder.value = null
        isOn = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                pausePlayback()
                activeRepeatIndex = null
                sequencePosition = 0f
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    fun seekToSegment(segment: SequenceSegment) {
        activeItemIndex = segment.itemIndex
        activeRepeatIndex = segment.repeatIndex
        playbackStartSeconds = segment.startTimeSeconds
        sequencePosition = segment.startTimeSeconds
        if (isOn) {
            playbackGeneration++
        }
    }

    fun beginPlayback(prerender: SequencePrerender) {
        if (sequenceItems.isEmpty()) return
        val startSegment = resolveStartSegment(prerender) ?: return
        seekToSegment(startSegment)
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
                            tempoPercent = sequencePercent,
                            beatTone = beatToneSetting,
                            leadTone = leadToneSetting,
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

    val currentSequencePercent = rememberUpdatedState(sequencePercent)
    val currentTogglePlayback = rememberUpdatedState(togglePlayback)

    LaunchedEffect(sequenceItems, beatToneSetting, leadToneSetting) {
        sequencePrerender = null
        prerenderToken++
        if (isOn) {
            pausePlayback()
        }
    }

    LaunchedEffect(prerenderToken, sequenceItems, beatToneSetting, leadToneSetting, sequencePercent) {
        if (sequenceItems.isEmpty()) {
            sequencePrerender = null
            return@LaunchedEffect
        }
        sequencePrerender = withContext(Dispatchers.Default) {
            renderSequence(
                items = sequenceItems,
                tempoPercent = sequencePercent,
                beatTone = beatToneSetting,
                leadTone = leadToneSetting,
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
            MetronomeLoopPlayer.create(context, loop)
        }
        oldPlayer?.release()
        playbackHolder.value = SequencePlaybackSlot(newPlayer, prerender)
        val startAt = playbackStartSeconds.coerceIn(0f, prerender.durationSeconds)
        sequencePosition = startAt
        newPlayer.start(startAt)
    }

    LaunchedEffect(isOn, loopEnabled, playbackGeneration) {
        if (!isOn) return@LaunchedEffect

        var attempts = 0
        while (playbackHolder.value == null && isActive && attempts < 500) {
            delay(10)
            attempts++
        }

        var lastPosition = -1f
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

                if (!loopEnabled) {
                    val duration = prerender.durationSeconds
                    val wrappedToStart = lastPosition >= 0f &&
                        lastPosition >= duration - 0.05f &&
                        position < minOf(0.15f, duration * 0.05f)
                    val atEnd = position >= duration - 0.02f
                    if (wrappedToStart || atEnd || (!player.isPlaying() && position > 0.05f)) {
                        pausePlayback()
                        return@withFrameNanos
                    }
                }
                lastPosition = position
            }
        }
    }

    val fallbackItemHeightPx = with(density) { 120.dp.roundToPx() }

    val showSequenceMap by remember {
        derivedStateOf {
            val count = sequenceItems.size
            if (count == 0) return@derivedStateOf false
            val layoutInfo = lazyListState.layoutInfo
            if (layoutInfo.viewportSize.height == 0) return@derivedStateOf false
            if (lazyListState.canScrollForward || lazyListState.canScrollBackward) {
                return@derivedStateOf true
            }
            val visibleIndices = layoutInfo.visibleItemsInfo.map { it.index }.toSet()
            (0 until count).any { it !in visibleIndices }
        }
    }

    LaunchedEffect(activeItemIndex, sequenceItems.size) {
        if (sequenceItems.isEmpty()) return@LaunchedEffect
        lazyListState.scrollSequenceItemToCenter(
            index = activeItemIndex.coerceIn(sequenceItems.indices),
            fallbackItemHeightPx = fallbackItemHeightPx,
        )
    }

    if (showSaveDialog) {
        SequenceSaveDialog(
            initialName = sequenceName,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                scope.launch {
                    if (context.saveNamedSequence(name, sequenceItems)) {
                        sequenceName = name
                        workspaceBaseline = WorkspaceBaseline(
                            name = name,
                            itemsKey = sequenceItemsContentKey(sequenceItems),
                        )
                        wasItemsDirty = false
                    }
                }
            },
        )
    }

    if (showLoadDialog) {
        SequenceLoadDialog(
            savedSequences = savedSequences,
            onDismiss = { showLoadDialog = false },
            onSelect = { saved ->
                showLoadDialog = false
                scope.launch {
                    pausePlayback()
                    context.loadNamedSequenceIntoWorkspace(saved)
                    sequenceName = saved.name
                    workspaceBaseline = WorkspaceBaseline(
                        name = saved.name,
                        itemsKey = sequenceItemsContentKey(saved.items),
                    )
                    wasItemsDirty = false
                    activeItemIndex = 0
                    activeRepeatIndex = null
                    sequencePosition = 0f
                }
            },
            onDelete = { saved ->
                scope.launch {
                    context.deleteSavedSequence(saved.id)
                    if (savedSequences.size <= 1) {
                        showLoadDialog = false
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .navigationBarBottomPadding(),
    ) {
        SequenceNameField(
            name = sequenceName,
            onNameChange = { sequenceName = it },
            showUntitled = showUntitled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = statusBarTop + 8.dp,
                    start = 12.dp,
                    end = 12.dp,
                ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val reordered = sequenceItems.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    scope.launch {
                        context.setSequenceItems(reordered)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RectangleShape),
                    state = lazyListState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(sequenceItems, key = { _, item -> item.id }) { index, item ->
                        val rowActiveRepeatIndex = if (index == activeItemIndex) activeRepeatIndex else null
                        ReorderableItem(
                            state = reorderableState,
                            key = item.id,
                            animateItemModifier = Modifier,
                        ) { isDragging ->
                            SequenceItemRow(
                                item = item,
                                tempoPercent = sequencePercent,
                                isDragging = isDragging,
                                isActive = index == activeItemIndex,
                                activeRepeatIndex = rowActiveRepeatIndex,
                                onSelect = {
                                    if (index != activeItemIndex) {
                                        activeRepeatIndex = null
                                    }
                                    activeItemIndex = index
                                    val prerender = sequencePrerender ?: return@SequenceItemRow
                                    val segment = resolveStartSegment(prerender) ?: return@SequenceItemRow
                                    seekToSegment(segment)
                                },
                                onRepeatClick = { repeatIndex ->
                                    activeItemIndex = index
                                    sequencePrerender?.let { prerender ->
                                        segmentForRepeat(index, repeatIndex, prerender.segments)
                                            ?.let { segment -> seekToSegment(segment) }
                                    }
                                },
                                onDelete = {
                                    if (index == activeItemIndex) {
                                        activeItemIndex = (index - 1).coerceAtLeast(0)
                                        activeRepeatIndex = null
                                    } else if (index < activeItemIndex) {
                                        activeItemIndex--
                                    }
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
            }
            if (showSequenceMap) {
                SequenceItemMap(
                    itemCount = sequenceItems.size,
                    activeIndex = activeItemIndex.coerceIn(sequenceItems.indices),
                    onItemScroll = { index ->
                        scope.launch {
                            lazyListState.scrollSequenceItemToCenter(
                                index = index.coerceIn(sequenceItems.indices),
                                fallbackItemHeightPx = fallbackItemHeightPx,
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BottomNavEdgePadding),
        ) {
            val iconButtonSize = sequenceNavIconButtonSize(maxWidth)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
            SequenceNavControls(
                loopEnabled = loopEnabled,
                onLoopChange = { loopEnabled = it },
                hasSavedSequence = hasSavedSequence,
                hasItems = sequenceItems.isNotEmpty(),
                iconButtonSize = iconButtonSize,
                onSave = { showSaveDialog = true },
                onLoad = { showLoadDialog = true },
                onClear = {
                    pausePlayback()
                    activeItemIndex = 0
                    activeRepeatIndex = null
                    sequencePosition = 0f
                    scope.launch {
                        context.setSequenceItems(emptyList())
                        sequenceName = ""
                        context.setSequenceName("")
                        workspaceBaseline = WorkspaceBaseline(
                            name = "",
                            itemsKey = sequenceItemsContentKey(emptyList()),
                        )
                        wasItemsDirty = false
                    }
                },
                onBack = {
                    pausePlayback()
                    activeRepeatIndex = null
                    sequencePosition = 0f
                    onBack()
                },
            )
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .onGloballyPositioned { circleDragCoordinates = it }
                    .pointerInput(circleCenter, circleRadiusPx, isOn) {
                        var dragStartedInCircle = false
                        var percentAdjustActive = false
                        var lastDragPosition = Offset.Zero
                        var lastPointerAngle = 0f
                        var totalAngularDrag = 0f
                        var gesturePercent = PercentDialMid
                        fun isInCircle(position: Offset): Boolean {
                            val dx = position.x - circleCenter.x
                            val dy = position.y - circleCenter.y
                            return dx * dx + dy * dy <= circleRadiusPx * circleRadiusPx
                        }
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                lastDragPosition = startOffset
                                dragStartedInCircle = isInCircle(startOffset)
                                percentAdjustActive = false
                                totalAngularDrag = 0f
                                gesturePercent = resolvedSequencePercent(currentSequencePercent.value)
                                lastPointerAngle = pointerAngleDegrees(circleCenter, startOffset)
                            },
                            onDragEnd = {
                                if (
                                    dragStartedInCircle &&
                                    totalAngularDrag < 5f &&
                                    isInCircle(lastDragPosition)
                                ) {
                                    currentTogglePlayback.value()
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                lastDragPosition = change.position
                                val angle = pointerAngleDegrees(circleCenter, change.position)
                                val delta = shortestAngleDelta(lastPointerAngle, angle)
                                lastPointerAngle = angle
                                totalAngularDrag += abs(delta)
                                if (isOn) return@detectDragGestures
                                val inCircle = isInCircle(change.position)
                                if (dragStartedInCircle) {
                                    if (!inCircle) return@detectDragGestures
                                    percentAdjustActive = true
                                    gesturePercent = resolvedSequencePercent(
                                        gesturePercent +
                                            percentChangeForAngleDelta(delta, gesturePercent),
                                    )
                                    sequencePercent = gesturePercent
                                    return@detectDragGestures
                                }
                                if (!percentAdjustActive) {
                                    percentAdjustActive = true
                                }
                                gesturePercent = resolvedSequencePercent(
                                    gesturePercent +
                                        percentChangeForAngleDelta(delta, gesturePercent),
                                )
                                sequencePercent = gesturePercent
                            },
                        )
                    },
            ) {
                CircleDisplay(
                    bpm = displayBpm,
                    isOn = isOn,
                    beatProgress = beatProgress,
                    onToggle = togglePlayback,
                    dialAngleDegrees = percentToDialAngle(sequencePercent),
                    showDialRangeTicks = true,
                    bottomHalfLabel = "${sequencePercent.roundToInt()}%",
                    modifier = Modifier.onGloballyPositioned { coords ->
                        circleRadiusPx = minOf(coords.size.width, coords.size.height) / 2f
                        circleDragCoordinates?.let { boxCoords ->
                            circleCenter = boxCoords.localPositionOf(
                                coords,
                                Offset(coords.size.width / 2f, coords.size.height / 2f),
                            )
                        }
                    },
                )
            }
            }
        }
    }
}

