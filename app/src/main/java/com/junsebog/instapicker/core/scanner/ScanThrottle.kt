package com.junsebog.instapicker.core.scanner

/**
 * A time-based gate: [allow] returns true at most once per [windowMs]. Extracted from
 * [BarcodeAnalyzer] so the scan cooldown is a pure, unit-testable rule with no ML Kit
 * or Android dependencies. Time is injected via [clock] to keep it deterministic.
 */
class ScanThrottle(
    private val windowMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var lastAllowedAt = 0L

    /**
     * True if at least [windowMs] has elapsed since the last allowed call — in which
     * case it records "now" as the new reference. False otherwise (still cooling down).
     */
    fun allow(): Boolean {
        val now = clock()
        return if (now - lastAllowedAt >= windowMs) {
            lastAllowedAt = now
            true
        } else {
            false
        }
    }
}
