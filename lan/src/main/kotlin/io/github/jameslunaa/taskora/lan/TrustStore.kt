package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Another device this one has paired with, known by its key [fingerprint]. */
@Serializable
data class PairedDevice(
    val deviceId: String,
    val name: String,
    val fingerprint: String,
    /** `android`, `linux` or `windows`. */
    val os: String,
)

/** The paired devices. Implementations must be safe to call from any thread. */
interface TrustStore {
    val devices: StateFlow<List<PairedDevice>>

    fun find(fingerprint: String): PairedDevice? = devices.value.firstOrNull { it.fingerprint == fingerprint }

    /** Adds [device], replacing an entry with the same device ID or fingerprint. */
    fun add(device: PairedDevice)

    fun remove(fingerprint: String)

    /** Keeps the stored name up to date when a device is renamed. */
    fun rename(fingerprint: String, name: String)
}

/** Kept in memory; for tests. */
open class MemoryTrustStore(initial: List<PairedDevice> = emptyList()) : TrustStore {
    protected val state = MutableStateFlow(initial)
    override val devices: StateFlow<List<PairedDevice>> = state.asStateFlow()

    override fun add(device: PairedDevice) = state.update { list ->
        list.filterNot { it.deviceId == device.deviceId || it.fingerprint == device.fingerprint } + device
    }

    override fun remove(fingerprint: String) = state.update { list -> list.filterNot { it.fingerprint == fingerprint } }

    override fun rename(fingerprint: String, name: String) = state.update { list ->
        list.map { if (it.fingerprint == fingerprint && it.name != name) it.copy(name = name) else it }
    }
}

/** Kept in a JSON file, written whenever it changes. */
class FileTrustStore(private val file: File) : MemoryTrustStore(load(file)) {
    override fun add(device: PairedDevice) = synchronized(this) { super.add(device); save() }
    override fun remove(fingerprint: String) = synchronized(this) { super.remove(fingerprint); save() }

    override fun rename(fingerprint: String, name: String) = synchronized(this) {
        val before = state.value
        super.rename(fingerprint, name)
        if (state.value != before) save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(Wire.json.encodeToString(SERIALIZER, state.value))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private companion object {
        val SERIALIZER = ListSerializer(PairedDevice.serializer())

        fun load(file: File): List<PairedDevice> =
            runCatching { Wire.json.decodeFromString(SERIALIZER, file.readText()) }.getOrDefault(emptyList())
    }
}
