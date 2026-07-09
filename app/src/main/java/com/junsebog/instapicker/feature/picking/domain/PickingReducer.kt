package com.junsebog.instapicker.feature.picking.domain

import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.AuditEntry.Action
import com.junsebog.instapicker.core.model.AuditEntry.ValidationOutcome
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState

/**
 * The heart of the app: a pure function `(state, intent) -> Reduction(state, effects)`.
 *
 * Time is injected via [now] so the reducer stays
 * deterministic and unit-testable. Side effects (save, log, camera) are returned as
 * [PickingEffect] data and executed by the ViewModel — never here.
 */
class PickingReducer(private val now: () -> Long) {

    fun reduce(state: PickingUiState, intent: PickingIntent): Reduction = when (intent) {
        is PickingIntent.LoadSession -> onLoad(state, intent)
        is PickingIntent.MoveToPending -> move(state, intent.itemId, PickState.PENDING)
        is PickingIntent.MoveToRemoved -> move(state, intent.itemId, PickState.REMOVED)
        is PickingIntent.StartScan -> onStartScan(state, intent.itemId)
        is PickingIntent.BarcodeDetected -> onBarcode(state, intent.raw)
        PickingIntent.CancelScan -> Reduction(
            state.copy(scanner = ScannerState.Idle),
            listOf(PickingEffect.CloseCamera),
        )
        is PickingIntent.Rollback -> onRollback(state, intent.itemId, intent.to)
        is PickingIntent.ChangeTab -> Reduction(state.copy(activeTab = intent.tab))
        PickingIntent.DismissMessage -> Reduction(state.copy(message = null))
    }

    private fun onLoad(state: PickingUiState, intent: PickingIntent.LoadSession): Reduction {
        val next = state.copy(
            sessionId = intent.sessionId,
            items = intent.items,
            activeTab = PickTab.PENDING,
            scanner = ScannerState.Idle,
            message = null,
        )
        return Reduction(next, listOf(PickingEffect.AppendLog(log(intent.sessionId, Action.LOAD))))
    }

    /** Free movement — only between PENDING and REMOVED, never out of ADDED. */
    private fun move(state: PickingUiState, itemId: String, to: PickState): Reduction {
        val item = state.item(itemId) ?: return Reduction(state)
        if (item.state == PickState.ADDED) {
            return Reduction(state.copy(message = warn("Use rollback to move an added item.")))
        }
        if (item.state == to) return Reduction(state)

        val updated = item.copy(state = to, remainingToScan = item.requestedQty)
        return Reduction(
            state.withItem(updated),
            listOf(
                PickingEffect.SaveItem(updated),
                PickingEffect.AppendLog(log(state.sessionId, Action.MOVE, updated, item.state, to)),
            ),
        )
    }

    /** A scan can only be started on a PENDING item. */
    private fun onStartScan(state: PickingUiState, itemId: String): Reduction {
        val item = state.item(itemId) ?: return Reduction(state)
        if (item.state != PickState.PENDING) return Reduction(state)
        return Reduction(
            state.copy(scanner = ScannerState.Active(itemId)),
            listOf(PickingEffect.OpenCamera(itemId)),
        )
    }

    /** The guarded transition — only reachable while the scanner is Active. */
    private fun onBarcode(state: PickingUiState, raw: String): Reduction {
        val active = state.scanner as? ScannerState.Active ?: return Reduction(state)
        val item = state.item(active.itemId) ?: return Reduction(state)

        return when {
            !Ean13Validator.isValid(raw) ->
                scanFailure(state, item, Action.SCAN_INVALID, ValidationOutcome.FAIL_FORMAT, raw)
            raw != item.ean13 ->
                scanFailure(state, item, Action.SCAN_MISMATCH, ValidationOutcome.FAIL_MISMATCH, raw)
            else -> onMatch(state, item)
        }
    }

    private fun scanFailure(
        state: PickingUiState,
        item: PickItem,
        action: Action,
        outcome: ValidationOutcome,
        raw: String,
    ): Reduction {
        val text = if (outcome == ValidationOutcome.FAIL_FORMAT) {
            "Invalid barcode format."
        } else {
            "Scanned barcode is not the expected one."
        }
        val entry = log(state.sessionId, action, item, item.state, item.state, outcome, "raw=$raw")
        return Reduction(state.copy(message = warn(text)), listOf(PickingEffect.AppendLog(entry)))
    }

    private fun onMatch(state: PickingUiState, item: PickItem): Reduction {
        val remaining = item.remainingToScan - 1
        return if (remaining <= 0) {
            val added = item.copy(state = PickState.ADDED, remainingToScan = 0)
            Reduction(
                state.withItem(added).copy(
                    scanner = ScannerState.Idle,
                    message = success("${added.name} added."),
                ),
                listOf(
                    PickingEffect.SaveItem(added),
                    PickingEffect.AppendLog(
                        log(state.sessionId, Action.SCAN_OK, added, item.state, PickState.ADDED, ValidationOutcome.PASS),
                    ),
                    PickingEffect.CloseCamera,
                ),
            )
        } else {
            val updated = item.copy(remainingToScan = remaining)
            Reduction(
                state.withItem(updated).copy(
                    message = success("Scanned ${item.requestedQty - remaining} of ${item.requestedQty}."),
                ),
                listOf(
                    PickingEffect.SaveItem(updated),
                    PickingEffect.AppendLog(
                        log(state.sessionId, Action.SCAN_OK, updated, item.state, item.state, ValidationOutcome.PASS),
                    ),
                ),
            )
        }
    }

    private fun onRollback(state: PickingUiState, itemId: String, to: PickState): Reduction {
        val item = state.item(itemId) ?: return Reduction(state)
        if (item.state != PickState.ADDED) return Reduction(state)

        val updated = item.copy(state = to, remainingToScan = item.requestedQty)
        return Reduction(
            state.withItem(updated),
            listOf(
                PickingEffect.SaveItem(updated),
                PickingEffect.AppendLog(log(state.sessionId, Action.ROLLBACK, updated, PickState.ADDED, to)),
            ),
        )
    }

    // --- helpers ----------------------------------------------------------------

    private fun PickingUiState.withItem(item: PickItem): PickingUiState =
        copy(items = items.map { if (it.id == item.id) item else it })

    private fun warn(text: String) = UiMessage(now(), text, UiMessage.Kind.WARNING)

    private fun success(text: String) = UiMessage(now(), text, UiMessage.Kind.SUCCESS)

    @Suppress("LongParameterList")
    private fun log(
        sessionId: String,
        action: Action,
        item: PickItem? = null,
        from: PickState? = null,
        to: PickState? = null,
        outcome: ValidationOutcome = ValidationOutcome.NA,
        detail: String = "",
    ) = AuditEntry(sessionId, now(), action, item?.id, from, to, outcome, detail)
}
