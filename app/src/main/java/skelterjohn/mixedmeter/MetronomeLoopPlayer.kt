package skelterjohn.mixedmeter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Process
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/**
 * Gapless loop playback: tries hardware-looped static [AudioTrack], then stream + feeder thread,
 * then [MediaPlayer] as last resort.
 */
class MetronomeLoopPlayer private constructor(
    private val backend: Backend,
    private val cycleDurationSeconds: Float,
    private val loopFile: File?,
) {
    private interface Backend {
        fun prime()
        fun start()
        fun stop()
        fun release()
        fun cyclePositionSeconds(cycleDurationSeconds: Float): Float
    }

    /** Hardware loop — gapless when the device accepts static mode. */
    private class StaticTrackBackend(
        private val track: AudioTrack,
        private val cycleFrameCount: Int,
    ) : Backend {
        override fun prime() {
            track.setPlaybackHeadPosition(0)
            track.play()
            track.pause()
            track.setPlaybackHeadPosition(0)
        }

        override fun start() {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.setPlaybackHeadPosition(0)
            track.play()
        }

        override fun stop() {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.setPlaybackHeadPosition(0)
        }

        override fun release() {
            stop()
            track.release()
        }

        override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING || cycleFrameCount <= 0) {
                return 0f
            }
            val head = track.playbackHeadPosition
            val inCycle = ((head % cycleFrameCount) + cycleFrameCount) % cycleFrameCount
            return inCycle.toFloat() / MetronomeClickWav.SAMPLE_RATE
        }
    }

    /** Continuously writes the cycle into a stream track — gapless, no flush on play. */
    private class StreamFeederBackend(
        private val track: AudioTrack,
        private val samples: ShortArray,
        private val cycleFrameCount: Int,
    ) : Backend {
        @Volatile
        private var feeding = false
        private var feederThread: Thread? = null

        override fun prime() {
            writeFullLoop()
            track.setPlaybackHeadPosition(0)
            track.play()
            track.pause()
            track.setPlaybackHeadPosition(0)
        }

        override fun start() {
            haltFeeder()
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
            track.setPlaybackHeadPosition(0)
            writeFullLoop()
            startFeeder()
            track.play()
        }

        override fun stop() {
            haltFeeder()
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
            track.setPlaybackHeadPosition(0)
        }

        private fun haltFeeder() {
            feeding = false
            feederThread?.join(500)
            feederThread = null
        }

        override fun release() {
            stop()
            track.release()
        }

        override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING || cycleFrameCount <= 0) {
                return 0f
            }
            val timestamp = AudioTimestamp()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && track.getTimestamp(timestamp)) {
                val inCycle = ((timestamp.framePosition % cycleFrameCount) + cycleFrameCount) %
                    cycleFrameCount
                return inCycle.toFloat() / MetronomeClickWav.SAMPLE_RATE
            }
            val head = track.playbackHeadPosition
            val inCycle = ((head % cycleFrameCount) + cycleFrameCount) % cycleFrameCount
            return inCycle.toFloat() / MetronomeClickWav.SAMPLE_RATE
        }

        private fun writeFullLoop() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            } else {
                track.write(samples, 0, samples.size)
            }
        }

        private fun startFeeder() {
            feeding = true
            feederThread = Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    while (feeding) {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                            } else {
                                track.write(samples, 0, samples.size)
                            }
                            if (written < 0) break
                        } else {
                            LockSupport.parkNanos(1_000_000L)
                        }
                    }
                },
                "MixedMeterLoopFeed",
            ).apply { start() }
        }
    }

    private class MediaPlayerBackend(
        private val mediaPlayer: MediaPlayer,
    ) : Backend {
        private val startNano = AtomicLong(0L)

        override fun prime() {
            mediaPlayer.start()
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
        }

        override fun start() {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
            mediaPlayer.seekTo(0)
            startNano.set(System.nanoTime())
            mediaPlayer.start()
        }

        override fun stop() {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
            mediaPlayer.seekTo(0)
            startNano.set(0L)
        }

        override fun release() {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            } catch (_: IllegalStateException) {
            }
            mediaPlayer.release()
        }

        override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
            if (!mediaPlayer.isPlaying || cycleDurationSeconds <= 0f) return 0f
            val cycleNanos = (cycleDurationSeconds * 1_000_000_000L).toLong()
            val elapsed = System.nanoTime() - startNano.get()
            return ((elapsed % cycleNanos).toFloat() / 1_000_000_000f).coerceAtLeast(0f)
        }
    }

    fun start() = backend.start()

    fun stop() = backend.stop()

    fun release() {
        backend.release()
        loopFile?.delete()
    }

    fun cyclePositionSeconds(): Float = backend.cyclePositionSeconds(cycleDurationSeconds)

    fun cycleDurationSeconds(): Float = cycleDurationSeconds

    companion object {
        fun create(context: Context, loop: MetronomeLoopRenderer.MetronomeLoop): MetronomeLoopPlayer {
            buildStaticPlayer(loop)?.let { return it }
            try {
                return buildStreamPlayer(loop)
            } catch (_: Exception) {
                return buildMediaPlayer(context, loop)
            }
        }

        private fun buildStaticPlayer(loop: MetronomeLoopRenderer.MetronomeLoop): MetronomeLoopPlayer? {
            val samples = loop.samples
            val cycleFrameCount = loop.cycleFrameCount.coerceAtLeast(1)
            val sampleRate = MetronomeClickWav.SAMPLE_RATE

            val minBufferBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferBytes <= 0) return null

            val minFrames = (minBufferBytes + 1) / 2
            val bufferFrames = maxOf(cycleFrameCount, minFrames)
            val padded = if (samples.size < bufferFrames) samples.copyOf(bufferFrames) else samples
            val bufferBytes = padded.size * 2

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return null
            }

            val written = track.write(padded, 0, padded.size)
            if (written < 0) {
                track.release()
                return null
            }

            track.setLoopPoints(0, cycleFrameCount, 0)

            val player = MetronomeLoopPlayer(
                backend = StaticTrackBackend(track, cycleFrameCount),
                cycleDurationSeconds = loop.cycleDurationSeconds,
                loopFile = null,
            )
            player.backend.prime()
            return player
        }

        private fun buildStreamPlayer(loop: MetronomeLoopRenderer.MetronomeLoop): MetronomeLoopPlayer {
            val sampleRate = MetronomeClickWav.SAMPLE_RATE
            val samples = loop.samples
            val cycleFrameCount = loop.cycleFrameCount.coerceAtLeast(1)

            val minBufferBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            require(minBufferBytes > 0) { "Invalid min buffer size" }

            val bufferBytes = maxOf(minBufferBytes, samples.size * 2 * 2)

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("Stream AudioTrack not initialized")
            }

            val player = MetronomeLoopPlayer(
                backend = StreamFeederBackend(track, samples, cycleFrameCount),
                cycleDurationSeconds = loop.cycleDurationSeconds,
                loopFile = null,
            )
            player.backend.prime()
            return player
        }

        private fun buildMediaPlayer(
            context: Context,
            loop: MetronomeLoopRenderer.MetronomeLoop,
        ): MetronomeLoopPlayer {
            val loopFile = File(context.cacheDir, "metronome_loop.wav")
            MetronomeClickWav.writeLoopWav(loopFile, loop.samples)

            val mediaPlayer = MediaPlayer()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mediaPlayer.setDataSource(loopFile.absolutePath)
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()

            val player = MetronomeLoopPlayer(
                backend = MediaPlayerBackend(mediaPlayer),
                cycleDurationSeconds = loop.cycleDurationSeconds,
                loopFile = loopFile,
            )
            player.backend.prime()
            return player
        }
    }
}
