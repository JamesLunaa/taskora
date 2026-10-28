package io.github.jameslunaa.taskora.reminders

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopReminderSchedulerTest {
    private val start = Instant.parse("2026-10-28T09:00:00Z")

    /** Wall-clock time that follows the test's virtual time, plus [jump] (a computer waking from sleep). */
    private class VirtualClock(private val scope: TestScope, private val start: Instant) : Clock() {
        var jumpMillis = 0L
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = start.plusMillis(scope.testScheduler.currentTime + jumpMillis)
    }

    @Test
    fun firesAtTheTime_andNotBefore() = runTest {
        val fired = mutableListOf<Pair<Long, Instant>>()
        val scheduler = DesktopReminderScheduler(backgroundScope, VirtualClock(this, start))
        scheduler.onFire = { id, at -> fired += id to at }

        val at = start.plusSeconds(60)
        scheduler.schedule(7, at)
        advanceTimeBy(59_000)
        runCurrent()
        assertTrue(fired.isEmpty())
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(7L to at), fired)
        assertTrue(scheduler.pendingIds.isEmpty())
    }

    @Test
    fun rescheduleAndCancel_replacePendingReminder() = runTest {
        val fired = mutableListOf<Long>()
        val scheduler = DesktopReminderScheduler(backgroundScope, VirtualClock(this, start))
        scheduler.onFire = { id, _ -> fired += id }

        scheduler.schedule(1, start.plusSeconds(10))
        scheduler.schedule(1, start.plusSeconds(30))
        scheduler.schedule(2, start.plusSeconds(10))
        scheduler.cancel(2)
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(fired.isEmpty())
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(listOf(1L), fired)
    }

    @Test
    fun wallClockJump_firesAtNextCheck() = runTest {
        val fired = mutableListOf<Long>()
        val clock = VirtualClock(this, start)
        val scheduler = DesktopReminderScheduler(backgroundScope, clock, checkEveryMillis = 15_000)
        scheduler.onFire = { id, _ -> fired += id }

        scheduler.schedule(3, start.plusSeconds(3600))
        runCurrent()
        // The computer slept for an hour: wall time moved on, but the coroutine's delay didn't.
        clock.jumpMillis = 3_600_000
        advanceTimeBy(15_001)
        runCurrent()
        assertEquals(listOf(3L), fired)
    }
}
