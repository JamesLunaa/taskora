package io.github.jameslunaa.taskora.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.domain.DueFilter
import io.github.jameslunaa.taskora.domain.StatusFilter
import io.github.jameslunaa.taskora.domain.TaskFilter
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.search
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime

data class SearchUiState(
    /** Empty until there's a query or a filter. */
    val results: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
)

class SearchViewModel(
    private val taskRepository: TaskRepository,
    taskListRepository: TaskListRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _filter = MutableStateFlow(TaskFilter())

    /** Exposed on its own so the search field reads it synchronously (no cursor jumps). */
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

    val uiState: StateFlow<SearchUiState> = combine(
        _filter,
        taskRepository.observeAllTasks(),
        taskListRepository.observeLists(),
    ) { filter, tasks, lists ->
        SearchUiState(
            results = if (filter.isEmpty) emptyList() else tasks.search(filter, LocalDateTime.now(clock)),
            lists = lists,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }

    fun setStatus(status: StatusFilter) = _filter.update { it.copy(status = status) }

    fun setList(listId: Long?) = _filter.update { it.copy(listId = listId) }

    fun togglePriority(priority: Priority) = _filter.update {
        it.copy(priorities = if (priority in it.priorities) it.priorities - priority else it.priorities + priority)
    }

    fun setDue(due: DueFilter) = _filter.update { it.copy(due = due) }

    /** Resets the filters but keeps the query. */
    fun clearFilters() = _filter.update { TaskFilter(query = it.query) }

    fun setCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch { taskRepository.setTaskCompleted(task.id, completed) }
    }
}
