package io.github.jameslunaa.taskora.lan

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager

/** Length-prefixed frames: a 4-byte big-endian length, then that many bytes. */
internal object Frames {
    fun read(input: InputStream): ByteArray {
        val data = DataInputStream(input)
        val length = try {
            data.readInt()
        } catch (end: EOFException) {
            throw ConnectionClosed()
        }
        if (length < 0 || length > Limits.MAX_MESSAGE_BYTES) throw ProtocolException("Message of $length bytes")
        return ByteArray(length).also(data::readFully)
    }

    fun write(output: OutputStream, bytes: ByteArray) {
        if (bytes.size > Limits.MAX_MESSAGE_BYTES) throw ProtocolException("Message of ${bytes.size} bytes")
        val header = byteArrayOf(
            (bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte(),
        )
        output.write(header + bytes)
        output.flush()
    }
}

/** The other side broke the protocol; the connection is closed. */
class ProtocolException(message: String) : IOException(message)

/** The other side closed the connection. */
class ConnectionClosed : IOException("Connection closed")

/**
 * TLS for both directions, with this device's key. Every certificate is accepted during the
 * handshake: trust is decided right after it, by the peer's key fingerprint (docs/lan.md,
 * "Connection"), so nothing may be served before that check.
 */
internal class Tls(identity: Identity) {
    private val context: SSLContext = SSLContext.getInstance("TLS").apply {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(identity.keyStore(), Identity.PASSWORD) }
        init(keys.keyManagers, arrayOf(AnyCertificate), SecureRandom())
    }

    fun serverSocket(port: Int): SSLServerSocket =
        (context.serverSocketFactory.createServerSocket(port) as SSLServerSocket).apply {
            needClientAuth = true
            enabledProtocols = protocols(supportedProtocols)
        }

    /** Wraps a connected plain socket as the TLS client. */
    fun clientSocket(plain: Socket): SSLSocket =
        (context.socketFactory.createSocket(plain, plain.inetAddress.hostAddress, plain.port, true) as SSLSocket).apply {
            useClientMode = true
            enabledProtocols = protocols(supportedProtocols)
        }

    private fun protocols(supported: Array<String>) =
        arrayOf("TLSv1.3", "TLSv1.2").filter { it in supported }.toTypedArray()

    /** Accepts any certificate chain, as long as there is one. */
    private object AnyCertificate : X509ExtendedTrustManager() {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = requireChain(chain)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = requireChain(chain)
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = requireChain(chain)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = requireChain(chain)
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = requireChain(chain)
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = requireChain(chain)

        private fun requireChain(chain: Array<out X509Certificate>?) {
            if (chain.isNullOrEmpty()) throw java.security.cert.CertificateException("No certificate")
        }
    }

    companion object {
        /** The peer's key fingerprint, once the handshake is done. */
        fun peerFingerprint(socket: SSLSocket): String =
            Identity.fingerprintOf((socket.session.peerCertificates.first() as X509Certificate).publicKey)
    }
}

/** The maths of code pairing (docs/lan.md, "By comparing a code"). */
object PairingMath {
    private val random = SecureRandom()

    fun nonce(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun commitment(nonce: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(nonce)

    /** Six digits, with leading zeros. */
    fun code(initiatorFingerprint: String, responderFingerprint: String, initiatorNonce: ByteArray, responderNonce: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(initiatorFingerprint.hexToBytes())
            update(responderFingerprint.hexToBytes())
            update(initiatorNonce)
            update(responderNonce)
            digest()
        }
        val value = ((digest[0].toLong() and 0xFF) shl 24) or ((digest[1].toLong() and 0xFF) shl 16) or
            ((digest[2].toLong() and 0xFF) shl 8) or (digest[3].toLong() and 0xFF)
        return "%06d".format(value % 1_000_000)
    }

    /** A QR pairing token: 16 random bytes in hex. */
    fun token(): String = ByteArray(16).also(random::nextBytes).toHex()
}

/**
 * Failed or cancelled code pairings: after [maxFailures] within [window], new pairings are
 * refused for [cooldown] (docs/lan.md, "Limits and timeouts").
 */
internal class PairingRateLimit(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxFailures: Int = 3,
    private val window: Long = 10 * 60_000L,
    private val cooldown: Long = 5 * 60_000L,
) {
    private val failures = ArrayDeque<Long>()

    @Synchronized
    fun allowed(): Boolean {
        val time = now()
        while (failures.isNotEmpty() && time - failures.first() > window) failures.removeFirst()
        if (failures.size < maxFailures) return true
        return time - failures.last() >= cooldown
    }

    @Synchronized
    fun failed() {
        failures.addLast(now())
        while (failures.size > maxFailures) failures.removeFirst()
    }
}
