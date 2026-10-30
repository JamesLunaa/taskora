package io.github.jameslunaa.taskora.devices

import io.github.jameslunaa.taskora.data.repository.TaskListRepository
import io.github.jameslunaa.taskora.data.repository.TaskRepository
import io.github.jameslunaa.taskora.lan.FileTrustStore
import io.github.jameslunaa.taskora.lan.Identity
import io.github.jameslunaa.taskora.lan.LanClient
import io.github.jameslunaa.taskora.lan.LanException
import io.github.jameslunaa.taskora.lan.LanServer
import io.github.jameslunaa.taskora.lan.LanUnavailable
import io.github.jameslunaa.taskora.lan.LocalAddress
import io.github.jameslunaa.taskora.lan.PairedDevice
import io.github.jameslunaa.taskora.lan.QrInvite
import io.github.jameslunaa.taskora.lan.RemoteDevice
import io.github.jameslunaa.taskora.lan.ServerEvent
import io.github.jameslunaa.taskora.lan.TrustStore
import io.github.jameslunaa.taskora.system.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/** How pairing started on this computer ended. */
enum class PairResult { PAIRED, DECLINED, BUSY, UNAVAILABLE }

/**
 * LAN devices on the desktop (docs/lan.md): this computer's identity and paired devices, the
 * server other devices connect to, discovery, and connections to paired devices. The desktop
 * stays reachable for as long as Taskora runs, in the tray included.
 */
class Devices(
    dataDir: File,
    taskListRepository: TaskListRepository,
    taskRepository: TaskRepository,
    clock: Clock,
    private val scope: CoroutineScope,
    /** Shown to other devices. */
    val name: String = localDeviceName(),
    private val trustStore: TrustStore = FileTrustStore(File(dataDir, "lan/devices.json")),
    private val identity: Identity = Identity.loadOrCreate(File(dataDir, "lan/identity.p12")),
) {
    private val os = if (Platform.isWindows) "windows" else "linux"
    val prompts = PairingRequests()
    private val server = LanServer(
        identity, { name }, os, trustStore, RepositoryLanHost(taskListRepository, taskRepository, clock), prompts,
    )
    private val client = LanClient(identity, { name }, os, trustStore)
    private var discovery: Discovery? = null
    private var discoveryJob: Job? = null
    private val remotes = mutableMapOf<String, RemoteDevice>()

    val paired: StateFlow<List<PairedDevice>> = trustStore.devices

    private val _discovered = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())

    /** Taskora devices on the network right now, by device ID. */
    val discovered: StateFlow<Map<String, DiscoveredDevice>> = _discovered.asStateFlow()

    /** While QR pairing is on. */
    val qrInvite: StateFlow<QrInvite?> = server.qrInvite

    /** Devices that unpaired this one while it wasn't looking. */
    val serverEvents: SharedFlow<ServerEvent> = server.events

    val fingerprint: String get() = identity.fingerprint

    /** Starts listening and announcing. Networking failures leave the app working without LAN. */
    fun start() {
        scope.launch(Dispatchers.IO) {
            runCatching { server.start() }.onFailure { return@launch }
            val bind = LocalAddress.best()?.let(InetAddress::getByName) ?: return@launch
            val found = runCatching { Discovery(bind, identity.deviceId, name, server.port, identity.fingerprint, os) }
                .getOrNull() ?: return@launch
            discovery = found
            discoveryJob = scope.launch {
                found.devices.collect { devices ->
                    _discovered.value = devices
                    // A device that (re)appeared: connect to it now rather than after the backoff.
                    synchronized(remotes) { remotes.values.toList() }
                        .filter { it.device.deviceId in devices }
                        .forEach(RemoteDevice::retryNow)
                }
            }
        }
    }

    suspend fun stop() {
        synchronized(remotes) { remotes.values.toList() }.forEach(RemoteDevice::stop)
        discoveryJob?.cancel()
        withContext(Dispatchers.IO) {
            discovery?.close()
            server.stop()
        }
    }

    /** The connection manager for [device], created on first use. */
    fun remote(device: PairedDevice): RemoteDevice = synchronized(remotes) {
        remotes.getOrPut(device.fingerprint) {
            RemoteDevice(device, client, { _discovered.value[device.deviceId]?.address }, scope)
        }
    }

    /** Pairs with [target] by comparing codes; the code shows through [prompts]. */
    suspend fun pair(target: DiscoveredDevice): PairResult {
        val session = try {
            client.connect(target.address)
        } catch (_: Exception) {
            return PairResult.UNAVAILABLE
        }
        return session.use {
            try {
                if (it.pairByCode { code -> prompts.confirmCode(it.peer, code) }) PairResult.PAIRED else PairResult.DECLINED
            } catch (e: LanException) {
                if (e.code == "busy") PairResult.BUSY else PairResult.DECLINED
            } catch (_: LanUnavailable) {
                PairResult.UNAVAILABLE
            }
        }
    }

    /** Shows a QR code for a phone to scan, until [stopQrPairing]. False when offline. */
    fun startQrPairing(): Boolean {
        val address = LocalAddress.best() ?: return false
        server.startQrPairing(address)
        return true
    }

    fun stopQrPairing() = server.stopQrPairing()

    /** Removes [device] here, and on the device too if it's reachable within a few seconds. */
    suspend fun unpair(device: PairedDevice) {
        val remote = synchronized(remotes) { remotes.remove(device.fingerprint) }
        runCatching { (remote ?: remote(device)).call(wait = 3.seconds) { unpair() } }
        remote?.stop()
        synchronized(remotes) { remotes.remove(device.fingerprint) }?.stop()
        trustStore.remove(device.fingerprint)
    }
}

/** The computer's name: Windows' COMPUTERNAME, /etc/hostname, or what the network calls it. */
fun localDeviceName(): String =
    System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: runCatching { File("/etc/hostname").readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: "Computer"
