package skelterjohn.mixedmeter

import android.os.Process
import java.util.concurrent.locks.LockSupport

/**
 * Dedicated timing thread using [System.nanoTime] on a steady grid (no shortening after late clicks).
 */
class MetronomeEngine(
    private val clickPlayer: MetronomeClickPlayer,
    private val onCycleAnchor: (Long) -> Unit,
) {
    @Volatile
    private var running = false

    private var thread: Thread? = null

    @Synchronized
    fun start(schedule: MetronomeClickSchedule) {
        if (running) return
        running = true
        thread = Thread({ runLoop(schedule) }, "MixedMeterMetronome").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(2_000)
        thread = null
    }

    private fun runLoop(schedule: MetronomeClickSchedule) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        clickPlayer.ensureReady()

        val useBoxSchedule = schedule.totalCycleNanos > 0L && schedule.clickOffsetsNanos.isNotEmpty()
        if (useBoxSchedule) {
            runBoxScheduleLoop(schedule)
        } else {
            runSimpleBpmLoop(schedule.beatPeriodNanos)
        }
    }

    /** No time signatures: fixed interval grid at 60/bpm. */
    private fun runSimpleBpmLoop(periodNanos: Long) {
        val anchorNanos = System.nanoTime()
        onCycleAnchor(anchorNanos)
        clickPlayer.play()

        var nextDeadlineNanos = anchorNanos + periodNanos
        while (running) {
            nextDeadlineNanos = skipLateSlots(nextDeadlineNanos, periodNanos)
            waitUntilDeadline(nextDeadlineNanos)
            if (!running) break
            clickPlayer.play()
            nextDeadlineNanos += periodNanos
        }
    }

    private fun runBoxScheduleLoop(schedule: MetronomeClickSchedule) {
        val anchorNanos = System.nanoTime()
        onCycleAnchor(anchorNanos)
        clickPlayer.play()

        var nextClickIndex = 1L
        var nextDeadlineNanos = deadlineForClickIndex(anchorNanos, nextClickIndex, schedule)
        while (running) {
            var now = System.nanoTime()
            while (running && now >= nextDeadlineNanos) {
                nextClickIndex++
                nextDeadlineNanos = deadlineForClickIndex(anchorNanos, nextClickIndex, schedule)
                now = System.nanoTime()
            }
            waitUntilDeadline(nextDeadlineNanos)
            if (!running) break
            clickPlayer.play()
            nextClickIndex++
            nextDeadlineNanos = deadlineForClickIndex(anchorNanos, nextClickIndex, schedule)
        }
    }

    private fun skipLateSlots(deadlineNanos: Long, periodNanos: Long): Long {
        var nextDeadline = deadlineNanos
        var now = System.nanoTime()
        while (running && now >= nextDeadline) {
            nextDeadline += periodNanos
            now = System.nanoTime()
        }
        return nextDeadline
    }

    private fun waitUntilDeadline(deadlineNanos: Long) {
        while (running && System.nanoTime() < deadlineNanos) {
            val remainingNanos = deadlineNanos - System.nanoTime()
            when {
                remainingNanos > 8_000_000L -> {
                    Thread.sleep((remainingNanos / 1_000_000L - 4L).coerceAtLeast(1L))
                }
                remainingNanos > 0L -> {
                    LockSupport.parkNanos(remainingNanos)
                }
            }
        }
    }
}

private fun deadlineForClickIndex(
    anchorNanos: Long,
    clickIndex: Long,
    schedule: MetronomeClickSchedule,
): Long {
    val offsets = schedule.clickOffsetsNanos
    val boundaryCount = offsets.size
    val cycle = clickIndex / boundaryCount
    val boundaryIndex = (clickIndex % boundaryCount).toInt()
    return anchorNanos + cycle * schedule.totalCycleNanos + offsets[boundaryIndex]
}
