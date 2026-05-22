package skelterjohn.mixedmeter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot clicks via [SoundPool], with [ToneGenerator] fallback if loading fails.
 */
class MetronomeClickPlayer(
    context: Context,
    private val useBeepTone: Boolean,
) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundId: Int
    private val isReady = AtomicBoolean(false)
    private val loadedLatch = CountDownLatch(1)
    private val useToneFallback: Boolean
    private val toneGenerator: ToneGenerator?

    init {
        val wavFile = MetronomeClickWav.cacheFile(context, useBeepTone)
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId > 0 && status == 0) {
                isReady.set(true)
                loadedLatch.countDown()
            }
        }
        soundId = soundPool.load(wavFile.absolutePath, 1)
        if (soundId == 0) {
            Log.e(TAG, "SoundPool failed to load click from ${wavFile.absolutePath}, using ToneGenerator")
            useToneFallback = true
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            loadedLatch.countDown()
        } else {
            useToneFallback = false
            toneGenerator = null
        }
    }

    /** Block until the click sample is ready (call from the metronome thread, not the UI thread). */
    fun ensureReady() {
        loadedLatch.await(3, TimeUnit.SECONDS)
    }

    fun play() {
        if (useToneFallback) {
            val toneType = if (useBeepTone) {
                ToneGenerator.TONE_PROP_BEEP
            } else {
                ToneGenerator.TONE_CDMA_PIP
            }
            toneGenerator?.startTone(toneType, 30)
            return
        }
        if (!isReady.get() || soundId == 0) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        toneGenerator?.release()
        soundPool.release()
    }

    private companion object {
        private const val TAG = "MetronomeClickPlayer"
    }
}
