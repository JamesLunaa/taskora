package io.github.jameslunaa.taskora.ui.tasks

import androidx.lifecycle.SavedStateHandle
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.testing.FakeTaskListRepository
import io.github.jameslunaa.taskora.testing.FakeTaskRepository
import io.github.jameslunaa.taskora.testing.MainDispatcherRule
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
import java.time.LocalDate
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
        viewModel.deleteTasks(listOf(task))
        assertTrue(viewModel.uiState.value.isEmpty)
        viewModel.restoreTasks(listOf(task))
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
        viewModel.setSortMode(TaskSortMode.TITLE, false)
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

    private fun titles(tasks: List<Task>) = tasks.map(Task::title)

    @Test
    fun sortDescending_reversesTheMode() = runWithState {
        viewModel.addTask("b")
        viewModel.addTask("A")
        viewModel.setSortMode(TaskSortMode.TITLE, true)
        assertEquals(listOf("b", "A"), titles(viewModel.uiState.value.activeTasks))
        assertTrue(viewModel.uiState.value.list!!.sortDescending)
    }

    @Test
    fun selection_togglesSelectsAllAndDropsDeletedTasks() = runWithState {
        viewModel.addTask("one")
        viewModel.addTask("two")
        val (one, two) = viewModel.uiState.value.activeTasks
        assertFalse(viewModel.uiState.value.isSelecting)
        viewModel.toggleSelection(one.id)
        assertEquals(setOf(one.id), viewModel.uiState.value.selectedIds)
        viewModel.selectAll()
        assertEquals(setOf(one.id, two.id), viewModel.uiState.value.selectedIds)
        viewModel.deleteTasks(listOf(one))
        assertEquals(setOf(two.id), viewModel.uiState.value.selectedIds)
        viewModel.toggleSelection(two.id)
        assertFalse(viewModel.uiState.value.isSelecting)
    }

    @Test
    fun bulkDueDate_setsAndClears_andClearingDropsTimeAndRecurrence() = runWithState {
        viewModel.addTask("one")
        viewModel.addTask("two")
        val ids = viewModel.uiState.value.activeTasks.map { it.id }
        val tomorrow = LocalDate.of(2026, 10, 21)
        viewModel.setDueDate(ids, tomorrow)
        assertEquals(listOf(tomorrow, tomorrow), viewModel.uiState.value.activeTasks.map { it.dueDate })
        viewModel.setDueDate(ids, null)
        assertEquals(listOf(null, null), viewModel.uiState.value.activeTasks.map { it.dueDate })
    }

    @Test
    fun bulkPriorityAndComplete_applyToEveryId() = runWithState {
        viewModel.addTask("one")
        viewModel.addTask("two")
        val ids = viewModel.uiState.value.activeTasks.map { it.id }
        viewModel.setPriority(ids, Priority.HIGH)
        assertTrue(viewModel.uiState.value.activeTasks.all { it.priority == Priority.HIGH })
        viewModel.setTasksCompleted(ids, true)
        assertTrue(viewModel.uiState.value.activeTasks.isEmpty())
        assertEquals(2, viewModel.uiState.value.completedTasks.size)
    }

    @Test
    fun moveTasks_leavesTheListAndLandsAtTheEndOfTheTarget() = runWithState {
        val workId = listRepository.addList("Work", 0)
        taskRepository.addTask(Task(listId = workId, title = "existing"))
        viewModel.addTask("one")
        viewModel.addTask("two")
        viewModel.moveTasks(viewModel.uiState.value.activeTasks.map { it.id }, workId)
        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(listOf("existing", "one", "two"), titles(taskRepository.observeTasks(workId).first()))
    }

    @Test
    fun reorder_persistsManualOrder() = runWithState {
        viewModel.addTask("a")
        viewModel.addTask("b")
        viewModel.addTask("c")
        viewModel.setSortMode(TaskSortMode.MANUAL, false)
        val (a, b, c) = viewModel.uiState.value.activeTasks
        viewModel.reorder(listOf(c.id, a.id, b.id))
        assertEquals(listOf("c", "a", "b"), titles(viewModel.uiState.value.activeTasks))
    }

    @Test
    fun allTasks_groupsActiveTasksByList_inDrawerOrder_skippingEmptyLists() = runTest {
        val workId = listRepository.addList("Work", 0)
        listRepository.addList("Empty", 0)
        taskRepository.addTask(Task(listId = workId, title = "w1"))
        taskRepository.addTask(Task(listId = TaskList.INBOX_ID, title = "i1"))
        taskRepository.addTask(Task(listId = workId, title = "w2"))
        val done = taskRepository.addTask(Task(listId = workId, title = "done"))
        taskRepository.setTaskCompleted(done, true)
        listId = TaskList.ALL_TASKS_ID
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val state = viewModel.uiState.value
        assertTrue(state.isAllTasks)
        assertFalse(state.isListMissing)
        assertEquals(listOf("Inbox", "Work"), state.sections.map { it.list?.name })
        assertEquals(listOf(listOf("i1"), listOf("w1", "w2")), state.sections.map { titles(it.tasks) })
        assertEquals(listOf("done"), titles(state.completedTasks))
    }

    @Test
    fun allTasks_addsNewTasksToTheInbox_andCantDeleteTheView() = runTest {
        listId = TaskList.ALL_TASKS_ID
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addTask("new")
        assertEquals(TaskList.INBOX_ID, taskRepository.tasks.value.single().listId)
        viewModel.deleteList()
        assertEquals(1, listRepository.lists.value.size)
    }

    @Test
    fun reorder_inAComputedSortMode_switchesTheListToMyOrder() = runWithState {
        viewModel.addTask("b")
        viewModel.addTask("a")
        viewModel.setSortMode(TaskSortMode.TITLE, true)
        val shown = viewModel.uiState.value.activeTasks
        viewModel.reorder(listOf(shown[1].id, shown[0].id))
        val state = viewModel.uiState.value
        assertEquals(TaskSortMode.MANUAL, state.list?.sortMode)
        assertEquals(listOf(shown[1].title, shown[0].title), titles(state.activeTasks))
    }
}
