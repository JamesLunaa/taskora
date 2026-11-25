package io.github.jameslunaa.taskora.ui.tasks

import androidx.lifecycle.SavedStateHandle
import io.github.jameslunaa.taskora.domain.DayFilter
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.testing.FakeSettingsRepository
import io.github.jameslunaa.taskora.testing.FakeTaskListRepository
import io.github.jameslunaa.taskora.testing.INBOX_ID
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
    private val settingsRepository = FakeSettingsRepository()
    private var listId = INBOX_ID

    // Lazy so it's created after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        TaskListViewModel(SavedStateHandle(mapOf("listId" to listId)), listRepository, taskRepository, settingsRepository, clock)
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
    fun deleteList_marksListMissing() = runTest {
        val workId = listRepository.addList("Work", 0)
        listId = workId
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.deleteList()
        assertTrue(viewModel.uiState.value.isListMissing)
        assertEquals(listOf(INBOX_ID), listRepository.lists.value.map { it.id })
    }

    @Test
    fun deleteList_worksForTheInboxToo() = runWithState {
        viewModel.deleteList()
        assertTrue(viewModel.uiState.value.isListMissing)
        assertTrue(listRepository.lists.value.isEmpty())
    }

    @Test
    fun opensAllTasks_withoutAListId() = runTest {
        val viewModel = TaskListViewModel(SavedStateHandle(), listRepository, taskRepository, settingsRepository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertTrue(viewModel.uiState.value.isAllTasks)
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
        taskRepository.addTask(Task(listId = INBOX_ID, title = "i1"))
        taskRepository.addTask(Task(listId = workId, title = "w2"))
        taskRepository.addTask(Task(listId = null, title = "loose"))
        val done = taskRepository.addTask(Task(listId = workId, title = "done"))
        taskRepository.setTaskCompleted(done, true)
        listId = TaskList.ALL_TASKS_ID
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val state = viewModel.uiState.value
        assertTrue(state.isAllTasks)
        assertFalse(state.isListMissing)
        // Tasks in no list come first, without a header.
        assertEquals(listOf(null, "Inbox", "Work"), state.sections.map { it.list?.name })
        assertEquals(listOf(listOf("loose"), listOf("i1"), listOf("w1", "w2")), state.sections.map { titles(it.tasks) })
        assertEquals(listOf("done"), titles(state.completedTasks))
    }

    @Test
    fun allTasks_addsNewTasksToNoList_andCantDeleteTheView() = runTest {
        listId = TaskList.ALL_TASKS_ID
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addTask("new")
        assertEquals(null, taskRepository.tasks.value.single().listId)
        viewModel.deleteList()
        assertEquals(1, listRepository.lists.value.size)
    }

    @Test
    fun allTasks_hidesTheListsFilteredOut_untilShownAgain() = runTest {
        val workId = listRepository.addList("Work", 0)
        taskRepository.addTask(Task(listId = INBOX_ID, title = "i1"))
        taskRepository.addTask(Task(listId = workId, title = "w1"))
        taskRepository.addTask(Task(listId = null, title = "loose"))
        val done = taskRepository.addTask(Task(listId = workId, title = "w done"))
        taskRepository.setTaskCompleted(done, true)
        val inboxDone = taskRepository.addTask(Task(listId = INBOX_ID, title = "i done"))
        taskRepository.setTaskCompleted(inboxDone, true)
        listId = TaskList.ALL_TASKS_ID
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        viewModel.setListShown(workId, false)
        viewModel.setListShown(null, false)
        var state = viewModel.uiState.value
        assertEquals(setOf(workId, null), state.hiddenListIds)
        assertEquals(listOf("i1"), titles(state.activeTasks))
        assertEquals(listOf("i done"), titles(state.completedTasks))

        // Clear completed leaves the hidden lists alone.
        viewModel.clearCompleted()
        assertEquals(listOf("w done"), titles(taskRepository.tasks.value.filter { it.isCompleted }))

        // A task added here is in no list, so no list shows again.
        viewModel.addTask("new")
        state = viewModel.uiState.value
        assertEquals(setOf(workId), state.hiddenListIds)
        assertEquals(listOf("loose", "new", "i1"), titles(state.activeTasks))

        // A deleted list drops out of the filter.
        listRepository.deleteList(workId)
        assertEquals(emptySet<Long?>(), viewModel.uiState.value.hiddenListIds)

        viewModel.setListShown(INBOX_ID, false)
        viewModel.showAllLists()
        assertEquals(listOf("loose", "new", "i1"), titles(viewModel.uiState.value.activeTasks))
    }

    @Test
    fun reorderLists_changesTheDrawerOrder() = runWithState {
        val workId = listRepository.addList("Work", 0)
        val homeId = listRepository.addList("Home", 0)
        viewModel.reorderLists(listOf(homeId, INBOX_ID, workId))
        assertEquals(listOf("Home", "Inbox", "Work"), viewModel.uiState.value.lists.map { it.name })
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

    @Test
    fun dayFilter_showsTodayWithOverdue_orTomorrow() = runWithState {
        val today = LocalDate.of(2026, 10, 20)
        taskRepository.addTask(Task(listId = 1, title = "Overdue", dueDate = today.minusDays(2)))
        taskRepository.addTask(Task(listId = 1, title = "Today", dueDate = today))
        taskRepository.addTask(Task(listId = 1, title = "Tomorrow", dueDate = today.plusDays(1)))
        taskRepository.addTask(Task(listId = 1, title = "Undated"))
        val doneEarlier = taskRepository.addTask(Task(listId = 1, title = "Done earlier", dueDate = today.minusDays(1)))
        taskRepository.setTaskCompleted(doneEarlier, true)

        viewModel.setDayFilter(DayFilter.TODAY)
        assertEquals(setOf("Overdue", "Today"), viewModel.uiState.value.activeTasks.map { it.title }.toSet())
        // Completed overdue tasks aren't "today" any more.
        assertTrue(viewModel.uiState.value.completedTasks.isEmpty())

        viewModel.setDayFilter(DayFilter.TOMORROW)
        assertEquals(listOf("Tomorrow"), viewModel.uiState.value.activeTasks.map { it.title })

        viewModel.setDayFilter(DayFilter.ALL)
        assertEquals(4, viewModel.uiState.value.activeTasks.size)
    }

    @Test
    fun dayFilter_isSharedAcrossLists() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setDayFilter(DayFilter.TOMORROW)
        val other = TaskListViewModel(SavedStateHandle(mapOf("listId" to TaskList.ALL_TASKS_ID)), listRepository, taskRepository, settingsRepository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { other.uiState.collect() }
        assertEquals(DayFilter.TOMORROW, other.uiState.value.dayFilter)
    }

    @Test
    fun dayFilter_newTasksAreDueThatDay() = runWithState {
        viewModel.setDayFilter(DayFilter.TOMORROW)
        viewModel.addTask("Call mom")
        assertEquals(LocalDate.of(2026, 10, 21), viewModel.uiState.value.activeTasks.single().dueDate)
    }
}
