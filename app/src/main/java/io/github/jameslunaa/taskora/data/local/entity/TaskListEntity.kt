package io.github.jameslunaa.taskora.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "CREATED") val sortMode: TaskSortMode = TaskSortMode.CREATED,
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
    sortMode = sortMode,
    activeTaskCount = activeTaskCount,
)

fun TaskListWithCount.toDomain() = list.toDomain(activeTaskCount)

fun TaskList.toEntity() = TaskListEntity(id = id, name = name, color = color, sortOrder = sortOrder, sortMode = sortMode)
