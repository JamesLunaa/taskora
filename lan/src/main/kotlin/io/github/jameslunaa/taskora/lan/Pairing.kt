package io.github.jameslunaa.taskora.lan

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/** The device on the other end of a connection, as its `hello` describes it. */
data class PeerInfo(
    val deviceId: String,
    val name: String,
    val os: String,
    val fingerprint: String,
) {
    fun toPairedDevice() = PairedDevice(deviceId, name, fingerprint, os)
}

/** Where pairing asks the user. Each returns whether the user accepted. */
interface PairingPrompts {
    /**
     * Shows the six-digit [code] next to [peer]'s name and waits for "Pair" (true) or "Cancel"
     * (false). The call is cancelled when the other side gives up or pairing times out, so the
     * prompt should close then.
     */
    suspend fun confirmCode(peer: PeerInfo, code: String): Boolean

    /** QR pairing: asks "Pair with <peer>?" on the computer. Cancelled like [confirmCode]. */
    suspend fun confirmQr(peer: PeerInfo): Boolean
}

/** The next pairing step, or a cancel when the connection has gone. */
internal suspend fun Connection.nextPairingStep(): Message =
    pairing.receiveCatching().getOrNull() ?: PairCancel("Connection closed")

/**
 * Steps 6–7 of code pairing, the same on both sides: asks this user through [prompt] while
 * listening to the other side. True only once both users have confirmed; a cancel from either
 * side (or a lost connection) ends it with false, closing this side's prompt.
 */
internal suspend fun Connection.confirmBoth(prompt: suspend () -> Boolean): Boolean = coroutineScope {
    val local = async { prompt() }
    var remoteConfirmed = false
    while (!local.isCompleted) {
        val step = select<Message?> {
            local.onAwait { null }
            pairing.onReceiveCatching { it.getOrNull() ?: PairCancel("Connection closed") }
        }
        when (step) {
            null -> Unit
            PairConfirm -> remoteConfirmed = true
            is PairCancel -> {
                local.cancel()
                return@coroutineScope false
            }
            else -> Unit // Out of order; ignored.
        }
    }
    if (!local.await()) {
        runCatching { send(PairCancel("Declined")) }
        return@coroutineScope false
    }
    send(PairConfirm)
    while (!remoteConfirmed) {
        when (nextPairingStep()) {
            PairConfirm -> remoteConfirmed = true
            is PairCancel -> return@coroutineScope false
            else -> Unit
        }
    }
    true
}
