package skelterjohn.mixedmeter

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

object MetronomeClickWav {
    /** Short metronome clicks do not need CD bandwidth; halves PCM size vs 44.1 kHz. */
    const val SAMPLE_RATE = 22_050

    fun clickSamples(tone: String): ShortArray = generateClickSamples(tone)

    fun cacheFile(context: Context, tone: String): File {
        val file = File(context.cacheDir, "metronome_${tone.lowercase()}.wav")
        if (!file.exists()) {
            writeMonoPcmWav(file, generateClickSamples(tone))
        }
        return file
    }

    private fun generateClickSamples(tone: String): ShortArray = when (tone) {
        "Snap" -> generateSnapSamples()
        "Thump" -> generateThumpSamples()
        "Bop" -> generateToneSamples(frequencyHz = 880.0)
        "Bip" -> generateToneSamples(frequencyHz = 1_200.0)
        else -> generateToneSamples(frequencyHz = 880.0)
    }

    private fun generateToneSamples(frequencyHz: Double): ShortArray {
        val durationMs = 8
        val sampleCount = SAMPLE_RATE * durationMs / 1000
        val amplitude = Short.MAX_VALUE * 0.45
        return ShortArray(sampleCount) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = 1.0 - (i.toDouble() / sampleCount)
            (sin(2.0 * PI * frequencyHz * t) * envelope * amplitude).toInt().toShort()
        }
    }

    /** Low fundamental with a soft decay — short bass-drum-like thump. */
    private fun generateThumpSamples(): ShortArray {
        val durationMs = 20
        val sampleCount = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
        val amplitude = Short.MAX_VALUE * 0.5
        val fundamentalHz = 80.0
        val subHz = 50.0
        return ShortArray(sampleCount) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = (1.0 - i.toDouble() / sampleCount).let { it * it }
            val body = sin(2.0 * PI * fundamentalHz * t) + 0.45 * sin(2.0 * PI * subHz * t)
            (body * envelope * amplitude / 1.45).toInt().toShort()
        }
    }

    /** Short noise burst — no pitched body, fast attack for a crisp snap. */
    private fun generateSnapSamples(): ShortArray {
        val durationMs = 5
        val sampleCount = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(1)
        val amplitude = Short.MAX_VALUE * 0.55
        return ShortArray(sampleCount) { i ->
            val t = i.toDouble() / sampleCount
            val envelope = (1.0 - t) * (1.0 - t) * (1.0 - t * 0.5)
            val noise = deterministicNoise(i)
            (noise * envelope * amplitude).toInt().toShort()
        }
    }

    private fun deterministicNoise(index: Int): Double {
        val x = (index * 1_103_515_245 + 12_345).toUInt()
        return (x shr 16).toDouble() / 32767.0 - 1.0
    }

    fun writeLoopWav(file: File, samples: ShortArray) {
        writeMonoPcmWav(file, samples)
    }

    private fun writeMonoPcmWav(file: File, samples: ShortArray) {
        val dataBytes = samples.size * 2
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            writeString(out, "RIFF")
            out.writeIntLE(36 + dataBytes)
            writeString(out, "WAVE")
            writeString(out, "fmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)
            out.writeShortLE(1)
            out.writeIntLE(SAMPLE_RATE)
            out.writeIntLE(SAMPLE_RATE * 2)
            out.writeShortLE(2)
            out.writeShortLE(16)
            writeString(out, "data")
            out.writeIntLE(dataBytes)
            for (sample in samples) {
                out.writeShortLE(sample.toInt())
            }
        }
    }

    private fun writeString(file: RandomAccessFile, value: String) {
        value.forEach { file.write(it.code) }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        writeByte(value and 0xff)
        writeByte((value shr 8) and 0xff)
        writeByte((value shr 16) and 0xff)
        writeByte((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        writeByte(value and 0xff)
        writeByte((value shr 8) and 0xff)
    }
}
