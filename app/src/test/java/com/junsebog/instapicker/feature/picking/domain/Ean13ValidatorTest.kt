package com.junsebog.instapicker.feature.picking.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Ean13Validator].
 * The reference barcodes below are real, well-formed EAN-13 codes whose check digit
 * is correct.
 */
class Ean13ValidatorTest {

    @Test
    fun `accepts valid EAN-13 barcodes`() {
        assertTrue(Ean13Validator.isValid("4006381333931"))
        assertTrue(Ean13Validator.isValid("5901234123457"))
        assertTrue(Ean13Validator.isValid("0012345678905"))
    }

    @Test
    fun `rejects a wrong check digit`() {
        // Same as the first valid code but the last digit is off by one.
        assertFalse(Ean13Validator.isValid("4006381333932"))
    }

    @Test
    fun `rejects the wrong length`() {
        assertFalse(Ean13Validator.isValid("400638133393"))   // 12 digits
        assertFalse(Ean13Validator.isValid("40063813339311")) // 14 digits
    }

    @Test
    fun `rejects non-numeric input`() {
        assertFalse(Ean13Validator.isValid("40063813339A1"))
    }

    @Test
    fun `rejects empty input`() {
        assertFalse(Ean13Validator.isValid(""))
    }
}
