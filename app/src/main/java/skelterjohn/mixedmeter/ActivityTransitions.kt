package skelterjohn.mixedmeter

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build

fun Context.startSequenceActivity() {
    val intent = Intent(this, SequenceActivity::class.java)
    val options = ActivityOptions.makeCustomAnimation(
        this,
        R.anim.slide_in_up,
        R.anim.slide_out_up,
    )
    startActivity(intent, options.toBundle())
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
