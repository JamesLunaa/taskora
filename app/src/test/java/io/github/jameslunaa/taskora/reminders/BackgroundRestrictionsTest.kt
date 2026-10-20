package io.github.jameslunaa.taskora.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRestrictionsTest {
    @Test
    fun aggressiveBrands_matchManufacturerOrBrand_caseInsensitive() {
        assertTrue(BackgroundRestrictions.isAggressiveBrand("vivo", "vivo"))
        assertTrue(BackgroundRestrictions.isAggressiveBrand("Xiaomi", "Redmi"))
        assertTrue(BackgroundRestrictions.isAggressiveBrand("unknown", "POCO"))
        assertTrue(BackgroundRestrictions.isAggressiveBrand("samsung", "samsung"))
        assertFalse(BackgroundRestrictions.isAggressiveBrand("Google", "google"))
        assertFalse(BackgroundRestrictions.isAggressiveBrand("motorola", "motorola"))
    }
}
