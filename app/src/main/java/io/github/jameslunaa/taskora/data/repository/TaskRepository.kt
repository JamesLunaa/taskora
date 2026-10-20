package io.github.jameslunaa.taskora.data.repository

import io.github.jameslunaa.taskora.data.local.dao.SubtaskDao
import io.github.jameslunaa.taskora.data.local.dao.TaskDao
import io.github.jameslunaa.taskora.data.local.entity.toDomain
import io.github.jameslunaa.taskora.data.local.entity.toEntity
import io.github.jameslunaa.taskora.domain.model.Subtask
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.nextOccurrence
import io.github.jameslunaa.taskora.reminders.ReminderScheduler
import io.github.jameslunaa.taskora.reminders.pendingReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface TaskRepository {
    fun observeTasks(listId: Long): Flow<List<Task>>

    /** Every task in every list, unordered. */
    fun observeAllTasks(): Flow<List<Task>>
    fun observeTask(id: Long): Flow<Task?>
    suspend fun getTask(id: Long): Task?

    /** Inserts [task] (its subtasks are ignored) at the end of its list. */
    suspend fun addTask(task: Task): Long

    /**
     * Updates the task's own fields; subtasks are managed separately. Like [setTaskCompleted],
     * completing a recurring task here creates its next occurrence.
     */
    suspend fun updateTask(task: Task)
    /** Completing a recurring task also creates its next occurrence. */
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    /** Moves the task's reminder to [delay] from now. */
    suspend fun snoozeReminder(id: Long, delay: Duration)

    /** Re-registers every future reminder with the system, e.g. after a reboot. */
    suspend fun rescheduleReminders()
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
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock,
) : TaskRepository {
    override fun observeTasks(listId: Long): Flow<List<Task>> =
        taskDao.observeByList(listId).map { tasks -> tasks.map { it.toDomain() } }

    override fun observeAllTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { tasks -> tasks.map { it.toDomain() } }

    override fun observeTask(id: Long): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    override suspend fun getTask(id: Long): Task? = taskDao.getById(id)?.toDomain()

    override suspend fun addTask(task: Task): Long {
        val id = taskDao.insert(
            task.copy(
                id = 0,
                title = task.title.trim(),
                createdAt = Instant.now(clock),
                sortOrder = taskDao.nextSortOrder(task.listId),
            ).toEntity(),
        )
        syncReminder(task.copy(id = id))
        return id
    }

    override suspend fun updateTask(task: Task) {
        val previous = taskDao.getById(task.id)?.task
        taskDao.update(task.copy(title = task.title.trim()).toEntity())
        // Task detail saves on every keystroke; only touch the alarm when it could have changed.
        if (previous?.reminderAt != task.reminderAt || previous?.isCompleted != task.isCompleted) {
            syncReminder(task)
        }
        if (previous?.isCompleted == false && task.isCompleted) {
            taskDao.getById(task.id)?.let { createNextOccurrence(it.toDomain()) }
        }
    }

    override suspend fun setTaskCompleted(id: Long, completed: Boolean) {
        val wasCompleted = taskDao.getById(id)?.task?.isCompleted ?: return
        taskDao.setCompleted(id, completed, if (completed) Instant.now(clock) else null)
        val task = taskDao.getById(id)?.toDomain() ?: return
        syncReminder(task)
        if (!wasCompleted && completed) createNextOccurrence(task)
    }

    private suspend fun createNextOccurrence(completed: Task) {
        val next = completed.nextOccurrence(LocalDate.now(clock), clock.zone) ?: return
        // Completing, un-completing and completing again would otherwise stack up copies.
        if (taskDao.countOpen(next.listId, next.title, checkNotNull(next.dueDate)) > 0) return
        val id = taskDao.insertNewWithSubtasks(
            next.copy(createdAt = Instant.now(clock), sortOrder = taskDao.nextSortOrder(next.listId)).toEntity(),
            next.subtasks.map { it.toEntity() },
        )
        syncReminder(next.copy(id = id))
    }

    override suspend fun snoozeReminder(id: Long, delay: Duration) {
        val at = Instant.now(clock).plus(delay)
        taskDao.setReminder(id, at)
        reminderScheduler.schedule(id, at)
    }

    override suspend fun rescheduleReminders() {
        taskDao.getWithPendingReminders(Instant.now(clock)).forEach { task ->
            task.reminderAt?.let { reminderScheduler.schedule(task.id, it) }
        }
    }

    override suspend fun deleteTask(id: Long) {
        taskDao.deleteById(id)
        reminderScheduler.cancel(id)
    }

    override suspend fun restoreTask(task: Task) {
        taskDao.insertWithSubtasks(task.toEntity(), task.subtasks.map { it.toEntity() })
        syncReminder(task)
    }

    override suspend fun clearCompleted(listId: Long) = taskDao.deleteCompleted(listId)

    override suspend fun addSubtask(taskId: Long, title: String): Long =
        subtaskDao.insert(
            Subtask(taskId = taskId, title = title.trim(), sortOrder = subtaskDao.nextSortOrder(taskId)).toEntity(),
        )

    override suspend fun updateSubtask(subtask: Subtask) =
        subtaskDao.update(subtask.copy(title = subtask.title.trim()).toEntity())

    override suspend fun setSubtaskCompleted(id: Long, completed: Boolean) = subtaskDao.setCompleted(id, completed)

    override suspend fun deleteSubtask(id: Long) = subtaskDao.deleteById(id)

    private fun syncReminder(task: Task) {
        val at = task.pendingReminder(Instant.now(clock))
        if (at != null) reminderScheduler.schedule(task.id, at) else reminderScheduler.cancel(task.id)
    }
}
