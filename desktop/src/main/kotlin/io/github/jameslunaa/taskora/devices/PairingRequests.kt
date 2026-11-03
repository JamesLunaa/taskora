package io.github.jameslunaa.taskora.devices

import io.github.jameslunaa.taskora.lan.PairingPrompts
import io.github.jameslunaa.taskora.lan.PeerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A pairing question for the user; the dialog answers it with [PairingRequests.answer]. */
sealed interface PairingPrompt {
    val peer: PeerInfo
    val answer: CompletableDeferred<Boolean>

    /** Both devices show [code]; "Pair" if it matches the other screen. */
    data class Code(override val peer: PeerInfo, val code: String) : PairingPrompt {
        override val answer = CompletableDeferred<Boolean>()
    }

    /** A phone scanned this computer's QR code: "Pair with <peer>?". */
    data class Qr(override val peer: PeerInfo) : PairingPrompt {
        override val answer = CompletableDeferred<Boolean>()
    }
}

/**
 * Pairing questions, for both sides of pairing: the server asks through [PairingPrompts], and
 * pairing started here asks through [confirmCode] too. The question disappears when it's
 * answered, or when the other side gives up (the waiting call is cancelled).
 */
class PairingRequests : PairingPrompts {
    private val _current = MutableStateFlow<PairingPrompt?>(null)
    val current: StateFlow<PairingPrompt?> = _current.asStateFlow()

    fun answer(prompt: PairingPrompt, accept: Boolean) {
        prompt.answer.complete(accept)
    }

    override suspend fun confirmCode(peer: PeerInfo, code: String) = ask(PairingPrompt.Code(peer, code))

    override suspend fun confirmQr(peer: PeerInfo) = ask(PairingPrompt.Qr(peer))

    private suspend fun ask(prompt: PairingPrompt): Boolean {
        _current.value = prompt
        return try {
            prompt.answer.await()
        } finally {
            _current.compareAndSet(prompt, null)
        }
    }
}
