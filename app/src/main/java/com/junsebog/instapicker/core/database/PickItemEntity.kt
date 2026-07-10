package com.junsebog.instapicker.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a picking-session line.
 */
@Entity(tableName = "pick_items")
data class PickItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val ean13: String,
    val requestedQty: Int,
    val remainingToScan: Int,
    val state: String,
    val updatedAt: Long,
)
