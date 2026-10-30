package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** This device's data, as the server shows it to paired devices. */
interface LanHost {
    suspend fun lists(): List<WireList>

    /** The tasks of [listId] ([ALL_LISTS] for all) in the list's order, or null if there's no such list. */
    suspend fun tasks(listId: Long, includeCompleted: Boolean): List<WireTask>?

    /** False when there's no such task. Setting the state a task already has must do nothing. */
    suspend fun setCompleted(taskId: Long, completed: Boolean): Boolean

    /** Adds a copy of [task] to [listId], or to the Inbox when it's null or gone; returns its new id. */
    suspend fun addTask(listId: Long?, task: WireTask): Long

    /** Emits whenever lists or tasks change. */
    val changes: Flow<Unit>

    companion object {
        const val ALL_LISTS = -1L
    }
}

/** What the server tells the app about, beyond the [TrustStore]'s own changes. */
sealed interface ServerEvent {
    /** [device] said it no longer trusts this one, so it was removed here too. */
    data class Unpaired(val device: PairedDevice) : ServerEvent
}

/** A QR pairing invitation (docs/lan.md, "By scanning a QR code"). */
data class QrInvite(val uri: String, val token: String, val expiresAtMillis: Long)

/**
 * Listens for other Taskora devices (docs/lan.md). Start it while this device should be
 * reachable; [stop] closes everything. Discovery (announcing [port]) is the app's job.
 */
