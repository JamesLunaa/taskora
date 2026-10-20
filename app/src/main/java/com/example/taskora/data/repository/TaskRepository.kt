package com.example.taskora.data.repository

import com.example.taskora.data.local.dao.SubtaskDao
import com.example.taskora.data.local.dao.TaskDao
import com.example.taskora.data.local.entity.toDomain
import com.example.taskora.data.local.entity.toEntity
import com.example.taskora.domain.model.Subtask
import com.example.taskora.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface TaskRepository {
    fun observeTasks(listId: Long): Flow<List<Task>>
    fun observeTask(id: Long): Flow<Task?>
    suspend fun getTask(id: Long): Task?

    /** Inserts [task] (its subtasks are ignored) at the end of its list. */
    suspend fun addTask(task: Task): Long

    /** Updates the task's own fields; subtasks are managed separately. */
    suspend fun updateTask(task: Task)
    suspend fun setTaskCompleted(id: Long, completed: Boolean)
    suspend fun deleteTask(id: Long)

    /** Puts back a deleted [task] exactly as it was, subtasks included. */
    suspend fun restoreTask(task: Task)
    suspend fun clearCompleted(listId: Long)

    suspend fun addSubtask(taskId: Long, title: String): Long
    suspend fun updateSubtask(subtask: Subtask)
    suspend fun setSubtaskCompleted(id: Long, completed: Boolean)
    suspend fun deleteSubtask(id: Long)
}

@Singleton
class OfflineTaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val subtaskDao: SubtaskDao,
    private val clock: Clock,
) : TaskRepository {
    override fun observeTasks(listId: Long): Flow<List<Task>> =
        taskDao.observeByList(listId).map { tasks -> tasks.map { it.toDomain() } }

    override fun observeTask(id: Long): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    override suspend fun getTask(id: Long): Task? = taskDao.getById(id)?.toDomain()

    override suspend fun addTask(task: Task): Long =
        taskDao.insert(
            task.copy(
                id = 0,
                title = task.title.trim(),
                createdAt = Instant.now(clock),
                sortOrder = taskDao.nextSortOrder(task.listId),
            ).toEntity(),
        )

    override suspend fun updateTask(task: Task) = taskDao.update(task.copy(title = task.title.trim()).toEntity())

    override suspend fun setTaskCompleted(id: Long, completed: Boolean) =
        taskDao.setCompleted(id, completed, if (completed) Instant.now(clock) else null)

    override suspend fun deleteTask(id: Long) = taskDao.deleteById(id)

    override suspend fun restoreTask(task: Task) =
        taskDao.insertWithSubtasks(task.toEntity(), task.subtasks.map { it.toEntity() })

    override suspend fun clearCompleted(listId: Long) = taskDao.deleteCompleted(listId)

    override suspend fun addSubtask(taskId: Long, title: String): Long =
        subtaskDao.insert(
            Subtask(taskId = taskId, title = title.trim(), sortOrder = subtaskDao.nextSortOrder(taskId)).toEntity(),
        )

    override suspend fun updateSubtask(subtask: Subtask) =
        subtaskDao.update(subtask.copy(title = subtask.title.trim()).toEntity())

    override suspend fun setSubtaskCompleted(id: Long, completed: Boolean) = subtaskDao.setCompleted(id, completed)

    override suspend fun deleteSubtask(id: Long) = subtaskDao.deleteById(id)
}
