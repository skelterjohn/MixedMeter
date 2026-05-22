package skelterjohn.mixedmeter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One-shot clicks via [SoundPool], with [ToneGenerator] fallback if loading fails.
 * Supports separate beat and lead tones (same bip/beep choices).
 */
class MetronomeClickPlayer(
    context: Context,
    private val useBeepBeatTone: Boolean,
    private val useBeepLeadTone: Boolean,
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

    private val beatSoundId: Int
    private val leadSoundId: Int
    private val loadedLatch = CountDownLatch(2)
    private val useToneFallback: Boolean
    private val toneGenerator: ToneGenerator?

    init {
        val beatWav = MetronomeClickWav.cacheFile(context, useBeepBeatTone)
        val leadWav = MetronomeClickWav.cacheFile(context, useBeepLeadTone)
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId > 0 && status == 0) {
                loadedLatch.countDown()
            }
        }
        beatSoundId = soundPool.load(beatWav.absolutePath, 1)
        leadSoundId = if (beatWav.absolutePath == leadWav.absolutePath) {
            beatSoundId
        } else {
            soundPool.load(leadWav.absolutePath, 1)
        }
        if (beatSoundId == 0 || (leadSoundId == 0 && leadSoundId != beatSoundId)) {
            Log.e(TAG, "SoundPool failed to load click samples, using ToneGenerator")
            useToneFallback = true
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            repeat(2) { loadedLatch.countDown() }
        } else {
            useToneFallback = false
            toneGenerator = null
            if (leadSoundId == beatSoundId) {
                loadedLatch.countDown()
            }
        }
    }

    /** Block until click samples are ready (call from the metronome thread, not the UI thread). */
    fun ensureReady() {
        loadedLatch.await(3, TimeUnit.SECONDS)
    }

    fun play(useLeadTone: Boolean = false) {
        if (useToneFallback) {
            val useBeep = if (useLeadTone) useBeepLeadTone else useBeepBeatTone
            val toneType = if (useBeep) {
                ToneGenerator.TONE_PROP_BEEP
            } else {
                ToneGenerator.TONE_CDMA_PIP
            }
            toneGenerator?.startTone(toneType, 30)
            return
        }
        val soundId = if (useLeadTone) leadSoundId else beatSoundId
        if (soundId == 0) return
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
