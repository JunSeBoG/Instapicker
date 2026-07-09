package com.junsebog.instapicker.feature.picking.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import com.junsebog.instapicker.feature.picking.domain.PickTab
import com.junsebog.instapicker.feature.picking.domain.PickingIntent
import com.junsebog.instapicker.feature.picking.domain.PickingUiState
import com.junsebog.instapicker.feature.picking.domain.ScannerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val ROBOLECTRIC_SDK = 34

/**
 * The screen is a pure function of [PickingUiState]; these tests pin what each state
 * renders. Running under Robolectric keeps them on the JVM — no emulator in CI.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK])
class PickingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each tab lists only the items in its own state`() {
        compose.setContent {
            PickingScreen(state = stateWith(activeTab = PickTab.PENDING), onIntent = {})
        }

        compose.onNodeWithText(PENDING_NAME).assertIsDisplayed()
        compose.onNodeWithText(REMOVED_NAME).assertDoesNotExist()
        compose.onNodeWithText(ADDED_NAME).assertDoesNotExist()
    }

    @Test
    fun `the removed tab lists only removed items`() {
        compose.setContent {
            PickingScreen(state = stateWith(activeTab = PickTab.REMOVED), onIntent = {})
        }

        compose.onNodeWithText(REMOVED_NAME).assertIsDisplayed()
        compose.onNodeWithText(PENDING_NAME).assertDoesNotExist()
        compose.onNodeWithText(ADDED_NAME).assertDoesNotExist()
    }

    @Test
    fun `an active scanner shows the overlay and Cancel emits CancelScan`() {
        val captured = mutableListOf<PickingIntent>()
        val pending = item(id = "p1", name = PENDING_NAME, qty = 1, remaining = 1, state = PickState.PENDING)
        compose.setContent {
            PickingScreen(
                state = PickingUiState(
                    sessionId = SESSION,
                    items = listOf(pending),
                    scanner = ScannerState.Active(itemId = "p1"),
                ),
                onIntent = { captured += it },
            )
        }

        compose.onNodeWithText(text = "Scanning item", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(PickingIntent.CancelScan, captured.last())
    }

    @Test
    fun `an empty tab shows its placeholder instead of a blank list`() {
        compose.setContent {
            PickingScreen(
                state = PickingUiState(sessionId = SESSION, activeTab = PickTab.ADDED),
                onIntent = {},
            )
        }

        compose.onNodeWithText(text = "Scan a product to add", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a stacked pending row shows scan progress`() {
        val stacked = item(id = "s1", name = "Rexona", qty = 3, remaining = 2, state = PickState.PENDING)
        compose.setContent {
            PickingScreen(
                state = PickingUiState(sessionId = SESSION, items = listOf(stacked)),
                onIntent = {},
            )
        }

        // 3 requested, 2 still to scan -> 1 scanned so far.
        compose.onNodeWithText("1/3").assertIsDisplayed()
    }

    @Test
    fun `tapping Scan emits StartScan for that row`() {
        val captured = mutableListOf<PickingIntent>()
        val pending = item(id = "p1", name = PENDING_NAME, qty = 1, remaining = 1, state = PickState.PENDING)
        compose.setContent {
            PickingScreen(
                state = PickingUiState(sessionId = SESSION, items = listOf(pending)),
                onIntent = { captured += it },
            )
        }

        compose.onNodeWithText("Scan").performClick()

        assertEquals(PickingIntent.StartScan(itemId = "p1"), captured.last())
    }

    private fun stateWith(activeTab: PickTab) = PickingUiState(
        sessionId = SESSION,
        activeTab = activeTab,
        items = listOf(
            item(id = "p1", name = PENDING_NAME, qty = 1, remaining = 1, state = PickState.PENDING),
            item(id = "r1", name = REMOVED_NAME, qty = 1, remaining = 1, state = PickState.REMOVED),
            item(id = "a1", name = ADDED_NAME, qty = 1, remaining = 0, state = PickState.ADDED),
        ),
    )

    private fun item(id: String, name: String, qty: Int, remaining: Int, state: PickState) = PickItem(
        id = id,
        name = name,
        ean13 = EAN,
        requestedQty = qty,
        remainingToScan = remaining,
        state = state,
    )

    private companion object {
        const val SESSION = "session-test"
        const val PENDING_NAME = "Aguardiente Special"
        const val REMOVED_NAME = "Listerine Cool Mint"
        const val ADDED_NAME = "Rexona Clinical"
        const val EAN = "7702835000806"
    }
}
