package com.junsebog.instapicker.feature.picking.data

import com.junsebog.instapicker.core.database.AppDatabase
import com.junsebog.instapicker.core.database.AuditLogEntity
import com.junsebog.instapicker.core.database.toDomain
import com.junsebog.instapicker.core.database.toEntity
import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for domain state. Writes are write-through: the ViewModel
 * runs the reducer's effects through here, and the UI observes the database. So
 * restoration after process death is simply re-reading the tables — never replaying
 * an in-memory cache.
 */
class PickingRepository(
    private val db: AppDatabase,
    private val productSource: ProductSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeItems(sessionId: String): Flow<List<PickItem>> =
        db.pickItemDao().observeSession(sessionId).map { rows -> rows.map { it.toDomain() } }

    fun observeLog(sessionId: String): Flow<List<AuditLogEntity>> =
        db.auditLogDao().observe(sessionId)

    /**
     * Seeds the session from [ProductSource] only if it has no rows yet. On a relaunch
     * the rows already exist, so the picker's saved progress is preserved rather than
     * overwritten.
     */
    suspend fun ensureSeeded(sessionId: String): List<PickItem> {
        if (db.pickItemDao().count(sessionId) == 0) {
            val items = productSource.load()
            db.pickItemDao().upsertAll(items.map { it.toEntity(sessionId = sessionId, updatedAt = clock()) })
            return items
        }
        return db.pickItemDao().loadSession(sessionId).map { it.toDomain() }
    }

    suspend fun persist(sessionId: String, item: PickItem) =
        db.pickItemDao().upsert(item.toEntity(sessionId = sessionId, updatedAt = clock()))

    suspend fun append(entry: AuditEntry) =
        db.auditLogDao().append(entry.toEntity())
}
