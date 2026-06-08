package skelterjohn.mixedmeter

import org.junit.Assert.assertEquals
import org.junit.Test

class SequenceBpmTest {
    @Test
    fun storedSequenceBpm_roundsToNearestInteger() {
        assertEquals(120f, storedSequenceBpm(119.6f))
        assertEquals(120f, storedSequenceBpm(120.4f))
        assertEquals(121f, storedSequenceBpm(120.5f))
    }

    @Test
    fun metronomeSnapshot_storesIntegerBpm_forMeterPattern() {
        val item = metronomeSnapshot(
            bpm = 120.7f,
            selectedNote = "♩",
            timeSignatures = listOf(TimeSignature(4, 4)),
        ) as SequenceItem.MeterPattern

        assertEquals(121f, item.bpm)
    }

    @Test
    fun displayBpmAtPercent_at100_matchesStoredInteger() {
        val item = SequenceItem.MeterPattern(
            bpm = 120f,
            selectedNote = "♩",
            timeSignatures = listOf(TimeSignature(4, 4)),
        )

        assertEquals(120, item.displayBpmAtPercent(100f))
        assertEquals(120f, scaledSequenceBpm(item.bpm, 100f))
    }
}
