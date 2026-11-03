package io.github.jameslunaa.taskora.devices

import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.lan.LanHost
import io.github.jameslunaa.taskora.lan.WirePriority
import io.github.jameslunaa.taskora.lan.WireSubtask
import io.github.jameslunaa.taskora.lan.WireTask
import io.github.jameslunaa.taskora.testing.FakeTaskListRepository
import io.github.jameslunaa.taskora.testing.FakeTaskRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RepositoryLanHostTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
    private val lists = FakeTaskListRepository(
        listOf(
            TaskList(id = TaskList.INBOX_ID, name = "Inbox", color = 1),
            TaskList(id = 2, name = "Home", color = 2, sortMode = TaskSortMode.PRIORITY),
        ),
    )
    private val tasks = FakeTaskRepository(clock)
    private val host = RepositoryLanHost(lists, tasks, clock)

    @Test
    fun tasks_comeInTheListsOrder_withoutCompletedUnlessAsked() = runTest {
        tasks.addTask(Task(listId = 2, title = "Low", priority = Priority.LOW))
        tasks.addTask(Task(listId = 2, title = "High", priority = Priority.HIGH))
        val done = tasks.addTask(Task(listId = 2, title = "Done"))
        tasks.setTaskCompleted(done, true)
        tasks.addTask(Task(listId = 1, title = "Inbox task"))

        assertEquals(listOf("High", "Low"), host.tasks(2, includeCompleted = false)!!.map { it.title })
        assertEquals(3, host.tasks(2, includeCompleted = true)!!.size)
        assertEquals(listOf("Inbox task", "High", "Low"), host.tasks(LanHost.ALL_LISTS, false)!!.map { it.title })
        assertNull(host.tasks(99, false))
        assertEquals(listOf("Inbox", "Home"), host.lists().map { it.name })
    }

    @Test
    fun setCompleted_isIdempotent() = runTest {
        val id = tasks.addTask(Task(listId = 1, title = "Water plants"))
        assertTrue(host.setCompleted(id, true))
        val completedAt = tasks.getTask(id)!!.completedAt
        assertTrue(host.setCompleted(id, true))
        assertEquals(completedAt, tasks.getTask(id)!!.completedAt)
        assertFalse(host.setCompleted(99, true))
    }

    @Test
    fun addTask_copiesSubtasks_fallsBackToInbox_andDropsPastReminders() = runTest {
        val sent = WireTask(
            id = 500, listId = 7, title = "  Call mum ", priority = WirePriority.HIGH, dueDate = LocalDate.of(2026, 11, 1),
            reminderAt = Instant.parse("2026-10-29T09:00:00Z"),
            subtasks = listOf(WireSubtask(1, "Ask about Sunday"), WireSubtask(2, "Birthday", completed = true)),
        )
        val id = host.addTask(listId = 2, task = sent)
        val added = tasks.getTask(id)!!
        assertEquals(2, added.listId)
        assertEquals("Call mum", added.title)
        assertEquals(Priority.HIGH, added.priority)
        assertNull(added.reminderAt)
        assertEquals(listOf("Ask about Sunday" to false, "Birthday" to true), added.subtasks.map { it.title to it.isCompleted })

        val future = host.addTask(listId = 99, task = sent.copy(reminderAt = Instant.parse("2026-11-01T08:00:00Z")))
        assertEquals(TaskList.INBOX_ID, tasks.getTask(future)!!.listId)
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), tasks.getTask(future)!!.reminderAt)
    }

    @Test
    fun changes_followTheRepositories() = runTest {
        val changed = async { host.changes.first() }
        runCurrent()
        tasks.addTask(Task(listId = 1, title = "New"))
        changed.await()
    }

    @Test
    fun wireRoundTrip_keepsTheTask() {
        val task = Task(id = 3, listId = 1, title = "Plan trip", notes = "Ask Ana", priority = Priority.MEDIUM, createdAt = clock.instant())
        val copy = task.toWire().toNewTask(listId = 1, now = clock.instant())
        assertEquals(task.copy(id = 0), copy)
    }
}
