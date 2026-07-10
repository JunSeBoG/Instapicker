package com.junsebog.instapicker.core.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the scan cooldown: the first detection passes, repeats inside the window are
 * suppressed, and once the window elapses a detection passes again.
 */
class ScanThrottleTest {

    private var now = 0L
    private val throttle = ScanThrottle(windowMs = WINDOW_MS, clock = { now })

    @Test
    fun `allows the first detection`() {
        now = 5_000L
        assertTrue(throttle.allow())
    }

    @Test
    fun `suppresses a detection inside the window`() {
        now = 5_000L
        throttle.allow()
        now = 5_500L // 500ms later, still within the 1000ms window
        assertFalse(throttle.allow())
    }

    @Test
    fun `allows again once the window has elapsed`() {
        now = 5_000L
        throttle.allow()
        now = 6_000L // exactly one window later; boundary is inclusive
        assertTrue(throttle.allow())
    }

    private companion object {
        const val WINDOW_MS = 1_000L
    }
}
