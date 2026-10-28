package io.github.jameslunaa.taskora.data.repository

import io.github.jameslunaa.taskora.data.local.TaskoraDatabase
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class OfflineTaskRepositoryTest {
    private class FakeScheduler : ReminderScheduler {
        val scheduled = mutableMapOf<Long, Instant>()
        override fun canScheduleExact() = true
        override fun schedule(taskId: Long, at: Instant) {
            scheduled[taskId] = at
        }
        override fun cancel(taskId: Long) {
            scheduled.remove(taskId)
        }
    }

    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var db: TaskoraDatabase
    private lateinit var scheduler: FakeScheduler
    private lateinit var repository: OfflineTaskRepository

    @Before
    fun setUp() {
        db = TaskoraDatabase.create(file = null, inboxName = "Inbox")
        scheduler = FakeScheduler()
        repository = OfflineTaskRepository(db.taskDao(), db.subtaskDao(), scheduler, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun tasks() = repository.observeTasks(TaskList.INBOX_ID).first()

    @Test
    fun completingRecurringTask_createsNextOccurrenceOnce() = runBlocking {
        val id = repository.addTask(
            Task(listId = TaskList.INBOX_ID, title = "Water", dueDate = today, recurrence = Recurrence(Frequency.DAILY)),
        )
        repository.addSubtask(id, "Balcony")

        repository.setTaskCompleted(id, true)
        repository.setTaskCompleted(id, false)
        repository.setTaskCompleted(id, true)

        val open = tasks().filterNot { it.isCompleted }
        assertEquals(1, open.size)
        assertEquals(today.plusDays(1), open.single().dueDate)
        assertEquals(listOf("Balcony"), open.single().subtasks.map { it.title })
        assertFalse(open.single().subtasks.single().isCompleted)
    }

    @Test
    fun completingViaUpdate_alsoCreatesNextOccurrence() = runBlocking {
        val id = repository.addTask(
            Task(listId = TaskList.INBOX_ID, title = "Water", dueDate = today, recurrence = Recurrence(Frequency.WEEKLY)),
        )
        val task = repository.getTask(id)!!
        repository.updateTask(task.copy(isCompleted = true, completedAt = now))
        assertEquals(listOf(today.plusWeeks(1)), tasks().filterNot { it.isCompleted }.map { it.dueDate })
    }

    @Test
    fun reminders_followCompletionAndDeletion() = runBlocking {
        val at = now.plusSeconds(3600)
        val id = repository.addTask(Task(listId = TaskList.INBOX_ID, title = "Call", reminderAt = at))
        assertEquals(at, scheduler.scheduled[id])

        repository.setTaskCompleted(id, true)
        assertTrue(id !in scheduler.scheduled)

        repository.setTaskCompleted(id, false)
        assertEquals(at, scheduler.scheduled[id])

        repository.deleteTask(id)
        assertTrue(id !in scheduler.scheduled)
    }
}
