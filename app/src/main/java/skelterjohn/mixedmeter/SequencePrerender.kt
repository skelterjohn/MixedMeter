package skelterjohn.mixedmeter

/**
 * One contiguous PCM buffer for an entire sequence (all items × repeats), played once
 * without looping so the downbeat at sample 0 is not heard again until the next run.
 */
data class SequenceSegment(
    val startTimeSeconds: Float,
    val endTimeSeconds: Float,
    val itemIndex: Int,
    val repeatIndex: Int,
    val schedule: MetronomeClickSchedule,
    val displayBpm: Float,
)

data class SequencePrerender(
    val samples: ShortArray,
    val totalFrameCount: Int,
    val durationSeconds: Float,
    val segments: List<SequenceSegment>,
)

fun segmentAt(positionSeconds: Float, segments: List<SequenceSegment>): SequenceSegment? {
    return segments.lastOrNull { segment ->
        positionSeconds >= segment.startTimeSeconds - 1e-4f &&
            positionSeconds < segment.endTimeSeconds - 1e-4f
    }
}

fun segmentForRepeat(
    itemIndex: Int,
    repeatIndex: Int,
    segments: List<SequenceSegment>,
): SequenceSegment? {
    return segments.firstOrNull { segment ->
        segment.itemIndex == itemIndex && segment.repeatIndex == repeatIndex
    }
}

fun renderSequence(
    items: List<SequenceItem>,
    useBeepBeatTone: Boolean,
    useBeepLeadTone: Boolean,
): SequencePrerender? {
    if (items.isEmpty()) return null

    val measureLoops = mutableListOf<MetronomeLoopRenderer.MetronomeLoop>()
    val segments = mutableListOf<SequenceSegment>()
    var startTimeSeconds = 0f

    items.forEachIndexed { itemIndex, item ->
        val schedule = item.metronomeSchedule()
        val loop = MetronomeLoopRenderer.render(
            schedule = schedule,
            useBeepBeatTone = useBeepBeatTone,
            useBeepLeadTone = useBeepLeadTone,
        )
        repeat(item.repeatCount) { repeatIndex ->
            segments.add(
                SequenceSegment(
                    startTimeSeconds = startTimeSeconds,
                    endTimeSeconds = startTimeSeconds + loop.cycleDurationSeconds,
                    itemIndex = itemIndex,
                    repeatIndex = repeatIndex,
                    schedule = schedule,
                    displayBpm = item.displayBpm(),
                ),
            )
            measureLoops.add(loop)
            startTimeSeconds += loop.cycleDurationSeconds
        }
    }

    val totalFrameCount = measureLoops.sumOf { it.samples.size }
    if (totalFrameCount <= 0) return null

    val buffer = ShortArray(totalFrameCount)
    var frameOffset = 0
    for (loop in measureLoops) {
        loop.samples.copyInto(buffer, destinationOffset = frameOffset)
        frameOffset += loop.samples.size
    }
    MetronomeLoopRenderer.fadeTailForLoopWrap(buffer)

    return SequencePrerender(
        samples = buffer,
        totalFrameCount = totalFrameCount,
        durationSeconds = totalFrameCount.toFloat() / MetronomeClickWav.SAMPLE_RATE,
        segments = segments,
    )
}
