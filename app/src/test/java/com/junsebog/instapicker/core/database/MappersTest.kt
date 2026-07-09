package com.junsebog.instapicker.core.database

import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec for the entity <-> domain mappers. Pure functions, so they define exactly how
 * the domain is stored and restored — written before the implementation.
 */
class MappersTest {

    @Test
    fun `pick item survives a round trip through the entity`() {
        val item = PickItem(
            id = ITEM_ID,
            name = NAME,
            ean13 = EAN,
            requestedQty = QTY,
            remainingToScan = REMAINING,
            state = PickState.PENDING,
        )

        val restored = item.toEntity(sessionId = SESSION, updatedAt = UPDATED_AT).toDomain()

        assertEquals(item, restored)
    }

    @Test
    fun `pick item entity carries session metadata and the state as text`() {
        val item = PickItem(
            id = ITEM_ID,
            name = NAME,
            ean13 = EAN,
            requestedQty = 1,
            remainingToScan = 0,
            state = PickState.ADDED,
        )

        val entity = item.toEntity(sessionId = SESSION, updatedAt = UPDATED_AT)

        assertEquals(SESSION, entity.sessionId)
        assertEquals(UPDATED_AT, entity.updatedAt)
        assertEquals("ADDED", entity.state)
    }

    @Test
    fun `audit entry maps to an insertable row with enums as text`() {
        val entry = AuditEntry(
            sessionId = SESSION,
            timestamp = TIMESTAMP,
            action = AuditEntry.Action.SCAN_OK,
            itemId = ITEM_ID,
            fromState = PickState.PENDING,
            toState = PickState.ADDED,
            outcome = AuditEntry.ValidationOutcome.PASS,
            detail = "x",
        )

        val row = entry.toEntity()

        assertEquals("SCAN_OK", row.action)
        assertEquals("PENDING", row.fromState)
        assertEquals("ADDED", row.toState)
        assertEquals("PASS", row.outcome)
        assertEquals(ITEM_ID, row.itemId)
    }

    @Test
    fun `audit entry with no item or states maps nulls through`() {
        val entry = AuditEntry(
            sessionId = SESSION,
            timestamp = TIMESTAMP,
            action = AuditEntry.Action.LOAD,
            itemId = null,
            fromState = null,
            toState = null,
            outcome = AuditEntry.ValidationOutcome.NA,
            detail = "",
        )

        val row = entry.toEntity()

        assertNull(row.itemId)
        assertNull(row.fromState)
        assertNull(row.toState)
        assertEquals("NA", row.outcome)
    }

    private companion object {
        const val ITEM_ID = "p1"
        const val NAME = "Milk"
        const val EAN = "4006381333931"
        const val SESSION = "s1"
        const val QTY = 3
        const val REMAINING = 2
        const val UPDATED_AT = 42L
        const val TIMESTAMP = 5L
    }
}
