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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
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
private val TIME_SIGNATURES_KEY = stringPreferencesKey("time_signatures")

data class TimeSignature(val numerator: Int, val denominator: Int)

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
    @OptIn(ExperimentalFoundationApi::class)
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

                val bpm by remember {
                    derivedStateOf { calculateBpm(tempoUnits) }
                }

                var noteDropdownExpanded by remember { mutableStateOf(false) }
                val noteOptions = remember { listOf("♪", "♪.", "♩", "♩.", "𝅗𝅥", "𝅗𝅥.", "𝅝") }
                var selectedNote by remember { mutableStateOf("♩") }

                var timeSignatures by remember { mutableStateOf(listOf<TimeSignature>()) }
                
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
                    preferences[TIME_SIGNATURES_KEY]?.let { saved ->
                        if (saved.isNotEmpty()) {
                            timeSignatures = saved.split(";").filter { it.contains("/") }.map {
                                val parts = it.split("/")
                                TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
                            }
                        }
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

                LaunchedEffect(timeSignatures) {
                    if (isLoaded) {
                        context.dataStore.edit { settings ->
                            settings[TIME_SIGNATURES_KEY] = timeSignatures.joinToString(";") { "${it.numerator}/${it.denominator}" }
                        }
                    }
                }

                val toneSetting by remember {
                    context.dataStore.data
                        .map { preferences -> preferences[TONE_KEY] ?: "bip" }
                }.collectAsState(initial = "bip")

                LaunchedEffect(isOn, toneSetting, bpm) {
                    if (isOn) {
                        Log.d("MixedMeter", "Starting metronome with tone: $toneSetting")
                        val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        val toneType = if (toneSetting == "beep") {
                            ToneGenerator.TONE_PROP_BEEP
                        } else {
                            ToneGenerator.TONE_CDMA_PIP
                        }

                        try {
                            var beatStartTimeNanos = -1L

                            while (true) {
                                withFrameNanos { frameTimeNanos ->
                                    if (beatStartTimeNanos == -1L) {
                                        beatStartTimeNanos = frameTimeNanos
                                        toneGenerator.startTone(toneType, 30)
                                    }

                                    val currentDurationNanos = (60_000_000_000f / bpm).toLong()
                                    var elapsed = frameTimeNanos - beatStartTimeNanos

                                    while (elapsed >= currentDurationNanos) {
                                        beatStartTimeNanos += currentDurationNanos
                                        elapsed = frameTimeNanos - beatStartTimeNanos
                                        toneGenerator.startTone(toneType, 30)
                                    }

                                    beatProgress = (elapsed.toFloat() / currentDurationNanos).coerceIn(0f, 1f)
                                }
                            }
                        } finally {
                            toneGenerator.release()
                        }
                    } else {
                        beatProgress = 0f
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(bottom = 32.dp)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { timeSignatures = listOf(TimeSignature(4, 4)) },
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear All",
                                        tint = Color.Black
                                    )
                                }
                                timeSignatures.forEachIndexed { index, ts ->
                                    if (index > 0) {
                                        IconButton(onClick = {
                                            timeSignatures = timeSignatures.toMutableList().apply {
                                                add(index, TimeSignature(4, 4))
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Insert Time Signature",
                                                tint = Color.Black
                                            )
                                        }
                                    }
                                    
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        TimeSignatureSelector(
                                            numerator = ts.numerator,
                                            onNumeratorChange = { newNum ->
                                                timeSignatures = timeSignatures.toMutableList().apply {
                                                    this[index] = this[index].copy(numerator = newNum)
                                                }
                                            },
                                            denominator = ts.denominator,
                                            onDenominatorChange = { newDen ->
                                                timeSignatures = timeSignatures.toMutableList().apply {
                                                    this[index] = this[index].copy(denominator = newDen)
                                                }
                                            },
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .combinedClickable(
                                                    onClick = { /* normal clicks handled by children */ },
                                                    onLongClick = { showMenu = true }
                                                )
                                        )
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            if (index > 0) {
                                                DropdownMenuItem(
                                                    text = { Text("Move Left") },
                                                    onClick = {
                                                        timeSignatures = timeSignatures.toMutableList().apply {
                                                            val item = removeAt(index)
                                                            add(index - 1, item)
                                                        }
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                            if (index < timeSignatures.size - 1) {
                                                DropdownMenuItem(
                                                    text = { Text("Move Right") },
                                                    onClick = {
                                                        timeSignatures = timeSignatures.toMutableList().apply {
                                                            val item = removeAt(index)
                                                            add(index + 1, item)
                                                        }
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                            if (timeSignatures.size > 1) {
                                                DropdownMenuItem(
                                                    text = { Text("Remove") },
                                                    onClick = {
                                                        timeSignatures = timeSignatures.toMutableList().apply {
                                                            removeAt(index)
                                                        }
                                                        showMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        timeSignatures = timeSignatures + TimeSignature(4, 4)
                                    },
                                    modifier = Modifier.padding(end = 32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Time Signature",
                                        tint = Color.Black
                                    )
                                }
                            }
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
        // Indicator line and tempo background
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
                    color = if (isOn) Color.White else Color.White.copy(alpha = 0.5f),
                    start = center,
                    end = Offset(x = size.width / 2, y = -2.dp.toPx()),
                    strokeWidth = 8.dp.toPx()
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MixedMeterTheme {
        CircleDisplay(bpm = 120f, isOn = false, beatProgress = 0f, onToggle = {})
    }
}
