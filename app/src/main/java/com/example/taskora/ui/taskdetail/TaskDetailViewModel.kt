package com.example.taskora.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskora.data.repository.TaskRepository
import com.example.taskora.domain.model.Priority
import com.example.taskora.domain.model.Subtask
import com.example.taskora.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TaskDetailUiState(
    val isLoading: Boolean = true,
    /** The task being edited; its subtasks come straight from the database. */
    val task: Task? = null,
    /** True once the task is gone (deleted here, or missing); the screen should close. */
    val isClosed: Boolean = false,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    // Key matches the TaskDetailRoute property name.
    private val taskId: Long = checkNotNull(savedStateHandle.get<Long>("taskId"))

    // Edits go into the draft, which is the source of truth for the text fields so typing
    // never fights with database emissions. Every change is saved straight away.
    private val draft = MutableStateFlow<Task?>(null)
    private val closed = MutableStateFlow(false)
    private var saveJob: Job? = null

    val uiState: StateFlow<TaskDetailUiState> = combine(
        draft,
        taskRepository.observeTask(taskId).map { it?.subtasks.orEmpty() },
        closed,
    ) { task, subtasks, isClosed ->
        TaskDetailUiState(
            isLoading = task == null && !isClosed,
            task = task?.copy(subtasks = subtasks),
            isClosed = isClosed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    init {
        viewModelScope.launch {
            val task = taskRepository.getTask(taskId)
            if (task == null) {
                closed.value = true
                return@launch
            }
            draft.value = task
            saveJob = launch {
                // Blank titles aren't saved; the last non-blank title is kept.
                draft.filterNotNull().drop(1).filter { it.title.isNotBlank() }
                    .collect { taskRepository.updateTask(it) }
            }
        }
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    fun setDueDate(date: LocalDate?) = edit { it.copy(dueDate = date, dueTime = if (date == null) null else it.dueTime) }

    fun setDueTime(time: LocalTime?) = edit { if (it.dueDate == null) it else it.copy(dueTime = time) }

    fun setPriority(priority: Priority) = edit { it.copy(priority = priority) }

    fun setCompleted(completed: Boolean) = edit {
        it.copy(isCompleted = completed, completedAt = if (completed) Instant.now(clock) else null)
    }

    fun addSubtask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskRepository.addSubtask(taskId, title) }
    }

    fun setSubtaskCompleted(subtask: Subtask, completed: Boolean) {
        viewModelScope.launch { taskRepository.setSubtaskCompleted(subtask.id, completed) }
    }

    fun deleteSubtask(subtask: Subtask) {
        viewModelScope.launch { taskRepository.deleteSubtask(subtask.id) }
    }

    fun deleteTask() {
        saveJob?.cancel()
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            closed.value = true
        }
    }

    private fun edit(transform: (Task) -> Task) {
        draft.update { current -> current?.let(transform) }
    }
}
