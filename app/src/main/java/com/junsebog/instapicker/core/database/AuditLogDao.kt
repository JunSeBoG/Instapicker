package com.junsebog.instapicker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Insert-only by design. The append-only audit trail exposes exactly two things:
 * append a record, and observe the records for a session in chronological order.
 * There is no update and no delete — the log can only grow.
 */
@Dao
interface AuditLogDao {

    @Insert
    suspend fun append(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE sessionId = :sessionId ORDER BY seq ASC")
    fun observe(sessionId: String): Flow<List<AuditLogEntity>>
}
