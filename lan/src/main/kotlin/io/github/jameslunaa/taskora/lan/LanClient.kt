package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Connecting to another device (docs/lan.md). */
class LanClient(
    private val identity: Identity,
    private val deviceName: () -> String,
    private val os: String,
    private val trustStore: TrustStore,
    private val timeouts: LanTimeouts = LanTimeouts(),
) {
    private val tls = Tls(identity)

    /**
     * Connects and exchanges `hello`. With [expectedFingerprint] (from a QR code), a device with
     * another key is refused. Throws [LanUnavailable] when the device can't be reached.
     */
    suspend fun connect(address: InetSocketAddress, expectedFingerprint: String? = null): LanSession {
        val socket = try {
            withContext(Dispatchers.IO) {
                val plain = Socket().apply { connect(address, timeouts.handshake.inWholeMilliseconds.toInt()) }
                tls.clientSocket(plain).apply {
                    soTimeout = timeouts.handshake.inWholeMilliseconds.toInt()
                    startHandshake()
                }
            }
        } catch (e: IOException) {
            throw LanUnavailable("Can't reach $address", e)
        }
        val fingerprint = Tls.peerFingerprint(socket)
        if (expectedFingerprint != null && fingerprint != expectedFingerprint) {
            runCatching { socket.close() }
            throw LanException(ErrorCode.REJECTED, "That's a different device")
        }
        val connection = Connection(socket, fingerprint, timeouts)
        connection.start()
        try {
            val known = trustStore.find(fingerprint)
            val reply = connection.request(timeouts.hello) {
                Hello(it, PROTOCOL_VERSION, identity.deviceId, deviceName(), os, paired = known != null)
            } as? Hello ?: throw ProtocolException("Expected hello")
            val peer = PeerInfo(reply.deviceId, reply.name, reply.os, fingerprint)
            var unpairedByPeer = false
            if (known != null && !reply.paired) {
                // The other device forgot this one (docs/lan.md, "Hello"): forget it too.
                trustStore.remove(fingerprint)
                unpairedByPeer = true
            } else if (known != null) {
                trustStore.rename(fingerprint, reply.name)
            }
            return LanSession(connection, peer, identity, trustStore, timeouts, unpairedByPeer)
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }
}

/** An open connection to [peer]. */
class LanSession internal constructor(
    private val connection: Connection,
    val peer: PeerInfo,
    private val identity: Identity,
    private val trustStore: TrustStore,
    private val timeouts: LanTimeouts,
    /** The peer no longer trusted this device, so it was removed here as well. */
    val unpairedByPeer: Boolean,
) : AutoCloseable {
    val isPaired: Boolean get() = trustStore.find(peer.fingerprint) != null
    val isOpen: Boolean get() = connection.isOpen

    /** Emits after [subscribe] whenever the peer's data changes. */
    val changes: SharedFlow<Unit> get() = connection.changes

    /** Suspends until the connection closes. */
    suspend fun awaitClosed() = connection.closed.await()

    suspend fun lists(): List<WireList> = (connection.request { GetLists(it) } as ListsReply).lists

    suspend fun tasks(listId: Long, includeCompleted: Boolean = false): List<WireTask> =
        (connection.request { GetTasks(it, listId, includeCompleted) } as TasksReply).tasks

    suspend fun setCompleted(taskId: Long, completed: Boolean) {
        connection.request { SetCompleted(it, taskId, completed) }
    }

    /** [key] identifies this copy, so a retry with the same key can't add it twice. */
    suspend fun addTask(key: String, listId: Long?, task: WireTask): Long =
        (connection.request { AddTask(it, key, listId, task) } as TaskAdded).taskId

    suspend fun subscribe() {
        connection.request { Subscribe(it) }
    }

    /** Removes the peer here, and asks it to remove this device too. */
    suspend fun unpair() {
        trustStore.remove(peer.fingerprint)
        runCatching { connection.request { Unpair(it) } }
    }

    /**
     * Code pairing as the initiator (docs/lan.md). [prompt] shows the code and returns whether
     * the user accepted. Returns true once both users have confirmed; both devices then trust
     * each other. Throws [LanException] with [ErrorCode.BUSY] if the other device can't pair now.
     */
    suspend fun pairByCode(prompt: suspend (code: String) -> Boolean): Boolean {
        val id = connection.newId()
        val error = connection.expectReply(id)
        try {
            val paired = withTimeoutOrNull(timeouts.pairing) {
                connection.send(PairStart(id))
                val commit = select<Message> {
                    error.onAwait { it }
                    connection.pairing.onReceiveCatching { it.getOrNull() ?: PairCancel("Connection closed") }
                }
                if (commit is ErrorReply) throw LanException(commit.code, commit.message)
                val commitment = (commit as? PairCommit)?.commitment ?: return@withTimeoutOrNull false
                val initiatorNonce = PairingMath.nonce()
                connection.send(PairNonce(initiatorNonce.toHex()))
                val responderNonce = (connection.nextPairingStep() as? PairReveal)?.nonce?.hexToBytes()
                    ?: return@withTimeoutOrNull false
                if (!PairingMath.commitment(responderNonce).toHex().equals(commitment)) {
                    // The other side changed its value after seeing ours: not a pairing to trust.
                    connection.send(PairCancel("Commitment mismatch"))
                    return@withTimeoutOrNull false
                }
                val code = PairingMath.code(identity.fingerprint, peer.fingerprint, initiatorNonce, responderNonce)
                connection.confirmBoth { prompt(code) }
            } ?: run {
                runCatching { connection.send(PairCancel("Timed out")) }
                false
            }
            if (paired) trustStore.add(peer.toPairedDevice())
            return paired
        } finally {
            connection.forget(id)
        }
    }

    /**
     * QR pairing as the phone: sends the scanned [token] and waits while the computer's user
     * decides. Returns true when paired; throws [LanException] with [ErrorCode.REJECTED] when
     * the token was wrong or expired, or the user declined.
     */
    suspend fun pairByToken(token: String): Boolean {
        val reply = connection.request(timeouts.pairing + timeouts.request) { PairToken(it, token) }
        if (reply !is PairDone) return false
        trustStore.add(peer.toPairedDevice())
        return true
    }

    override fun close() = connection.close()
}
