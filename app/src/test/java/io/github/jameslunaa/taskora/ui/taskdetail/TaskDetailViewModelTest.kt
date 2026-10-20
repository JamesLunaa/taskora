package io.github.jameslunaa.taskora.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.testing.FakeTaskListRepository
import io.github.jameslunaa.taskora.testing.FakeTaskRepository
import io.github.jameslunaa.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class TaskDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = FakeTaskRepository(clock)

    private suspend fun TestScope.viewModelFor(taskId: Long): TaskDetailViewModel {
        val viewModel = TaskDetailViewModel(
            SavedStateHandle(mapOf("taskId" to taskId)),
            repository,
            FakeTaskListRepository(),
            clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private suspend fun stored(id: Long) = repository.getTask(id)!!

    @Test
    fun missingTask_closesScreen() = runTest {
        val viewModel = viewModelFor(42)
        assertTrue(viewModel.uiState.value.isClosed)
    }

    @Test
    fun edits_areSaved() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "Old"))
        val viewModel = viewModelFor(id)

        viewModel.setTitle("New title")
        viewModel.setNotes("Some notes")
        viewModel.setPriority(Priority.HIGH)

        val task = stored(id)
        assertEquals("New title", task.title)
        assertEquals("Some notes", task.notes)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals("New title", viewModel.uiState.value.task?.title)
    }

    @Test
    fun blankTitle_isNotSaved() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "Keep me"))
        val viewModel = viewModelFor(id)
        viewModel.setTitle("  ")
        assertEquals("Keep me", stored(id).title)
        assertEquals("  ", viewModel.uiState.value.task?.title)
    }

    @Test
    fun clearingDueDate_alsoClearsTime_andTimeNeedsADate() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)

        viewModel.setDueTime(LocalTime.NOON)
        assertNull(stored(id).dueTime)

        viewModel.setDueDate(LocalDate.of(2026, 10, 21))
        viewModel.setDueTime(LocalTime.NOON)
        assertEquals(LocalTime.NOON, stored(id).dueTime)

        viewModel.setDueDate(null)
        assertNull(stored(id).dueDate)
        assertNull(stored(id).dueTime)
    }

    @Test
    fun moveToList_changesListId() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)
        viewModel.moveToList(2)
        assertEquals(2L, stored(id).listId)
    }

    @Test
    fun setReminder_savesAndClears() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)
        val at = now.plusSeconds(600)
        viewModel.setReminder(at)
        assertEquals(at, stored(id).reminderAt)
        viewModel.setReminder(null)
        assertNull(stored(id).reminderAt)
    }

    @Test
    fun setRecurrence_addsDueDateWhenMissing_andClearingDueDateClearsIt() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)
        viewModel.setRecurrence(Recurrence(Frequency.DAILY))
        assertEquals(LocalDate.of(2026, 10, 20), stored(id).dueDate)
        assertEquals(Frequency.DAILY, stored(id).recurrence?.frequency)

        viewModel.setDueDate(null)
        assertNull(stored(id).recurrence)
    }

    @Test
    fun completing_setsCompletedAt() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)
        viewModel.setCompleted(true)
        assertEquals(now, stored(id).completedAt)
        viewModel.setCompleted(false)
        assertNull(stored(id).completedAt)
    }

    @Test
    fun subtasks_showInState() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)
        viewModel.addSubtask("First")
        viewModel.addSubtask(" ")
        val subtask = viewModel.uiState.value.task!!.subtasks.single()
        viewModel.setSubtaskCompleted(subtask, true)
        assertTrue(viewModel.uiState.value.task!!.subtasks.single().isCompleted)
        viewModel.deleteSubtask(subtask)
        assertTrue(viewModel.uiState.value.task!!.subtasks.isEmpty())
    }

    @Test
    fun deleteTask_removesItAndCloses() = runTest {
        val id = repository.addTask(Task(listId = 1, title = "t"))
        val viewModel = viewModelFor(id)
        assertFalse(viewModel.uiState.value.isClosed)
        viewModel.deleteTask()
        assertNull(repository.getTask(id))
        assertTrue(viewModel.uiState.value.isClosed)
    }
}
