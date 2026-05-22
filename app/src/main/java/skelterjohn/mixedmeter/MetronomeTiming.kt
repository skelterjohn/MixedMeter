package skelterjohn.mixedmeter

data class BeatBoxTiming(
    val sectionIndex: Int,
    val beatIndex: Int,
    val startTime: Float,
    val duration: Float,
)

data class MetronomeClickSchedule(
    val boxes: List<BeatBoxTiming>,
    val clickOffsetsNanos: LongArray,
    /** Parallel to [clickOffsetsNanos]; true for the first beat of each time signature section. */
    val clickUseLeadTone: BooleanArray,
    val totalCycleNanos: Long,
    val beatPeriodNanos: Long,
)

fun buildMetronomeClickSchedule(
    bpm: Float,
    selectedNoteValue: Float,
    timeSignatures: List<TimeSignature>,
): MetronomeClickSchedule {
    val boxes = mutableListOf<BeatBoxTiming>()
    val clickOffsetsNanos = mutableListOf<Long>()
    val clickUseLeadTone = mutableListOf<Boolean>()
    var sectionStartNanos = 0L

    timeSignatures.forEachIndexed { sectionIndex, ts ->
        if (ts.denominator == 0 || ts.numerator <= 0) return@forEachIndexed
        val boxDurationNanos = boxDurationNanos(bpm, ts.denominator, selectedNoteValue)
        repeat(ts.numerator) { beatIndex ->
            val offsetNanos = sectionStartNanos + beatIndex * boxDurationNanos
            clickOffsetsNanos.add(offsetNanos)
            clickUseLeadTone.add(beatIndex == 0)
            boxes.add(
                BeatBoxTiming(
                    sectionIndex = sectionIndex,
                    beatIndex = beatIndex,
                    startTime = offsetNanos / 1_000_000_000f,
                    duration = boxDurationNanos / 1_000_000_000f,
                ),
            )
        }
        sectionStartNanos += ts.numerator * boxDurationNanos
    }

    // Plain metronome (no time signatures): one click per BPM tick, same as before.
    val beatPeriodNanos = (60_000_000_000.0 / bpm).toLong().coerceAtLeast(1L)

    return MetronomeClickSchedule(
        boxes = boxes,
        clickOffsetsNanos = clickOffsetsNanos.toLongArray(),
        clickUseLeadTone = clickUseLeadTone.toBooleanArray(),
        totalCycleNanos = sectionStartNanos,
        beatPeriodNanos = beatPeriodNanos,
    )
}

/** Nanoseconds per box; single division avoids float drift across many short beats (e.g. 9/16). */
fun boxDurationNanos(bpm: Float, denominator: Int, selectedNoteValue: Float): Long {
    if (bpm <= 0f || denominator <= 0 || selectedNoteValue <= 0f) return 0L
    return (60_000_000_000.0 / bpm / denominator / selectedNoteValue).toLong().coerceAtLeast(1L)
}

fun metronomePlaybackPosition(
    cycleAnchorNanos: Long,
    getSchedule: () -> MetronomeClickSchedule,
): Float {
    val schedule = getSchedule()
    val elapsed = System.nanoTime() - cycleAnchorNanos
    if (schedule.totalCycleNanos > 0L && schedule.clickOffsetsNanos.isNotEmpty()) {
        return ((elapsed % schedule.totalCycleNanos).toFloat() / 1_000_000_000f).coerceAtLeast(0f)
    }
    val period = schedule.beatPeriodNanos
    return ((elapsed % period).toFloat() / period.toFloat()).coerceIn(0f, 1f)
}
