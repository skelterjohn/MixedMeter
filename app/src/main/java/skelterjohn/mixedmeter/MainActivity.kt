package skelterjohn.mixedmeter

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.map
import skelterjohn.mixedmeter.ui.theme.MixedMeterTheme
import kotlin.math.sqrt

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val TEMPO_UNITS_KEY = floatPreferencesKey("tempo_units")
val TONE_KEY = stringPreferencesKey("tone_setting")
private val SELECTED_NOTE_KEY = stringPreferencesKey("selected_note")
private val NUMERATOR_KEY = intPreferencesKey("numerator")
private val DENOMINATOR_KEY = intPreferencesKey("denominator")

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MixedMeterTheme {
                val context = LocalContext.current
                var tempoUnits by remember { mutableFloatStateOf(180f) } // 120 BPM = 180 units
                var circleCenter by remember { mutableStateOf(Offset.Zero) }
                var boxLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                var isOn by remember { mutableStateOf(false) }
                var beatProgress by remember { mutableFloatStateOf(0f) }
                var isLoaded by remember { mutableStateOf(false) }
                var currentBeat by remember { mutableIntStateOf(1) }

                val bpm by remember {
                    derivedStateOf { calculateBpm(tempoUnits) }
                }

                var noteDropdownExpanded by remember { mutableStateOf(false) }
                val noteOptions = remember { listOf("♪", "♪.", "♩", "♩.", "𝅗𝅥", "𝅗𝅥.", "𝅝") }
                var selectedNote by remember { mutableStateOf("♩") }

                var numerator by remember { mutableIntStateOf(4) }
                var denominator by remember { mutableIntStateOf(4) }
                
                var committedBpm by remember { mutableFloatStateOf(bpm) }
                var pulsingBpm by remember { mutableFloatStateOf(bpm) }

                LaunchedEffect(Unit) {
                    val preferences = context.dataStore.data.first()
                    preferences[TEMPO_UNITS_KEY]?.let { savedUnits ->
                        tempoUnits = savedUnits
                        val loadedBpm = calculateBpm(savedUnits)
                        committedBpm = loadedBpm
                        pulsingBpm = loadedBpm
                    }
                    preferences[SELECTED_NOTE_KEY]?.let { savedNote ->
                        selectedNote = savedNote
                    }
                    preferences[NUMERATOR_KEY]?.let { savedNumerator ->
                        numerator = savedNumerator
                    }
                    preferences[DENOMINATOR_KEY]?.let { savedDenominator ->
                        denominator = savedDenominator
                    }
                    isLoaded = true
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

                LaunchedEffect(numerator) {
                    if (isLoaded) {
                        context.dataStore.edit { settings ->
                            settings[NUMERATOR_KEY] = numerator
                        }
                    }
                }

                LaunchedEffect(denominator) {
                    if (isLoaded) {
                        context.dataStore.edit { settings ->
                            settings[DENOMINATOR_KEY] = denominator
                        }
                    }
                }

                val toneSetting by remember {
                    context.dataStore.data
                        .map { preferences -> preferences[TONE_KEY] ?: "bip" }
                }.collectAsState(initial = "bip")

                LaunchedEffect(isOn, toneSetting, numerator) {
                    if (isOn) {
                        Log.d("MixedMeter", "Starting metronome with tone: $toneSetting")
                        val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        val mainToneType = if (toneSetting == "beep") {
                            ToneGenerator.TONE_PROP_BEEP
                        } else {
                            ToneGenerator.TONE_CDMA_PIP
                        }
                        val accentedToneType = ToneGenerator.TONE_PROP_BEEP2

                        try {
                            var beatStartTimeNanos = -1L
                            var beatInMeasure = 0
                            val effectiveNumerator = if (numerator > 0) numerator else 4

                            while (true) {
                                withFrameNanos { frameTimeNanos ->
                                    var isNewBeat = false
                                    if (beatStartTimeNanos == -1L) {
                                        beatStartTimeNanos = frameTimeNanos
                                        pulsingBpm = committedBpm
                                        isNewBeat = true
                                        beatInMeasure = 0
                                    }

                                    var currentDurationNanos = (60_000_000_000f / pulsingBpm).toLong()
                                    var elapsed = frameTimeNanos - beatStartTimeNanos

                                    while (elapsed >= currentDurationNanos) {
                                        beatStartTimeNanos += currentDurationNanos
                                        pulsingBpm = committedBpm
                                        currentDurationNanos = (60_000_000_000f / pulsingBpm).toLong()
                                        elapsed = frameTimeNanos - beatStartTimeNanos
                                        isNewBeat = true
                                        beatInMeasure = (beatInMeasure + 1) % effectiveNumerator
                                    }

                                    if (isNewBeat) {
                                        currentBeat = beatInMeasure + 1
                                        if (beatInMeasure == 0) {
                                            toneGenerator.startTone(accentedToneType, 10)
                                        } else {
                                            toneGenerator.startTone(mainToneType, 10)
                                        }
                                    }

                                    beatProgress = (elapsed.toFloat() / currentDurationNanos).coerceIn(0f, 1f)
                                }
                            }
                        } finally {
                            toneGenerator.release()
                        }
                    } else {
                        beatProgress = 0f
                        currentBeat = 1
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Gray
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .onGloballyPositioned { boxLayoutCoordinates = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { },
                                    onDragEnd = {
                                        committedBpm = bpm
                                    },
                                    onDragCancel = {
                                        committedBpm = bpm
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()

                                        // Vector from center to current touch
                                        val dx = change.position.x - circleCenter.x
                                        val dy = change.position.y - circleCenter.y
                                        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(100f)

                                        val sensitivity = 150f / distance
                                        val delta = dragAmount.x - dragAmount.y
                                        tempoUnits = (tempoUnits + delta * sensitivity).coerceIn(0f, 380f)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            TimeSignatureSelector(
                                numerator = numerator,
                                onNumeratorChange = { numerator = it },
                                denominator = denominator,
                                onDenominatorChange = { denominator = it },
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = bpm.toInt().toString(),
                                    color = Color.Black,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box {
                                    Text(
                                        text = " = $selectedNote",
                                        color = Color.Black,
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
                                                text = { Text(note, fontSize = 32.sp) },
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
                                beatProgress = beatProgress,
                                currentBeat = currentBeat,
                                onToggle = {
                                    if (!isOn) {
                                        committedBpm = bpm
                                    }
                                    isOn = !isOn
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

                        IconButton(
                            onClick = {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(100.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.Black,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeSignatureSelector(
    numerator: Int,
    onNumeratorChange: (Int) -> Unit,
    denominator: Int,
    onDenominatorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val denominatorOptions = listOf(1, 2, 4, 8, 16, 32)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-12).dp)
    ) {
        // Numerator
        BasicTextField(
            value = if (numerator == 0) "" else numerator.toString(),
            onValueChange = {
                val filtered = it.filter { char -> char.isDigit() }
                if (filtered.length <= 3) {
                    onNumeratorChange(filtered.toIntOrNull() ?: 0)
                }
            },
            textStyle = TextStyle(
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.Black
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .widthIn(min = 64.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    if (numerator == 0) {
                        Text(
                            text = "4",
                            style = TextStyle(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = Color.Black.copy(alpha = 0.3f)
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Denominator
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (denominator == 0) "4" else denominator.toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .clickable { expanded = true }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                denominatorOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.toString(), fontSize = 32.sp) },
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

@Composable
fun CircleDisplay(
    bpm: Float,
    isOn: Boolean,
    beatProgress: Float,
    currentBeat: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minBpm = 30f
    val maxBpm = 220f
    val startAngle = 120f // 30 degrees clockwise of straight down (90 + 30)
    val sweepAngle = 300f // Full rotation minus the 60 degree gap at the bottom
    val ratio = ((bpm - minBpm) / (maxBpm - minBpm)).coerceIn(0f, 1f)
    val currentAngle = startAngle + ratio * sweepAngle

    Box(
        modifier = modifier
            .size(200.dp)
            .clip(CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        // Concentric borders: thin white outside, thick black inside
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = 2.dp, color = Color.White, shape = CircleShape)
                .padding(2.dp)
                .border(width = 8.dp, color = Color.Black, shape = CircleShape)
        )
        // Indicator line and tempo animation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val innerRadius = size.width / 2 - 12.dp.toPx()
            
            if (isOn) {
                // Background starts white at the beginning of each beat
                drawCircle(
                    color = Color.White,
                    radius = innerRadius,
                    center = center
                )
                
                // Grey sweep covers the white clockwise
                drawArc(
                    color = Color.Gray,
                    startAngle = currentAngle,
                    sweepAngle = 360f * beatProgress,
                    useCenter = true,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2)
                )
            } else {
                // Solid grey when off
                drawCircle(
                    color = Color.Gray,
                    radius = innerRadius,
                    center = center
                )
            }

            // Faint track for the dial
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
                topLeft = Offset(12.dp.toPx(), 12.dp.toPx()),
                size = Size(size.width - 24.dp.toPx(), size.height - 24.dp.toPx())
            )

            // Indicator line
            rotate(degrees = currentAngle - 270f) {
                drawLine(
                    color = Color.White,
                    start = center,
                    end = Offset(x = size.width / 2, y = -2.dp.toPx()),
                    strokeWidth = 8.dp.toPx()
                )
            }
        }

        if (isOn) {
            Text(
                text = currentBeat.toString(),
                color = Color.Black,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MixedMeterTheme {
        CircleDisplay(bpm = 120f, isOn = false, beatProgress = 0f, currentBeat = 1, onToggle = {})
    }
}
