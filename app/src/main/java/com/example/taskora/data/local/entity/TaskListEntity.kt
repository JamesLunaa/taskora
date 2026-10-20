package com.example.taskora.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.taskora.domain.model.TaskList

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sortOrder: Int,
)

data class TaskListWithCount(
    @Embedded val list: TaskListEntity,
    val activeTaskCount: Int,
)

fun TaskListEntity.toDomain(activeTaskCount: Int = 0) = TaskList(
    id = id,
    name = name,
    color = color,
    sortOrder = sortOrder,
    activeTaskCount = activeTaskCount,
)

fun TaskListWithCount.toDomain() = list.toDomain(activeTaskCount)

fun TaskList.toEntity() = TaskListEntity(id = id, name = name, color = color, sortOrder = sortOrder)
