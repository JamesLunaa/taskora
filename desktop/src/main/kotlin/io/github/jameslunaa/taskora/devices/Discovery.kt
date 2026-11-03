package io.github.jameslunaa.taskora.devices

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/** A Taskora device announced on the network (docs/lan.md, "Discovery"). */
data class DiscoveredDevice(
    val deviceId: String,
    val name: String,
    val os: String,
    /** The first 16 hex digits of its fingerprint: a hint, checked in full over TLS. */
    val fingerprintHint: String,
    val address: InetSocketAddress,
)

/**
 * Announces this device as `_taskora._tcp` and lists the others, through JmDNS on [bindAddress]
 * (the network interface the default route uses). Blocking: create and close it off the UI thread.
 */
class Discovery(
    bindAddress: InetAddress,
    private val ownDeviceId: String,
    name: String,
    port: Int,
    fingerprint: String,
    os: String,
) : Closeable {
    private val jmdns: JmDNS = JmDNS.create(bindAddress, "taskora-${ownDeviceId.take(8)}")

    private val _devices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())

    /** By device ID, without this device. */
    val devices: StateFlow<Map<String, DiscoveredDevice>> = _devices.asStateFlow()

    /** Service name → device ID, for removals (which only carry the name). */
    private val idsByName = mutableMapOf<String, String>()

    private val listener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            val id = synchronized(idsByName) { idsByName.remove(event.name) } ?: return
            _devices.update { it - id }
        }

        override fun serviceResolved(event: ServiceEvent) {
            val device = event.info.toDevice() ?: return
            if (device.deviceId == ownDeviceId) return
            synchronized(idsByName) { idsByName[event.name] = device.deviceId }
            _devices.update { it + (device.deviceId to device) }
        }
    }

    init {
        jmdns.registerService(
            ServiceInfo.create(
                SERVICE_TYPE,
                name,
                port,
                0,
                0,
                mapOf("v" to "1", "id" to ownDeviceId, "fp" to fingerprint.take(16), "os" to os),
            ),
        )
        jmdns.addServiceListener(SERVICE_TYPE, listener)
    }

    private fun ServiceInfo.toDevice(): DiscoveredDevice? {
        if (getPropertyString("v") != "1") return null
        val id = getPropertyString("id") ?: return null
        val host = inet4Addresses.firstOrNull() ?: inet6Addresses.firstOrNull() ?: return null
        return DiscoveredDevice(
            deviceId = id,
            name = name,
            os = getPropertyString("os") ?: "",
            fingerprintHint = getPropertyString("fp") ?: "",
            address = InetSocketAddress(host, port),
        )
    }

    override fun close() {
        runCatching {
            jmdns.removeServiceListener(SERVICE_TYPE, listener)
            jmdns.unregisterAllServices()
            jmdns.close()
        }
    }

    companion object {
        const val SERVICE_TYPE = "_taskora._tcp.local."
    }
}
