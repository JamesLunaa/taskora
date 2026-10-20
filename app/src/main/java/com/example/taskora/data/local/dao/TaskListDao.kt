package com.example.taskora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.taskora.data.local.entity.TaskListEntity
import com.example.taskora.data.local.entity.TaskListWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {
    @Query(
        """
        SELECT l.*, (SELECT COUNT(*) FROM tasks t WHERE t.listId = l.id AND t.isCompleted = 0) AS activeTaskCount
        FROM task_lists l
        ORDER BY l.sortOrder, l.id
        """,
    )
    fun observeAll(): Flow<List<TaskListWithCount>>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    fun observeById(id: Long): Flow<TaskListEntity?>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM task_lists")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(list: TaskListEntity): Long

    @Update
    suspend fun update(list: TaskListEntity)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteById(id: Long)
}
