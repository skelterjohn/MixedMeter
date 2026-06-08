package skelterjohn.mixedmeter

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Context.startSequenceActivity() {
    val intent = Intent(this, SequenceActivity::class.java)
    val options = ActivityOptions.makeCustomAnimation(
        this,
        R.anim.slide_in_up,
        R.anim.slide_out_up,
    )
    startActivity(intent, options.toBundle())
}

fun Modifier.twoFingerVerticalSwipe(
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onTwoFingerActiveChanged: ((Boolean) -> Unit)? = null,
    swipeThreshold: Dp = 64.dp,
): Modifier = pointerInput(onSwipeUp, onSwipeDown, onTwoFingerActiveChanged, swipeThreshold) {
    val thresholdPx = swipeThreshold.toPx()
    awaitEachGesture {
        var startAvgY: Float? = null
        var twoFingerGesture = false
        var notifiedActive = false
        var navigationTriggered = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                twoFingerGesture = true
                if (!notifiedActive) {
                    notifiedActive = true
                    onTwoFingerActiveChanged?.invoke(true)
                }
                val avgY = pressed.map { it.position.y }.average().toFloat()
                val start = startAvgY ?: avgY.also { startAvgY = it }
                if (!navigationTriggered) {
                    val delta = avgY - start
                    when {
                        delta < -thresholdPx -> {
                            navigationTriggered = true
                            onSwipeUp?.invoke()
                        }
                        delta > thresholdPx -> {
                            navigationTriggered = true
                            onSwipeDown?.invoke()
                        }
                    }
                }
            }
            if (twoFingerGesture) {
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (notifiedActive) {
            onTwoFingerActiveChanged?.invoke(false)
        }
    }
}

fun Activity.finishWithSlideDown() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.slide_in_down,
            R.anim.slide_out_down,
        )
        finish()
    } else {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_down)
    }
}
