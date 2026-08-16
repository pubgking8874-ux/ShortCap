package com.shortscap.app.shorts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * P1-5 — Room-backed [ShortsLimitCycleStore] persistence tests (Robolectric +
 * real Room/SQLite). Process death / app restart is simulated by discarding
 * the store object and creating a BRAND-NEW store over the SAME persisted
 * SQLite database — the new store has no memory of the old one, so every
 * assertion proves the cycle was durable on disk (P1-5 §4/§9/§21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomShortsLimitCycleStoreTest {

    private lateinit var context: Context
    private lateinit var db: com.shortscap.app.db.ShortsCapDatabase
    private lateinit var executor: Executor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Single-thread executor shared by Room's executors AND the store's
        // dispatcher (Robolectric legacy SQLite binds to one thread).
        executor = Executors.newSingleThreadExecutor()
        db = Room.inMemoryDatabaseBuilder(context, com.shortscap.app.db.ShortsCapDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A brand-new store over the SAME persisted database (restart). */
    private fun freshStore(): RoomShortsLimitCycleStore =
        RoomShortsLimitCycleStore(db.shortsLimitCycleDao(), ioDispatcher = executor.asCoroutineDispatcher())

    @Test
    fun `saved cycle survives store recreation`() {
        val store1 = freshStore()
        val saved = store1.save(
            ShortsLimitCycle(
                limitCount = 200,
                currentCount = 127,
                cycleStartedAt = 1_000_000L,
                cycleExpiresAt = 1_000_000L + 24L * 3_600_000L,
                status = ShortsLimitCycleStatus.ACTIVE,
                warningTriggered = true,
                createdAt = 1_000_000L,
                updatedAt = 1_000_000L,
            )
        )
        assertTrue(saved.localId > 0L)

        // Process death: brand-new store over the same DB.
        val store2 = freshStore()
        val loaded = store2.currentCycle()
        assertNotNull(loaded)
        assertEquals(200, loaded!!.limitCount)
        assertEquals(127, loaded.currentCount)
        assertEquals(ShortsLimitCycleStatus.ACTIVE, loaded.status)
        assertTrue(loaded.warningTriggered)
        assertEquals(saved.localId, loaded.localId)
    }

    @Test
    fun `limit reached cycle survives restart`() {
        val store1 = freshStore()
        store1.save(
            ShortsLimitCycle(
                limitCount = 2,
                currentCount = 2,
                cycleStartedAt = 1_000_000L,
                cycleExpiresAt = 1_000_000L + 24L * 3_600_000L,
                status = ShortsLimitCycleStatus.LIMIT_REACHED,
                limitReached = true,
                createdAt = 1_000_000L,
                updatedAt = 1_000_000L,
            )
        )
        val store2 = freshStore()
        val loaded = store2.currentCycle()
        assertEquals(ShortsLimitCycleStatus.LIMIT_REACHED, loaded!!.status)
        assertTrue(loaded.limitReached)
    }

    @Test
    fun `updated cycle persists over the same row`() {
        val store1 = freshStore()
        val saved = store1.save(
            ShortsLimitCycle(
                limitCount = 200,
                currentCount = 1,
                cycleStartedAt = 1_000_000L,
                cycleExpiresAt = 1_000_000L + 24L * 3_600_000L,
                status = ShortsLimitCycleStatus.ACTIVE,
                createdAt = 1_000_000L,
                updatedAt = 1_000_000L,
            )
        )
        store1.save(saved.copy(currentCount = 150, updatedAt = 2_000_000L))

        val store2 = freshStore()
        assertEquals(150, store2.currentCycle()!!.currentCount)
        // Exactly one row — no duplicate cycle created.
        assertEquals(1, store2.history().size)
    }

    @Test
    fun `disable keeps history`() {
        val store1 = freshStore()
        store1.save(
            ShortsLimitCycle(
                limitCount = 200,
                cycleStartedAt = 1_000_000L,
                cycleExpiresAt = 1_000_000L + 24L * 3_600_000L,
                status = ShortsLimitCycleStatus.ACTIVE,
                createdAt = 1_000_000L,
                updatedAt = 1_000_000L,
            )
        )
        store1.markDisabled()

        val store2 = freshStore()
        assertNull(store2.currentCycle()) // no active window
        assertEquals(1, store2.history().size) // history preserved
        assertEquals(ShortsLimitCycleStatus.DISABLED, store2.history().first().status)
    }

    @Test
    fun `no active cycle returns null`() {
        val store = freshStore()
        assertNull(store.currentCycle())
        assertTrue(store.history().isEmpty())
    }
}
