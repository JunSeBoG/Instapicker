package com.junsebog.instapicker.feature.picking.domain

/**
 * Validates that a string is a well-formed EAN-13 barcode.
 *
 * Pure and stateless — no Android, no I/O — so every branch is provable in fast JVM
 * unit tests. This only checks that the code is a structurally valid EAN-13; matching
 * it against a specific product's identifier is a separate, higher-level decision.
 */
object Ean13Validator {

    private const val EAN13_LENGTH = 13
    private const val CHECK_DIGIT_BASE = 10
    private const val ODD_POSITION_MULTIPLIER = 3

    /**
     * @return true only if [code] is exactly 13 digits and its trailing check digit
     * satisfies the EAN-13 checksum.
     */
    fun isValid(code: String): Boolean {
        if (code.length != EAN13_LENGTH || code.any { !it.isDigit() }) return false

        val digits = code.map { it - '0' }

        // Checksum: over the first 12 digits, digits in even 0-based positions keep
        // their value and digits in odd positions are multiplied by 3; the check
        // digit is whatever makes the grand total a multiple of 10.
        val weightedSum = digits.take(EAN13_LENGTH - 1)
            .mapIndexed { index, digit ->
                if (index % 2 == 0) digit else digit * ODD_POSITION_MULTIPLIER
            }
            .sum()
        val expectedCheckDigit =
            (CHECK_DIGIT_BASE - weightedSum % CHECK_DIGIT_BASE) % CHECK_DIGIT_BASE

        return digits.last() == expectedCheckDigit
    }
}
