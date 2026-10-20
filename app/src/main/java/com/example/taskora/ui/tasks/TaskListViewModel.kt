package com.example.taskora.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskora.data.repository.TaskListRepository
import com.example.taskora.data.repository.TaskRepository
import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskListUiState(
    val isLoading: Boolean = true,
    val listName: String = "",
    val activeTasks: List<Task> = emptyList(),
    /** Most recently completed first. */
    val completedTasks: List<Task> = emptyList(),
) {
    val isEmpty: Boolean get() = activeTasks.isEmpty() && completedTasks.isEmpty()
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    taskListRepository: TaskListRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {
    // Key matches the TaskListRoute property name.
    private val listId: Long = savedStateHandle.get<Long>("listId") ?: TaskList.INBOX_ID

    val uiState: StateFlow<TaskListUiState> = combine(
        taskListRepository.observeList(listId),
        taskRepository.observeTasks(listId),
    ) { list, tasks ->
        val (completed, active) = tasks.partition { it.isCompleted }
        TaskListUiState(
            isLoading = false,
            listName = list?.name.orEmpty(),
            activeTasks = active,
            completedTasks = completed.sortedByDescending { it.completedAt },
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
}
