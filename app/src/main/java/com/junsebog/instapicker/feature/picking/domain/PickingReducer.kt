package com.junsebog.instapicker.feature.picking.domain

import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.AuditEntry.Action
import com.junsebog.instapicker.core.model.AuditEntry.ValidationOutcome
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState

/**
 * The heart of the app: a pure function `(state, intent) -> Reduction(state, effects)`.
 *
 * Time is injected via [now] so the reducer stays deterministic and unit-testable.
 * Side effects (save, log, camera) are returned as [PickingEffect] data and executed
 * by the ViewModel — never here.
 */
class PickingReducer(private val now: () -> Long) {

    fun reduce(state: PickingUiState, intent: PickingIntent): Reduction = when (intent) {
        is PickingIntent.LoadSession -> onLoad(state = state, intent = intent)
        is PickingIntent.MoveToPending -> move(state = state, itemId = intent.itemId, to = PickState.PENDING)
        is PickingIntent.MoveToRemoved -> move(state = state, itemId = intent.itemId, to = PickState.REMOVED)
        is PickingIntent.StartScan -> onStartScan(state = state, itemId = intent.itemId)
        is PickingIntent.BarcodeDetected -> onBarcode(state = state, raw = intent.raw)
        PickingIntent.CancelScan -> Reduction(
            state = state.copy(scanner = ScannerState.Idle),
            effects = listOf(PickingEffect.CloseCamera),
        )
        is PickingIntent.Rollback -> onRollback(state = state, itemId = intent.itemId, to = intent.to)
        is PickingIntent.ChangeTab -> Reduction(state = state.copy(activeTab = intent.tab))
        PickingIntent.DismissMessage -> Reduction(state = state.copy(message = null))
        PickingIntent.ToggleLog -> Reduction(state = state.copy(logOpen = !state.logOpen))
    }

    private fun onLoad(state: PickingUiState, intent: PickingIntent.LoadSession): Reduction {
        val next = state.copy(
            sessionId = intent.sessionId,
            items = intent.items,
            activeTab = PickTab.PENDING,
            scanner = ScannerState.Idle,
            message = null,
        )
        val entry = log(sessionId = intent.sessionId, action = Action.LOAD)
        return Reduction(state = next, effects = listOf(PickingEffect.AppendLog(entry)))
    }

    /** Free movement — only between PENDING and REMOVED, never out of ADDED. */
    private fun move(state: PickingUiState, itemId: String, to: PickState): Reduction {
        val item = state.item(itemId) ?: return Reduction(state = state)
        if (item.state == PickState.ADDED) {
            return Reduction(state = state.copy(message = warn("Use rollback to move an added item.")))
        }
        if (item.state == to) return Reduction(state = state)

        val updated = item.copy(state = to, remainingToScan = item.requestedQty)
        val entry = log(
            sessionId = state.sessionId,
            action = Action.MOVE,
            item = updated,
            from = item.state,
            to = to,
        )
        return Reduction(
            state = state.withItem(updated),
            effects = listOf(PickingEffect.SaveItem(updated), PickingEffect.AppendLog(entry)),
        )
    }

    /** A scan can only be started on a PENDING item. */
    private fun onStartScan(state: PickingUiState, itemId: String): Reduction {
        val item = state.item(itemId) ?: return Reduction(state = state)
        if (item.state != PickState.PENDING) return Reduction(state = state)
        return Reduction(
            state = state.copy(scanner = ScannerState.Active(itemId)),
            effects = listOf(PickingEffect.OpenCamera(itemId)),
        )
    }

