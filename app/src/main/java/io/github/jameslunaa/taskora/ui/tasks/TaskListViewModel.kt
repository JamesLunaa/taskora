package io.github.jameslunaa.taskora.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.data.settings.SettingsRepository
import io.github.jameslunaa.taskora.domain.DayFilter
import io.github.jameslunaa.taskora.domain.matchesDay
import io.github.jameslunaa.taskora.domain.model.Priority
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.domain.model.TaskSortMode
import io.github.jameslunaa.taskora.domain.sortedFor
import io.github.jameslunaa.taskora.domain.withDueDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * A run of active tasks. [list] is the header to show above them; null for no header: a single
 * list's tasks, or the tasks in no list at the top of All tasks.
 */
data class TaskSection(val list: TaskList?, val tasks: List<Task>)

data class TaskListUiState(
    val isLoading: Boolean = true,
    /** Null for [isAllTasks], which isn't a real list. */
    val list: TaskList? = null,
    /** All lists, for the drawer. */
    val lists: List<TaskList> = emptyList(),
    /** Open tasks everywhere, those in no list included: the All tasks count in the drawer. */
    val allTasksCount: Int = 0,
    /**
     * Active tasks, each section sorted by its list's sort mode. All tasks starts with the tasks in no
     * list (oldest first), then has one section per list.
     */
    val sections: List<TaskSection> = emptyList(),
    /** Most recently completed first. */
    val completedTasks: List<Task> = emptyList(),
    /** The list no longer exists (e.g. it was just deleted); the screen should go to All tasks. */
    val isListMissing: Boolean = false,
    /** The virtual "All tasks" view: every list's tasks, grouped by list. */
    val isAllTasks: Boolean = false,
    /** Tasks picked in selection mode; selection mode is on while this isn't empty. */
    val selectedIds: Set<Long> = emptySet(),
    /** Applied to both active and completed tasks; it carries over to other lists. */
    val dayFilter: DayFilter = DayFilter.ALL,
    /**
     * Lists whose tasks All tasks hides (null is the tasks in no list). Not saved: it's gone once
     * All tasks closes.
     */
    val hiddenListIds: Set<Long?> = emptySet(),
) {
    val activeTasks: List<Task> get() = sections.flatMap { it.tasks }
    val isEmpty: Boolean get() = activeTasks.isEmpty() && completedTasks.isEmpty()
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

sealed interface TaskListEvent {
    data class OpenList(val listId: Long) : TaskListEvent
}

@HiltViewModel
class TaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskListRepository: TaskListRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    // Key matches the TaskListRoute property name.
    private val listId: Long = savedStateHandle.get<Long>("listId") ?: TaskList.ALL_TASKS_ID
    private val isAllTasks = listId == TaskList.ALL_TASKS_ID

    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)
    val events: Flow<TaskListEvent> = _events.receiveAsFlow()

    private val _addedTaskIds = Channel<Long>(Channel.BUFFERED)

    /** Each task added here, once it's saved, so the screen can bring it into view. */
    val addedTaskIds: Flow<Long> = _addedTaskIds.receiveAsFlow()

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val hiddenListIds = MutableStateFlow<Set<Long?>>(emptySet())

    val uiState: StateFlow<TaskListUiState> = combine(
        if (isAllTasks) flowOf(null) else taskListRepository.observeList(listId),
        combine(taskListRepository.observeLists(), taskRepository.observeActiveTaskCount(), ::Pair),
        if (isAllTasks) taskRepository.observeAllTasks() else taskRepository.observeTasks(listId),
        combine(selection, hiddenListIds, ::Pair),
        settingsRepository.settings.map { it.dayFilter }.distinctUntilChanged(),
    ) { list, (lists, allTasksCount), allTasks, (selected, hidden), dayFilter ->
        val today = LocalDate.now(clock)
        val tasks = allTasks.filter {
            (dayFilter == DayFilter.ALL || it.matchesDay(dayFilter, today)) && it.listId !in hidden
        }
        val (completed, active) = tasks.partition { it.isCompleted }
        val sections = if (isAllTasks) {
            val byList = active.groupBy { it.listId }
            listOfNotNull(byList[null]?.let { TaskSection(null, it.sortedFor(TaskSortMode.CREATED)) }) +
                lists.mapNotNull { l ->
                    byList[l.id]?.let { TaskSection(l, it.sortedFor(l.sortMode, l.sortDescending)) }
                }
        } else if (active.isEmpty()) {
            emptyList()
        } else {
            listOf(TaskSection(null, active.sortedFor(list?.sortMode ?: TaskSortMode.CREATED, list?.sortDescending ?: false)))
        }
        TaskListUiState(
            isLoading = false,
            list = list,
            lists = lists,
            allTasksCount = allTasksCount,
            sections = sections,
            completedTasks = completed.sortedByDescending { it.completedAt },
            isListMissing = !isAllTasks && list == null,
            isAllTasks = isAllTasks,
            // Deleted, moved-away or filtered-out tasks drop out of the selection.
            selectedIds = selected.intersect(tasks.mapTo(HashSet()) { it.id }),
            dayFilter = dayFilter,
            // A deleted list can't be hidden anymore.
            hiddenListIds = hidden.filterTo(HashSet()) { id -> id == null || lists.any { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    fun setDayFilter(filter: DayFilter) {
        viewModelScope.launch { settingsRepository.setDayFilter(filter) }
    }

    /** Shows or hides [listId]'s tasks in All tasks; a null [listId] is the tasks in no list. */
    fun setListShown(listId: Long?, shown: Boolean) =
        hiddenListIds.update { if (shown) it - listId else it + listId }

    fun showAllLists() {
        hiddenListIds.value = emptySet()
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        // Tasks added in All tasks are in no list, which is shown again so the new task doesn't vanish.
        val target = if (isAllTasks) null else listId
        if (isAllTasks) setListShown(null, true)
        // Under a day filter the new task is due that day, so it doesn't vanish once added.
        val today = LocalDate.now(clock)
        val dueDate = when (uiState.value.dayFilter) {
            DayFilter.ALL -> null
            DayFilter.TODAY -> today
            DayFilter.TOMORROW -> today.plusDays(1)
        }
        viewModelScope.launch {
            _addedTaskIds.send(taskRepository.addTask(Task(listId = target, title = title, dueDate = dueDate)))
        }
    }

    fun setCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch { taskRepository.setTaskCompleted(task.id, completed) }
    }

    fun deleteTasks(tasks: List<Task>) {
        viewModelScope.launch { taskRepository.deleteTasks(tasks.map { it.id }) }
    }

    fun restoreTasks(tasks: List<Task>) {
        viewModelScope.launch { taskRepository.restoreTasks(tasks) }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            if (isAllTasks) {
                // Only the lists on screen: completed tasks of hidden lists aren't visible to clear.
                val hidden = hiddenListIds.value
                (listOf(null) + uiState.value.lists.map { it.id })
                    .filter { it !in hidden }
                    .forEach { taskRepository.clearCompleted(it) }
            } else {
                taskRepository.clearCompleted(listId)
            }
        }
    }

    // Bulk edits. A single task from the long-press sheet is just a one-element collection.

    fun setDueDate(ids: Collection<Long>, date: LocalDate?) {
        viewModelScope.launch { taskRepository.updateTasks(ids) { it.withDueDate(date) } }
    }

    fun setPriority(ids: Collection<Long>, priority: Priority) {
        viewModelScope.launch { taskRepository.updateTasks(ids) { it.copy(priority = priority) } }
    }

    fun setTasksCompleted(ids: Collection<Long>, completed: Boolean) {
        viewModelScope.launch { taskRepository.setTasksCompleted(ids, completed) }
    }

    /** A null [targetListId] takes the tasks out of their lists. */
    fun moveTasks(ids: Collection<Long>, targetListId: Long?) {
        viewModelScope.launch { taskRepository.moveTasks(ids, targetListId) }
    }

    /** Persists a drag in the lists drawer; [orderedIds] is every list in its new order. */
    fun reorderLists(orderedIds: List<Long>) {
        viewModelScope.launch { taskListRepository.reorderLists(orderedIds) }
    }

    /** Persists a drag; [orderedIds] is the list's active tasks in their new order, which becomes "My order". */
    fun reorder(orderedIds: List<Long>) {
        val list = uiState.value.list
        viewModelScope.launch {
            taskRepository.reorderTasks(orderedIds)
            if (list != null && list.sortMode != TaskSortMode.MANUAL) {
                taskListRepository.updateList(list.copy(sortMode = TaskSortMode.MANUAL))
            }
        }
    }

    // Selection

    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }

    fun selectAll() {
        selection.value = uiState.value.activeTasks.mapTo(HashSet()) { it.id }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    /** Creates a list and opens it. */
    fun createList(name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = taskListRepository.addList(name, color)
            _events.send(TaskListEvent.OpenList(id))
        }
    }

    fun editList(name: String, color: Int) {
        val list = uiState.value.list ?: return
        if (name.isBlank()) return
        viewModelScope.launch { taskListRepository.updateList(list.copy(name = name.trim(), color = color)) }
    }

    fun setSortMode(mode: TaskSortMode, descending: Boolean) {
        val list = uiState.value.list ?: return
        viewModelScope.launch { taskListRepository.updateList(list.copy(sortMode = mode, sortDescending = descending)) }
    }

    /** Deletes the current list and its tasks; [TaskListUiState.isListMissing] then flips to true. */
    fun deleteList() {
        if (isAllTasks) return
        viewModelScope.launch { taskListRepository.deleteList(listId) }
    }
}
