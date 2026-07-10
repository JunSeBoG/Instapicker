package com.junsebog.instapicker.feature.picking.domain

import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem

/**
 * Side effects the reducer REQUESTS as plain data. The reducer never performs them;
 * the ViewModel executes them. This is what keeps the reducer pure and testable.
 */
sealed interface PickingEffect {
    data class SaveItem(val item: PickItem) : PickingEffect
    data class AppendLog(val entry: AuditEntry) : PickingEffect
    data class OpenCamera(val itemId: String) : PickingEffect
    data object CloseCamera : PickingEffect
}

/** The reducer's output: the next state plus the effects the ViewModel must run. */
data class Reduction(
    val state: PickingUiState,
    val effects: List<PickingEffect> = emptyList(),
)
