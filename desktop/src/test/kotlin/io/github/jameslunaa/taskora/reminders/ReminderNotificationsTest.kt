package io.github.jameslunaa.taskora.reminders

import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.testing.FakeNotifier
import io.github.jameslunaa.taskora.testing.FakeTaskRepository
import io.github.jameslunaa.taskora.ui.common.RelativeDayLabels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderNotificationsTest {
    private val now = Instant.parse("2026-10-28T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val tasks = FakeTaskRepository(clock)
    private val notifier = FakeNotifier()
    private val strings = ReminderStrings(RelativeDayLabels("Yesterday", "Today", "Tomorrow"), "Complete", "Snooze", "Open")

    private suspend fun addTask(reminderAt: Instant?, completed: Boolean = false) = tasks.addTask(
        Task(
            listId = TaskList.INBOX_ID,
            title = "Call mom",
            notes = "\nAbout Sunday\nmore",
            dueDate = LocalDate.of(2026, 10, 28),
            reminderAt = reminderAt,
            isCompleted = completed,
        ),
    )

    @Test
    fun showsTitleDueAndFirstNoteLine() = runTest {
        val reminders = ReminderNotifications(tasks, notifier, backgroundScope, clock, strings = { strings })
        val id = addTask(now)
        reminders.fire(id, now)
        runCurrent()
        val shown = notifier.shown.single()
        assertEquals("Call mom", shown.title)
        assertEquals("Today\nAbout Sunday", shown.body)
    }

    @Test
    fun staleReminders_areDropped() = runTest {
        val reminders = ReminderNotifications(tasks, notifier, backgroundScope, clock, strings = { strings })
        val moved = addTask(now.plusSeconds(600))
        val done = addTask(now, completed = true)
        reminders.fire(moved, now)
        reminders.fire(done, now)
        reminders.fire(999, now)
        runCurrent()
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun actions_completeSnoozeAndOpen() = runTest {
        val reminders = ReminderNotifications(tasks, notifier, backgroundScope, clock, strings = { strings })

        notifier.answer = NotificationAction.COMPLETE
        val a = addTask(now)
        reminders.fire(a, now)
        runCurrent()
        assertTrue(tasks.getTask(a)!!.isCompleted)

        notifier.answer = NotificationAction.SNOOZE
        val b = addTask(now)
        reminders.fire(b, now)
        runCurrent()
        assertEquals(now.plus(ReminderNotifications.SNOOZE), tasks.getTask(b)!!.reminderAt)

        notifier.answer = NotificationAction.OPEN
        val c = addTask(now)
        val opened = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(c, reminders.openRequests.first())
        }
        reminders.fire(c, now)
        runCurrent()
        assertTrue(opened.isCompleted)
    }
}
