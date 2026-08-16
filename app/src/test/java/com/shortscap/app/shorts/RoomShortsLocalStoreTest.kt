package com.shortscap.app.shorts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shortscap.app.db.ShortsCapDatabase
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * P1-2 — Durable Shorts local store tests (Robolectric + real Room/SQLite).
 *
 * Verifies that Shorts usage/events waiting for backend synchronization
 * survive process death: "restart" discards the store object and creates a
 * BRAND-NEW [RoomShortsLocalStore] over the SAME persisted SQLite database,
 * so every assertion proves the records were durable on disk (P1-2 STEP 8 /
 * STEP 10-13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native SQLite: the legacy Robolectric SQLite binds each connection to a
// single thread, which breaks Room's multi-threaded connection pool
// ("Illegal connection pointer"); native mode supports real multi-threading.
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomShortsLocalStoreTest {

    private lateinit var context: Context
    private lateinit var db: ShortsCapDatabase
    private lateinit var executor: Executor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric's legacy SQLite binds each connection to ONE thread,
        // so EVERY SQLite touch (Room's query/transaction executors AND the
        // store's runBlocking dispatcher) must share the same single thread.
        executor = Executors.newSingleThreadExecutor()
        db = Room.inMemoryDatabaseBuilder(context, ShortsCapDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A brand-new store over the SAME persisted database (restart). */
    private fun freshStore(): RoomShortsLocalStore =
        RoomShortsLocalStore(db.shortsStoreDao(), ioDispatcher = executor.asCoroutineDispatcher())

    private fun usage(
        platform: ShortPlatform = ShortPlatform.YOUTUBE,
        surface: ShortSurface = ShortSurface.YOUTUBE_SHORTS,
        count: Int = 1,
        occurredAt: Long = 1_000L,
    ) = LocalShortsUsage(
        platform = platform,
        surface = surface,
        detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        confidence = 0.95f,
        occurredAt = occurredAt,
        durationMillis = 4_000L,
        countDelta = count,
    )

    private fun event(occurredAt: Long = 2_000L) = LocalShortsEvent(
        eventType = "SHORT_COUNTED",
        platform = ShortPlatform.YOUTUBE,
        surface = ShortSurface.YOUTUBE_SHORTS,
        detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        confidence = 0.95f,
        occurredAt = occurredAt,
        durationMillis = 4_000L,
    )

    // ------------------------------------------------------------------
    // Persistence across process death
    // ------------------------------------------------------------------

    @Test
    fun `usage records survive process death in insertion order`() {
        val store1 = freshStore()
        store1.recordUsage(usage(count = 1, occurredAt = 1_000L))
        store1.recordUsage(usage(platform = ShortPlatform.INSTAGRAM, surface = ShortSurface.INSTAGRAM_REELS, count = 2, occurredAt = 2_000L))
        store1.recordUsage(usage(count = 3, occurredAt = 3_000L))

        val store2 = freshStore()
        val snapshot = store2.usageSnapshot()
        assertEquals("3 usage records survive", 3, snapshot.size)
        assertEquals("insertion order kept", 1_000L, snapshot[0].occurredAt)
        assertEquals("cross-platform identity kept", ShortPlatform.INSTAGRAM, snapshot[1].platform)
        assertEquals("surface kept", ShortSurface.INSTAGRAM_REELS, snapshot[1].surface)
        assertEquals("detection method kept", DetectionMethod.PLATFORM_ADAPTER, snapshot[2].detectionMethod)
        assertEquals("count delta kept", 3, snapshot[2].countDelta)
        assertEquals("confidence kept", 0.95f, snapshot[2].confidence, 0.0001f)
    }

    @Test
    fun `events survive process death in insertion order`() {
        val store1 = freshStore()
        store1.recordEvent(event(occurredAt = 10_000L))
        store1.recordEvent(event(occurredAt = 20_000L))

        val store2 = freshStore()
        val events = store2.eventSnapshot()
        assertEquals(2, events.size)
        assertEquals(10_000L, events[0].occurredAt)
        assertEquals(20_000L, events[1].occurredAt)
        assertEquals("SHORT_COUNTED", events[0].eventType)
    }

    @Test
    fun `usage and events survive together across restart`() {
        val store1 = freshStore()
        store1.recordUsage(usage())
        store1.recordEvent(event())

        val store2 = freshStore()
        assertEquals(1, store2.usageSnapshot().size)
        assertEquals(1, store2.eventSnapshot().size)
    }

    // ------------------------------------------------------------------
    // Clear (after confirmed sync)
    // ------------------------------------------------------------------

    @Test
    fun `clear removes all records permanently`() {
        val store1 = freshStore()
        store1.recordUsage(usage())
        store1.recordEvent(event())
        store1.clear()

        val store2 = freshStore()
        assertTrue(store2.usageSnapshot().isEmpty())
        assertTrue(store2.eventSnapshot().isEmpty())
    }
}
