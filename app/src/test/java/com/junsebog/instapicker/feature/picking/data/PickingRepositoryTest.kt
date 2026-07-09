package com.junsebog.instapicker.feature.picking.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.junsebog.instapicker.core.database.AppDatabase
import com.junsebog.instapicker.core.model.PickItem
import com.junsebog.instapicker.core.model.PickState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_SDK = 34

/**
 * Proves session resilience with a real (Robolectric) Room database: state written
 * through the repository survives a brand-new database instance — i.e. a process
 * restart. Honors the "restoration must be proven by a test" rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class PickingRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val source = object : ProductSource {
        override suspend fun load(): List<PickItem> = listOf(milk(remaining = QTY, state = PickState.PENDING))
    }

    @After
    fun cleanup() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `written state survives a fresh database instance`() = runTest {
        val first = newDatabase()
        newRepository(first).apply {
            ensureSeeded(SESSION)
            persist(sessionId = SESSION, item = milk(remaining = 1, state = PickState.PENDING))
        }
        first.close() // simulate process death

        val second = newDatabase()
        val restored = newRepository(second).ensureSeeded(SESSION)
        second.close()

        assertEquals(1, restored.size)
        assertEquals(1, restored.first().remainingToScan) // the mutation persisted
    }

    @Test
    fun `ensureSeeded preserves progress instead of re-seeding on relaunch`() = runTest {
        val first = newDatabase()
        newRepository(first).apply {
            ensureSeeded(SESSION)
            persist(sessionId = SESSION, item = milk(remaining = 0, state = PickState.ADDED))
        }
        first.close()

        val second = newDatabase()
        val restored = newRepository(second).ensureSeeded(SESSION) // must NOT re-seed to PENDING
        second.close()

        assertEquals(PickState.ADDED, restored.first().state)
    }

    private fun newDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME).build()

    private fun newRepository(db: AppDatabase) =
        PickingRepository(db = db, productSource = source, clock = { FIXED_TIME })

    private fun milk(remaining: Int, state: PickState) = PickItem(
        id = ITEM_ID,
        name = NAME,
        ean13 = EAN,
        requestedQty = QTY,
        remainingToScan = remaining,
        state = state,
    )

    private companion object {
        const val DB_NAME = "test-restore.db"
        const val SESSION = "s1"
        const val ITEM_ID = "p1"
        const val NAME = "Milk"
        const val EAN = "8401043321817"
        const val QTY = 3
        const val FIXED_TIME = 0L
    }
}
