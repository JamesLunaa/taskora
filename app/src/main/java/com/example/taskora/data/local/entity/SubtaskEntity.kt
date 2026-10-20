package com.example.taskora.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.taskora.domain.model.Subtask

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean,
    val sortOrder: Int,
)

fun SubtaskEntity.toDomain() = Subtask(
    id = id,
    taskId = taskId,
    title = title,
    isCompleted = isCompleted,
    sortOrder = sortOrder,
)

fun Subtask.toEntity() = SubtaskEntity(
    id = id,
    taskId = taskId,
    title = title,
    isCompleted = isCompleted,
    sortOrder = sortOrder,
)
