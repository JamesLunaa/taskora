package io.github.jameslunaa.taskora.reminders

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one coroutine per pending reminder while the app runs (in the window or the tray).
 * It re-checks the wall clock at least every [checkEveryMillis], because coroutine delays use a
 * monotonic clock that stops while the computer sleeps. Nothing fires while Taskora isn't running.
 */
class DesktopReminderScheduler(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val checkEveryMillis: Long = 15_000,
) : ReminderScheduler {
    /** Called when a reminder is due; set once the app is wired up. */
    var onFire: suspend (taskId: Long, scheduledAt: Instant) -> Unit = { _, _ -> }

    private val jobs = ConcurrentHashMap<Long, Job>()

    override fun canScheduleExact(): Boolean = true

    override fun schedule(taskId: Long, at: Instant) {
        cancel(taskId)
        jobs[taskId] = scope.launch {
            while (true) {
                val left = Duration.between(Instant.now(clock), at).toMillis()
                if (left <= 0) break
                delay(minOf(left, checkEveryMillis))
            }
            jobs.remove(taskId, coroutineContext.job)
            onFire(taskId, at)
        }
    }

    override fun cancel(taskId: Long) {
        jobs.remove(taskId)?.cancel()
    }

    /** Task ids with a reminder waiting (for tests). */
    val pendingIds: Set<Long> get() = jobs.keys.toSet()
}
