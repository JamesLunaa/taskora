package io.github.jameslunaa.taskora.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.taskora.AppContainer
import io.github.jameslunaa.taskora.devices.Devices
import io.github.jameslunaa.taskora.devices.PairResult
import io.github.jameslunaa.taskora.devices.toWire
import io.github.jameslunaa.taskora.domain.model.Task
import io.github.jameslunaa.taskora.lan.PairedDevice
import io.github.jameslunaa.taskora.lan.ServerEvent
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.copied_here
import io.github.jameslunaa.taskora.resources.device_action_failed
import io.github.jameslunaa.taskora.resources.pair_busy
import io.github.jameslunaa.taskora.resources.pair_declined
import io.github.jameslunaa.taskora.resources.pair_unavailable
import io.github.jameslunaa.taskora.resources.paired_with
import io.github.jameslunaa.taskora.resources.send_may_have_failed
import io.github.jameslunaa.taskora.resources.sent_to_device
import io.github.jameslunaa.taskora.resources.unpaired_by
import io.github.jameslunaa.taskora.ui.common.scopedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** The browser pane for [device], with its ViewModel scoped to that device. */
@Composable
fun DeviceBrowserHost(
    devices: Devices,
    device: PairedDevice,
    container: AppContainer,
    onMessage: suspend (String) -> Unit,
    onUnpaired: () -> Unit,
) {
    val viewModel = scopedViewModel("device-${device.fingerprint}") {
        DeviceBrowserViewModel(devices.remote(device), container.taskRepository, container.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DeviceBrowserEvent.CopiedHere -> onMessage(getString(Res.string.copied_here, event.title))
                DeviceBrowserEvent.Unreachable -> onMessage(getString(Res.string.device_action_failed))
            }
        }
    }
    DeviceBrowserPane(
        device,
        state,
        remember(viewModel) {
            DeviceBrowserActions(
                onSelectList = viewModel::selectList,
                onCheckedChange = viewModel::setCompleted,
                onCopyHere = viewModel::copyHere,
                onRetry = viewModel::retry,
                onUnpair = {
                    scope.launch {
                        devices.unpair(device)
                        onUnpaired()
                    }
                },
            )
        },
    )
}

/**
 * The dialogs and messages LAN devices need anywhere in the window: pairing confirmations
 * (from either side), "Add device", and devices that unpaired this one.
 */
@Composable
fun DevicesOverlay(
    devices: Devices,
    addingDevice: Boolean,
    onCloseAddDevice: () -> Unit,
    onPaired: (PairedDevice) -> Unit,
    onMessage: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val prompt by devices.prompts.current.collectAsState()
    prompt?.let { current -> PairingDialog(current) { devices.prompts.answer(current, it) } }

    LaunchedEffect(devices) {
        devices.serverEvents.collect { event ->
            if (event is ServerEvent.Unpaired) onMessage(getString(Res.string.unpaired_by, event.device.name))
        }
    }

    if (!addingDevice) return
    val paired by devices.paired.collectAsState()
    val discovered by devices.discovered.collectAsState()
    val qrInvite by devices.qrInvite.collectAsState()
    var qrAvailable by remember { mutableStateOf(true) }
    var connectingTo by remember { mutableStateOf<String?>(null) }
    // Pairings started here report themselves; the watcher below is for QR pairing.
    val startedHere = remember { mutableSetOf<String>() }
    val pairedIds = paired.map { it.deviceId }.toSet()
    AddDeviceDialog(
        thisName = devices.name,
        found = discovered.values.filter { it.deviceId !in pairedIds }.sortedBy { it.name.lowercase() },
        connectingTo = connectingTo,
        qrInvite = qrInvite,
        qrAvailable = qrAvailable,
        onPair = { target ->
            startedHere += target.deviceId
            connectingTo = target.deviceId
            scope.launch {
                val result = devices.pair(target)
                connectingTo = null
                when (result) {
                    PairResult.PAIRED -> {
                        onMessage(getString(Res.string.paired_with, target.name))
                        devices.paired.value.firstOrNull { it.deviceId == target.deviceId }?.let(onPaired)
                        onCloseAddDevice()
                    }
                    PairResult.DECLINED -> onMessage(getString(Res.string.pair_declined))
                    PairResult.BUSY -> onMessage(getString(Res.string.pair_busy, target.name))
                    PairResult.UNAVAILABLE -> onMessage(getString(Res.string.pair_unavailable, target.name))
                }
            }
        },
        onQrVisible = { visible ->
            if (visible) qrAvailable = devices.startQrPairing() else devices.stopQrPairing()
        },
        onDismiss = onCloseAddDevice,
    )
    // A phone that paired by QR: open it.
    LaunchedEffect(Unit) {
        val before = devices.paired.value.map { it.fingerprint }.toSet()
        devices.paired.collect { now ->
            now.firstOrNull { it.fingerprint !in before && it.deviceId !in startedHere }?.let { added ->
                onMessage(getString(Res.string.paired_with, added.name))
                onPaired(added)
                onCloseAddDevice()
            }
        }
    }
}

/** Sends copies of [tasks] to [device], then says whether they arrived. */
fun sendTasks(scope: CoroutineScope, devices: Devices, tasks: List<Task>, device: PairedDevice, onMessage: suspend (String) -> Unit) {
    scope.launch {
        val remote = devices.remote(device)
        val sent = runCatching { tasks.forEach { remote.addTask(listId = null, task = it.toWire()) } }.isSuccess
        onMessage(getString(if (sent) Res.string.sent_to_device else Res.string.send_may_have_failed, device.name))
    }
}
