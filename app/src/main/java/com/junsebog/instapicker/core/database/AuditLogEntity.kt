package com.junsebog.instapicker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one append-only audit record.
 *
 * `seq` is a monotonic, auto-generated primary key that gives the log a stable
 * chronological order. Append-only is enforced by the DAO, which exposes insert
 * only — there is deliberately no `@Update` or `@Delete` anywhere.
 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val action: String,
    val itemId: String?,
    val fromState: String?,
    val toState: String?,
    val outcome: String,
    val detail: String,
)
