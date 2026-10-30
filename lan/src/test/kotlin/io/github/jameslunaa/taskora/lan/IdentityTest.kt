package io.github.jameslunaa.taskora.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IdentityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun createdIdentity_hasAValidSelfSignedCertificate() {
        val identity = Identity.create()
        identity.certificate.checkValidity()
        identity.certificate.verify(identity.certificate.publicKey)
        assertEquals("CN=Taskora ${identity.deviceId}", identity.certificate.subjectX500Principal.name)
        assertTrue(identity.certificate.notAfter.toInstant().isAfter(java.time.Instant.now().plusSeconds(90L * 365 * 86400)))
        assertTrue(identity.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun roundTripsThroughBytes_andFile() {
        val identity = Identity.create()
        val copy = Identity.fromBytes(identity.toBytes())
        assertEquals(identity.deviceId, copy.deviceId)
        assertEquals(identity.fingerprint, copy.fingerprint)

        val file = File(tmp.root, "lan/identity.p12")
        val created = Identity.loadOrCreate(file)
        assertEquals(created.fingerprint, Identity.loadOrCreate(file).fingerprint)
        assertNotEquals(identity.fingerprint, created.fingerprint)
    }
}
