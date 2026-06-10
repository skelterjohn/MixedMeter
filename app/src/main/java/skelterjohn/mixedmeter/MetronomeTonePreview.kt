package skelterjohn.mixedmeter

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/** Plays a single metronome click for tone selection preview. */
object MetronomeTonePreview {
    private const val MIN_PREVIEW_MS = 100

    @Volatile
    private var activePlayer: MediaPlayer? = null

    private var previewFile: File? = null

    fun play(context: Context, tone: String) {
        stop()

        val samples = previewSamples(tone)
        if (samples.isEmpty()) return

        val file = File(context.cacheDir, "tone_preview.wav")
        MetronomeClickWav.writeLoopWav(file, samples)

        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { completed ->
                synchronized(this) {
                    if (activePlayer === completed) {
                        activePlayer = null
                        previewFile?.delete()
                        previewFile = null
                    }
                }
                stopAndRelease(completed)
            }
            player.prepare()
            synchronized(this) {
                activePlayer = player
                previewFile = file
            }
            player.start()
        } catch (_: Exception) {
            synchronized(this) {
                if (activePlayer === player) {
                    activePlayer = null
                    previewFile = null
                }
            }
            stopAndRelease(player)
            file.delete()
        }
    }

    fun stop() {
        synchronized(this) {
            stopAndRelease(activePlayer)
            activePlayer = null
            previewFile?.delete()
            previewFile = null
        }
    }

    private fun previewSamples(tone: String): ShortArray {
        val click = MetronomeClickWav.clickSamples(tone)
        val minSamples = MetronomeClickWav.SAMPLE_RATE * MIN_PREVIEW_MS / 1000
        if (click.size >= minSamples) return click
        return click + ShortArray(minSamples - click.size)
    }

    private fun stopAndRelease(player: MediaPlayer?) {
        if (player == null) return
        try {
            player.setOnCompletionListener(null)
            if (player.isPlaying) {
                player.stop()
            }
        } catch (_: IllegalStateException) {
        }
        player.release()
    }
}
