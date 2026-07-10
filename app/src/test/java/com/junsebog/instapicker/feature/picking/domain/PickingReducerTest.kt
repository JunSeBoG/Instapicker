package com.junsebog.instapicker.feature.picking.domain

import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec for [PickingReducer]
 * Each test pins one transition or guard of the state machine.
 */
class PickingReducerTest {

    private val reducer = PickingReducer(now = { FIXED_TIME })

    // --- moves ------------------------------------------------------------------

    @Test
    fun `move pending to removed updates state and requests save plus log`() {
        val result = reducer.reduce(stateWith(item()), PickingIntent.MoveToRemoved(ITEM_ID))

        assertEquals(PickState.REMOVED, result.state.item(ITEM_ID)!!.state)
        assertTrue(result.effects.any { it is PickingEffect.SaveItem })
        assertTrue(result.effects.any { it is PickingEffect.AppendLog })
    }

    @Test
    fun `an added item cannot be plain-moved`() {
        val start = stateWith(item(state = PickState.ADDED, remaining = 0))
        val result = reducer.reduce(start, PickingIntent.MoveToRemoved(ITEM_ID))

        assertEquals(PickState.ADDED, result.state.item(ITEM_ID)!!.state) // unchanged
        assertNotNull(result.state.message)                              // warned
    }

    // --- the scan lock ----------------------------------------------------------

    @Test
    fun `added is unreachable without an active scan`() {
        val result = reducer.reduce(stateWith(item()), PickingIntent.BarcodeDetected(VALID_EAN))
        assertEquals(PickState.PENDING, result.state.item(ITEM_ID)!!.state)
    }

    @Test
    fun `a removed item cannot start a scan`() {
        val start = stateWith(item(state = PickState.REMOVED))
        val result = reducer.reduce(start, PickingIntent.StartScan(ITEM_ID))

        assertEquals(ScannerState.Idle, result.state.scanner)
        assertTrue(result.effects.none { it is PickingEffect.OpenCamera })
    }

    @Test
    fun `start scan on a pending item opens the camera`() {
        val result = reducer.reduce(stateWith(item()), PickingIntent.StartScan(ITEM_ID))

        assertEquals(ScannerState.Active(ITEM_ID), result.state.scanner)
        assertTrue(result.effects.any { it is PickingEffect.OpenCamera })
    }

    @Test
    fun `a matching scan on a single-unit item promotes it and closes the camera`() {
        val start = stateWith(item(), scanner = ScannerState.Active(ITEM_ID))
        val result = reducer.reduce(start, PickingIntent.BarcodeDetected(VALID_EAN))

        assertEquals(PickState.ADDED, result.state.item(ITEM_ID)!!.state)
        assertEquals(ScannerState.Idle, result.state.scanner)
        assertTrue(result.effects.any { it is PickingEffect.CloseCamera })

        // The item that gets saved must be the promoted one, in its ADDED state.
        val saved = result.effects.filterIsInstance<PickingEffect.SaveItem>().single()
        assertEquals(PickState.ADDED, saved.item.state)
    }

    @Test
    fun `a mismatching scan never changes state and logs a failure`() {
        val start = stateWith(item(), scanner = ScannerState.Active(ITEM_ID))
        val result = reducer.reduce(start, PickingIntent.BarcodeDetected(OTHER_VALID_EAN))

        assertEquals(PickState.PENDING, result.state.item(ITEM_ID)!!.state)
        assertNotNull(result.state.message)
        assertTrue(result.effects.none { it is PickingEffect.SaveItem })
        assertTrue(result.effects.any { it is PickingEffect.AppendLog })
    }

    @Test
    fun `an invalid barcode never changes state`() {
        val start = stateWith(item(), scanner = ScannerState.Active(ITEM_ID))
        val result = reducer.reduce(start, PickingIntent.BarcodeDetected(INVALID_EAN))

        assertEquals(PickState.PENDING, result.state.item(ITEM_ID)!!.state)
    }

    // --- stacking & rollback ----------------------------------------------------

    @Test
    fun `a stacked item needs one matching scan per unit before it is added`() {
        var state = stateWith(item(qty = STACK), scanner = ScannerState.Active(ITEM_ID))

        state = reducer.reduce(state, PickingIntent.BarcodeDetected(VALID_EAN)).state
        assertEquals(PickState.PENDING, state.item(ITEM_ID)!!.state)
        assertEquals(2, state.item(ITEM_ID)!!.remainingToScan)

        state = reducer.reduce(rescan(state), PickingIntent.BarcodeDetected(VALID_EAN)).state
        assertEquals(1, state.item(ITEM_ID)!!.remainingToScan)

        state = reducer.reduce(rescan(state), PickingIntent.BarcodeDetected(VALID_EAN)).state
        assertEquals(PickState.ADDED, state.item(ITEM_ID)!!.state)
        assertEquals(0, state.item(ITEM_ID)!!.remainingToScan)
    }

