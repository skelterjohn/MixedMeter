package skelterjohn.mixedmeter

internal data class LoopPlayerSlot(
    val player: MetronomeLoopPlayer,
    val schedule: MetronomeClickSchedule,
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

fun SequenceItem.metronomeSchedule(): MetronomeClickSchedule {
    val (itemBpm, note, signatures) = when (this) {
        is SequenceItem.PlainBpm -> Triple(bpm, selectedNote, emptyList())
        is SequenceItem.MeterPattern -> Triple(bpm, selectedNote, timeSignatures)
    }
    return buildMetronomeClickSchedule(
        bpm = itemBpm,
        selectedNoteValue = noteValueForSymbol(note),
        timeSignatures = signatures,
    )
}

fun SequenceItem.displayBpm(): Float = when (this) {
    is SequenceItem.PlainBpm -> bpm
    is SequenceItem.MeterPattern -> bpm
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
