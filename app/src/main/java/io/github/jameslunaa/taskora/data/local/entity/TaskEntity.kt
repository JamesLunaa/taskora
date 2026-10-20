package io.github.jameslunaa.taskora.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.domain.model.Subtask
import io.github.jameslunaa.taskora.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listId"), Index("dueDate")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val title: String,
    val notes: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val reminderAt: Instant?,
    val priority: Priority,
    // Recurrence is flattened; all three are null when the task doesn't repeat.
    val recurrenceFrequency: Frequency?,
    val recurrenceInterval: Int?,
    /** Bitmask of [DayOfWeek], bit 0 = Monday. */
    val recurrenceDays: Int?,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val createdAt: Instant,
    val sortOrder: Int,
)

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val subtasks: List<SubtaskEntity>,
)

fun TaskWithSubtasks.toDomain(): Task = task.toDomain(
    subtasks.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.toDomain() },
)

fun TaskEntity.toDomain(subtasks: List<Subtask> = emptyList()) = Task(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    dueDate = dueDate,
    dueTime = dueTime,
    reminderAt = reminderAt,
    priority = priority,
    recurrence = recurrenceFrequency?.let { frequency ->
        Recurrence(
            frequency = frequency,
            interval = recurrenceInterval ?: 1,
            daysOfWeek = recurrenceDays?.let(::daysFromMask).orEmpty(),
        )
    },
    isCompleted = isCompleted,
    completedAt = completedAt,
    createdAt = createdAt,
    sortOrder = sortOrder,
    subtasks = subtasks,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    dueDate = dueDate,
    dueTime = dueTime,
    reminderAt = reminderAt,
    priority = priority,
    recurrenceFrequency = recurrence?.frequency,
    recurrenceInterval = recurrence?.interval,
    recurrenceDays = recurrence?.daysOfWeek?.let(::daysToMask),
    isCompleted = isCompleted,
    completedAt = completedAt,
    createdAt = createdAt,
    sortOrder = sortOrder,
)

internal fun daysToMask(days: Set<DayOfWeek>): Int =
    days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun daysFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf()) { mask and (1 shl (it.value - 1)) != 0 }
