package io.github.jameslunaa.taskora.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingMathTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    @Test
    fun code_isSixDigits_andDependsOnEverything() {
        val na = PairingMath.nonce()
        val nb = PairingMath.nonce()
        val code = PairingMath.code(a, b, na, nb)
        assertTrue(code.matches(Regex("\\d{6}")))
        assertEquals(code, PairingMath.code(a, b, na, nb))
        assertNotEquals(code, PairingMath.code(b, a, na, nb))
        assertNotEquals(code, PairingMath.code(a, b, nb, na))
    }

    @Test
    fun rateLimit_blocksAfterThreeFailures_forFiveMinutes() {
        var time = 0L
        val limit = PairingRateLimit({ time })
        repeat(3) {
            assertTrue(limit.allowed())
            limit.failed()
            time += 60_000
        }
        assertFalse(limit.allowed())
        time = 120_000L + 4 * 60_000 // 4 minutes after the last failure: still blocked.
        assertFalse(limit.allowed())
        time = 120_000L + 5 * 60_000 // 5 minutes after it.
        assertTrue(limit.allowed())
    }

    @Test
    fun pairingLink_parsesOnlyValidLinks() {
        val fp = "0123456789abcdef".repeat(4)
        val token = "00112233445566778899aabbccddeeff"
        val link = PairingLink.parse("taskora://pair?v=1&id=dev-1&fp=$fp&host=192.168.1.20&port=47390&token=$token")!!
        assertEquals(fp, link.fingerprint)
        assertEquals(47390, link.address.port)
        assertEquals("192.168.1.20", link.address.hostString)
        assertNull(PairingLink.parse("taskora://pair?v=2&id=dev-1&fp=$fp&host=h&port=1&token=$token"))
        assertNull(PairingLink.parse("https://example.com/?fp=$fp"))
        assertNull(PairingLink.parse("taskora://pair?v=1&id=dev-1&fp=short&host=h&port=1&token=$token"))
    }
}
