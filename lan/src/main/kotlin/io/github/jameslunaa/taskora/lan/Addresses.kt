package io.github.jameslunaa.taskora.lan

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URI

object LocalAddress {
    /**
     * This device's IP address on the network a phone is most likely on: the one the default
     * route uses (finding it sends nothing), else the first private IPv4 address. Null offline.
     */
    fun best(): String? {
        runCatching {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("192.0.2.1"), 9)
                val address = socket.localAddress
                if (!address.isAnyLocalAddress && !address.isLoopbackAddress) return address.hostAddress
            }
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}

/** A scanned `taskora://pair?...` link (docs/lan.md, "By scanning a QR code"). */
data class PairingLink(
    val deviceId: String,
    val fingerprint: String,
    val address: InetSocketAddress,
    val token: String,
) {
    companion object {
        /** Null for anything that isn't a valid version-1 pairing link. */
        fun parse(text: String): PairingLink? = runCatching {
            val uri = URI(text.trim())
            if (uri.scheme != "taskora" || uri.host != "pair") return null
            val query = uri.rawQuery.split('&').associate {
                val (key, value) = it.split('=', limit = 2)
                // The String overload: the Charset one needs Android 13.
                key to java.net.URLDecoder.decode(value, "UTF-8")
            }
            if (query["v"] != PROTOCOL_VERSION.toString()) return null
            val fingerprint = query.getValue("fp").takeIf { fp -> fp.length == 64 && fp.all { it in '0'..'9' || it in 'a'..'f' } }
                ?: return null
            val token = query.getValue("token").takeIf { t -> t.length == 32 && t.all { it in '0'..'9' || it in 'a'..'f' } }
                ?: return null
            val port = query.getValue("port").toInt().takeIf { it in 1..65535 } ?: return null
            PairingLink(query.getValue("id"), fingerprint, InetSocketAddress(query.getValue("host"), port), token)
        }.getOrNull()
    }
}
