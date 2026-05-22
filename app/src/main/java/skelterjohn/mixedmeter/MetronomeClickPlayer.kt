package skelterjohn.mixedmeter

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin

/**
 * Short pre-buffered PCM click via alternating [AudioTrack]s so rapid beats avoid stop/start jitter.
 */
class MetronomeClickPlayer(
    private val useBeepTone: Boolean,
) {
    private val sampleRate = 44_100
    private val clickSamples: ShortArray = generateClickSamples(useBeepTone)
    private val tracks: Array<AudioTrack> = arrayOf(
        createAudioTrack(clickSamples.size * 2),
        createAudioTrack(clickSamples.size * 2),
    )
    private var nextTrackIndex = 0

    init {
        tracks.forEach { it.write(clickSamples, 0, clickSamples.size) }
    }

    /** Prime both tracks so the first audible [play] starts sooner. */
    fun warmUp() {
        tracks.forEach { track ->
            if (track.state != AudioTrack.STATE_INITIALIZED) return@forEach
            track.setStereoVolume(0f, 0f)
            track.setPlaybackHeadPosition(0)
            track.play()
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.setStereoVolume(1f, 1f)
        }
    }

    fun play() {
        val track = tracks[nextTrackIndex]
        nextTrackIndex = 1 - nextTrackIndex
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
        }
        track.setPlaybackHeadPosition(0)
        track.play()
    }

    fun release() {
        tracks.forEach { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
                track.release()
            }
        }
    }

    private fun createAudioTrack(bufferBytes: Int): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val bufferSize = bufferBytes.coerceAtLeast(minBufferSize)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }
    }

    private fun generateClickSamples(beep: Boolean): ShortArray {
        val durationMs = 6
        val sampleCount = sampleRate * durationMs / 1000
        val frequencyHz = if (beep) 880.0 else 1_200.0
        val amplitude = Short.MAX_VALUE * 0.45
        return ShortArray(sampleCount) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = 1.0 - (i.toDouble() / sampleCount)
            (sin(2.0 * PI * frequencyHz * t) * envelope * amplitude).toInt().toShort()
        }
    }
}
