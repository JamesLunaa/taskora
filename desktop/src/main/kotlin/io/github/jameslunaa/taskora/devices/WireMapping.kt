package io.github.jameslunaa.taskora.devices

import io.github.jameslunaa.taskora.domain.model.Frequency
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Recurrence
import io.github.jameslunaa.taskora.domain.model.Subtask
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.lan.WireFrequency
import io.github.jameslunaa.taskora.lan.WireList
import io.github.jameslunaa.taskora.lan.WirePriority
import io.github.jameslunaa.taskora.lan.WireRecurrence
import io.github.jameslunaa.taskora.lan.WireSubtask
import io.github.jameslunaa.taskora.lan.WireTask
import java.time.Instant

// Tasks and lists as the LAN protocol carries them (docs/lan.md, "The Task object").

fun TaskList.toWire() = WireList(id = id, name = name, color = color, activeCount = activeTaskCount)

fun Task.toWire() = WireTask(
    id = id,
    listId = listId,
    title = title,
    notes = notes,
    dueDate = dueDate,
    dueTime = dueTime,
    reminderAt = reminderAt,
    priority = WirePriority.valueOf(priority.name),
    recurrence = recurrence?.let {
        WireRecurrence(WireFrequency.valueOf(it.frequency.name), it.interval, it.daysOfWeek.sorted())
    },
    completed = isCompleted,
    completedAt = completedAt,
    createdAt = createdAt,
    subtasks = subtasks.sortedBy { it.sortOrder }.map { WireSubtask(it.id, it.title, it.isCompleted) },
)

/**
 * A new local task from a [WireTask] sent by another device (or copied from one): fresh ids,
 * [listId] here, and no reminder if it's already past.
 */
fun WireTask.toNewTask(listId: Long, now: Instant): Task = Task(
    listId = listId,
    title = title.trim(),
    notes = notes,
    dueDate = dueDate,
    dueTime = dueTime.takeIf { dueDate != null },
    reminderAt = reminderAt?.takeIf { it.isAfter(now) },
    priority = Priority.valueOf(priority.name),
    recurrence = recurrence?.let { wire ->
        Recurrence(
            Frequency.valueOf(wire.frequency.name),
            wire.interval,
            if (wire.frequency == WireFrequency.WEEKLY) wire.daysOfWeek.toSet() else emptySet(),
        )
    },
    isCompleted = completed,
    completedAt = completedAt.takeIf { completed },
    createdAt = now,
    subtasks = subtasks.mapIndexed { index, subtask ->
        Subtask(taskId = 0, title = subtask.title.trim(), isCompleted = subtask.completed, sortOrder = index)
    },
)
