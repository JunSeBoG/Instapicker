package com.junsebog.instapicker.feature.picking.ui

import androidx.lifecycle.SavedStateHandle
import com.junsebog.instapicker.core.database.AuditLogEntity
import com.junsebog.instapicker.core.model.AuditEntry
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import com.junsebog.instapicker.feature.picking.data.PickingRepository
import com.junsebog.instapicker.feature.picking.domain.PickTab
import com.junsebog.instapicker.feature.picking.domain.PickingIntent
import com.junsebog.instapicker.feature.picking.domain.ScannerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** In-memory [PickingRepository] fake — no Room, so ViewModel tests stay fast. */
private class FakePickingRepository(private val seed: List<PickItem>) : PickingRepository {

    private val items = MutableStateFlow<List<PickItem>>(emptyList())
    val appended = mutableListOf<AuditEntry>()

    override fun observeItems(sessionId: String): Flow<List<PickItem>> = items.asStateFlow()

    override fun observeLog(sessionId: String): Flow<List<AuditLogEntity>> =
        MutableStateFlow<List<AuditLogEntity>>(emptyList()).asStateFlow()

    override suspend fun ensureSeeded(sessionId: String): List<PickItem> {
        if (items.value.isEmpty()) items.value = seed
        return items.value
    }

    override suspend fun persist(sessionId: String, item: PickItem) {
        items.update { list -> list.map { if (it.id == item.id) item else it } }
    }

    override suspend fun append(entry: AuditEntry) {
        appended += entry
    }

    fun current(id: String): PickItem = items.value.first { it.id == id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PickingViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dispatching a move updates state and persists the item`() {
        val repo = FakePickingRepository(seed = listOf(pendingMilk()))
        val viewModel = PickingViewModel(repository = repo, savedState = SavedStateHandle())

        viewModel.dispatch(PickingIntent.MoveToRemoved(ITEM_ID))

        assertEquals(PickState.REMOVED, viewModel.state.value.item(ITEM_ID)!!.state)
        assertEquals(PickState.REMOVED, repo.current(ITEM_ID).state) // write-through persisted
    }

    @Test
    fun `changing tab is remembered in saved state`() {
        val saved = SavedStateHandle()
        val viewModel = PickingViewModel(repository = FakePickingRepository(seed = emptyList()), savedState = saved)

        viewModel.dispatch(PickingIntent.ChangeTab(PickTab.ADDED))

        assertEquals(PickTab.ADDED, viewModel.state.value.activeTab)
        assertEquals("ADDED", saved.get<String>(KEY_TAB))
    }

    @Test
    fun `the exact active view is restored from saved state`() {
        val saved = SavedStateHandle(mapOf(KEY_TAB to "REMOVED", KEY_SCAN to ITEM_ID))
        val viewModel = PickingViewModel(repository = FakePickingRepository(seed = emptyList()), savedState = saved)

        assertEquals(PickTab.REMOVED, viewModel.state.value.activeTab)
        assertEquals(ScannerState.Active(ITEM_ID), viewModel.state.value.scanner)
    }

    private fun pendingMilk() = PickItem(
        id = ITEM_ID,
        name = NAME,
        ean13 = EAN,
        requestedQty = 1,
        remainingToScan = 1,
        state = PickState.PENDING,
    )

    private companion object {
        const val ITEM_ID = "p1"
        const val NAME = "Milk"
        const val EAN = "8401043321817"
        const val KEY_TAB = "active_tab"
        const val KEY_SCAN = "scanner_item"
    }
}
