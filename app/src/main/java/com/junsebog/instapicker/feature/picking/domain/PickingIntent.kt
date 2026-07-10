package com.junsebog.instapicker.feature.picking.domain

import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState

/**
 * The only way to request a change to the picking session. The UI emits these; the
 * reducer consumes them. One per user-meaningful action.
 */
sealed interface PickingIntent {
    data class LoadSession(val sessionId: String, val items: List<PickItem>) : PickingIntent
    data class MoveToPending(val itemId: String) : PickingIntent
    data class MoveToRemoved(val itemId: String) : PickingIntent
    data class StartScan(val itemId: String) : PickingIntent
    data class BarcodeDetected(val raw: String) : PickingIntent
    data object CancelScan : PickingIntent
    data class Rollback(val itemId: String, val to: PickState) : PickingIntent
    data class ChangeTab(val tab: PickTab) : PickingIntent
    data object DismissMessage : PickingIntent
    data object ToggleLog : PickingIntent
}
