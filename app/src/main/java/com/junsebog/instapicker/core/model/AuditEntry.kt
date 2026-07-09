package com.junsebog.instapicker.core.model

/**
 * One append-only record in the operational audit log. Produced by the reducer as
 * plain data (an effect payload) and written, unchanged, by the logger.
 *
 * Every user action, state change, and validation outcome becomes one of these —
 * including failures, because the log tracks outcomes, not just successes.
 */
data class AuditEntry(
    val sessionId: String,
    val timestamp: Long,
    val action: Action,
    val itemId: String?,
    val fromState: PickState?,
    val toState: PickState?,
    val outcome: ValidationOutcome,
    val detail: String = "",
) {
    enum class Action { LOAD, MOVE, SCAN_OK, SCAN_MISMATCH, SCAN_INVALID, ROLLBACK }

    enum class ValidationOutcome { NA, PASS, FAIL_FORMAT, FAIL_MISMATCH }
}
