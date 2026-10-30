package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/** A paired device's state, as the apps show it (docs/lan.md, "Connection loss"). */
enum class DeviceState { ONLINE, CONNECTING, UNAVAILABLE, NOT_PAIRED }

/**
 * Keeps a connection to a paired [device] while [start]ed, e.g. while the user browses it:
 * reconnects with exponential backoff (1, 2, 4, 8, 16, then every 30 seconds), or at once on
 * [retryNow]; subscribes to changes; and asks for a refresh after every (re)connection and every
 * `changed` (docs/lan.md, "Connection loss").
 */
class RemoteDevice(
    val device: PairedDevice,
    private val client: LanClient,
    /** The device's current address from discovery, or null while it isn't announced. */
    private val address: () -> InetSocketAddress?,
    private val scope: CoroutineScope,
    private val maxBackoff: Duration = 30.seconds,
) {
    private val _state = MutableStateFlow(DeviceState.CONNECTING)
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    private val _refreshes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** Fetch what's shown again: after connecting, after reconnecting, and when the device's data changed. */
    val refreshes: SharedFlow<Unit> = _refreshes.asSharedFlow()

    private val session = MutableStateFlow<LanSession?>(null)
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { keepConnected() }
    }

    fun stop() {
        job?.cancel()
        job = null
        session.value?.close()
        session.value = null
    }

    /** Retry now instead of waiting out the backoff: discovery saw the device, or the network came back. */
    fun retryNow() {
        wakeUp.trySend(Unit)
    }

    private suspend fun keepConnected() {
        var backoff = 1.seconds
        try {
            while (currentCoroutineContext().isActive) {
                val target = address()
                if (target == null) {
                    _state.value = DeviceState.UNAVAILABLE
                } else {
                    if (_state.value != DeviceState.UNAVAILABLE) _state.value = DeviceState.CONNECTING
                    val connected = runCatching { client.connect(target) }.getOrNull()
                    if (connected != null) {
                        if (!connected.isPaired) {
                            connected.close()
                            _state.value = DeviceState.NOT_PAIRED
                            return
                        }
                        backoff = 1.seconds
                        if (runCatching { connected.subscribe() }.isSuccess) {
                            session.value = connected
                            _state.value = DeviceState.ONLINE
                            _refreshes.tryEmit(Unit)
                            // A child of this loop, so stop() ends it too.
                            coroutineScope {
                                val changes = launch { connected.changes.collect { _refreshes.tryEmit(Unit) } }
                                connected.awaitClosed()
                                changes.cancel()
                            }
                        }
                        session.value = null
                        connected.close()
                        _state.value = DeviceState.CONNECTING
                        continue
                    }
                    _state.value = DeviceState.UNAVAILABLE
                }
                withTimeoutOrNull(backoff) { wakeUp.receive() }
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
            }
        } finally {
            session.value?.close()
            session.value = null
        }
    }

    /**
     * Runs [block] on the connection, waiting up to [wait] for one. Throws [LanUnavailable] when
     * the device can't be reached in time, or [LanException] for an `error` reply.
     */
    suspend fun <T> call(wait: Duration = 15.seconds, block: suspend LanSession.() -> T): T {
        start()
        // Skips a session that just closed and hasn't been cleared yet.
        val current = withTimeoutOrNull(wait) { session.filterNotNull().first { it.isOpen } }
            ?: throw LanUnavailable("${device.name} isn't available")
        return current.block()
    }

    /**
     * Copies [task] to the device. A dropped connection is retried with the same key for up to
     * [retryFor], so the device never gets it twice; after that it throws [LanUnavailable] and
     * the user should be told the copy may not have arrived.
     */
    suspend fun addTask(listId: Long?, task: WireTask, retryFor: Duration = 2.minutes): Long {
        val key = UUID.randomUUID().toString()
        val deadline = System.nanoTime() + retryFor.inWholeNanoseconds
        while (true) {
            val left = (deadline - System.nanoTime()).coerceAtLeast(0)
            try {
                return call(wait = left.nanoseconds) { addTask(key, listId, task) }
            } catch (e: LanUnavailable) {
                if (System.nanoTime() >= deadline) throw e
                retryNow()
            }
        }
    }
}
