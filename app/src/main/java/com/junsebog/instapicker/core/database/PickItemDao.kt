package com.junsebog.instapicker.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PickItemDao {

    /** Observed by the ViewModel — the UI is rebuilt whenever the table changes. */
    @Query("SELECT * FROM pick_items WHERE sessionId = :sessionId ORDER BY name")
    fun observeSession(sessionId: String): Flow<List<PickItemEntity>>

    @Query("SELECT * FROM pick_items WHERE sessionId = :sessionId ORDER BY name")
    suspend fun loadSession(sessionId: String): List<PickItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PickItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PickItemEntity>)

    @Query("SELECT COUNT(*) FROM pick_items WHERE sessionId = :sessionId")
    suspend fun count(sessionId: String): Int
}
