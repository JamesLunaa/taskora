package io.github.jameslunaa.taskora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.jameslunaa.taskora.data.local.entity.SubtaskEntity

@Dao
interface SubtaskDao {
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM subtasks WHERE taskId = :taskId")
    suspend fun nextSortOrder(taskId: Long): Int

    @Insert
    suspend fun insert(subtask: SubtaskEntity): Long

    @Update
    suspend fun update(subtask: SubtaskEntity)

    @Query("UPDATE subtasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
