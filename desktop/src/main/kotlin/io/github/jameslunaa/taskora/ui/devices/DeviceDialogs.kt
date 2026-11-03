package io.github.jameslunaa.taskora.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jameslunaa.taskora.devices.DiscoveredDevice
import io.github.jameslunaa.taskora.devices.PairingPrompt
import io.github.jameslunaa.taskora.lan.DeviceState
import io.github.jameslunaa.taskora.lan.PairedDevice
import io.github.jameslunaa.taskora.lan.QrInvite
import io.github.jameslunaa.taskora.resources.Res
import io.github.jameslunaa.taskora.resources.add_device
import io.github.jameslunaa.taskora.resources.add_device_network
import io.github.jameslunaa.taskora.resources.add_device_none
import io.github.jameslunaa.taskora.resources.add_device_qr
import io.github.jameslunaa.taskora.resources.add_device_qr_hint
import io.github.jameslunaa.taskora.resources.add_device_qr_offline
import io.github.jameslunaa.taskora.resources.add_device_this
import io.github.jameslunaa.taskora.resources.cancel
import io.github.jameslunaa.taskora.resources.close
import io.github.jameslunaa.taskora.resources.device_connecting
import io.github.jameslunaa.taskora.resources.device_not_paired
import io.github.jameslunaa.taskora.resources.device_online
import io.github.jameslunaa.taskora.resources.device_unavailable
import io.github.jameslunaa.taskora.resources.devices
import io.github.jameslunaa.taskora.resources.pair
import io.github.jameslunaa.taskora.resources.pair_code_hint
import io.github.jameslunaa.taskora.resources.pair_qr_hint
import io.github.jameslunaa.taskora.resources.pair_with
import io.github.jameslunaa.taskora.resources.pairing_waiting
import io.github.jameslunaa.taskora.ui.icons.outlined.Computer
import io.github.jameslunaa.taskora.ui.icons.outlined.Smartphone
import io.nayuki.qrcodegen.QrCode
import org.jetbrains.compose.resources.stringResource
import java.awt.image.BufferedImage

/** A device's icon, by its `os`. */
fun deviceIcon(os: String): ImageVector = if (os == "android") Icons.Outlined.Smartphone else Icons.Outlined.Computer

@Composable
fun DeviceState.label(): String = stringResource(
    when (this) {
        DeviceState.ONLINE -> Res.string.device_online
        DeviceState.CONNECTING -> Res.string.device_connecting
        DeviceState.UNAVAILABLE -> Res.string.device_unavailable
        DeviceState.NOT_PAIRED -> Res.string.device_not_paired
    },
)

/**
 * The sidebar's Devices section: paired devices, each with a dot that is filled while the device
 * is announced on the network, and a button to add one.
 */
@Composable
fun DevicesSection(
    paired: List<PairedDevice>,
    onlineIds: Set<String>,
    selectedFingerprint: String?,
    onSelect: (PairedDevice) -> Unit,
    onAdd: () -> Unit,
) {
    val itemColors = NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
        Text(
            stringResource(Res.string.devices),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(Res.string.add_device))
        }
    }
    paired.forEach { device ->
        val online = device.deviceId in onlineIds
        NavigationDrawerItem(
            label = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { Icon(deviceIcon(device.os), contentDescription = null) },
            badge = {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            },
            selected = device.fingerprint == selectedFingerprint,
            onClick = { onSelect(device) },
            colors = itemColors,
        )
    }
}

/** Asks the user to confirm a pairing; shown whichever side started it. */
@Composable
fun PairingDialog(prompt: PairingPrompt, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = { Icon(deviceIcon(prompt.peer.os), contentDescription = null) },
        title = { Text(stringResource(Res.string.pair_with, prompt.peer.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                when (prompt) {
                    is PairingPrompt.Code -> {
                        Text(
                            prompt.code.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Text(stringResource(Res.string.pair_code_hint, prompt.peer.name), textAlign = TextAlign.Center)
                    }
                    is PairingPrompt.Qr -> Text(stringResource(Res.string.pair_qr_hint, prompt.peer.name), textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(Res.string.pair)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * Adding a device: Taskora devices found on the network (pairing by code), or a QR code for a
 * phone. QR pairing is on only while that tab shows.
 */
@Composable
fun AddDeviceDialog(
    thisName: String,
    found: List<DiscoveredDevice>,
    /** The device being contacted, while pairing starts. */
    connectingTo: String?,
    qrInvite: QrInvite?,
    qrAvailable: Boolean,
    onPair: (DiscoveredDevice) -> Unit,
    onQrVisible: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    DisposableEffect(tab) {
        onQrVisible(tab == 1)
        onDispose { onQrVisible(false) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(460.dp),
        title = { Text(stringResource(Res.string.add_device)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.add_device_this, thisName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryTabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(Res.string.add_device_network)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(Res.string.add_device_qr)) })
                }
                // Lists start at the top; waiting and the QR code sit in the middle.
                val centered = tab == 1 || found.isEmpty()
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 280.dp),
                    contentAlignment = if (centered) Alignment.Center else Alignment.TopCenter,
                ) {
                    if (tab == 0) {
                        NetworkDevices(found, connectingTo, onPair)
                    } else {
                        QrTab(qrInvite, qrAvailable)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}

@Composable
private fun NetworkDevices(found: List<DiscoveredDevice>, connectingTo: String?, onPair: (DiscoveredDevice) -> Unit) {
    if (found.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(32.dp))
            Text(stringResource(Res.string.add_device_none), textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
        items(found, key = { it.deviceId }) { device ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(deviceIcon(device.os), contentDescription = null) },
                headlineContent = { Text(device.name) },
                supportingContent = if (connectingTo == device.deviceId) {
                    { Text(stringResource(Res.string.pairing_waiting, device.name)) }
                } else {
                    null
                },
                trailingContent = {
                    TextButton(onClick = { onPair(device) }, enabled = connectingTo == null) {
                        Text(stringResource(Res.string.pair))
                    }
                },
            )
        }
    }
}

@Composable
private fun QrTab(invite: QrInvite?, available: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !available -> Text(stringResource(Res.string.add_device_qr_offline), textAlign = TextAlign.Center)
            invite == null || invite.token.isEmpty() -> CircularProgressIndicator(Modifier.size(32.dp))
            else -> {
                val image = remember(invite.uri) { qrImage(invite.uri) }
                // Dark modules on white in both themes, so every camera reads it.
                Image(
                    image,
                    contentDescription = null,
                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)),
                )
                Text(stringResource(Res.string.add_device_qr_hint), textAlign = TextAlign.Center)
            }
        }
    }
}

/** [text] as a QR code: black modules on white, with the standard 4-module quiet zone. */
private fun qrImage(text: String): ImageBitmap {
    val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
    val border = 4
    val scale = 8
    val size = (qr.size + border * 2) * scale
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val dark = qr.getModule(x / scale - border, y / scale - border)
            image.setRGB(x, y, if (dark) 0x000000 else 0xFFFFFF)
        }
    }
    return image.toComposeImageBitmap()
}
