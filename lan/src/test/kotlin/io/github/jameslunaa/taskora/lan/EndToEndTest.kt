package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EndToEndTest {
    private class FakeHost : LanHost {
        val tasks = mutableMapOf(1L to WireTask(id = 1, listId = 1, title = "Water plants"))
        var added = 0
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        override suspend fun lists() = listOf(WireList(1, "Inbox", -1, tasks.values.count { !it.completed }))
        override suspend fun tasks(listId: Long, includeCompleted: Boolean) =
            if (listId != 1L && listId != LanHost.ALL_LISTS) null else tasks.values.filter { includeCompleted || !it.completed }
        override suspend fun setCompleted(taskId: Long, completed: Boolean): Boolean {
            val task = tasks[taskId] ?: return false
            tasks[taskId] = task.copy(completed = completed)
            return true
        }
        override suspend fun addTask(listId: Long?, task: WireTask): Long {
            added++
            val id = (tasks.keys.maxOrNull() ?: 0) + 1
            tasks[id] = task.copy(id = id, listId = listId ?: 1)
            return id
        }
    }

    private class Prompts(var answer: Boolean = true) : PairingPrompts {
        val codes = mutableListOf<String>()
        val qrPeers = mutableListOf<String>()
        var hang = false
        override suspend fun confirmCode(peer: PeerInfo, code: String): Boolean {
            codes += code
            if (hang) awaitCancellation()
            return answer
        }
        override suspend fun confirmQr(peer: PeerInfo): Boolean {
            qrPeers += peer.name
            return answer
        }
    }

    private val timeouts = LanTimeouts(unpaired = 2.seconds, pairing = 3.seconds, request = 3.seconds)
    private val serverIdentity = Identity.create()
    private val clientIdentity = Identity.create()
    private val serverTrust = MemoryTrustStore()
    private val clientTrust = MemoryTrustStore()
    private val host = FakeHost()
    private val serverPrompts = Prompts()
    private val server = LanServer(serverIdentity, { "Laptop" }, "linux", serverTrust, host, serverPrompts, timeouts)
    private val client = LanClient(clientIdentity, { "Phone" }, "android", clientTrust, timeouts)

    init {
        server.start(preferredPort = 0)
    }

    @After
    fun tearDown() = server.stop()

    private val address get() = InetSocketAddress("127.0.0.1", server.port)

    private fun run(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        runBlocking(Dispatchers.Default) { withTimeout(20.seconds) { block() } }

    private suspend fun pairedSession(): LanSession {
        val session = client.connect(address)
        assertTrue(session.pairByCode { true })
        return session
    }

    @Test
    fun unpairedDevice_canOnlyPair() = run {
        client.connect(address).use { session ->
            assertFalse(session.isPaired)
            assertEquals("Laptop", session.peer.name)
            val error = runCatching { session.lists() }.exceptionOrNull() as LanException
            assertEquals(ErrorCode.NOT_PAIRED, error.code)
        }
    }

    @Test
    fun codePairing_bothSeeTheSameCode_andTrustEachOther() = run {
        val clientCodes = mutableListOf<String>()
        client.connect(address).use { session ->
            assertTrue(session.pairByCode { clientCodes += it; true })
            assertEquals(serverPrompts.codes, clientCodes)
            assertEquals(serverIdentity.fingerprint, clientTrust.devices.value.single().fingerprint)
            // The server stores the client a moment after the client's last step.
            val stored = withTimeout(3.seconds) { serverTrust.devices.first { it.isNotEmpty() } }.single()
            assertEquals(clientIdentity.fingerprint, stored.fingerprint)
            assertEquals("Phone", stored.name)
            // Same connection, now paired.
            assertEquals("Inbox", session.lists().single().name)
        }
    }

    @Test
    fun declinedOnEitherSide_storesNothing() = run {
        serverPrompts.answer = false
        client.connect(address).use { assertFalse(it.pairByCode { true }) }
        serverPrompts.answer = true
        client.connect(address).use { assertFalse(it.pairByCode { false }) }
        assertTrue(serverTrust.devices.value.isEmpty())
        assertTrue(clientTrust.devices.value.isEmpty())
    }

    @Test
    fun cancelOnOneSide_closesTheOtherSidesPrompt() = run {
        serverPrompts.hang = true
        client.connect(address).use { assertFalse(it.pairByCode { false }) }
        assertTrue(serverTrust.devices.value.isEmpty())
    }

    @Test
    fun requests_work_andAddingIsIdempotent() = run {
        pairedSession().use { session ->
            assertEquals(listOf("Water plants"), session.tasks(1).map { it.title })
            session.setCompleted(1, true)
            assertTrue(session.tasks(1).isEmpty())
            assertEquals(1, session.tasks(LanHost.ALL_LISTS, includeCompleted = true).size)
            assertEquals(ErrorCode.NOT_FOUND, (runCatching { session.tasks(99) }.exceptionOrNull() as LanException).code)
            assertEquals(ErrorCode.NOT_FOUND, (runCatching { session.setCompleted(99, true) }.exceptionOrNull() as LanException).code)

            val copy = WireTask(title = "Call mum", subtasks = listOf(WireSubtask(title = "Ask about Sunday")))
            val first = session.addTask("key-1", null, copy)
            assertEquals(first, session.addTask("key-1", null, copy))
            assertNotEquals(first, session.addTask("key-2", null, copy))
            assertEquals(2, host.added)
        }
    }

    @Test
    fun subscribe_sendsChanged() = run {
        pairedSession().use { session ->
            session.subscribe()
            val changed = async { session.changes.first() }
            kotlinx.coroutines.delay(100)
            host.changes.emit(Unit)
            withTimeout(3.seconds) { changed.await() }
        }
    }

    @Test
    fun qrPairing_asksTheComputer_andTokensAreSingleUse() = run {
        server.startQrPairing("127.0.0.1")
        val invite = server.qrInvite.filterNotNull().first()
        val link = PairingLink.parse(invite.uri)!!
        assertEquals(serverIdentity.fingerprint, link.fingerprint)

        client.connect(address, expectedFingerprint = link.fingerprint).use { session ->
            assertTrue(session.pairByToken(link.token))
        }
        assertEquals(listOf("Phone"), serverPrompts.qrPeers)
        assertEquals(1, serverTrust.devices.value.size)
        assertEquals(1, clientTrust.devices.value.size)

        // The used token doesn't work again, and a new QR code replaced it.
        val other = LanClient(Identity.create(), { "Someone else" }, "android", MemoryTrustStore(), timeouts)
        other.connect(address).use { session ->
            val error = runCatching { session.pairByToken(link.token) }.exceptionOrNull() as LanException
            assertEquals(ErrorCode.REJECTED, error.code)
        }
        val next = server.qrInvite.filterNotNull().first { it.token.isNotEmpty() && it.token != link.token }
        assertNotEquals(link.token, next.token)
    }

    @Test
    fun qrLink_fromAnotherDevice_isRefused() = run {
        try {
            client.connect(address, expectedFingerprint = "0".repeat(64)).close()
            fail("Connected to the wrong device")
        } catch (e: LanException) {
            assertEquals(ErrorCode.REJECTED, e.code)
        }
    }

    @Test
    fun forgottenOnOneSide_isForgottenOnTheOther() = run {
        pairedSession().close()
        serverTrust.remove(clientIdentity.fingerprint)
        client.connect(address).use { session ->
            assertTrue(session.unpairedByPeer)
            assertFalse(session.isPaired)
        }
        assertTrue(clientTrust.devices.value.isEmpty())
    }

    @Test
    fun unpair_removesBothSides() = run {
        pairedSession().use { it.unpair() }
        assertTrue(clientTrust.devices.value.isEmpty())
        assertTrue(serverTrust.devices.value.isEmpty())
    }

    @Test
    fun repeatedFailures_makePairingBusy() = run {
        serverPrompts.answer = false
        repeat(3) { client.connect(address).use { assertFalse(it.pairByCode { true }) } }
        client.connect(address).use { session ->
            val error = runCatching { session.pairByCode { true } }.exceptionOrNull() as LanException
            assertEquals(ErrorCode.BUSY, error.code)
        }
    }

    @Test
    fun idleUnpairedConnection_isDropped() = run {
        client.connect(address).use { session ->
            withTimeout(5.seconds) { session.awaitClosed() }
            assertFalse(session.isOpen)
        }
    }

    @Test
    fun remoteDevice_reconnects_andRefreshes() = run {
        pairedSession().close()
        val device = clientTrust.devices.value.single()
        var reachable = true
        val remote = RemoteDevice(device, client, { if (reachable) address else null }, this, maxBackoff = 1.seconds)
        remote.start()
        withTimeout(5.seconds) { remote.state.first { it == DeviceState.ONLINE } }
        assertEquals("Inbox", remote.call { lists() }.single().name)

        // A change on the device asks for a refresh.
        remote.refreshes.first()
        val refreshed = async { remote.refreshes.drop(1).first() }
        kotlinx.coroutines.delay(100)
        host.changes.emit(Unit)
        withTimeout(3.seconds) { refreshed.await() }

        // The device goes away, then comes back.
        reachable = false
        server.stop()
        withTimeout(5.seconds) { remote.state.first { it == DeviceState.UNAVAILABLE } }
        reachable = true
        server.start(preferredPort = 0)
        remote.retryNow()
        withTimeout(10.seconds) { remote.state.first { it == DeviceState.ONLINE } }
        val id = remote.addTask(null, WireTask(title = "Sent"))
        assertEquals("Sent", host.tasks[id]?.title)
        remote.stop()
    }

    @Test
    fun remoteDevice_thatForgotUs_isNotPaired() = run {
        pairedSession().close()
        val device = clientTrust.devices.value.single()
        serverTrust.remove(clientIdentity.fingerprint)
        val remote = RemoteDevice(device, client, { address }, this)
        remote.start()
        withTimeout(5.seconds) { remote.state.first { it == DeviceState.NOT_PAIRED } }
        assertTrue(clientTrust.devices.value.isEmpty())
        remote.stop()
    }

    @Test
    fun pairingTimesOut_whenNobodyAnswers() = run {
        serverPrompts.hang = true
        client.connect(address).use { session ->
            val never = CompletableDeferred<Boolean>()
            assertFalse(session.pairByCode { never.await() })
        }
        assertTrue(serverTrust.devices.value.isEmpty())
        assertNull(clientTrust.devices.value.firstOrNull())
    }
}
