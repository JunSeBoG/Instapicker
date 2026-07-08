package com.junsebog.instapicker.core.model

/**
 * A single line in the picking session — one product the picker has to handle.
 *
 * @property id stable identifier for the row (also used as a stable key in the list).
 * @property name human-readable product name shown to the picker.
 * @property ean13 the expected barcode; a scan must match this exactly to add the item.
 * @property requestedQty how many identical units the order asks for (a "stack").
 * @property remainingToScan how many valid, matching scans are still required before
 *   the row can move to [PickState.ADDED]. Starts equal to [requestedQty]; when it
 *   reaches 0 the row is promoted to ADDED.
 * @property state the workflow state the row is currently in.
 */
data class PickItem(
    val id: String,
    val name: String,
    val ean13: String,
    val requestedQty: Int,
    val remainingToScan: Int,
    val state: PickState,
) {
    init {
        // Defensive invariants: an item can never be in an impossible shape.
        require(requestedQty >= 1) { "requestedQty must be >= 1, was $requestedQty" }
        require(remainingToScan in 0..requestedQty) {
            "remainingToScan must be within 0..$requestedQty, was $remainingToScan"
        }
    }
}
