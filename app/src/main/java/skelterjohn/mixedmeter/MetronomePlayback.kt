package skelterjohn.mixedmeter

import kotlin.math.roundToInt

internal data class LoopPlayerSlot(
    val player: MetronomeLoopPlayer,
    val schedule: MetronomeClickSchedule,
)

internal data class SequencePlaybackSlot(
    val player: MetronomeLoopPlayer,
    val prerender: SequencePrerender,
)

fun noteValueForSymbol(note: String): Float {
    return when (note) {
        "♪" -> 0.125f
        "♪." -> 0.1875f
        "♩" -> 0.25f
        "♩." -> 0.375f
        "𝅗𝅥" -> 0.5f
        "𝅗𝅥." -> 0.75f
        "𝅝" -> 1.0f
        else -> 0.25f
    }
}

fun scaledSequenceBpm(baseBpm: Float, tempoPercent: Float): Float =
    baseBpm * tempoPercent / 100f

fun SequenceItem.metronomeSchedule(tempoPercent: Float = 100f): MetronomeClickSchedule {
    return when (this) {
        is SequenceItem.PlainBpm -> buildMetronomeClickSchedule(
            bpm = scaledSequenceBpm(bpm, tempoPercent),
            selectedNoteValue = noteValueForSymbol(selectedNote),
            timeSignatures = emptyList(),
        )
        is SequenceItem.MeterPattern -> buildMetronomeClickSchedule(
            bpm = scaledSequenceBpm(bpm, tempoPercent),
            selectedNoteValue = noteValueForSymbol(selectedNote),
            timeSignatures = timeSignatures,
            beatClickModes = reconcileBeatClickModes(beatClickModes, timeSignatures),
        )
    }
}

fun SequenceItem.displayBpm(): Float = when (this) {
    is SequenceItem.PlainBpm -> bpm
    is SequenceItem.MeterPattern -> bpm
}

fun SequenceItem.displayBpmAtPercent(tempoPercent: Float): Int =
    scaledSequenceBpm(displayBpm(), tempoPercent).roundToInt()

/** Monotonic playback timeline; UI and loop swaps derive position from elapsed wall time. */
data class PlaybackAnchor(
    val positionSeconds: Float,
    val nanoTime: Long,
) {
    fun elapsedPositionSeconds(nowNano: Long = System.nanoTime()): Float =
        positionSeconds + (nowNano - nanoTime) / 1_000_000_000f
}

fun positionInCycleSeconds(positionSeconds: Float, schedule: MetronomeClickSchedule): Float {
    val total = schedule.totalCycleNanos / 1_000_000_000f
    if (total > 0f) {
        val wrapped = positionSeconds % total
        return if (wrapped < 0f) wrapped + total else wrapped
    }
    val period = schedule.beatPeriodNanos / 1_000_000_000f
    if (period > 0f) {
        val wrapped = positionSeconds % period
        return if (wrapped < 0f) wrapped + period else wrapped
    }
    return positionSeconds
}

fun beatBoxProgress(
    isOn: Boolean,
    playbackPosition: Float,
    schedule: MetronomeClickSchedule,
): Float {
    if (!isOn) return 0f
    val totalDuration = schedule.totalCycleNanos / 1_000_000_000f
    if (totalDuration <= 0f) {
        val beatPeriod = schedule.beatPeriodNanos / 1_000_000_000f
        val inBeat = playbackPosition % beatPeriod
        return (inBeat / beatPeriod).coerceIn(0f, 1f)
    }
    val position = playbackPosition % totalDuration
    val box = schedule.boxes.firstOrNull { beat ->
        position >= beat.startTime && position < beat.startTime + beat.duration
    } ?: return 0f
    if (box.duration <= 0f) return 0f
    return ((position - box.startTime) / box.duration).coerceIn(0f, 1f)
}
