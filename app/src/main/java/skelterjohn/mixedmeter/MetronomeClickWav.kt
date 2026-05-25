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

    private fun generateClickSamples(tone: String): ShortArray {
        val durationMs = 8
        val sampleCount = SAMPLE_RATE * durationMs / 1000
        val frequencyHz = if (tone == "Bop") 880.0 else 1_200.0
        val amplitude = Short.MAX_VALUE * 0.45
        return ShortArray(sampleCount) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = 1.0 - (i.toDouble() / sampleCount)
            (sin(2.0 * PI * frequencyHz * t) * envelope * amplitude).toInt().toShort()
        }
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
