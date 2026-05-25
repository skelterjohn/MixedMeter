package skelterjohn.mixedmeter

/**
 * Builds one PCM cycle of the full meter pattern with clicks at sample-accurate positions.
 */
object MetronomeLoopRenderer {

    data class MetronomeLoop(
        val samples: ShortArray,
        val cycleFrameCount: Int,
        val cycleDurationSeconds: Float,
    )

    fun render(
        schedule: MetronomeClickSchedule,
        beatTone: String,
        leadTone: String,
    ): MetronomeLoop {
        val beatClick = MetronomeClickWav.clickSamples(beatTone)
        val leadClick = if (beatTone == leadTone) {
            beatClick
        } else {
            MetronomeClickWav.clickSamples(leadTone)
        }

        val hasTimeSignatures =
            schedule.totalCycleNanos > 0L && schedule.clickOffsetsNanos.isNotEmpty()

        val cycleNanos = plainLoopCycleNanos(schedule)

        val cycleFrameCount = nanosToFrames(cycleNanos).coerceAtLeast(1)
        val buffer = ShortArray(cycleFrameCount)

        if (hasTimeSignatures) {
            for (i in schedule.clickOffsetsNanos.indices) {
                val click = if (schedule.clickUseLeadTone.getOrElse(i) { false }) {
                    leadClick
                } else {
                    beatClick
                }
                val startFrame = nanosToFrames(schedule.clickOffsetsNanos[i])
                    .coerceIn(0, cycleFrameCount - 1)
                mixAt(buffer, startFrame, click)
            }
        } else {
            val periodNanos = schedule.beatPeriodNanos
            val beatCount = (cycleNanos / periodNanos).toInt()
            for (beat in 0 until beatCount) {
                val click = if (beat == 0) leadClick else beatClick
                val startFrame = nanosToFrames(beat * periodNanos)
                    .coerceIn(0, cycleFrameCount - 1)
                mixAt(buffer, startFrame, click)
            }
        }

        seamLoopBoundary(buffer)

        return MetronomeLoop(
            samples = buffer,
            cycleFrameCount = cycleFrameCount,
            cycleDurationSeconds = cycleFrameCount.toFloat() / MetronomeClickWav.SAMPLE_RATE,
        )
    }

    /** Fade the tail toward zero so a loop wrap back to sample 0 is not a click. */
    fun fadeTailForLoopWrap(buffer: ShortArray) {
        seamLoopBoundary(buffer)
    }

    private fun seamLoopBoundary(buffer: ShortArray) {
        val fadeFrames = (MetronomeClickWav.SAMPLE_RATE / 200).coerceAtMost(buffer.size / 4)
        for (i in 0 until fadeFrames) {
            val gain = 1f - (i + 1).toFloat() / fadeFrames
            val index = buffer.size - 1 - i
            buffer[index] = (buffer[index] * gain).toInt().toShort()
        }
    }

    private fun nanosToFrames(nanos: Long): Int {
        return ((nanos * MetronomeClickWav.SAMPLE_RATE) / 1_000_000_000L).toInt()
    }

    private fun mixAt(destination: ShortArray, destinationOffset: Int, source: ShortArray) {
        for (i in source.indices) {
            val index = destinationOffset + i
            if (index >= destination.size) break
            val mixed = destination[index].toInt() + source[i].toInt()
            destination[index] = mixed.coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt(),
            ).toShort()
        }
    }
}
