package io.github.jameslunaa.taskora.devices

import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.lan.DeviceState
import io.github.jameslunaa.taskora.lan.MemoryTrustStore
import io.github.jameslunaa.taskora.lan.Identity
import io.github.jameslunaa.taskora.devices.toWire
import io.github.jameslunaa.taskora.testing.FakeTaskListRepository
import io.github.jameslunaa.taskora.testing.FakeTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.time.Clock
import kotlin.io.path.createTempDirectory

/**
 * Two complete [Devices] over the real network: mDNS discovery, pairing by code, browsing,
 * ticking off, sending and unpairing. Ignored by default because it needs multicast on the
 * local network (CI runners may not have it); run it by hand after changing the LAN code.
 */
@Ignore("Manual: needs multicast on the local network")
class DevicesLiveCheck {
    private fun device(name: String, scope: CoroutineScope): Triple<Devices, FakeTaskRepository, MemoryTrustStore> {
        val clock = Clock.systemUTC()
        val tasks = FakeTaskRepository(clock)
        val trust = MemoryTrustStore()
        val dir = createTempDirectory("taskora-$name").toFile()
        val devices = Devices(dir, FakeTaskListRepository(), tasks, clock, scope, name = name, trustStore = trust, identity = Identity.create())
        // Auto-accept every pairing question, like a user tapping Pair.
        scope.launch { devices.prompts.current.filterNotNull().collect { devices.prompts.answer(it, true) } }
        return Triple(devices, tasks, trust)
    }

    @Test
    fun twoComputers_discoverPairBrowseAndSend() = runBlocking(Dispatchers.Default) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (a, aTasks, aTrust) = device("Alpha", scope)
        val (b, bTasks, bTrust) = device("Beta", scope)
        bTasks.addTask(Task(listId = 1, title = "Water plants"))
        a.start(); b.start()
        val t0 = System.currentTimeMillis()
        val bSeen = withTimeout(20_000) { a.discovered.first { it.values.any { d -> d.name == "Beta" } } }.values.first { it.name == "Beta" }
        println("LIVE discovered Beta at ${bSeen.address} after ${System.currentTimeMillis() - t0} ms")
        println("LIVE pair: " + a.pair(bSeen))
        assertEquals(1, aTrust.devices.value.size)
        withTimeout(5_000) { bTrust.devices.first { it.isNotEmpty() } }
        println("LIVE both trust each other")

        val remote = a.remote(aTrust.devices.value.single())
        remote.start()
        withTimeout(10_000) { remote.state.first { it == DeviceState.ONLINE } }
        val remoteTasks = remote.call { tasks(1) }
        println("LIVE Alpha sees on Beta: " + remoteTasks.map { it.title })
        remote.call { setCompleted(remoteTasks.single().id!!, true) }
        println("LIVE ticked off on Beta: " + bTasks.getTask(remoteTasks.single().id!!)!!.isCompleted)
        val sentId = remote.addTask(null, Task(listId = 1, title = "Buy milk").toWire())
        println("LIVE sent to Beta: " + bTasks.getTask(sentId)?.title)
        a.unpair(aTrust.devices.value.single())
        withTimeout(5_000) { bTrust.devices.first { it.isEmpty() } }
        println("LIVE unpaired on both: a=${aTrust.devices.value.size} b=${bTrust.devices.value.size}")
        a.stop(); b.stop()
        scope.cancel()
    }
}
