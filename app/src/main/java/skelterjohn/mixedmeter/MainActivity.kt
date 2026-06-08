package skelterjohn.mixedmeter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme
import kotlin.math.abs

/** UI finishes the current beat on [uiSchedule], then swaps to [newPlayer]. */
private data class PendingLoopSwap(
    val uiSchedule: MetronomeClickSchedule,
    val deferStartPositionSeconds: Float,
    val newPlayer: MetronomeLoopPlayer,
    val newSchedule: MetronomeClickSchedule,
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val TEMPO_UNITS_KEY = floatPreferencesKey("tempo_units")
val TONE_KEY = stringPreferencesKey("tone_setting")
val LEAD_TONE_KEY = stringPreferencesKey("lead_tone_setting")
val SUBDIVISION_TONE_KEY = stringPreferencesKey("subdivision_tone_setting")
val SUBDIVISION_KEY = intPreferencesKey("subdivision")
val TONE_OPTIONS = listOf("Bop", "Bip", "Snap", "Thump")

const val DEFAULT_BEAT_TONE = "Bop"
const val DEFAULT_LEAD_TONE = "Bip"
const val DEFAULT_SUBDIVISION_TONE = "Snap"

fun decodeSubdivision(stored: Int?): Int? =
    stored?.takeIf { it in SubdivisionMin..SubdivisionMax }

fun Context.subdivisionFlow(): Flow<Int?> =
    dataStore.data.map { preferences -> decodeSubdivision(preferences[SUBDIVISION_KEY]) }

suspend fun Context.setSubdivision(subdivision: Int?) {
    dataStore.edit { preferences ->
        if (subdivision == null) {
            preferences.remove(SUBDIVISION_KEY)
        } else {
            preferences[SUBDIVISION_KEY] = subdivision
        }
    }
}
private val SELECTED_NOTE_KEY = stringPreferencesKey("selected_note")
private val TIME_SIGNATURES_KEY = stringPreferencesKey("time_signatures")
private val BEAT_CLICK_ACTIVE_KEY = stringPreferencesKey("beat_click_active")

data class TimeSignature(val numerator: Int, val denominator: Int)

private fun parseStoredTimeSignatures(saved: String): List<TimeSignature> {
    if (saved.isBlank()) return emptyList()
    return saved.split(Regex("[;|]+"))
        .filter { it.contains("/") }
        .map { segment ->
            val parts = segment.split("/")
            TimeSignature(
                numerator = (parts.getOrNull(0)?.toIntOrNull() ?: 4).coerceIn(1, 999),
                denominator = (parts.getOrNull(1)?.toIntOrNull() ?: 4).coerceIn(1, 999),
            )
        }
        .take(4)
}

/** If beat-click data has more beats than the parsed numerator, trust the beat count. */
private fun timeSignaturesAlignedWithBeatClick(
    timeSignatures: List<TimeSignature>,
    beatClickModes: List<List<BeatClickMode>>,
): List<TimeSignature> =
    timeSignatures.mapIndexed { index, ts ->
        val beatCount = beatClickModes.getOrNull(index)?.size ?: 0
        if (beatCount > ts.numerator) ts.copy(numerator = beatCount) else ts
    }

private fun List<TimeSignature>.withoutIndex(index: Int): List<TimeSignature> =
    if (index !in indices) this else toMutableList().apply { removeAt(index) }

private fun List<TimeSignature>.withNumeratorAt(index: Int, numerator: Int): List<TimeSignature> =
    if (index !in indices) this else toMutableList().apply {
        this[index] = this[index].copy(numerator = numerator)
    }

private fun List<TimeSignature>.withDenominatorAt(index: Int, denominator: Int): List<TimeSignature> =
    if (index !in indices) this else toMutableList().apply {
        this[index] = this[index].copy(denominator = denominator)
    }

private fun List<TimeSignature>.movedLeft(index: Int): List<TimeSignature> =
    if (index <= 0 || index !in indices) {
        this
    } else {
        toMutableList().apply {
            val item = removeAt(index)
            add(index - 1, item)
        }
    }

private fun List<TimeSignature>.movedRight(index: Int): List<TimeSignature> =
    if (index !in indices || index >= lastIndex) {
        this
    } else {
        toMutableList().apply {
            val item = removeAt(index)
            add(index + 1, item)
        }
    }

private fun calculateBpm(tempoUnits: Float): Float {
    val interval = (tempoUnits / 10f).toInt()
    val remainder = tempoUnits % 10f
    val baseBpm = 30 + interval * 5
    return if (remainder < 6f) {
        baseBpm.toFloat()
    } else {
        baseBpm + (remainder - 5f)
    }
}

private fun bpmToTempoUnits(bpm: Float): Float {
    val clamped = bpm.coerceIn(BpmDialMinBpm, BpmDialMaxBpm)
    val interval = ((clamped - BpmDialMinBpm) / 5f).toInt().coerceIn(0, 38)
    val baseBpm = BpmDialMinBpm + interval * 5
    val remainder = if (clamped - baseBpm < 0.05f) {
        0f
    } else {
        clamped - baseBpm + 5f
    }
    return (interval * 10f + remainder).coerceIn(0f, 380f)
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeSetting by remember {
                context.dataStore.data
                    .map { preferences -> preferences[THEME_KEY] ?: DEFAULT_THEME }
            }.collectAsState(initial = DEFAULT_THEME)

            MixedMeterTheme(themeName = themeSetting) {
                val theme = currentAppTheme()
                val scope = rememberCoroutineScope()
                var tempoUnits by remember { mutableFloatStateOf(180f) } // 120 BPM = 180 units
                var circleCenter by remember { mutableStateOf(Offset.Zero) }
                var boxLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var isOn by remember { mutableStateOf(false) }
                var playbackPosition by remember { mutableFloatStateOf(0f) }
                var playbackAnchor by remember { mutableStateOf<PlaybackAnchor?>(null) }
                var isLoaded by remember { mutableStateOf(false) }
                var preferencesHydrated by remember { mutableStateOf(false) }

                val bpm by remember {
                    derivedStateOf { calculateBpm(tempoUnits) }
                }

                var noteDropdownExpanded by remember { mutableStateOf(false) }
                val subdivision by remember {
                    context.subdivisionFlow()
                }.collectAsState(initial = null)
                val noteOptions = remember { listOf("♪", "♪.", "♩", "♩.", "𝅗𝅥", "𝅗𝅥.", "𝅝") }
                var selectedNote by remember { mutableStateOf("♩") }

                var timeSignatures by remember { mutableStateOf(listOf<TimeSignature>()) }
                var beatClickModes by remember { mutableStateOf<List<List<BeatClickMode>>>(emptyList()) }
                var pendingTimeSignatureRemovalIndex by remember { mutableStateOf<Int?>(null) }

                var committedBpm by remember { mutableFloatStateOf(bpm) }

                LaunchedEffect(Unit) {
                    val preferences = context.dataStore.data.first()
                    preferences[TEMPO_UNITS_KEY]?.let { savedUnits ->
                        tempoUnits = savedUnits
                        committedBpm = storedSequenceBpm(calculateBpm(savedUnits))
                    }
                    preferences[SELECTED_NOTE_KEY]?.let { savedNote ->
                        selectedNote = savedNote
                    }
                    val savedBeatClickModes = preferences[BEAT_CLICK_ACTIVE_KEY]?.let(::decodeBeatClickModes)
                        ?: emptyList()
                    preferences[TIME_SIGNATURES_KEY]?.let { saved ->
                        val parsed = parseStoredTimeSignatures(saved)
                        timeSignatures = timeSignaturesAlignedWithBeatClick(parsed, savedBeatClickModes)
                    }
                    beatClickModes = reconcileBeatClickModes(savedBeatClickModes, timeSignatures)
                    isLoaded = true
                    preferencesHydrated = true
                }

                LaunchedEffect(committedBpm) {
                    if (isLoaded) {
                        context.dataStore.edit { settings ->
                            settings[TEMPO_UNITS_KEY] = tempoUnits
                        }
                    }
                }

                LaunchedEffect(selectedNote) {
                    if (isLoaded) {
                        context.dataStore.edit { settings ->
                            settings[SELECTED_NOTE_KEY] = selectedNote
                        }
                    }
                }

                LaunchedEffect(timeSignatures) {
                    if (!preferencesHydrated) return@LaunchedEffect
                    beatClickModes = reconcileBeatClickModes(beatClickModes, timeSignatures)
                    context.dataStore.edit { settings ->
                        settings[TIME_SIGNATURES_KEY] = timeSignatures.joinToString("|") { "${it.numerator}/${it.denominator}" }
                    }
                }

                LaunchedEffect(beatClickModes) {
                    if (!preferencesHydrated || timeSignatures.isEmpty()) return@LaunchedEffect
                    context.dataStore.edit { settings ->
                        settings[BEAT_CLICK_ACTIVE_KEY] = encodeBeatClickModes(beatClickModes)
                    }
                }

                LaunchedEffect(pendingTimeSignatureRemovalIndex) {
                    val index = pendingTimeSignatureRemovalIndex ?: return@LaunchedEffect
                    withFrameNanos { }
                    timeSignatures = timeSignatures.withoutIndex(index)
                    pendingTimeSignatureRemovalIndex = null
                }

                val beatToneSetting by remember {
                    context.dataStore.data
                        .map { preferences -> preferences[TONE_KEY] ?: DEFAULT_BEAT_TONE }
                }.collectAsState(initial = DEFAULT_BEAT_TONE)

                val leadToneSetting by remember {
                    context.dataStore.data
                        .map { preferences -> preferences[LEAD_TONE_KEY] ?: DEFAULT_LEAD_TONE }
                }.collectAsState(initial = DEFAULT_LEAD_TONE)

                val subdivisionToneSetting by remember {
                    context.dataStore.data
                        .map { preferences -> preferences[SUBDIVISION_TONE_KEY] ?: DEFAULT_SUBDIVISION_TONE }
                }.collectAsState(initial = DEFAULT_SUBDIVISION_TONE)

                val selectedNoteValue by remember {
                    derivedStateOf { noteValueForSymbol(selectedNote) }
                }

                val metronomeClickSchedule by remember {
                    derivedStateOf {
                        buildMetronomeClickSchedule(
                            bpm = committedBpm,
                            selectedNoteValue = selectedNoteValue,
                            timeSignatures = timeSignatures,
                            beatClickModes = beatClickModes,
                        )
                    }
                }

                val loopPlayerHolder = remember { mutableStateOf<LoopPlayerSlot?>(null) }
                var pendingLoopSwap by remember { mutableStateOf<PendingLoopSwap?>(null) }

                fun stopMainPlayback() {
                    pendingLoopSwap?.newPlayer?.release()
                    pendingLoopSwap = null
                    loopPlayerHolder.value?.player?.stop()
                    loopPlayerHolder.value?.player?.release()
                    loopPlayerHolder.value = null
                    playbackAnchor = null
                    playbackPosition = 0f
                    isOn = false
                }

                PausePlaybackWhenNotFocused(onPause = ::stopMainPlayback)

                val activeSchedule by remember {
                    derivedStateOf {
                        pendingLoopSwap?.uiSchedule
                            ?: if (isOn) {
                                loopPlayerHolder.value?.schedule ?: metronomeClickSchedule
                            } else {
                                metronomeClickSchedule
                            }
                    }
                }

                fun updatePlaybackPosition() {
                    if (pendingLoopSwap != null) {
                        playbackAnchor?.let { anchor ->
                            playbackPosition = anchor.elapsedPositionSeconds()
                        }
                        return
                    }
                    val player = loopPlayerHolder.value?.player
                    if (player != null && player.isPlaying()) {
                        playbackPosition = player.cyclePositionSeconds()
                        return
                    }
                    playbackAnchor?.let { anchor ->
                        playbackPosition = anchor.elapsedPositionSeconds()
                    }
                }

                val beatBoxSchedule by remember {
                    derivedStateOf {
                        val schedule = activeSchedule
                        schedule.boxes to schedule.totalCycleNanos / 1_000_000_000f
                    }
                }

                val activeBeatBox by remember {
                    derivedStateOf {
                        if (!isOn) return@derivedStateOf null
                        val (boxes, totalDuration) = beatBoxSchedule
                        if (totalDuration <= 0f) return@derivedStateOf null
                        val position = playbackPosition % totalDuration
                        boxes.firstOrNull { box ->
                            position >= box.startTime && position < box.startTime + box.duration
                        }
                    }
                }

                val activeBoxProgress by remember {
                    derivedStateOf {
                        beatBoxProgress(isOn, playbackPosition, activeSchedule)
                    }
                }

                var previousTimeSignatures by remember {
                    mutableStateOf<List<TimeSignature>?>(null)
                }
                var previousCommittedBpm by remember { mutableFloatStateOf(committedBpm) }
                var previousSelectedNote by remember { mutableStateOf(selectedNote) }
                var previousBeatTone by remember { mutableStateOf(beatToneSetting) }
                var previousLeadTone by remember { mutableStateOf(leadToneSetting) }
                var previousSubdivision by remember { mutableStateOf(subdivision) }
                var previousSubdivisionTone by remember { mutableStateOf(subdivisionToneSetting) }
                var previousBeatClickModes by remember {
                    mutableStateOf<List<List<BeatClickMode>>?>(null)
                }

                // Rebuild loop when meter/BPM/tone changes.
                LaunchedEffect(
                    committedBpm,
                    timeSignatures,
                    beatClickModes,
                    selectedNote,
                    beatToneSetting,
                    leadToneSetting,
                    subdivision,
                    subdivisionToneSetting,
                    isLoaded,
                ) {
                    if (!isLoaded) return@LaunchedEffect

                    val timeSignaturesChanged = previousTimeSignatures != null &&
                        previousTimeSignatures != timeSignatures
                    previousTimeSignatures = timeSignatures.toList()

                    if (timeSignaturesChanged && isOn) {
                        pendingLoopSwap?.newPlayer?.release()
                        pendingLoopSwap = null
                        loopPlayerHolder.value?.player?.stop()
                        playbackAnchor = null
                        playbackPosition = 0f
                        isOn = false
                    }

                    val bpmChanged = previousCommittedBpm != committedBpm
                    val noteChanged = previousSelectedNote != selectedNote
                    val beatToneChanged = previousBeatTone != beatToneSetting
                    val leadToneChanged = previousLeadTone != leadToneSetting
                    val subdivisionChanged = previousSubdivision != subdivision
                    val subdivisionToneChanged = previousSubdivisionTone != subdivisionToneSetting
                    val beatClickModesChanged = previousBeatClickModes != null &&
                        previousBeatClickModes != beatClickModes
                    val scheduleTimingChanged = timeSignaturesChanged || noteChanged || bpmChanged
                    val onlyAudioChange = isOn && !scheduleTimingChanged &&
                        (beatToneChanged || leadToneChanged || beatClickModesChanged ||
                            subdivisionChanged || subdivisionToneChanged)
                    val canDeferLoopSwap = isOn && !timeSignaturesChanged && !noteChanged && bpmChanged

                    if (pendingLoopSwap != null && !canDeferLoopSwap && !onlyAudioChange) {
                        pendingLoopSwap?.newPlayer?.release()
                        pendingLoopSwap = null
                    }

                    val schedule = metronomeClickSchedule

                    if (onlyAudioChange) {
                        val newPlayer = withContext(Dispatchers.Default) {
                            val loop = MetronomeLoopRenderer.render(
                                schedule = schedule,
                                beatTone = beatToneSetting,
                                leadTone = leadToneSetting,
                                subdivision = subdivision,
                                subdivisionTone = subdivisionToneSetting,
                            )
                            MetronomeLoopPlayer.create(context, loop)
                        }
                        val startPos = positionInCycleSeconds(
                            playbackAnchor?.elapsedPositionSeconds() ?: playbackPosition,
                            schedule,
                        )
                        pendingLoopSwap?.newPlayer?.release()
                        pendingLoopSwap = null
                        val oldPlayer = loopPlayerHolder.value?.player
                        loopPlayerHolder.value = LoopPlayerSlot(newPlayer, schedule)
                        playbackAnchor = PlaybackAnchor(startPos, System.nanoTime())
                        newPlayer.start(startPos)
                        oldPlayer?.stop()
                        oldPlayer?.release()
                        updatePlaybackPosition()
                        previousCommittedBpm = committedBpm
                        previousSelectedNote = selectedNote
                        previousBeatTone = beatToneSetting
                        previousLeadTone = leadToneSetting
                        previousSubdivision = subdivision
                        previousSubdivisionTone = subdivisionToneSetting
                        previousBeatClickModes = beatClickModes.map { it.toList() }
                        return@LaunchedEffect
                    }

                    if (canDeferLoopSwap) {
                        val existingPending = pendingLoopSwap
                        val existingSlot = loopPlayerHolder.value
                        val oldSchedule = existingPending?.uiSchedule ?: existingSlot?.schedule
                        val canDeferSwap = existingPending != null ||
                            (existingSlot?.player?.isPlaying() == true)
                        if (oldSchedule != null && canDeferSwap) {
                            val deferStart = when {
                                existingPending != null -> existingPending.deferStartPositionSeconds
                                existingSlot?.player?.isPlaying() == true -> {
                                    val pos = existingSlot.player.cyclePositionSeconds()
                                    playbackAnchor = PlaybackAnchor(pos, System.nanoTime())
                                    pos
                                }
                                else -> playbackAnchor?.elapsedPositionSeconds() ?: 0f
                            }
                            if (existingPending != null) {
                                existingPending.newPlayer.release()
                            } else {
                                val slot = existingSlot!!
                                slot.player.stop()
                                slot.player.release()
                                loopPlayerHolder.value = null
                            }
                            updatePlaybackPosition()
                            val newPlayer = withContext(Dispatchers.Default) {
                                val loop = MetronomeLoopRenderer.render(
                                    schedule = schedule,
                                    beatTone = beatToneSetting,
                                    leadTone = leadToneSetting,
                                    subdivision = subdivision,
                                    subdivisionTone = subdivisionToneSetting,
                                )
                                MetronomeLoopPlayer.create(context, loop)
                            }
                            pendingLoopSwap = PendingLoopSwap(
                                uiSchedule = oldSchedule,
                                deferStartPositionSeconds = deferStart,
                                newPlayer = newPlayer,
                                newSchedule = schedule,
                            )
                            return@LaunchedEffect
                        }
                    }

                    val hotStart = isOn
                    val oldPlayer = loopPlayerHolder.value?.player
                    val newPlayer = withContext(Dispatchers.Default) {
                        val loop = MetronomeLoopRenderer.render(
                            schedule = schedule,
                            beatTone = beatToneSetting,
                            leadTone = leadToneSetting,
                            subdivision = subdivision,
                            subdivisionTone = subdivisionToneSetting,
                        )
                        MetronomeLoopPlayer.create(context, loop)
                    }
                    oldPlayer?.release()
                    loopPlayerHolder.value = LoopPlayerSlot(newPlayer, schedule)

                    if (hotStart) {
                        playbackPosition = 0f
                        newPlayer.start()
                    }

                    previousCommittedBpm = committedBpm
                    previousSelectedNote = selectedNote
                    previousBeatTone = beatToneSetting
                    previousLeadTone = leadToneSetting
                    previousSubdivision = subdivision
                    previousSubdivisionTone = subdivisionToneSetting
                    previousBeatClickModes = beatClickModes.map { it.toList() }
                }

                LaunchedEffect(pendingLoopSwap) {
                    val pending = pendingLoopSwap ?: return@LaunchedEffect
                    while (isActive && isOn && pendingLoopSwap === pending) {
                        withFrameNanos {
                            updatePlaybackPosition()
                        }
                        val position = playbackPosition
                        if (hasCrossedBeatBoundary(
                                pending.deferStartPositionSeconds,
                                position,
                                pending.uiSchedule,
                            )
                        ) {
                            break
                        }
                        if (remainingSecondsInCurrentBeat(position, pending.uiSchedule) <= 1e-4f) {
                            break
                        }
                    }
                    if (pendingLoopSwap !== pending) return@LaunchedEffect

                    if (!isOn) {
                        pending.newPlayer.release()
                        pendingLoopSwap = null
                        return@LaunchedEffect
                    }

                    pendingLoopSwap = null
                    loopPlayerHolder.value = LoopPlayerSlot(pending.newPlayer, pending.newSchedule)
                    playbackAnchor = PlaybackAnchor(0f, System.nanoTime())
                    playbackPosition = 0f
                    pending.newPlayer.start(0f)
                    updatePlaybackPosition()
                    previousCommittedBpm = committedBpm
                    previousSelectedNote = selectedNote
                    previousBeatTone = beatToneSetting
                    previousLeadTone = leadToneSetting
                    previousSubdivision = subdivision
                    previousSubdivisionTone = subdivisionToneSetting
                    previousBeatClickModes = beatClickModes.map { it.toList() }
                }

                LaunchedEffect(isOn) {
                    if (!isOn) {
                        pendingLoopSwap?.newPlayer?.release()
                        pendingLoopSwap = null
                        loopPlayerHolder.value?.player?.stop()
                        playbackAnchor = null
                        playbackPosition = 0f
                        return@LaunchedEffect
                    }
                    if (pendingLoopSwap != null) {
                        while (isActive && isOn && pendingLoopSwap != null) {
                            withFrameNanos { updatePlaybackPosition() }
                        }
                        if (!isOn || pendingLoopSwap != null) return@LaunchedEffect
                    }
                    val schedule = metronomeClickSchedule
                    val player = withContext(Dispatchers.Default) {
                        val loop = MetronomeLoopRenderer.render(
                            schedule = schedule,
                            beatTone = beatToneSetting,
                            leadTone = leadToneSetting,
                            subdivision = subdivision,
                            subdivisionTone = subdivisionToneSetting,
                        )
                        MetronomeLoopPlayer.create(context, loop)
                    }
                    loopPlayerHolder.value?.player?.release()
                    loopPlayerHolder.value = LoopPlayerSlot(player, schedule)
                    playbackAnchor = PlaybackAnchor(0f, System.nanoTime())
                    playbackPosition = 0f
                    player.start(0f)
                    while (isActive && isOn) {
                        if (pendingLoopSwap != null) {
                            withFrameNanos { updatePlaybackPosition() }
                            continue
                        }
                        withFrameNanos { updatePlaybackPosition() }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = theme.background,
                    contentWindowInsets = WindowInsets.statusBars,
                ) { innerPadding ->
                    val focusManager = LocalFocusManager.current
                    val circleRadiusPx = with(LocalDensity.current) { 100.dp.toPx() }
                    var twoFingerActive by remember { mutableStateOf(false) }
                    var isDialDragging by remember { mutableStateOf(false) }
                    val currentTwoFingerActive = rememberUpdatedState(twoFingerActive)
                    val toggleMetronome = {
                        focusManager.clearFocus()
                        if (isOn) {
                            pendingLoopSwap?.newPlayer?.release()
                            pendingLoopSwap = null
                            loopPlayerHolder.value?.player?.stop()
                            playbackAnchor = null
                            playbackPosition = 0f
                        } else {
                            committedBpm = storedSequenceBpm(bpm)
                            playbackPosition = 0f
                        }
                        isOn = !isOn
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(WindowInsets.ime)
                            .padding(innerPadding)
                            .navigationBarBottomPadding()
                            .onGloballyPositioned { boxLayoutCoordinates = it }
                            .twoFingerSwipe(
                                onSwipeUp = { context.startSequenceActivity() },
                                onSwipeLeft = { context.startSettingsActivity() },
                                onTwoFingerActiveChanged = { twoFingerActive = it },
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { focusManager.clearFocus() })
                            }
                            .pointerInput(circleCenter, circleRadiusPx, isOn, twoFingerActive) {
                                coroutineScope {
                                var dragStartedInCircle = false
                                var bpmAdjustActive = false
                                var dialDragCancelled = false
                                var tempoAtDragStart = 0f
                                var lastDragPosition = Offset.Zero
                                var totalAngularDrag = 0f
                                var gestureBpm = 0f
                                val dialPauseCommit = DialDragPauseCommit(this)
                                fun commitDialBpm() {
                                    val intBpm = storedSequenceBpm(gestureBpm)
                                    tempoUnits = bpmToTempoUnits(intBpm)
                                    committedBpm = intBpm
                                    isDialDragging = false
                                }
                                fun isInCircle(position: Offset): Boolean {
                                    val dx = position.x - circleCenter.x
                                    val dy = position.y - circleCenter.y
                                    return dx * dx + dy * dy <= circleRadiusPx * circleRadiusPx
                                }
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        focusManager.clearFocus()
                                        dialPauseCommit.reset()
                                        isDialDragging = false
                                        tempoAtDragStart = tempoUnits
                                        dialDragCancelled = false
                                        lastDragPosition = startOffset
                                        dragStartedInCircle = isInCircle(startOffset)
                                        bpmAdjustActive = false
                                        totalAngularDrag = 0f
                                        gestureBpm = calculateBpm(tempoUnits)
                                            .coerceIn(BpmDialMinBpm, BpmDialMaxBpm)
                                    },
                                    onDragEnd = {
                                        dialPauseCommit.onGestureEnd()
                                        isDialDragging = false
                                        if (dialDragCancelled) return@detectDragGestures
                                        when {
                                            dragStartedInCircle && totalAngularDrag < 5f &&
                                                isInCircle(lastDragPosition) -> toggleMetronome()
                                            bpmAdjustActive -> commitDialBpm()
                                        }
                                    },
                                    onDragCancel = {
                                        dialPauseCommit.onGestureEnd()
                                        isDialDragging = false
                                        if (dialDragCancelled) return@detectDragGestures
                                        if (bpmAdjustActive) commitDialBpm()
                                    },
                                    onDrag = { change, _ ->
                                        if (currentTwoFingerActive.value) {
                                            if (!dialDragCancelled) {
                                                dialPauseCommit.reset()
                                                tempoUnits = tempoAtDragStart
                                                dialDragCancelled = true
                                                isDialDragging = false
                                            }
                                            return@detectDragGestures
                                        }
                                        change.consume()
                                        val dragDelta = change.position - lastDragPosition
                                        lastDragPosition = change.position
                                        if (dialPauseCommit.frozen) return@detectDragGestures
                                        if (dragStartedInCircle || !bpmAdjustActive) {
                                            bpmAdjustActive = true
                                            isDialDragging = true
                                        }
                                        val delta = angularDragDeltaDegrees(
                                            circleCenter,
                                            change.position,
                                            dragDelta,
                                            circleRadiusPx,
                                        )
                                        totalAngularDrag += abs(delta)
                                        val previousBpm = gestureBpm
                                        gestureBpm = (gestureBpm + bpmChangeForAngleDelta(delta))
                                            .coerceIn(BpmDialMinBpm, BpmDialMaxBpm)
                                        tempoUnits = bpmToTempoUnits(gestureBpm)
                                        if (bpmAdjustActive && gestureBpm != previousBpm) {
                                            dialPauseCommit.onValueChanged(::commitDialBpm)
                                        }
                                    },
                                )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        val timeSignaturesScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) {
                            MeterScrollRow(
                                scrollState = timeSignaturesScrollState,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MeterRowStartSpacer()
                                timeSignatures.forEachIndexed { index, _ ->
                                    if (index > 0) {
                                        MeterInsertGapSpacer()
                                    }
                                    Box(
                                        modifier = Modifier.width(MeterTimeSignatureSlotMinWidth),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        IconButton(onClick = {
                                            focusManager.clearFocus()
                                            pendingTimeSignatureRemovalIndex = index
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Time Signature",
                                                tint = theme.iconTint,
                                            )
                                        }
                                    }
                                }
                                MeterRowEndSpacer(canAdd = timeSignatures.size < 4)
                            }

                            MeterScrollRow(
                                scrollState = timeSignaturesScrollState,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MeterRowStartSpacer()
                                timeSignatures.forEachIndexed { index, ts ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier.width(MeterInsertSlotWidth),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            InsertTimeSignatureButton(
                                                enabled = timeSignatures.size < 4,
                                                onClick = {
                                                    timeSignatures = timeSignatures.toMutableList().apply {
                                                        add(index, TimeSignature(4, 4))
                                                    }
                                                },
                                                contentDescription = "Insert Time Signature",
                                            )
                                        }
                                    }

                                    key(index) {
                                        TimeSignatureSelectorCell(
                                            index = index,
                                            timeSignature = ts,
                                            timeSignatureCount = timeSignatures.size,
                                            onNumeratorChange = { newNum ->
                                                timeSignatures = timeSignatures.withNumeratorAt(index, newNum)
                                            },
                                            onDenominatorChange = { newDen ->
                                                timeSignatures = timeSignatures.withDenominatorAt(index, newDen)
                                            },
                                            onRemove = {
                                                pendingTimeSignatureRemovalIndex = index
                                            },
                                            onMoveLeft = {
                                                timeSignatures = timeSignatures.movedLeft(index)
                                            },
                                            onMoveRight = {
                                                timeSignatures = timeSignatures.movedRight(index)
                                            },
                                        )
                                    }
                                }
                                if (timeSignatures.isEmpty()) {
                                    Box(
                                        modifier = Modifier.padding(start = 4.dp, end = 32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CreateTimeSignatureButton(
                                            onClick = {
                                                timeSignatures = listOf(TimeSignature(4, 4))
                                            },
                                        )
                                    }
                                } else if (timeSignatures.size < 4) {
                                    Box(
                                        modifier = Modifier
                                            .width(MeterTrailingAddSlotWidth)
                                            .padding(start = 4.dp, end = 32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        InsertTimeSignatureButton(
                                            enabled = true,
                                            onClick = {
                                                timeSignatures = timeSignatures + TimeSignature(4, 4)
                                            },
                                            contentDescription = "Add Time Signature",
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(MeterRowEndSpacerWidth))
                                }
                            }

                            MeterScrollRow(
                                scrollState = timeSignaturesScrollState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                MeterRowStartSpacer()
                                timeSignatures.forEachIndexed { index, ts ->
                                    if (index > 0) {
                                        MeterInsertGapSpacer()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(MeterTimeSignatureSlotMinWidth)
                                            .fillMaxHeight(),
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(0.dp),
                                        ) {
                                            val num = ts.numerator
                                            if (num > 0) {
                                                repeat(num) { beatIndex ->
                                                    val currentBeat = activeBeatBox
                                                    val isCurrentBeat = currentBeat?.sectionIndex == index &&
                                                        currentBeat?.beatIndex == beatIndex
                                                    val clickMode = beatClickMode(
                                                        beatClickModes,
                                                        index,
                                                        beatIndex,
                                                    )
                                                    val boxColor = when {
                                                        isCurrentBeat -> theme.beatBoxPlaying
                                                        clickMode == BeatClickMode.INACTIVE -> theme.beatBoxInactive
                                                        else -> theme.beatBoxActive
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxSize()
                                                            .background(boxColor)
                                                            .border(1.dp, theme.text)
                                                            .clickable {
                                                                focusManager.clearFocus()
                                                                beatClickModes = toggleBeatClickMode(
                                                                    beatClickModes,
                                                                    index,
                                                                    beatIndex,
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        BeatClickModeIcon(
                                                            mode = clickMode,
                                                            tint = theme.text,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                MeterRowEndSpacer(canAdd = timeSignatures.size < 4)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 140.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = resolvedBpm(bpm).toString(),
                                    color = theme.text,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box {
                                    Text(
                                        text = " = $selectedNote",
                                        color = theme.text,
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { noteDropdownExpanded = true }
                                    )
                                    DropdownMenu(
                                        expanded = noteDropdownExpanded,
                                        onDismissRequest = { noteDropdownExpanded = false }
                                    ) {
                                        noteOptions.forEach { note ->
                                            DropdownMenuItem(
                                                text = { Text(note, fontSize = 32.sp, color = DropdownMenuTextColor) },
                                                onClick = {
                                                    selectedNote = note
                                                    noteDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            CircleDisplay(
                                bpm = bpm,
                                isOn = isOn,
                                beatProgress = activeBoxProgress,
                                onToggle = toggleMetronome,
                                isDialDragging = isDialDragging,
                                showBpmRangeLabels = true,
                                bottomHalfOverlay = {
                                    SubdivisionSelector(
                                        subdivision = subdivision,
                                        onSubdivisionChange = { value ->
                                            scope.launch { context.setSubdivision(value) }
                                        },
                                    )
                                },
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    boxLayoutCoordinates?.let { boxCoords ->
                                        // Calculate center relative to the parent Box
                                        circleCenter = boxCoords.localPositionOf(
                                            coords,
                                            Offset(coords.size.width / 2f, coords.size.height / 2f)
                                        )
                                    }
                                }
                            )
                        }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(BottomNavEdgePadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BottomNavIconButton(
                                onClick = { context.startSequenceActivity() },
                            ) {
                                ArrowDropUpNavIcon()
                            }
                            if (timeSignatures.isNotEmpty()) {
                                BottomNavIconButton(
                                    onClick = {
                                        scope.launch {
                                            context.appendSequenceItem(
                                                metronomeSnapshot(
                                                    bpm = bpm,
                                                    selectedNote = selectedNote,
                                                    timeSignatures = timeSignatures,
                                                    beatClickModes = beatClickModes,
                                                ),
                                            )
                                            context.startSequenceActivity()
                                        }
                                    },
                                ) {
                                    AddToSequenceNavIcon()
                                }
                            }
                            BottomNavIconButton(
                                onClick = { context.startSettingsActivity() },
                            ) {
                                SettingsNavIcon()
                            }
                        }
                    }
                }
            }
        }
    }
}

private val MeterInsertSlotWidth = 24.dp
private val MeterTimeSignatureSlotMinWidth = 56.dp
private val MeterBeatBoxIconSize = 11.dp
private val MeterRowStartSpacerWidth = 16.dp
private val MeterRowEndSpacerWidth = 32.dp
private val MeterTrailingAddSlotWidth = 60.dp

@Composable
private fun BeatClickModeIcon(
    mode: BeatClickMode,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        BeatClickMode.INACTIVE, BeatClickMode.BEAT -> Unit
        BeatClickMode.LEAD -> {
            Canvas(modifier = modifier.size(MeterBeatBoxIconSize)) {
                drawCircle(color = tint)
            }
        }
    }
}

@Composable
private fun RowScope.MeterRowStartSpacer() {
    Spacer(modifier = Modifier.width(MeterRowStartSpacerWidth))
}

@Composable
private fun RowScope.MeterInsertGapSpacer() {
    Spacer(modifier = Modifier.width(MeterInsertSlotWidth))
}

@Composable
private fun RowScope.MeterRowEndSpacer(canAdd: Boolean) {
    Spacer(
        modifier = Modifier.width(
            if (canAdd) MeterTrailingAddSlotWidth else MeterRowEndSpacerWidth,
        ),
    )
}

@Composable
private fun MeterScrollRow(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
private fun TimeSignatureSelectorCell(
    index: Int,
    timeSignature: TimeSignature,
    timeSignatureCount: Int,
    onNumeratorChange: (Int) -> Unit,
    onDenominatorChange: (Int) -> Unit,
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(pendingRemove) {
        if (!pendingRemove) return@LaunchedEffect
        focusManager.clearFocus()
        withFrameNanos { }
        onRemove()
        pendingRemove = false
    }
    Box(
        modifier = Modifier.width(MeterTimeSignatureSlotMinWidth),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            key(index, timeSignature.denominator) {
                TimeSignatureSelector(
                    numerator = timeSignature.numerator,
                    onNumeratorChange = onNumeratorChange,
                    denominator = timeSignature.denominator,
                    onDenominatorChange = onDenominatorChange,
                    onLongClick = { showMenu = true },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (index > 0) {
                    DropdownMenuItem(
                        text = { Text("Move Left", color = DropdownMenuTextColor) },
                        onClick = {
                            showMenu = false
                            onMoveLeft()
                        },
                    )
                }
                if (index < timeSignatureCount - 1) {
                    DropdownMenuItem(
                        text = { Text("Move Right", color = DropdownMenuTextColor) },
                        onClick = {
                            showMenu = false
                            onMoveRight()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove", color = DropdownMenuTextColor) },
                    onClick = {
                        showMenu = false
                        pendingRemove = true
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateTimeSignatureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = currentAppTheme()
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(5))
            .background(theme.buttonSurface, RoundedCornerShape(5))
            .border(1.dp, theme.buttonBorder, RoundedCornerShape(5))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "create time signature",
            color = theme.text,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun InsertTimeSignatureButton(
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val theme = currentAppTheme()
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .shadow(2.dp, RoundedCornerShape(5))
            .size(20.dp)
            .background(theme.buttonSurface, RoundedCornerShape(5))
            .border(1.dp, theme.buttonBorder, RoundedCornerShape(5))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = contentDescription,
            tint = theme.iconTint,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun TimeSignatureSelector(
    numerator: Int,
    onNumeratorChange: (Int) -> Unit,
    denominator: Int,
    onDenominatorChange: (Int) -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = currentAppTheme()
    var expanded by remember { mutableStateOf(false) }
    var isEditingNumerator by remember { mutableStateOf(false) }
    var numeratorEditText by remember { mutableStateOf("") }
    var numeratorHadFocus by remember { mutableStateOf(false) }
    var canCommitNumeratorEdit by remember { mutableStateOf(true) }
    val numeratorFocusRequester = remember { FocusRequester() }
    DisposableEffect(Unit) {
        onDispose { canCommitNumeratorEdit = false }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val denominatorOptions = listOf(1, 2, 4, 8, 16, 32)
    val numeratorTextStyle = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = theme.text,
    )

    fun commitNumeratorEdit() {
        if (!isEditingNumerator || !canCommitNumeratorEdit) return
        val parsed = numeratorEditText.toIntOrNull()?.takeIf { it in 1..999 }
        onNumeratorChange(parsed ?: numerator.coerceAtLeast(1))
        isEditingNumerator = false
        numeratorHadFocus = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(isEditingNumerator) {
        if (!isEditingNumerator) return@LaunchedEffect
        numeratorHadFocus = false
        withFrameNanos { }
        try {
            numeratorFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            isEditingNumerator = false
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        // Numerator — static Text when idle so reload always shows the saved value
        Box(
            modifier = Modifier
                .zIndex(1f)
                .widthIn(min = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isEditingNumerator) {
                val showPlaceholder = numeratorEditText.isEmpty()
                BasicTextField(
                    value = numeratorEditText,
                    onValueChange = { newText ->
                        if (newText.contains('\n')) {
                            commitNumeratorEdit()
                            return@BasicTextField
                        }
                        val filtered = newText.filter { char -> char.isDigit() }
                        if (filtered.length <= 3) {
                            numeratorEditText = filtered
                        }
                    },
                    singleLine = true,
                    textStyle = numeratorTextStyle,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { commitNumeratorEdit() },
                    ),
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 40.dp)
                        .focusRequester(numeratorFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                numeratorHadFocus = true
                            } else if (isEditingNumerator && numeratorHadFocus && canCommitNumeratorEdit) {
                                commitNumeratorEdit()
                            }
                        },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (showPlaceholder) {
                                Text(
                                    text = numerator.coerceAtLeast(1).toString(),
                                    style = numeratorTextStyle.copy(
                                        color = theme.text.copy(alpha = 0.3f),
                                    ),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                Text(
                    text = numerator.coerceAtLeast(1).toString(),
                    style = numeratorTextStyle,
                    modifier = Modifier.then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    numeratorEditText = ""
                                    isEditingNumerator = true
                                },
                                onLongClick = onLongClick,
                            )
                        } else {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                numeratorEditText = ""
                                isEditingNumerator = true
                            }
                        },
                    ),
                )
            }
        }

        // Denominator
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (denominator == 0) "4" else denominator.toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = { expanded = true },
                                onLongClick = onLongClick,
                            )
                        } else {
                            Modifier.clickable { expanded = true }
                        },
                    ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                denominatorOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.toString(), fontSize = 32.sp, color = DropdownMenuTextColor) },
                        onClick = {
                            onDenominatorChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