class LanServer(
    private val identity: Identity,
    private val deviceName: () -> String,
    private val os: String,
    private val trustStore: TrustStore,
    private val host: LanHost,
    private val prompts: PairingPrompts,
    private val timeouts: LanTimeouts = LanTimeouts(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val tls = Tls(identity)
    private var scope: CoroutineScope? = null
    private var serverSocket: SSLServerSocket? = null
    private val connections = AtomicInteger(0)
    private val unpairedConnections = AtomicInteger(0)
    private val pairingBusy = AtomicBoolean(false)
    private val rateLimit = PairingRateLimit(now)
    private val addedTasks = AddedTaskKeys(now)

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val _qrInvite = MutableStateFlow<QrInvite?>(null)

    /** The current QR invitation while QR pairing is on; it changes when used or expired. */
    val qrInvite: StateFlow<QrInvite?> = _qrInvite.asStateFlow()
    private var qrJob: Job? = null

    /** The port it listens on, once started. */
    val port: Int get() = serverSocket?.localPort ?: 0

    /** Starts listening, on [preferredPort] if it's free, otherwise on any free port. */
    fun start(preferredPort: Int = DEFAULT_PORT) {
        if (scope != null) return
        val socket = try {
            tls.serverSocket(preferredPort)
        } catch (_: BindException) {
            tls.serverSocket(0)
        }
        serverSocket = socket
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serverScope
        serverScope.launch { acceptLoop(socket, serverScope) }
    }

    fun stop() {
        stopQrPairing()
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    /** Shows a QR invitation for [hostAddress] (see [LocalAddress]) until [stopQrPairing]. */
    fun startQrPairing(hostAddress: String) {
        val serverScope = scope ?: return
        qrJob?.cancel()
        qrJob = serverScope.launch {
            while (isActive) {
                val invite = newInvite(hostAddress)
                _qrInvite.value = invite
                // A new invitation when this one expires, or as soon as it's used (value changes).
                withTimeoutOrNull(timeouts.qrToken) { qrInvite.collectUntilChanged(invite) }
            }
        }
    }

    fun stopQrPairing() {
        qrJob?.cancel()
        qrJob = null
        _qrInvite.value = null
    }

    private fun newInvite(hostAddress: String): QrInvite {
        val token = PairingMath.token()
        val uri = "taskora://pair?v=$PROTOCOL_VERSION&id=${identity.deviceId}&fp=${identity.fingerprint}" +
            "&host=$hostAddress&port=$port&token=$token"
        return QrInvite(uri, token, now() + timeouts.qrToken.inWholeMilliseconds)
    }

    private suspend fun acceptLoop(socket: ServerSocket, serverScope: CoroutineScope) {
        while (serverScope.isActive) {
            val client = try {
                withContext(Dispatchers.IO) { socket.accept() } as SSLSocket
            } catch (_: SocketException) {
                return // Closed by stop().
            } catch (_: IOException) {
                continue
            }
            if (connections.incrementAndGet() > MAX_CONNECTIONS) {
                connections.decrementAndGet()
                runCatching { client.close() }
                continue
            }
            serverScope.launch {
                try {
                    serve(client)
                } finally {
                    connections.decrementAndGet()
                    runCatching { client.close() }
                }
            }
        }
    }

    private suspend fun serve(socket: SSLSocket) {
        val fingerprint = try {
            socket.soTimeout = timeouts.handshake.inWholeMilliseconds.toInt()
            withContext(Dispatchers.IO) { socket.startHandshake() }
            Tls.peerFingerprint(socket)
        } catch (_: IOException) {
            return
        }
        val connection = Connection(socket, fingerprint, timeouts)
        connection.start()
        try {
            val hello = withTimeoutOrNull(timeouts.hello) { connection.requests.receiveCatching().getOrNull() } as? Hello
                ?: return
            if (hello.protocol != PROTOCOL_VERSION) {
                connection.send(ErrorReply(hello.id, ErrorCode.UNSUPPORTED_VERSION, "Protocol $PROTOCOL_VERSION only"))
                return
            }
            val peer = PeerInfo(hello.deviceId, hello.name, hello.os, fingerprint)
            var paired = trustStore.find(fingerprint) != null
            if (paired && !hello.paired) {
                // The other side forgot this device (docs/lan.md, "Hello"): forget it too.
                trustStore.find(fingerprint)?.let { _events.tryEmit(ServerEvent.Unpaired(it)) }
                trustStore.remove(fingerprint)
                paired = false
            }
            if (paired) trustStore.rename(fingerprint, hello.name)
            connection.send(Hello(hello.id, PROTOCOL_VERSION, identity.deviceId, deviceName(), os, paired))
            if (paired) {
                serveRequests(connection, peer)
            } else {
                if (unpairedConnections.incrementAndGet() > MAX_UNPAIRED) return
                try {
                    if (servePairing(connection, peer)) serveRequests(connection, peer)
                } finally {
                    unpairedConnections.decrementAndGet()
                }
            }
        } finally {
            connection.close()
        }
    }

    /**
     * An unpaired connection: it must start pairing within [LanTimeouts.unpaired]. Returns true
     * once it's paired. Anything else gets `not_paired`.
     */
    private suspend fun servePairing(connection: Connection, peer: PeerInfo): Boolean {
        val deadline = now() + timeouts.unpaired.inWholeMilliseconds
        while (true) {
            val left = deadline - now()
            if (left <= 0) return false
            val request = withTimeoutOrNull(left) { connection.requests.receiveCatching().getOrNull() } ?: return false
            when (request) {
                is PairStart -> if (respondCodePairing(connection, request, peer)) return true
                is PairToken -> if (respondQrPairing(connection, request, peer)) return true
                else -> connection.send(ErrorReply(request.id, ErrorCode.NOT_PAIRED, "Pair first"))
            }
        }
    }

    private suspend fun respondCodePairing(connection: Connection, start: PairStart, peer: PeerInfo): Boolean {
        if (!rateLimit.allowed() || !pairingBusy.compareAndSet(false, true)) {
            connection.send(ErrorReply(start.id, ErrorCode.BUSY, "Pairing isn't possible right now"))
            return false
        }
        var paired = false
        try {
            paired = withTimeoutOrNull(timeouts.pairing) {
                val responderNonce = PairingMath.nonce()
                connection.send(PairCommit(PairingMath.commitment(responderNonce).toHex()))
                val initiatorNonce = (connection.nextPairingStep() as? PairNonce)?.nonce?.hexToBytes()
                    ?.takeIf { it.size == 32 } ?: return@withTimeoutOrNull false
                connection.send(PairReveal(responderNonce.toHex()))
                val code = PairingMath.code(peer.fingerprint, identity.fingerprint, initiatorNonce, responderNonce)
                connection.confirmBoth { prompts.confirmCode(peer, code) }
            } ?: run {
                runCatching { connection.send(PairCancel("Timed out")) }
                false
            }
        } catch (_: IOException) {
            paired = false
        } finally {
            pairingBusy.set(false)
            if (!paired) rateLimit.failed()
        }
        if (paired) trustStore.add(peer.toPairedDevice())
        return paired
    }

    private suspend fun respondQrPairing(connection: Connection, request: PairToken, peer: PeerInfo): Boolean {
        val invite = _qrInvite.value
        val valid = invite != null && invite.token == request.token && now() < invite.expiresAtMillis
        if (!valid || !pairingBusy.compareAndSet(false, true)) {
            // A wrong token also retires the shown one: nobody gets to keep guessing.
            if (invite != null && !valid) _qrInvite.value = invite.copy(token = "", expiresAtMillis = 0)
            connection.send(ErrorReply(request.id, ErrorCode.REJECTED, "Invalid or expired code"))
            return false
        }
        // Single use: the shown QR code is replaced.
        _qrInvite.value = invite!!.copy(token = "", expiresAtMillis = 0)
        try {
            val accepted = withTimeoutOrNull(timeouts.pairing) {
                coroutineScope {
                    val prompt = async { prompts.confirmQr(peer) }
                    select {
                        // The phone gave up: close the prompt.
                        connection.closed.onAwait {
                            prompt.cancel()
                            false
                        }
                        prompt.onAwait { it }
                    }
                }
            } ?: false
            if (!accepted) {
                runCatching { connection.send(ErrorReply(request.id, ErrorCode.REJECTED, "Declined")) }
                return false
            }
            trustStore.add(peer.toPairedDevice())
            connection.send(PairDone(request.id))
            return true
        } finally {
            pairingBusy.set(false)
        }
    }

    private suspend fun serveRequests(connection: Connection, peer: PeerInfo) {
        while (true) {
            val request = connection.requests.receiveCatching().getOrNull() ?: return
            // Removed meanwhile (e.g. unpaired here): only pairing is possible from now on.
            if (trustStore.find(peer.fingerprint) == null) {
                connection.send(ErrorReply(request.id, ErrorCode.NOT_PAIRED, "Not paired"))
                continue
            }
            try {
                handle(connection, request, peer)
            } catch (e: LanException) {
                connection.send(ErrorReply(request.id, e.code, e.message))
            } catch (e: IOException) {
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                connection.send(ErrorReply(request.id, ErrorCode.BAD_REQUEST, e.message))
            }
        }
    }

    private suspend fun handle(connection: Connection, request: Request, peer: PeerInfo) {
        when (request) {
            is GetLists -> connection.send(ListsReply(request.id, host.lists()))
            is GetTasks -> {
                val tasks = host.tasks(request.listId, request.includeCompleted)
                    ?: throw LanException(ErrorCode.NOT_FOUND, "No list ${request.listId}")
                connection.send(TasksReply(request.id, tasks))
            }
            is SetCompleted -> {
                if (!host.setCompleted(request.taskId, request.completed)) {
                    throw LanException(ErrorCode.NOT_FOUND, "No task ${request.taskId}")
                }
                connection.send(OkReply(request.id))
            }
            is AddTask -> {
                val taskId = addedTasks.getOrAdd(request.key) { host.addTask(request.listId, request.task) }
                connection.send(TaskAdded(request.id, taskId))
            }
            is Subscribe -> {
                connection.send(OkReply(request.id))
                connectionScope(connection).launch {
                    // At most one `changed` a second.
                    host.changes.conflate().collect {
                        connection.send(Changed)
                        delay(1_000)
                    }
                }
            }
            is Unpair -> {
                trustStore.find(peer.fingerprint)?.let { _events.tryEmit(ServerEvent.Unpaired(it)) }
                trustStore.remove(peer.fingerprint)
                connection.send(OkReply(request.id))
            }
            is PairStart, is PairToken -> throw LanException(ErrorCode.BAD_REQUEST, "Already paired")
            is Hello -> throw LanException(ErrorCode.BAD_REQUEST, "Hello already sent")
            is Ping -> connection.send(Pong(request.id))
        }
    }

    /** Work that lives as long as [connection]. */
    private fun connectionScope(connection: Connection): CoroutineScope {
        val job = Job(scope?.coroutineContext?.get(Job))
        connection.closed.invokeOnCompletion { job.cancel() }
        return CoroutineScope(Dispatchers.IO + job)
    }

    companion object {
        const val DEFAULT_PORT = 47390
        const val MAX_CONNECTIONS = 8
        const val MAX_UNPAIRED = 2
    }
}

/** `add_task` keys seen in the last hour, so a retried copy isn't added twice. */
internal class AddedTaskKeys(private val now: () -> Long, private val keepMillis: Long = 60 * 60_000L) {
    private val keys = LinkedHashMap<String, Pair<Long, Long>>()

    suspend fun getOrAdd(key: String, add: suspend () -> Long): Long {
        synchronized(this) {
            val time = now()
            keys.entries.removeAll { time - it.value.second > keepMillis }
            keys[key]?.let { return it.first }
        }
        val taskId = add()
        synchronized(this) { keys[key] = taskId to now() }
        return taskId
    }
}

/** Suspends until this flow's value is no longer [current]. */
private suspend fun <T> StateFlow<T>.collectUntilChanged(current: T) {
    first { it != current }
}
