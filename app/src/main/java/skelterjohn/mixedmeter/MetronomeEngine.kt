package skelterjohn.mixedmeter

import android.os.Process
import java.util.concurrent.locks.LockSupport
import kotlin.math.min

/**
 * Dedicated timing thread using [System.nanoTime] deadlines (not coroutine [delay]).
 */
class MetronomeEngine(
    private val clickPlayer: MetronomeClickPlayer,
    private val getSchedule: () -> MetronomeClickSchedule,
    private val onCycleAnchor: (Long) -> Unit,
) {
    @Volatile
    private var running = false

    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread(::runLoop, "MixedMeterMetronome").apply {
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

    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var anchorNanos = 0L
        var clickIndex = 0L

        while (running) {
            val schedule = getSchedule()

            if (anchorNanos == 0L) {
                anchorNanos = System.nanoTime()
                onCycleAnchor(anchorNanos)
                clickPlayer.play()
                clickIndex = 1L
                continue
            }

            val deadlineNanos = nextDeadlineNanos(anchorNanos, clickIndex, schedule)
            val intervalNanos = intervalBeforeDeadline(anchorNanos, clickIndex, schedule)
            waitUntilPlayTime(deadlineNanos, intervalNanos)
            if (!running) break
            clickPlayer.play()
            clickIndex++
        }
    }

    private fun waitUntilPlayTime(deadlineNanos: Long, intervalNanos: Long) {
        val latencyNanos = min(
            CLICK_OUTPUT_LATENCY_NANOS,
            intervalNanos / 6L,
        ).coerceAtLeast(0L)
        val playAtNanos = deadlineNanos - latencyNanos
        while (running && System.nanoTime() < playAtNanos) {
            val remainingNanos = playAtNanos - System.nanoTime()
            when {
                remainingNanos > 5_000_000L -> {
                    Thread.sleep((remainingNanos / 1_000_000L - 3L).coerceAtLeast(1L))
                }
                remainingNanos > 300_000L -> {
                    LockSupport.parkNanos(remainingNanos)
                }
            }
        }
    }
}

private const val CLICK_OUTPUT_LATENCY_NANOS = 8_000_000L

private fun nextDeadlineNanos(
    anchorNanos: Long,
    clickIndex: Long,
    schedule: MetronomeClickSchedule,
): Long {
    val offsets = schedule.clickOffsetsNanos
    if (schedule.totalCycleNanos > 0L && offsets.isNotEmpty()) {
        val boundaryCount = offsets.size
        val cycle = clickIndex / boundaryCount
        val boundaryIndex = (clickIndex % boundaryCount).toInt()
        return anchorNanos + cycle * schedule.totalCycleNanos + offsets[boundaryIndex]
    }
    return anchorNanos + clickIndex * schedule.beatPeriodNanos
}

private fun intervalBeforeDeadline(
    anchorNanos: Long,
    clickIndex: Long,
    schedule: MetronomeClickSchedule,
): Long {
    val offsets = schedule.clickOffsetsNanos
    if (schedule.totalCycleNanos > 0L && offsets.isNotEmpty()) {
        val boundaryCount = offsets.size
        val index = (clickIndex % boundaryCount).toInt()
        val nextClickIndex = clickIndex + 1
        val cycle = clickIndex / boundaryCount
        val nextCycle = nextClickIndex / boundaryCount
        val nextBoundaryIndex = (nextClickIndex % boundaryCount).toInt()
        val current = anchorNanos + cycle * schedule.totalCycleNanos + offsets[index]
        val next = anchorNanos + nextCycle * schedule.totalCycleNanos + offsets[nextBoundaryIndex]
        return (next - current).coerceAtLeast(1L)
    }
    return schedule.beatPeriodNanos
}
