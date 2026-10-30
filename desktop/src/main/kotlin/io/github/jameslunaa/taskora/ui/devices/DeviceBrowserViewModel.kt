package io.github.jameslunaa.taskora.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.devices.toNewTask
import io.github.jameslunaa.taskora.domain.model.TaskList
import io.github.jameslunaa.taskora.lan.DeviceState
import io.github.jameslunaa.taskora.lan.LanHost
import io.github.jameslunaa.taskora.lan.RemoteDevice
import io.github.jameslunaa.taskora.lan.WireList
import io.github.jameslunaa.taskora.lan.WireTask
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class DeviceBrowserState(
    val connection: DeviceState = DeviceState.CONNECTING,
    val lists: List<WireList> = emptyList(),
    /** [LanHost.ALL_LISTS] for all of them. */
    val listId: Long = TaskList.INBOX_ID,
    val tasks: List<WireTask> = emptyList(),
    /** False until the first successful fetch. */
    val isLoaded: Boolean = false,
)

sealed interface DeviceBrowserEvent {
    data class CopiedHere(val title: String) : DeviceBrowserEvent

    /** The device couldn't be reached for an action. */
    data object Unreachable : DeviceBrowserEvent
}

/**
 * Browsing a paired device: its lists and open tasks, fetched again after every (re)connection
 * and every change there (docs/lan.md, "Connection loss"). Tasks can be ticked off there, or
 * copied to this computer's Inbox.
 */
class DeviceBrowserViewModel(
    private val remote: RemoteDevice,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(DeviceBrowserState())
    val state: StateFlow<DeviceBrowserState> = _state.asStateFlow()

    private val _events = Channel<DeviceBrowserEvent>(Channel.BUFFERED)
    val events: Flow<DeviceBrowserEvent> = _events.receiveAsFlow()

    init {
        remote.start()
        viewModelScope.launch { remote.state.collect { connection -> _state.update { it.copy(connection = connection) } } }
        viewModelScope.launch { remote.refreshes.collect { refresh() } }
    }

    fun selectList(listId: Long) {
        _state.update { it.copy(listId = listId) }
        viewModelScope.launch { refresh() }
    }

    fun setCompleted(task: WireTask, completed: Boolean) {
        val id = task.id ?: return
        // Shown at once; the device's `changed` brings the real state.
        _state.update { state ->
            state.copy(tasks = state.tasks.map { if (it.id == id) it.copy(completed = completed) else it })
        }
        viewModelScope.launch {
            runCatching { remote.call { setCompleted(id, completed) } }.onFailure {
                _events.send(DeviceBrowserEvent.Unreachable)
                refresh()
            }
        }
    }

    fun copyHere(task: WireTask) {
        viewModelScope.launch {
            taskRepository.addTaskWithSubtasks(task.toNewTask(TaskList.INBOX_ID, Instant.now(clock)))
            _events.send(DeviceBrowserEvent.CopiedHere(task.title))
        }
    }

    /** Retry now, e.g. from the "unavailable" banner. */
    fun retry() = remote.retryNow()

    private suspend fun refresh() {
        runCatching {
            remote.call {
                val lists = lists()
                val wanted = _state.value.listId
                // The list was deleted there: back to the Inbox.
                val listId = if (wanted == LanHost.ALL_LISTS || lists.any { it.id == wanted }) wanted else TaskList.INBOX_ID
                Triple(lists, listId, tasks(listId))
            }
        }.onSuccess { (lists, listId, tasks) ->
            _state.update { it.copy(lists = lists, listId = listId, tasks = tasks, isLoaded = true) }
        }
    }

    override fun onCleared() {
        remote.stop()
    }
}
