package com.example.taskora.testing

import com.example.taskora.data.repository.TaskListRepository
import com.example.taskora.data.repository.TaskRepository
import com.example.taskora.domain.model.Subtask
import com.example.taskora.domain.model.Task
import com.example.taskora.domain.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant

class FakeTaskListRepository(
    initial: List<TaskList> = listOf(TaskList(id = TaskList.INBOX_ID, name = "Inbox", color = TaskList.DEFAULT_COLOR)),
) : TaskListRepository {
    val lists = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override fun observeLists(): Flow<List<TaskList>> = lists

    override fun observeList(id: Long): Flow<TaskList?> = lists.map { all -> all.find { it.id == id } }

    override suspend fun addList(name: String, color: Int): Long {
        val id = nextId++
        lists.update { it + TaskList(id = id, name = name, color = color, sortOrder = it.size) }
        return id
    }

    override suspend fun updateList(list: TaskList) = lists.update { all -> all.map { if (it.id == list.id) list else it } }

    override suspend fun deleteList(id: Long) = lists.update { all -> all.filterNot { it.id == id } }
}

/** In-memory stand-in for the Room-backed repository; subtasks live inside their Task. */
class FakeTaskRepository(private val clock: Clock) : TaskRepository {
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    private var nextTaskId = 1L
    private var nextSubtaskId = 1L

    override fun observeTasks(listId: Long): Flow<List<Task>> =
        tasks.map { all -> all.filter { it.listId == listId }.sortedBy { it.sortOrder } }

    override fun observeTask(id: Long): Flow<Task?> = tasks.map { all -> all.find { it.id == id } }

    override suspend fun getTask(id: Long): Task? = tasks.value.find { it.id == id }

    override suspend fun addTask(task: Task): Long {
        val id = nextTaskId++
        tasks.update {
            it + task.copy(
                id = id,
                title = task.title.trim(),
                createdAt = Instant.now(clock),
                sortOrder = it.count { t -> t.listId == task.listId },
                subtasks = emptyList(),
            )
        }
        return id
    }

    override suspend fun updateTask(task: Task) = modify(task.id) { task.copy(title = task.title.trim(), subtasks = it.subtasks) }

    override suspend fun setTaskCompleted(id: Long, completed: Boolean) =
        modify(id) { it.copy(isCompleted = completed, completedAt = if (completed) Instant.now(clock) else null) }

    override suspend fun snoozeReminder(id: Long, delay: Duration) =
        modify(id) { it.copy(reminderAt = Instant.now(clock).plus(delay)) }

    override suspend fun rescheduleReminders() = Unit

    override suspend fun deleteTask(id: Long) = tasks.update { all -> all.filterNot { it.id == id } }

    override suspend fun restoreTask(task: Task) = tasks.update { it + task }

    override suspend fun clearCompleted(listId: Long) =
        tasks.update { all -> all.filterNot { it.listId == listId && it.isCompleted } }

    override suspend fun addSubtask(taskId: Long, title: String): Long {
        val id = nextSubtaskId++
        modify(taskId) {
            it.copy(subtasks = it.subtasks + Subtask(id = id, taskId = taskId, title = title.trim(), sortOrder = it.subtasks.size))
        }
        return id
    }

    override suspend fun updateSubtask(subtask: Subtask) = modifySubtask(subtask.id) { subtask }

    override suspend fun setSubtaskCompleted(id: Long, completed: Boolean) =
        modifySubtask(id) { it.copy(isCompleted = completed) }

    override suspend fun deleteSubtask(id: Long) =
        tasks.update { all -> all.map { task -> task.copy(subtasks = task.subtasks.filterNot { it.id == id }) } }

    private fun modify(id: Long, transform: (Task) -> Task) =
        tasks.update { all -> all.map { if (it.id == id) transform(it) else it } }

    private fun modifySubtask(id: Long, transform: (Subtask) -> Subtask) =
        tasks.update { all ->
            all.map { task -> task.copy(subtasks = task.subtasks.map { if (it.id == id) transform(it) else it }) }
        }
}
