package io.github.jameslunaa.taskora.ui.search

import io.github.jameslunaa.taskora.domain.DueFilter
import io.github.jameslunaa.taskora.domain.StatusFilter
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.testing.FakeTaskListRepository
import io.github.jameslunaa.taskora.testing.FakeTaskRepository
import io.github.jameslunaa.taskora.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC)
    private val tasks = FakeTaskRepository(clock)

    // Lazy so it's created after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy { SearchViewModel(tasks, FakeTaskListRepository(), clock) }

    private fun runWithState(block: suspend () -> Unit) = runTest {
        tasks.addTask(Task(listId = 1, title = "Pay rent", priority = Priority.HIGH))
        tasks.addTask(Task(listId = 1, title = "Buy milk"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        block()
    }

    private fun titles() = viewModel.uiState.value.results.map { it.title }

    @Test
    fun noQueryOrFilters_showsNothing() = runWithState {
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals(1, viewModel.uiState.value.lists.size)
    }

    @Test
    fun query_and_filters_narrowResults() = runWithState {
        viewModel.setQuery("pay")
        assertEquals(listOf("Pay rent"), titles())

        viewModel.setQuery("")
        viewModel.togglePriority(Priority.HIGH)
        assertEquals(listOf("Pay rent"), titles())
        viewModel.togglePriority(Priority.HIGH)
        assertTrue(titles().isEmpty())

        viewModel.setDue(DueFilter.NO_DATE)
        assertEquals(setOf("Pay rent", "Buy milk"), titles().toSet())
    }

    @Test
    fun clearFilters_keepsQuery() = runWithState {
        viewModel.setQuery("milk")
        viewModel.setStatus(StatusFilter.COMPLETED)
        assertTrue(titles().isEmpty())
        viewModel.clearFilters()
        assertEquals("milk", viewModel.filter.value.query)
        assertEquals(listOf("Buy milk"), titles())
    }

    @Test
    fun completingFromResults_dropsItFromOpenResults() = runWithState {
        viewModel.setQuery("milk")
        viewModel.setCompleted(viewModel.uiState.value.results.single(), true)
        assertTrue(titles().isEmpty())
    }
}
