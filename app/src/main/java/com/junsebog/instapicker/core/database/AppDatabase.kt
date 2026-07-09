package com.junsebog.instapicker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Room database — the single source of truth for domain state (`pick_items`)
 * and the append-only audit trail (`audit_log`).
 *
 * `exportSchema = true` writes the schema JSON to the configured location so DB
 * versions and migrations are tracked in git.
 */
@Database(
    entities = [PickItemEntity::class, AuditLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pickItemDao(): PickItemDao
    abstract fun auditLogDao(): AuditLogDao
}
