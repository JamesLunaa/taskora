package io.github.jameslunaa.taskora.reminders

import io.github.jameslunaa.taskora.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderRulesTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val later = now.plusSeconds(3600)

    @Test
    fun pendingReminder_onlyForFutureRemindersOnOpenTasks() {
        assertEquals(later, Task(listId = 1, title = "t", reminderAt = later).pendingReminder(now))
        assertNull(Task(listId = 1, title = "t").pendingReminder(now))
        assertNull(Task(listId = 1, title = "t", reminderAt = now.minusSeconds(1)).pendingReminder(now))
        assertNull(Task(listId = 1, title = "t", reminderAt = later, isCompleted = true).pendingReminder(now))
    }

    @Test
    fun shouldNotify_rejectsStaleAlarms() {
        val task = Task(listId = 1, title = "t", reminderAt = later)
        assertTrue(task.shouldNotify(later))
        assertFalse((null as Task?).shouldNotify(later))
        assertFalse(task.copy(isCompleted = true).shouldNotify(later))
        // Reminder was moved (e.g. snoozed) after this alarm was scheduled.
        assertFalse(task.shouldNotify(now))
    }
}
