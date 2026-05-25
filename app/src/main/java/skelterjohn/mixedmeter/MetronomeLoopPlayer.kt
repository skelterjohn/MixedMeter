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
        fun start(fromPositionSeconds: Float = 0f)
        fun stop()
        fun release()
        fun isPlaying(): Boolean
        fun cyclePositionSeconds(cycleDurationSeconds: Float): Float
        fun seekToSeconds(seconds: Float, totalDurationSeconds: Float) {}
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

        override fun start(fromPositionSeconds: Float) {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.setPlaybackHeadPosition(secondsToFrame(fromPositionSeconds, cycleFrameCount))
            track.play()
        }

        override fun stop() {
            track.haltImmediately()
        }

        override fun release() {
            stop()
            track.release()
        }

        override fun isPlaying(): Boolean =
            track.playState == AudioTrack.PLAYSTATE_PLAYING

        override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING || cycleFrameCount <= 0) {
                return 0f
            }
            val head = track.playbackHeadPosition
            val inCycle = ((head % cycleFrameCount) + cycleFrameCount) % cycleFrameCount
            return inCycle.toFloat() / MetronomeClickWav.SAMPLE_RATE
        }

        override fun seekToSeconds(seconds: Float, totalDurationSeconds: Float) {
            try {
                track.setPlaybackHeadPosition(
                    secondsToFrame(seconds.coerceIn(0f, totalDurationSeconds), cycleFrameCount),
                )
            } catch (_: IllegalStateException) {
            }
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
        @Volatile
        private var feedLoopOffset = 0
        @Volatile
        private var timelineStartFrame = 0

        override fun prime() {
            feedLoopOffset = 0
            timelineStartFrame = 0
            writeFullLoop()
            track.setPlaybackHeadPosition(0)
            track.play()
            track.pause()
            track.setPlaybackHeadPosition(0)
        }

        override fun start(fromPositionSeconds: Float) {
            haltFeederAndJoin()
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
            track.setPlaybackHeadPosition(0)
            timelineStartFrame = secondsToFrame(fromPositionSeconds, cycleFrameCount)
            feedLoopOffset = timelineStartFrame
            if (timelineStartFrame == 0) {
                writeFullLoop()
            }
            startFeeder()
            track.play()
        }

        override fun seekToSeconds(seconds: Float, totalDurationSeconds: Float) {
            val wasPlaying = track.playState == AudioTrack.PLAYSTATE_PLAYING
            haltFeederAndJoin()
            track.flush()
            track.setPlaybackHeadPosition(0)
            timelineStartFrame = secondsToFrame(seconds.coerceIn(0f, totalDurationSeconds), cycleFrameCount)
            feedLoopOffset = timelineStartFrame
            startFeeder()
            if (wasPlaying) {
                track.play()
            }
        }

        override fun stop() {
            haltFeeder()
            track.haltImmediately()
        }

        /** Stop feeding without blocking — used for an instant user-facing halt. */
        private fun haltFeeder() {
            feeding = false
            feederThread?.interrupt()
        }

        private fun haltFeederAndJoin() {
            haltFeeder()
            feederThread?.join(500)
            feederThread = null
        }

        override fun release() {
            stop()
            track.release()
        }

        override fun isPlaying(): Boolean =
            track.playState == AudioTrack.PLAYSTATE_PLAYING

        override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING || cycleFrameCount <= 0) {
                return 0f
            }
            val head = playbackHeadFrames()
            val inCycle = ((timelineStartFrame + head) % cycleFrameCount + cycleFrameCount) %
                cycleFrameCount
            return inCycle.toFloat() / MetronomeClickWav.SAMPLE_RATE
        }

        private fun playbackHeadFrames(): Int {
            val timestamp = AudioTimestamp()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && track.getTimestamp(timestamp)) {
                return timestamp.framePosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            return track.playbackHeadPosition
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
                    var offset = feedLoopOffset
                    while (feeding) {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                            } else {
                                track.write(samples, offset, samples.size - offset)
                            }
                            if (written < 0) break
                            offset = 0
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
        private val looping: Boolean = true,
    ) : Backend {
        private val startNano = AtomicLong(0L)
        private var prepared = true

        override fun prime() {
            mediaPlayer.start()
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
            prepared = true
        }

        override fun start(fromPositionSeconds: Float) {
            if (!prepared) {
                mediaPlayer.prepare()
                prepared = true
            }
            val ms = (fromPositionSeconds.coerceAtLeast(0f) * 1000f).toInt()
            mediaPlayer.seekTo(ms)
            startNano.set(System.nanoTime())
            mediaPlayer.start()
        }

        override fun stop() {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
            } catch (_: IllegalStateException) {
            }
            prepared = false
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

        override fun isPlaying(): Boolean = mediaPlayer.isPlaying

        override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
            if (!mediaPlayer.isPlaying || cycleDurationSeconds <= 0f) return 0f
            return mediaPlayer.currentPosition.coerceAtLeast(0) / 1000f
        }

        override fun seekToSeconds(seconds: Float, totalDurationSeconds: Float) {
            val ms = (seconds.coerceIn(0f, totalDurationSeconds) * 1000f).toInt()
            try {
                mediaPlayer.seekTo(ms)
            } catch (_: IllegalStateException) {
            }
        }
    }

    fun start(fromPositionSeconds: Float = 0f) = backend.start(fromPositionSeconds)

    fun isPlaying(): Boolean = backend.isPlaying()

    fun stop() = backend.stop()

    fun release() {
        backend.release()
        loopFile?.delete()
    }

    fun cyclePositionSeconds(): Float = backend.cyclePositionSeconds(cycleDurationSeconds)

    fun cycleDurationSeconds(): Float = cycleDurationSeconds

    fun seekToSeconds(seconds: Float) {
        backend.seekToSeconds(seconds, cycleDurationSeconds)
    }

    companion object {
        private fun secondsToFrame(seconds: Float, frameCount: Int): Int {
            if (frameCount <= 0) return 0
            return (seconds.coerceAtLeast(0f) * MetronomeClickWav.SAMPLE_RATE)
                .toInt()
                .coerceIn(0, frameCount - 1)
        }

        /** Linear playback through [loop] once (no wrap). Used for full sequence prerenders. */
        fun createOneShot(context: Context, loop: MetronomeLoopRenderer.MetronomeLoop): MetronomeLoopPlayer {
            return try {
                buildOneShotStreamPlayer(loop)
            } catch (_: Exception) {
                buildOneShotMediaPlayer(context, loop)
            }
        }
        private fun AudioTrack.haltImmediately() {
            try {
                pause()
                flush()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stop()
                }
                setPlaybackHeadPosition(0)
            } catch (_: IllegalStateException) {
            }
        }

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

        private class OneShotStreamBackend(
            private val track: AudioTrack,
            private val samples: ShortArray,
            private val totalFrameCount: Int,
        ) : Backend {
            @Volatile
            private var feeding = false
            private var feederThread: Thread? = null
            @Volatile
            private var timelineStartFrame = 0

            override fun prime() {
                timelineStartFrame = 0
                track.setPlaybackHeadPosition(0)
                track.play()
                track.pause()
                track.setPlaybackHeadPosition(0)
            }

            override fun start(fromPositionSeconds: Float) {
                haltFeederAndJoin()
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
                track.setPlaybackHeadPosition(0)
                timelineStartFrame = secondsToFrame(fromPositionSeconds, totalFrameCount)
                startFeederOnce()
                track.play()
            }

            override fun stop() {
                haltFeeder()
                track.haltImmediately()
            }

            override fun release() {
                stop()
                track.release()
            }

            override fun isPlaying(): Boolean {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return false
                val head = currentHeadFrame()
                return timelineStartFrame + head < totalFrameCount
            }

            override fun cyclePositionSeconds(cycleDurationSeconds: Float): Float {
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return 0f
                val head = currentHeadFrame()
                return (timelineStartFrame + head)
                    .coerceAtMost(totalFrameCount)
                    .toFloat() / MetronomeClickWav.SAMPLE_RATE
            }

            override fun seekToSeconds(seconds: Float, totalDurationSeconds: Float) {
                val wasPlaying = track.playState == AudioTrack.PLAYSTATE_PLAYING
                haltFeederAndJoin()
                track.flush()
                track.setPlaybackHeadPosition(0)
                timelineStartFrame = secondsToFrame(seconds.coerceIn(0f, totalDurationSeconds), totalFrameCount)
                startFeederOnce()
                if (wasPlaying) {
                    track.play()
                }
            }

            private fun currentHeadFrame(): Int {
                val timestamp = AudioTimestamp()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && track.getTimestamp(timestamp)) {
                    return timestamp.framePosition.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                return track.playbackHeadPosition
            }

            private fun haltFeeder() {
                feeding = false
                feederThread?.interrupt()
            }

            private fun haltFeederAndJoin() {
                haltFeeder()
                feederThread?.join(500)
                feederThread = null
            }

            private fun startFeederOnce() {
                feeding = true
                feederThread = Thread(
                    {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                        var offset = timelineStartFrame
                        while (feeding && offset < samples.size) {
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                val remaining = samples.size - offset
                                val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    track.write(samples, offset, remaining, AudioTrack.WRITE_BLOCKING)
                                } else {
                                    track.write(samples, offset, remaining)
                                }
                                if (written <= 0) break
                                offset += written
                            } else {
                                LockSupport.parkNanos(1_000_000L)
                            }
                        }
                        feeding = false
                    },
                    "MixedMeterSequenceFeed",
                ).apply { start() }
            }
        }

        private fun buildOneShotStreamPlayer(loop: MetronomeLoopRenderer.MetronomeLoop): MetronomeLoopPlayer {
            val sampleRate = MetronomeClickWav.SAMPLE_RATE
            val samples = loop.samples
            val totalFrameCount = samples.size.coerceAtLeast(1)

            val minBufferBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            require(minBufferBytes > 0) { "Invalid min buffer size" }

            val bufferBytes = maxOf(minBufferBytes, minBufferBytes * 4)

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
                throw IllegalStateException("One-shot stream AudioTrack not initialized")
            }

            val player = MetronomeLoopPlayer(
                backend = OneShotStreamBackend(track, samples, totalFrameCount),
                cycleDurationSeconds = loop.cycleDurationSeconds,
                loopFile = null,
            )
            player.backend.prime()
            return player
        }

        private fun buildOneShotMediaPlayer(
            context: Context,
            loop: MetronomeLoopRenderer.MetronomeLoop,
        ): MetronomeLoopPlayer {
            val loopFile = File(context.cacheDir, "metronome_sequence.wav")
            MetronomeClickWav.writeLoopWav(loopFile, loop.samples)

            val mediaPlayer = MediaPlayer()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mediaPlayer.setDataSource(loopFile.absolutePath)
            mediaPlayer.isLooping = false
            mediaPlayer.prepare()

            val player = MetronomeLoopPlayer(
                backend = MediaPlayerBackend(mediaPlayer, looping = false),
                cycleDurationSeconds = loop.cycleDurationSeconds,
                loopFile = loopFile,
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
