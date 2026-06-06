package skelterjohn.mixedmeter

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SubdivisionRenderTest {

    @Test
    fun plainMetronome_subdivision2_placesClickBetweenBeats() {
        val schedule = buildMetronomeClickSchedule(120f, 1f, emptyList())
        val loop = MetronomeLoopRenderer.render(
            schedule = schedule,
            beatTone = "Bop",
            leadTone = "Bop",
            subdivision = 2,
            subdivisionTone = "Bip",
        )
        val periodFrames = (0.5 * MetronomeClickWav.SAMPLE_RATE).toInt()
        val mid = periodFrames / 2
        val maxNearMid = (mid - 20..mid + 200)
            .filter { it in loop.samples.indices }
            .maxOf { abs(loop.samples[it].toInt()) }
        val maxAtZero = (0..200).maxOf { abs(loop.samples[it].toInt()) }
        assertTrue("expected subdivision click near mid-beat", maxNearMid > 1000)
        assertTrue("expected beat click at start", maxAtZero > 1000)
    }

    @Test
    fun meterPattern_subdivision3_placesClicksWithinBeat() {
        val schedule = buildMetronomeClickSchedule(
            bpm = 120f,
            selectedNoteValue = 1f,
            timeSignatures = listOf(TimeSignature(4, 4)),
        )
        val loop = MetronomeLoopRenderer.render(
            schedule = schedule,
            beatTone = "Bop",
            leadTone = "Bop",
            subdivision = 3,
            subdivisionTone = "Bip",
        )
        val beatDurationFrames = (0.5 * MetronomeClickWav.SAMPLE_RATE).toInt()
        val third = beatDurationFrames / 3
        val maxNearThird = (third - 20..third + 200)
            .filter { it in loop.samples.indices }
            .maxOf { abs(loop.samples[it].toInt()) }
        assertTrue("expected subdivision click near first third", maxNearThird > 1000)
    }
}
