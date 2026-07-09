package com.junsebog.instapicker.feature.picking.data

import com.junsebog.instapicker.core.database.AuditLogEntity
import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for domain state and the append-only audit log. The
 * ViewModel depends on this abstraction — the Room-backed implementation lives in
 * [RoomPickingRepository], and tests can substitute an in-memory fake.
 */
interface PickingRepository {

    /** Observed by the ViewModel; the UI is rebuilt whenever the rows change. */
    fun observeItems(sessionId: String): Flow<List<PickItem>>

    fun observeLog(sessionId: String): Flow<List<AuditLogEntity>>

    /** Seeds the session only if it has no rows yet, preserving saved progress. */
    suspend fun ensureSeeded(sessionId: String): List<PickItem>

    /** Write-through persist of one item's new state. */
    suspend fun persist(sessionId: String, item: PickItem)

    /** Appends one record to the audit log. */
    suspend fun append(entry: AuditEntry)
}
