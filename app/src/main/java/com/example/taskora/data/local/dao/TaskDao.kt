package com.example.taskora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.taskora.data.local.entity.SubtaskEntity
import com.example.taskora.data.local.entity.TaskEntity
import com.example.taskora.data.local.entity.TaskWithSubtasks
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE listId = :listId ORDER BY sortOrder, createdAt")
    fun observeByList(listId: Long): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskWithSubtasks?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskWithSubtasks?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM tasks WHERE listId = :listId")
    suspend fun nextSortOrder(listId: Long): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    /** Re-inserts a task with its original id and subtasks (used to undo a delete). */
    @Transaction
    suspend fun insertWithSubtasks(task: TaskEntity, subtasks: List<SubtaskEntity>) {
        insert(task)
        insertSubtasks(subtasks)
    }

    /** Inserts a new task and its subtasks (whose taskId is filled in here); returns the task id. */
    @Transaction
    suspend fun insertNewWithSubtasks(task: TaskEntity, subtasks: List<SubtaskEntity>): Long {
        val id = insert(task)
        insertSubtasks(subtasks.map { it.copy(id = 0, taskId = id) })
        return id
    }

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Instant?)

    @Query("UPDATE tasks SET reminderAt = :reminderAt WHERE id = :id")
    suspend fun setReminder(id: Long, reminderAt: Instant?)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND reminderAt > :after")
    suspend fun getWithPendingReminders(after: Instant): List<TaskEntity>

    @Query(
        "SELECT COUNT(*) FROM tasks WHERE listId = :listId AND title = :title AND dueDate = :dueDate AND isCompleted = 0",
    )
    suspend fun countOpen(listId: Long, title: String, dueDate: LocalDate): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE listId = :listId AND isCompleted = 1")
    suspend fun deleteCompleted(listId: Long)
}
