package io.github.jameslunaa.taskora.lan

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * This device on the network: its ID, its EC P-256 key and a self-signed certificate for it
 * (TLS needs one; nothing checks its contents). See docs/lan.md, "Device identity".
 */
class Identity(
    val deviceId: String,
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {
    /** SHA-256 of the public key (SubjectPublicKeyInfo), in hex: how other devices know this one. */
    val fingerprint: String = fingerprintOf(certificate.publicKey)

    /** The identity as a PKCS#12 file's bytes. It lives in app-private storage, so the password is fixed. */
    fun toBytes(): ByteArray {
        val store = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        store.setKeyEntry(deviceId, privateKey, PASSWORD, arrayOf<Certificate>(certificate))
        return ByteArrayOutputStream().also { store.store(it, PASSWORD) }.toByteArray()
    }

    /** A key store holding just this identity, for a TLS KeyManagerFactory. */
    internal fun keyStore(): KeyStore = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry(deviceId, privateKey, PASSWORD, arrayOf<Certificate>(certificate))
    }

    companion object {
        internal val PASSWORD = "taskora".toCharArray()

        fun create(random: SecureRandom = SecureRandom()): Identity {
            val deviceId = UUID.randomUUID().toString()
            val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }
                .generateKeyPair()
            val certificate = SelfSignedCertificate.create("Taskora $deviceId", keys.public, keys.private, random)
            return Identity(deviceId, keys.private, certificate)
        }

        fun fromBytes(bytes: ByteArray): Identity {
            val store = KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(bytes), PASSWORD) }
            val alias = store.aliases().toList().single()
            return Identity(
                deviceId = alias,
                privateKey = store.getKey(alias, PASSWORD) as PrivateKey,
                certificate = store.getCertificate(alias) as X509Certificate,
            )
        }

        /** Loads the identity from [file], or creates one and saves it there. */
        fun loadOrCreate(file: File): Identity {
            if (file.exists()) runCatching { return fromBytes(file.readBytes()) }
            val identity = create()
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeBytes(identity.toBytes())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            return identity
        }

        fun fingerprintOf(key: PublicKey): String = MessageDigest.getInstance("SHA-256").digest(key.encoded).toHex()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' }) { "Not lowercase hex" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * A minimal X.509 v3 certificate, self-signed with ECDSA-SHA256, without extensions. Written
 * with a small DER encoder so the apps don't need BouncyCastle for this one structure.
 */
internal object SelfSignedCertificate {
    private const val ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2"
    private const val COMMON_NAME = "2.5.4.3"

    fun create(commonName: String, publicKey: PublicKey, privateKey: PrivateKey, random: SecureRandom): X509Certificate {
        val name = Der.sequence(Der.set(Der.sequence(Der.oid(COMMON_NAME), Der.utf8(commonName))))
        val algorithm = Der.sequence(Der.oid(ECDSA_WITH_SHA256))
        val now = ZonedDateTime.now(ZoneOffset.UTC).withNano(0)
        val tbs = Der.sequence(
            Der.explicit(0, Der.integer(BigInteger.valueOf(2))),
            Der.integer(BigInteger(1, ByteArray(16).also(random::nextBytes))),
            algorithm,
            name,
            Der.sequence(Der.time(now.minusDays(1)), Der.time(now.plusYears(100))),
            name,
            publicKey.encoded,
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey, random)
            update(tbs)
            sign()
        }
        val der = Der.sequence(tbs, algorithm, Der.bitString(signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }
}

/** Just the DER encodings [SelfSignedCertificate] needs. */
internal object Der {
    fun sequence(vararg parts: ByteArray) = tagged(0x30, concat(parts))
    fun set(vararg parts: ByteArray) = tagged(0x31, concat(parts))
    fun integer(value: BigInteger) = tagged(0x02, value.toByteArray())
    fun utf8(value: String) = tagged(0x0C, value.toByteArray(Charsets.UTF_8))
    fun bitString(bytes: ByteArray) = tagged(0x03, byteArrayOf(0) + bytes)
    fun explicit(tag: Int, inner: ByteArray) = tagged(0xA0 or tag, inner)

    /** UTCTime before 2050, GeneralizedTime from then on (RFC 5280). */
    fun time(at: ZonedDateTime): ByteArray =
        if (at.year < 2050) {
            tagged(0x17, at.format(DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")).toByteArray(Charsets.US_ASCII))
        } else {
            tagged(0x18, at.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")).toByteArray(Charsets.US_ASCII))
        }

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val body = ByteArrayOutputStream()
        body.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = mutableListOf<Int>()
            var rest = arc
            do {
                groups.add((rest and 0x7F).toInt())
                rest = rest shr 7
            } while (rest > 0)
            groups.reversed().forEachIndexed { index, group ->
                body.write(if (index < groups.size - 1) group or 0x80 else group)
            }
        }
        return tagged(0x06, body.toByteArray())
    }

    private fun tagged(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val length = content.size
        when {
            length < 0x80 -> out.write(length)
            length <= 0xFF -> { out.write(0x81); out.write(length) }
            length <= 0xFFFF -> { out.write(0x82); out.write(length shr 8); out.write(length and 0xFF) }
            else -> { out.write(0x83); out.write(length shr 16); out.write((length shr 8) and 0xFF); out.write(length and 0xFF) }
        }
        out.write(content)
        return out.toByteArray()
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray =
        ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
}