    @Test
    fun `rollback returns an added item and restores its scan balance`() {
        val added = item(qty = STACK, remaining = 0, state = PickState.ADDED)
        val result = reducer.reduce(stateWith(added), PickingIntent.Rollback(ITEM_ID, PickState.PENDING))

        assertEquals(PickState.PENDING, result.state.item(ITEM_ID)!!.state)
        assertEquals(STACK, result.state.item(ITEM_ID)!!.remainingToScan)

        // The saved item must carry the rolled-back state and the restored balance.
        val saved = result.effects.filterIsInstance<PickingEffect.SaveItem>().single()
        assertEquals(PickState.PENDING, saved.item.state)
        assertEquals(STACK, saved.item.remainingToScan)
    }

    // --- ui-only transitions ----------------------------------------------------

    @Test
    fun `changing tab produces no effects`() {
        val result = reducer.reduce(stateWith(item()), PickingIntent.ChangeTab(PickTab.ADDED))

        assertTrue(result.effects.isEmpty())
        assertEquals(PickTab.ADDED, result.state.activeTab)
    }

    @Test
    fun `dismiss clears the message`() {
        val warned = reducer.reduce(
            stateWith(item(state = PickState.ADDED, remaining = 0)),
            PickingIntent.MoveToRemoved(ITEM_ID),
        ).state
        assertNotNull(warned.message)

        val cleared = reducer.reduce(warned, PickingIntent.DismissMessage).state
        assertNull(cleared.message)
    }

    @Test
    fun `consecutive messages get distinct ids even under a fixed clock`() {
        val start = stateWith(item(), scanner = ScannerState.Active(ITEM_ID))
        val first = reducer.reduce(start, PickingIntent.BarcodeDetected(OTHER_VALID_EAN)).state
        val second = reducer.reduce(first, PickingIntent.BarcodeDetected(OTHER_VALID_EAN)).state

        assertNotNull(first.message)
        assertNotNull(second.message)
        assertNotEquals(first.message!!.id, second.message!!.id)
    }

    @Test
    fun `load injects items and logs LOAD without resetting the active view`() {
        val start = PickingUiState(
            sessionId = "s",
            activeTab = PickTab.REMOVED,
            scanner = ScannerState.Active(ITEM_ID),
        )
        val result = reducer.reduce(start, PickingIntent.LoadSession(sessionId = "s", items = listOf(item())))

        assertEquals(1, result.state.items.size)
        assertEquals(PickTab.REMOVED, result.state.activeTab)             // view preserved
        assertEquals(ScannerState.Active(ITEM_ID), result.state.scanner)  // view preserved
        assertTrue(result.effects.any { it is PickingEffect.AppendLog })
    }

    @Test
    fun `toggling the log flips its visibility and produces no effects`() {
        val opened = reducer.reduce(stateWith(item()), PickingIntent.ToggleLog)
        assertTrue(opened.state.logOpen)
        assertTrue(opened.effects.isEmpty())

        val closed = reducer.reduce(opened.state, PickingIntent.ToggleLog)
        assertFalse(closed.state.logOpen)
    }

    // --- helpers ----------------------------------------------------------------

    private fun item(
        id: String = ITEM_ID,
        qty: Int = 1,
        remaining: Int = qty,
        state: PickState = PickState.PENDING,
    ) = PickItem(
        id = id,
        name = "Milk",
        ean13 = VALID_EAN,
        requestedQty = qty,
        remainingToScan = remaining,
        state = state,
    )

    private fun stateWith(
        vararg items: PickItem,
        scanner: ScannerState = ScannerState.Idle,
    ) = PickingUiState(sessionId = "s", items = items.toList(), scanner = scanner)

    private fun rescan(state: PickingUiState) = state.copy(scanner = ScannerState.Active(ITEM_ID))

    private companion object {
        const val FIXED_TIME = 1_000L
        const val ITEM_ID = "p1"
        const val STACK = 3
        const val VALID_EAN = "4006381333931"
        const val OTHER_VALID_EAN = "5901234123457"
        const val INVALID_EAN = "123"
    }
}
