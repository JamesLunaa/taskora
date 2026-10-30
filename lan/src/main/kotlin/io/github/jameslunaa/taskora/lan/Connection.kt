package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The timeouts of docs/lan.md, "Limits and timeouts"; tests shorten them. */
data class LanTimeouts(
    val handshake: Duration = 10.seconds,
    val hello: Duration = 10.seconds,
    val request: Duration = 15.seconds,
    val ping: Duration = 20.seconds,
    val idle: Duration = 60.seconds,
    val unpaired: Duration = 30.seconds,
    val pairing: Duration = 2.minutes,
    val qrToken: Duration = 5.minutes,
)

/** An `error` reply, or a failure the other side reported. */
class LanException(val code: String, message: String?) : IOException(message ?: code)

/** No reply in time, or the connection is gone: the device is unavailable for now. */
class LanUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * One TLS connection after its handshake. It matches replies to the requests sent on it, answers
 * pings, sends its own, and routes everything else: requests to [requests], `changed` to
 * [changes], and pairing steps to [pairing]. A protocol error, or [LanTimeouts.idle] without
 * anything received, closes it.
 */
internal class Connection(
    private val socket: SSLSocket,
    val peerFingerprint: String,
    private val timeouts: LanTimeouts,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Reply>>()
    private val isClosed = AtomicBoolean(false)

    val requests = Channel<Request>(Channel.UNLIMITED)
    val pairing = Channel<Message>(Channel.UNLIMITED)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Completes when the connection closes, for whatever reason. */
    val closed = CompletableDeferred<Unit>()

    fun start() {
        socket.soTimeout = timeouts.idle.inWholeMilliseconds.toInt()
        scope.launch { readLoop() }
        scope.launch {
            while (isActive) {
                delay(timeouts.ping)
                runCatching { send(Ping(nextId.getAndIncrement())) }
            }
        }
    }

    suspend fun send(message: Message) {
        if (isClosed.get()) throw LanUnavailable("Connection closed")
        val bytes = Wire.encode(message)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    Frames.write(socket.outputStream, bytes)
                } catch (e: IOException) {
                    close()
                    throw LanUnavailable("Connection lost", e)
                }
            }
        }
    }

    /**
     * Sends the request [build] makes with a fresh id and waits for its reply. An `error` reply
     * is thrown as [LanException]; no reply within [timeout] as [LanUnavailable].
     */
    suspend fun request(timeout: Duration = timeouts.request, build: (Long) -> Request): Reply {
        val id = nextId.getAndIncrement()
        val reply = CompletableDeferred<Reply>()
        pending[id] = reply
        try {
            send(build(id))
            val result = withTimeoutOrNull(timeout) { reply.await() } ?: throw LanUnavailable("No reply")
            if (result is ErrorReply) throw LanException(result.code, result.message)
            return result
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun readLoop() {
        try {
            while (true) {
                val frame = withContext(Dispatchers.IO) { Frames.read(socket.inputStream) }
                when (val decoded = Wire.decode(frame)) {
                    is Decoded.Malformed -> throw ProtocolException(decoded.reason)
                    is Decoded.Unknown -> send(ErrorReply(decoded.id ?: 0, ErrorCode.UNSUPPORTED, "Unknown type ${decoded.type}"))
                    is Decoded.Invalid -> {
                        val waiting = decoded.id?.let(pending::get)
                        if (waiting != null) {
                            waiting.completeExceptionally(ProtocolException("Bad ${decoded.type}: ${decoded.reason}"))
                        } else {
                            send(ErrorReply(decoded.id ?: 0, ErrorCode.BAD_REQUEST, decoded.reason))
                        }
                    }
                    is Decoded.Valid -> route(decoded.message)
                }
            }
        } catch (_: Exception) {
            // Closed, timed out (idle), or broken: all end the connection.
        } finally {
            close()
        }
    }

    private suspend fun route(message: Message) {
        // A hello is both: the client's is a request, the server's the reply to it.
        if (message is Reply) {
            pending[message.id]?.let {
                it.complete(message)
                return
            }
        }
        when (message) {
            is Ping -> send(Pong(message.id))
            is Pong -> Unit
            Changed -> _changes.tryEmit(Unit)
            is PairCommit, is PairNonce, is PairReveal, PairConfirm, is PairCancel -> pairing.send(message)
            is Request -> requests.send(message)
            else -> Unit // A reply nobody waits for any more.
        }
    }

    fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        pending.values.forEach { it.completeExceptionally(LanUnavailable("Connection closed")) }
        requests.close()
        pairing.close()
        closed.complete(Unit)
        scope.cancel()
    }

    val isOpen: Boolean get() = !isClosed.get()

    /** For a request whose success isn't a reply (pair_start): its id and where an error lands. */
    fun newId(): Long = nextId.getAndIncrement()

    fun expectReply(id: Long): CompletableDeferred<Reply> = CompletableDeferred<Reply>().also { pending[id] = it }

    fun forget(id: Long) {
        pending.remove(id)
    }
}
