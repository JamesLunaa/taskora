package com.example.taskora.ui.tasks

import androidx.lifecycle.SavedStateHandle
import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskList
import com.example.taskora.domain.model.TaskSortMode
import com.example.taskora.testing.FakeTaskListRepository
import com.example.taskora.testing.FakeTaskRepository
import com.example.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TaskListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC)
    private val taskRepository = FakeTaskRepository(clock)
    private val listRepository = FakeTaskListRepository()
    private var listId = TaskList.INBOX_ID

    // Lazy so it's created after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        TaskListViewModel(SavedStateHandle(mapOf("listId" to listId)), listRepository, taskRepository)
    }

    private fun runWithState(block: suspend () -> Unit) = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        block()
    }

    @Test
    fun loadsListName_andStartsEmpty() = runWithState {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Inbox", state.list?.name)
        assertFalse(state.isListMissing)
        assertTrue(state.isEmpty)
    }

    @Test
    fun addTask_ignoresBlankTitles() = runWithState {
        viewModel.addTask("   ")
        viewModel.addTask("Buy milk")
        assertEquals(listOf("Buy milk"), viewModel.uiState.value.activeTasks.map { it.title })
    }

    @Test
    fun completing_movesTaskToCompletedSection() = runWithState {
        viewModel.addTask("Buy milk")
        val task = viewModel.uiState.value.activeTasks.single()
        viewModel.setCompleted(task, true)
        val state = viewModel.uiState.value
        assertTrue(state.activeTasks.isEmpty())
        assertEquals(listOf(task.id), state.completedTasks.map { it.id })
    }

    @Test
    fun deleteThenRestore_bringsTaskBack() = runWithState {
        viewModel.addTask("Buy milk")
        val task = viewModel.uiState.value.activeTasks.single()
        viewModel.deleteTask(task)
        assertTrue(viewModel.uiState.value.isEmpty)
        viewModel.restoreTask(task)
        assertEquals(listOf(task), viewModel.uiState.value.activeTasks)
    }

    @Test
    fun clearCompleted_onlyRemovesCompletedTasks() = runWithState {
        viewModel.addTask("Keep")
        viewModel.addTask("Done")
        val done = viewModel.uiState.value.activeTasks.first { it.title == "Done" }
        viewModel.setCompleted(done, true)
        viewModel.clearCompleted()
        val state = viewModel.uiState.value
        assertEquals(listOf("Keep"), state.activeTasks.map(Task::title))
        assertTrue(state.completedTasks.isEmpty())
    }

    @Test
    fun activeTasks_followListSortMode() = runWithState {
        viewModel.addTask("b")
        viewModel.addTask("A")
        assertEquals(listOf("b", "A"), viewModel.uiState.value.activeTasks.map(Task::title))
        viewModel.setSortMode(TaskSortMode.TITLE)
        assertEquals(TaskSortMode.TITLE, viewModel.uiState.value.list?.sortMode)
        assertEquals(listOf("A", "b"), viewModel.uiState.value.activeTasks.map(Task::title))
    }

    @Test
    fun createList_emitsOpenListEvent() = runWithState {
        viewModel.createList("  ", 0)
        viewModel.createList("Work", 0xFF00FF00.toInt())
        val event = viewModel.events.first()
        val created = listRepository.lists.value.single { it.name == "Work" }
        assertEquals(TaskListEvent.OpenList(created.id), event)
        assertEquals(2, listRepository.lists.value.size)
    }

    @Test
    fun editList_renamesAndRecolors() = runWithState {
        viewModel.editList(" Home ", 0xFF123456.toInt())
        val list = viewModel.uiState.value.list!!
        assertEquals("Home", list.name)
        assertEquals(0xFF123456.toInt(), list.color)
    }

    @Test
    fun deleteList_marksListMissing_butInboxIsKept() = runTest {
        val workId = listRepository.addList("Work", 0)
        listId = workId
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.deleteList()
        assertTrue(viewModel.uiState.value.isListMissing)
        assertEquals(listOf(TaskList.INBOX_ID), listRepository.lists.value.map { it.id })
    }

    @Test
    fun deleteList_isIgnoredForInbox() = runWithState {
        viewModel.deleteList()
        assertFalse(viewModel.uiState.value.isListMissing)
    }
}
