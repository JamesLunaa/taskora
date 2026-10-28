package io.github.jameslunaa.taskora.testing

import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.domain.model.AppSettings
import io.github.jameslunaa.taskora.domain.model.AppWindow
import io.github.jameslunaa.taskora.domain.model.WindowBounds
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import io.github.jameslunaa.taskora.domain.model.ThemeMode
import io.github.jameslunaa.taskora.system.Autostart
import io.github.jameslunaa.taskora.reminders.NotificationAction
import io.github.jameslunaa.taskora.reminders.NotificationContent
import io.github.jameslunaa.taskora.reminders.Notifier
import io.github.jameslunaa.taskora.domain.model.Subtask
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant

class FakeTaskListRepository(
    initial: List<TaskList> = listOf(TaskList(id = TaskList.INBOX_ID, name = "Inbox", color = TaskList.DEFAULT_COLOR)),
) : TaskListRepository {
    val lists = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override fun observeLists(): Flow<List<TaskList>> = lists

    override fun observeList(id: Long): Flow<TaskList?> = lists.map { all -> all.find { it.id == id } }

    override suspend fun addList(name: String, color: Int): Long {
        val id = nextId++
        lists.update { it + TaskList(id = id, name = name, color = color, sortOrder = it.size) }
        return id
    }

    override suspend fun updateList(list: TaskList) = lists.update { all -> all.map { if (it.id == list.id) list else it } }

    override suspend fun deleteList(id: Long) = lists.update { all -> all.filterNot { it.id == id } }
}

/** In-memory stand-in for the Room-backed repository; subtasks live inside their Task. */
class FakeTaskRepository(private val clock: Clock) : TaskRepository {
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    private var nextTaskId = 1L
    private var nextSubtaskId = 1L

    override fun observeTasks(listId: Long): Flow<List<Task>> =
        tasks.map { all -> all.filter { it.listId == listId }.sortedBy { it.sortOrder } }

    override fun observeAllTasks(): Flow<List<Task>> = tasks

    override fun observeTask(id: Long): Flow<Task?> = tasks.map { all -> all.find { it.id == id } }

    override suspend fun getTask(id: Long): Task? = tasks.value.find { it.id == id }

    override suspend fun addTask(task: Task): Long {
        val id = nextTaskId++
        tasks.update {
            it + task.copy(
                id = id,
                title = task.title.trim(),
                createdAt = Instant.now(clock),
                sortOrder = it.count { t -> t.listId == task.listId },
                subtasks = emptyList(),
            )
        }
        return id
    }

    override suspend fun updateTask(task: Task) = modify(task.id) { task.copy(title = task.title.trim(), subtasks = it.subtasks) }

    override suspend fun setTaskCompleted(id: Long, completed: Boolean) =
        modify(id) { it.copy(isCompleted = completed, completedAt = if (completed) Instant.now(clock) else null) }

    override suspend fun snoozeReminder(id: Long, delay: Duration) =
        modify(id) { it.copy(reminderAt = Instant.now(clock).plus(delay)) }

    override suspend fun rescheduleReminders() = Unit

    override suspend fun deleteTask(id: Long) = tasks.update { all -> all.filterNot { it.id == id } }

    override suspend fun restoreTask(task: Task) = tasks.update { it + task }

    override suspend fun clearCompleted(listId: Long) =
        tasks.update { all -> all.filterNot { it.listId == listId && it.isCompleted } }

    override suspend fun updateTasks(ids: Collection<Long>, transform: (Task) -> Task) =
        tasks.update { all -> all.map { if (it.id in ids) transform(it) else it } }

    override suspend fun setTasksCompleted(ids: Collection<Long>, completed: Boolean) =
        tasks.update { all ->
            all.map {
                if (it.id in ids) it.copy(isCompleted = completed, completedAt = if (completed) Instant.now(clock) else null) else it
            }
        }

    override suspend fun deleteTasks(ids: Collection<Long>): List<Task> {
        val deleted = tasks.value.filter { it.id in ids }
        tasks.update { all -> all.filterNot { it.id in ids } }
        return deleted
    }

    override suspend fun restoreTasks(tasks: List<Task>) = this.tasks.update { it + tasks }

    override suspend fun moveTasks(ids: Collection<Long>, listId: Long) {
        var next = tasks.value.count { it.listId == listId }
        tasks.update { all ->
            all.map { if (it.id in ids) it.copy(listId = listId, sortOrder = next++) else it }
        }
    }

    override suspend fun reorderTasks(orderedIds: List<Long>) =
        tasks.update { all -> all.map { task -> orderedIds.indexOf(task.id).let { if (it >= 0) task.copy(sortOrder = it) else task } } }

    override suspend fun addSubtask(taskId: Long, title: String): Long {
        val id = nextSubtaskId++
        modify(taskId) {
            it.copy(subtasks = it.subtasks + Subtask(id = id, taskId = taskId, title = title.trim(), sortOrder = it.subtasks.size))
        }
        return id
    }

    override suspend fun updateSubtask(subtask: Subtask) = modifySubtask(subtask.id) { subtask }

    override suspend fun setSubtaskCompleted(id: Long, completed: Boolean) =
        modifySubtask(id) { it.copy(isCompleted = completed) }

    override suspend fun deleteSubtask(id: Long) =
        tasks.update { all -> all.map { task -> task.copy(subtasks = task.subtasks.filterNot { it.id == id }) } }

    private fun modify(id: Long, transform: (Task) -> Task) =
        tasks.update { all -> all.map { if (it.id == id) transform(it) else it } }

    private fun modifySubtask(id: Long, transform: (Subtask) -> Subtask) =
        tasks.update { all ->
            all.map { task -> task.copy(subtasks = task.subtasks.map { if (it.id == id) transform(it) else it }) }
        }
}

class FakeSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }
    override suspend fun setCloseToTray(enabled: Boolean) = settings.update { it.copy(closeToTray = enabled) }
    override suspend fun setWindowLevel(window: AppWindow, level: WindowLevel) = updateLook(window) { it.copy(level = level) }
    override suspend fun setWindowOpacity(window: AppWindow, opacity: Float) = updateLook(window) { it.copy(opacity = opacity) }
    override suspend fun setNoteOpen(open: Boolean) = settings.update { it.copy(note = it.note.copy(isOpen = open)) }
    override suspend fun setNoteListId(listId: Long) = settings.update { it.copy(note = it.note.copy(listId = listId)) }
    override suspend fun setNoteBounds(bounds: WindowBounds) = settings.update { it.copy(note = it.note.copy(bounds = bounds)) }

    private fun updateLook(window: AppWindow, change: (WindowLook) -> WindowLook) = settings.update {
        when (window) {
            AppWindow.MAIN -> it.copy(mainWindow = change(it.mainWindow))
            AppWindow.NOTE -> it.copy(noteWindow = change(it.noteWindow))
        }
    }
}

class FakeAutostart(override val isAvailable: Boolean = true) : Autostart {
    private var enabled = false
    override fun isEnabled() = enabled
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled && isAvailable
    }
}

class FakeNotifier(
    override val isAvailable: Boolean = true,
    /** What the user "clicks" on each notification. */
    var answer: NotificationAction? = null,
) : Notifier {
    val shown = mutableListOf<NotificationContent>()
    override suspend fun show(content: NotificationContent): NotificationAction? {
        shown += content
        return answer
    }
}
