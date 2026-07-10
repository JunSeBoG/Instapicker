package com.junsebog.instapicker.feature.picking.data

import com.junsebog.instapicker.core.database.AppDatabase
import com.junsebog.instapicker.core.database.toDomain
import com.junsebog.instapicker.core.database.toEntity
import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [PickingRepository]. Writes are write-through: the ViewModel runs the
 * reducer's effects through here, and the UI observes the database. So restoration
 * after process death is simply re-reading the tables — never replaying a cache.
 */
class RoomPickingRepository(
    private val db: AppDatabase,
    private val productSource: ProductSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : PickingRepository {

    override fun observeItems(sessionId: String): Flow<List<PickItem>> =
        db.pickItemDao().observeSession(sessionId).map { rows -> rows.map { it.toDomain() } }

    override fun observeLog(sessionId: String): Flow<List<AuditEntry>> =
        db.auditLogDao().observe(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun ensureSeeded(sessionId: String): List<PickItem> {
        if (db.pickItemDao().count(sessionId) == 0) {
            val items = productSource.load()
            db.pickItemDao().upsertAll(items.map { it.toEntity(sessionId = sessionId, updatedAt = clock()) })
            return items
        }
        return db.pickItemDao().loadSession(sessionId).map { it.toDomain() }
    }

    override suspend fun persist(sessionId: String, item: PickItem) =
        db.pickItemDao().upsert(item.toEntity(sessionId = sessionId, updatedAt = clock()))

    override suspend fun append(entry: AuditEntry) =
        db.auditLogDao().append(entry.toEntity())
}
