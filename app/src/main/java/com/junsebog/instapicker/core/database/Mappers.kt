package com.junsebog.instapicker.core.database

import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState

/**
 * Pure conversions between Room entities and domain models. Keeping the enum <-> text
 * mapping here (not in Room type converters) leaves the entities as plain storage
 * shapes and makes the conversion provable in JVM unit tests.
 */

fun PickItemEntity.toDomain(): PickItem = PickItem(
    id = id,
    name = name,
    ean13 = ean13,
    requestedQty = requestedQty,
    remainingToScan = remainingToScan,
    state = PickState.valueOf(state),
)

fun PickItem.toEntity(sessionId: String, updatedAt: Long): PickItemEntity = PickItemEntity(
    id = id,
    sessionId = sessionId,
    name = name,
    ean13 = ean13,
    requestedQty = requestedQty,
    remainingToScan = remainingToScan,
    state = state.name,
    updatedAt = updatedAt,
)

fun AuditEntry.toEntity(): AuditLogEntity = AuditLogEntity(
    sessionId = sessionId,
    timestamp = timestamp,
    action = action.name,
    itemId = itemId,
    fromState = fromState?.name,
    toState = toState?.name,
    outcome = outcome.name,
    detail = detail,
)

fun AuditLogEntity.toDomain(): AuditEntry = AuditEntry(
    sessionId = sessionId,
    timestamp = timestamp,
    action = AuditEntry.Action.valueOf(action),
    itemId = itemId,
    fromState = fromState?.let { PickState.valueOf(it) },
    toState = toState?.let { PickState.valueOf(it) },
    outcome = AuditEntry.ValidationOutcome.valueOf(outcome),
    detail = detail,
)