    /** The guarded transition — only reachable while the scanner is Active. */
    private fun onBarcode(state: PickingUiState, raw: String): Reduction {
        val active = state.scanner as? ScannerState.Active ?: return Reduction(state = state)
        val item = state.item(active.itemId) ?: return Reduction(state = state)

        return when {
            !Ean13Validator.isValid(raw) -> scanFailure(
                state = state,
                item = item,
                action = Action.SCAN_INVALID,
                outcome = ValidationOutcome.FAIL_FORMAT,
                raw = raw,
            )
            raw != item.ean13 -> scanFailure(
                state = state,
                item = item,
                action = Action.SCAN_MISMATCH,
                outcome = ValidationOutcome.FAIL_MISMATCH,
                raw = raw,
            )
            else -> onMatch(state = state, item = item)
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
        val entry = log(
            sessionId = state.sessionId,
            action = action,
            item = item,
            from = item.state,
            to = item.state,
            outcome = outcome,
            detail = "raw=$raw",
        )
        return Reduction(
            state = state.copy(message = warn(text)),
            effects = listOf(PickingEffect.AppendLog(entry)),
        )
    }

    private fun onMatch(state: PickingUiState, item: PickItem): Reduction {
        val remaining = item.remainingToScan - 1
        return if (remaining <= 0) {
            promoteToAdded(state = state, item = item)
        } else {
            decrementBalance(state = state, item = item, remaining = remaining)
        }
    }

    private fun promoteToAdded(state: PickingUiState, item: PickItem): Reduction {
        val added = item.copy(state = PickState.ADDED, remainingToScan = 0)
        val entry = log(
            sessionId = state.sessionId,
            action = Action.SCAN_OK,
            item = added,
            from = item.state,
            to = PickState.ADDED,
            outcome = ValidationOutcome.PASS,
        )
        return Reduction(
            state = state.withItem(added).copy(
                scanner = ScannerState.Idle,
                message = success("${added.name} added."),
            ),
            effects = listOf(
                PickingEffect.SaveItem(added),
                PickingEffect.AppendLog(entry),
                PickingEffect.CloseCamera,
            ),
        )
    }

    private fun decrementBalance(state: PickingUiState, item: PickItem, remaining: Int): Reduction {
        val updated = item.copy(remainingToScan = remaining)
        val entry = log(
            sessionId = state.sessionId,
            action = Action.SCAN_OK,
            item = updated,
            from = item.state,
            to = item.state,
            outcome = ValidationOutcome.PASS,
        )
        val scanned = item.requestedQty - remaining
        return Reduction(
            state = state.withItem(updated).copy(
                message = success("Scanned $scanned of ${item.requestedQty}."),
            ),
            effects = listOf(PickingEffect.SaveItem(updated), PickingEffect.AppendLog(entry)),
        )
    }

    private fun onRollback(state: PickingUiState, itemId: String, to: PickState): Reduction {
        val item = state.item(itemId) ?: return Reduction(state = state)
        if (item.state != PickState.ADDED) return Reduction(state = state)

        val updated = item.copy(state = to, remainingToScan = item.requestedQty)
        val entry = log(
            sessionId = state.sessionId,
            action = Action.ROLLBACK,
            item = updated,
            from = PickState.ADDED,
            to = to,
        )
        return Reduction(
            state = state.withItem(updated),
            effects = listOf(PickingEffect.SaveItem(updated), PickingEffect.AppendLog(entry)),
        )
    }

    // --- helpers ----------------------------------------------------------------

    private fun PickingUiState.withItem(item: PickItem): PickingUiState =
        copy(items = items.map { if (it.id == item.id) item else it })

    private fun warn(text: String) = UiMessage(id = now(), text = text, kind = UiMessage.Kind.WARNING)

    private fun success(text: String) = UiMessage(id = now(), text = text, kind = UiMessage.Kind.SUCCESS)

    @Suppress("LongParameterList")
    private fun log(
        sessionId: String,
        action: Action,
        item: PickItem? = null,
        from: PickState? = null,
        to: PickState? = null,
        outcome: ValidationOutcome = ValidationOutcome.NA,
        detail: String = "",
    ) = AuditEntry(
        sessionId = sessionId,
        timestamp = now(),
        action = action,
        itemId = item?.id,
        fromState = from,
        toState = to,
        outcome = outcome,
        detail = detail,
    )
}
