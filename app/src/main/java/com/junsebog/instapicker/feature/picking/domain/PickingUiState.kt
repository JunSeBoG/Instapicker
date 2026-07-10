package com.junsebog.instapicker.feature.picking.domain

import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState

/** The visible tab / active view. A UI concern, distinct from an item's [PickState]. */
enum class PickTab { PENDING, REMOVED, ADDED }

/** Whether the full-screen scanner is closed, or open for a specific item. */
sealed interface ScannerState {
    data object Idle : ScannerState
    data class Active(val itemId: String) : ScannerState
}

/** A soft, auto-dismissable message (e.g. a barcode mismatch). */
data class UiMessage(val id: Long, val text: String, val kind: Kind) {
    enum class Kind { INFO, WARNING, SUCCESS }
}

/**
 * The single immutable snapshot that drives the whole screen. The UI is a pure
 * function of this value; nothing else is a source of truth.
 */
data class PickingUiState(
    val sessionId: String,
    val items: List<PickItem> = emptyList(),
    val activeTab: PickTab = PickTab.PENDING,
    val scanner: ScannerState = ScannerState.Idle,
    val message: UiMessage? = null,
    val log: List<AuditEntry> = emptyList(),
    val logOpen: Boolean = false,
) {
    fun item(id: String): PickItem? = items.firstOrNull { it.id == id }

    fun itemsIn(state: PickState): List<PickItem> = items.filter { it.state == state }
}
