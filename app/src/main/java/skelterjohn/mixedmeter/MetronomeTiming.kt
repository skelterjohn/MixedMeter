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

/** Minimum plain-BPM loop length — must match [MetronomeLoopRenderer]. */
const val MIN_PLAIN_LOOP_CYCLE_NANOS = 500_000_000L

private const val BEAT_BOUNDARY_EPSILON_SECONDS = 1e-4f

/** One hardware loop cycle for plain BPM (multi-beat); meter loops use [MetronomeClickSchedule.totalCycleNanos]. */
fun plainLoopCycleNanos(schedule: MetronomeClickSchedule): Long {
    if (schedule.totalCycleNanos > 0L) return schedule.totalCycleNanos
    val periodNanos = schedule.beatPeriodNanos
    val beatCount = ((MIN_PLAIN_LOOP_CYCLE_NANOS + periodNanos - 1) / periodNanos)
        .coerceAtLeast(1)
    return beatCount * periodNanos
}

fun loopCycleSeconds(schedule: MetronomeClickSchedule): Float {
    return plainLoopCycleNanos(schedule) / 1_000_000_000f
}

private fun loopPositionSeconds(positionSeconds: Float, totalDuration: Float): Float {
    if (totalDuration <= 0f) return positionSeconds
    val wrapped = positionSeconds % totalDuration
    return if (wrapped < 0f) wrapped + totalDuration else wrapped
}

/** Beat box containing [positionSeconds] within one cycle (handles boundary floats). */
fun beatBoxAt(positionSeconds: Float, schedule: MetronomeClickSchedule): BeatBoxTiming? {
    if (schedule.totalCycleNanos <= 0L || schedule.boxes.isEmpty()) return null
    val totalDuration = schedule.totalCycleNanos / 1_000_000_000f
    val position = loopPositionSeconds(positionSeconds, totalDuration)
    return schedule.boxes.lastOrNull { box ->
        position >= box.startTime - BEAT_BOUNDARY_EPSILON_SECONDS &&
            position <= box.startTime + box.duration + BEAT_BOUNDARY_EPSILON_SECONDS
    }
}

/** Seconds until the current beat/box ends at [positionSeconds] in the loop. */
fun remainingSecondsInCurrentBeat(
    positionSeconds: Float,
    schedule: MetronomeClickSchedule,
): Float {
    if (schedule.totalCycleNanos > 0L && schedule.boxes.isNotEmpty()) {
        val totalDuration = schedule.totalCycleNanos / 1_000_000_000f
        val position = loopPositionSeconds(positionSeconds, totalDuration)
        beatBoxAt(positionSeconds, schedule)?.let { box ->
            return (box.startTime + box.duration - position).coerceAtLeast(0f)
        }
        val nextBox = schedule.boxes.firstOrNull { box -> box.startTime > position }
        if (nextBox != null) {
            return (nextBox.startTime - position).coerceAtLeast(0f)
        }
        return (totalDuration - position).coerceAtLeast(0f)
    }
    val loopDuration = loopCycleSeconds(schedule)
    val period = schedule.beatPeriodNanos / 1_000_000_000f
    if (period <= 0f) return 0f
    val position = loopPositionSeconds(positionSeconds, loopDuration)
    val inBeat = loopPositionSeconds(position, period)
    return (period - inBeat).coerceAtLeast(0f)
}

/** Loop time (seconds) at the end of the beat/box containing [positionSeconds]. */
fun endOfBeatContaining(positionSeconds: Float, schedule: MetronomeClickSchedule): Float {
    if (schedule.totalCycleNanos > 0L && schedule.boxes.isNotEmpty()) {
        val box = beatBoxAt(positionSeconds, schedule) ?: return 0f
        return box.startTime + box.duration
    }
    val loopDuration = loopCycleSeconds(schedule)
    val period = schedule.beatPeriodNanos / 1_000_000_000f
    if (period <= 0f) return 0f
    val position = loopPositionSeconds(positionSeconds, loopDuration)
    val inBeat = loopPositionSeconds(position, period)
    return position - inBeat + period
}

/** True once playback has moved past the beat that contained [startPositionSeconds]. */
fun hasCrossedBeatBoundary(
    startPositionSeconds: Float,
    currentPositionSeconds: Float,
    schedule: MetronomeClickSchedule,
): Boolean {
    if (schedule.totalCycleNanos > 0L && schedule.boxes.isNotEmpty()) {
        val totalDuration = schedule.totalCycleNanos / 1_000_000_000f
        val start = loopPositionSeconds(startPositionSeconds, totalDuration)
        val current = loopPositionSeconds(currentPositionSeconds, totalDuration)
        val beatEnd = endOfBeatContaining(startPositionSeconds, schedule)
        if (beatEnd <= 0f) return false
        return if (start < beatEnd) {
            current >= beatEnd - BEAT_BOUNDARY_EPSILON_SECONDS || current < start
        } else {
            current >= beatEnd - BEAT_BOUNDARY_EPSILON_SECONDS
        }
    }
    val loopDuration = loopCycleSeconds(schedule)
    if (loopDuration <= 0f) return false
    val period = schedule.beatPeriodNanos / 1_000_000_000f
    if (period <= 0f) return false
    val start = loopPositionSeconds(startPositionSeconds, loopDuration)
    val current = loopPositionSeconds(currentPositionSeconds, loopDuration)
    val inBeat = loopPositionSeconds(start, period)
    val beatEnd = start - inBeat + period
    return if (start < beatEnd) {
        current >= beatEnd - BEAT_BOUNDARY_EPSILON_SECONDS || current < start
    } else {
        current >= beatEnd - BEAT_BOUNDARY_EPSILON_SECONDS
    }
}

/** Nanoseconds per box; single division avoids float drift across many short beats (e.g. 9/16). */
fun boxDurationNanos(bpm: Float, denominator: Int, selectedNoteValue: Float): Long {
    if (bpm <= 0f || denominator <= 0 || selectedNoteValue <= 0f) return 0L
    return (60_000_000_000.0 / bpm / denominator / selectedNoteValue).toLong().coerceAtLeast(1L)
}

