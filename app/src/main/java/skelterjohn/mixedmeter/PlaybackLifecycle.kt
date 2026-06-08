package skelterjohn.mixedmeter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Stops metronome playback when this screen loses foreground focus or when the whole app
 * moves to the background. Uses both signals because some devices delay or skip activity
 * [Lifecycle.Event.ON_STOP] while audio keeps playing.
 */
@Composable
fun PausePlaybackWhenNotFocused(onPause: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, onPause) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(onPause) {
        val processOwner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onPause()
            }
        }
        processOwner.lifecycle.addObserver(observer)
        onDispose { processOwner.lifecycle.removeObserver(observer) }
    }
}
