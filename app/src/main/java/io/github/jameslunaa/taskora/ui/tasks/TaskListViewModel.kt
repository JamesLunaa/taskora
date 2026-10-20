package io.github.jameslunaa.taskora.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.domain.sortedFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskListUiState(
    val isLoading: Boolean = true,
    val list: TaskList? = null,
    /** All lists, for the drawer. */
    val lists: List<TaskList> = emptyList(),
    /** Sorted by the list's sort mode. */
    val activeTasks: List<Task> = emptyList(),
    /** Most recently completed first. */
    val completedTasks: List<Task> = emptyList(),
    /** The list no longer exists (e.g. it was just deleted); the screen should go to the Inbox. */
    val isListMissing: Boolean = false,
) {
    val isEmpty: Boolean get() = activeTasks.isEmpty() && completedTasks.isEmpty()
}

sealed interface TaskListEvent {
    data class OpenList(val listId: Long) : TaskListEvent
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskListRepository: TaskListRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {
    // Key matches the TaskListRoute property name.
    private val listId: Long = savedStateHandle.get<Long>("listId") ?: TaskList.INBOX_ID

    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)
    val events: Flow<TaskListEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<TaskListUiState> = combine(
        taskListRepository.observeList(listId),
        taskListRepository.observeLists(),
        taskRepository.observeTasks(listId),
    ) { list, lists, tasks ->
        val (completed, active) = tasks.partition { it.isCompleted }
        TaskListUiState(
            isLoading = false,
            list = list,
            lists = lists,
            activeTasks = active.sortedFor(list?.sortMode ?: TaskSortMode.CREATED),
            completedTasks = completed.sortedByDescending { it.completedAt },
            isListMissing = list == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskRepository.addTask(Task(listId = listId, title = title)) }
    }

    fun setCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch { taskRepository.setTaskCompleted(task.id, completed) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { taskRepository.deleteTask(task.id) }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch { taskRepository.restoreTask(task) }
    }

    fun clearCompleted() {
        viewModelScope.launch { taskRepository.clearCompleted(listId) }
    }

    /** Creates a list and opens it. */
    fun createList(name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = taskListRepository.addList(name, color)
            _events.send(TaskListEvent.OpenList(id))
        }
    }

    fun editList(name: String, color: Int) {
        val list = uiState.value.list ?: return
        if (name.isBlank()) return
        viewModelScope.launch { taskListRepository.updateList(list.copy(name = name.trim(), color = color)) }
    }

    fun setSortMode(mode: TaskSortMode) {
        val list = uiState.value.list ?: return
        viewModelScope.launch { taskListRepository.updateList(list.copy(sortMode = mode)) }
    }

    /** Deletes the current list and its tasks; [TaskListUiState.isListMissing] then flips to true. */
    fun deleteList() {
        if (listId == TaskList.INBOX_ID) return
        viewModelScope.launch { taskListRepository.deleteList(listId) }
    }
}
