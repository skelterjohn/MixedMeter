package skelterjohn.mixedmeter

import android.content.Context
import java.io.InputStream

/** Loads mono PCM WAV clicks from [ASSET_DIR]; every *.wav file becomes a tone option. */
object MetronomeToneAssets {
    private const val ASSET_DIR = "metronome-tones"

    val builtInTones = listOf("Bop", "Bip", "Snap", "Thump")

    @Volatile
    private var assetSamples: Map<String, ShortArray> = emptyMap()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            assetSamples = loadAssetTones(context.applicationContext)
            loaded = true
        }
    }

    fun toneOptions(): List<String> = builtInTones + assetSamples.keys.sorted()

    fun samplesFor(tone: String): ShortArray? = assetSamples[tone]

    fun effectiveTone(stored: String?, default: String): String {
        val options = toneOptions()
        if (stored != null && stored in options) return stored
        return default
    }

    private fun loadAssetTones(context: Context): Map<String, ShortArray> {
        val assetManager = context.assets
        val filenames = try {
            assetManager.list(ASSET_DIR).orEmpty()
        } catch (_: Exception) {
            emptyArray()
        }
        val tones = linkedMapOf<String, ShortArray>()
        for (filename in filenames.sorted()) {
            if (!filename.endsWith(".wav", ignoreCase = true)) continue
            val toneName = toneNameFromFilename(filename)
            if (toneName in tones) continue
            val path = "$ASSET_DIR/$filename"
            val samples = try {
                assetManager.open(path).use { parseMonoPcmWav(it) }
            } catch (_: Exception) {
                null
            } ?: continue
            tones[toneName] = samples
        }
        return tones
    }

    private fun toneNameFromFilename(filename: String): String {
        val base = filename.substringBeforeLast('.')
        return base.split('_').joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
    }

    private fun parseMonoPcmWav(input: InputStream): ShortArray? {
        val bytes = input.readBytes()
        if (bytes.size < 44) return null
        if (!bytes.readString(0, 4).equals("RIFF", ignoreCase = true)) return null
        if (!bytes.readString(8, 4).equals("WAVE", ignoreCase = true)) return null

        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.readString(offset, 4)
            val chunkSize = bytes.readIntLE(offset + 4)
            val chunkDataOffset = offset + 8
            if (chunkDataOffset > bytes.size) break
            when (chunkId.lowercase()) {
                "fmt " -> {
                    if (chunkSize >= 16 && chunkDataOffset + 16 <= bytes.size) {
                        channels = bytes.readShortLE(chunkDataOffset + 2).toInt()
                        sampleRate = bytes.readIntLE(chunkDataOffset + 4)
                        bitsPerSample = bytes.readShortLE(chunkDataOffset + 14).toInt()
                    }
                }
                "data" -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize.coerceAtMost(bytes.size - chunkDataOffset)
                }
            }
            offset = chunkDataOffset + chunkSize + (chunkSize and 1)
        }

        if (sampleRate != MetronomeClickWav.SAMPLE_RATE) return null
        if (bitsPerSample != 16 || channels !in 1..2) return null
        if (dataOffset < 0 || dataSize < 2) return null

        val frameSize = channels * 2
        val frameCount = dataSize / frameSize
        if (frameCount <= 0) return null

        val samples = ShortArray(frameCount)
        var byteIndex = dataOffset
        for (i in 0 until frameCount) {
            val left = bytes.readShortLE(byteIndex)
            byteIndex += 2
            samples[i] = if (channels == 1) {
                left
            } else {
                val right = bytes.readShortLE(byteIndex)
                byteIndex += 2
                ((left.toInt() + right.toInt()) / 2).toShort()
            }
        }
        return normalizePeak(samples)
    }

    private fun normalizePeak(samples: ShortArray): ShortArray {
        var peak = 1
        for (sample in samples) {
            peak = maxOf(peak, kotlin.math.abs(sample.toInt()))
        }
        if (peak <= 1) return samples
        val targetPeak = (Short.MAX_VALUE * MetronomeClickWav.CLICK_PEAK).toInt()
        val scale = targetPeak.toDouble() / peak
        return ShortArray(samples.size) { i ->
            (samples[i] * scale).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun ByteArray.readString(offset: Int, length: Int): String =
        String(copyOfRange(offset, offset + length), Charsets.US_ASCII)

    private fun ByteArray.readIntLE(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.readShortLE(offset: Int): Short {
        val value = (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)
        return value.toShort()
    }
}
