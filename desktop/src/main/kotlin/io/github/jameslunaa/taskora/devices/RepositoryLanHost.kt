package io.github.jameslunaa.taskora.devices

import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.sortedFor
import io.github.jameslunaa.taskora.lan.LanHost
import io.github.jameslunaa.taskora.lan.WireList
import io.github.jameslunaa.taskora.lan.WireTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/** This computer's lists and tasks, as paired devices see them. */
class RepositoryLanHost(
    private val taskListRepository: TaskListRepository,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : LanHost {
    override suspend fun lists(): List<WireList> = taskListRepository.observeLists().first().map { it.toWire() }

    override suspend fun tasks(listId: Long, includeCompleted: Boolean): List<WireTask>? {
        val lists = taskListRepository.observeLists().first()
        val tasks = if (listId == LanHost.ALL_LISTS) {
            // Grouped by list, in the sidebar's order, each list in its own sort order.
            val byList = taskRepository.observeAllTasks().first().groupBy { it.listId }
            lists.flatMap { list -> byList[list.id].orEmpty().sortedFor(list.sortMode, list.sortDescending) }
        } else {
            val list: TaskList = lists.firstOrNull { it.id == listId } ?: return null
            taskRepository.observeTasks(listId).first().sortedFor(list.sortMode, list.sortDescending)
        }
        return tasks.filter { includeCompleted || !it.isCompleted }.map { it.toWire() }
    }

    override suspend fun setCompleted(taskId: Long, completed: Boolean): Boolean {
        val task = taskRepository.getTask(taskId) ?: return false
        // Already in that state: nothing to do, so a retried request is harmless.
        if (task.isCompleted != completed) taskRepository.setTaskCompleted(taskId, completed)
        return true
    }

    override suspend fun addTask(listId: Long?, task: WireTask): Long {
        val lists = taskListRepository.observeLists().first()
        val target = listId?.takeIf { id -> lists.any { it.id == id } } ?: TaskList.INBOX_ID
        return taskRepository.addTaskWithSubtasks(task.toNewTask(target, Instant.now(clock)))
    }

    override val changes: Flow<Unit> =
        combine(taskListRepository.observeLists(), taskRepository.observeAllTasks()) { _, _ -> }.drop(1)
}
