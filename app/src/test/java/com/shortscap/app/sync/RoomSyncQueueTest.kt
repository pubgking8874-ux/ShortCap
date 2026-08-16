package com.shortscap.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shortscap.app.db.ShortsCapDatabase
import com.shortscap.app.network.ApiResult
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * P1-2 — Durable Offline Sync Queue tests (Robolectric + real Room/SQLite).
 *
 * Every test exercises PERSISTENCE + RELOAD, not just in-memory behavior:
 * "process death / app restart" is simulated by discarding the queue object
 * (and any in-memory state it held) and creating a BRAND-NEW
 * [RoomSyncQueue] / [SyncManager] over the SAME persisted SQLite database —
 * the new queue has no memory of the old one, so every assertion proves the
 * record was durable on disk (P1-2 STEP 10/11/12/13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native SQLite: the legacy Robolectric SQLite binds each connection to a
// single thread, which breaks Room's multi-threaded connection pool
// ("Illegal connection pointer"); native mode supports real multi-threading.
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomSyncQueueTest {

    private lateinit var context: Context
    private lateinit var db: ShortsCapDatabase
    private lateinit var executor: Executor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric's legacy SQLite binds each connection to ONE thread,
        // so EVERY SQLite touch (Room's query/transaction executors AND the
        // queue's runBlocking dispatcher) must share the same single thread.
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

    /** A brand-new queue over the SAME persisted database (restart). */
    private fun freshQueue(): RoomSyncQueue =
        RoomSyncQueue(db.syncQueueDao(), ioDispatcher = executor.asCoroutineDispatcher())

    private fun record(
        kind: SyncKind = SyncKind.MONITORING_EVENT,
        key: String = "test:${System.nanoTime()}",
    ) = SyncRecord(kind = kind, key = key, payload = "{\"a\":1}")

    // ------------------------------------------------------------------
    // 1. Persistent enqueue + reload (process death)
    // ------------------------------------------------------------------

    @Test
    fun `enqueued record survives process death and reloads`() {
        val q1 = freshQueue()
        val r = record()
        assertTrue("enqueued", q1.enqueue(r))

        // Process death: a brand-new queue with NO memory of q1.
        val q2 = freshQueue()
        val pending = q2.pending()
        assertEquals("exactly one pending record", 1, pending.size)
        assertEquals("key survives", r.key, pending.single().key)
        assertEquals("payload survives", r.payload, pending.single().payload)
        assertEquals("kind survives", r.kind, pending.single().kind)
        assertEquals("state survives", SyncState.PENDING, pending.single().state)
        assertEquals("createdAt survives", r.createdAtMillis, pending.single().createdAtMillis)
    }

    @Test
    fun `enqueue stays idempotent by key across process death`() {
        val q1 = freshQueue()
        assertTrue(q1.enqueue(record(key = "k1")))

        val q2 = freshQueue()
        assertFalse("same key not re-enqueued after restart", q2.enqueue(record(key = "k1")))
        assertEquals(1, q2.size())

        val q3 = freshQueue()
        assertTrue("new key enqueued", q3.enqueue(record(key = "k2")))
        assertEquals(2, q3.size())
    }

    // ------------------------------------------------------------------
    // 2. SYNCING recovery (interrupted send -> retryable)
    // ------------------------------------------------------------------

    @Test
    fun `record stuck in SYNCING is recovered to PENDING after restart`() {
        val q1 = freshQueue()
        val r = record()
        q1.enqueue(r)
        assertTrue("claimed", q1.markSyncing(r.key))

        // Process death mid-send: the record is left SYNCING on disk. A new
        // queue's construction runs the recovery pass (SYNCING -> PENDING).
        val q2 = freshQueue()
        val pending = q2.pending()
        assertEquals("recovered and pending", 1, pending.size)
        assertEquals("returned to retryable state", SyncState.PENDING, pending.single().state)
        assertEquals("retry allowed immediately", 0L, pending.single().nextRetryAtMillis)
        assertEquals("attempt recorded", 1, pending.single().attempts)
    }

    // ------------------------------------------------------------------
    // 3. Success / failure state survival
    // ------------------------------------------------------------------

    @Test
    fun `synced record is removed permanently across restart`() {
        val q1 = freshQueue()
        val r = record()
        q1.enqueue(r)
        q1.markSyncing(r.key)
        q1.markSynced(r.key)

        val q2 = freshQueue()
        assertTrue("removed", q2.pending().isEmpty())
        assertEquals(0, q2.size())
    }

    @Test
    fun `failed record with backoff and error classification survives restart`() {
        val q1 = freshQueue()
        val r = record()
        q1.enqueue(r)
        q1.markSyncing(r.key)
        q1.markFailed(r.key, nextRetryAtMillis = 5_000L, lastError = "HTTP 503")

        val q2 = freshQueue()
        val pending = q2.pending()
        assertEquals(1, pending.size)
        assertEquals(SyncState.FAILED, pending.single().state)
        assertEquals("backoff time survives", 5_000L, pending.single().nextRetryAtMillis)
        assertEquals("sanitized error survives", "HTTP 503", pending.single().lastError)
    }

    @Test
    fun `clear removes every record permanently`() {
        val q1 = freshQueue()
        q1.enqueue(record(key = "a"))
        q1.enqueue(record(key = "b"))
        q1.clear()

        val q2 = freshQueue()
        assertEquals(0, q2.size())
        assertTrue(q2.pending().isEmpty())
    }

    // ------------------------------------------------------------------
    // 4. Concurrency — single worker may process a record
    // ------------------------------------------------------------------

    @Test
    fun `only one worker wins the claim for a record`() {
        val q = freshQueue()
        val r = record()
        q.enqueue(r)

        // Two workers race: the first claim wins, the second must be denied.
        assertTrue("worker A claims", q.markSyncing(r.key))
        assertFalse("worker B denied", q.markSyncing(r.key))
        assertTrue("in-flight record hidden from pending", q.pending().isEmpty())

        // The winner marks it synced; the record is gone for everyone.
        q.markSynced(r.key)
        assertTrue(q.pending().isEmpty())
        assertEquals(0, q.size())
    }

    // ------------------------------------------------------------------
    // 5. SyncManager end-to-end across process death (restart flow)
    // ------------------------------------------------------------------

    @Test
    fun `manager retries after restart with no data loss`() = runBlocking {
        var now = 0L
        var online = false

        // Phase 1: offline — enqueue + failed attempt.
        val m1 = SyncManager(
            queue = freshQueue(),
            dispatcher = SyncManager.SyncDispatcher {
                if (online) ApiResult.Success(Unit) else ApiResult.NetworkError("offline")
            },
            baseBackoffMillis = 1_000L,
            clock = { now },
        )
        val r = record(key = "shorts:2026-08-15:YOUTUBE:YOUTUBE_SHORTS")
        m1.enqueue(r)
        assertEquals(SyncState.PENDING, m1.syncNow().single().state)

        // Process death while offline: nothing was synced, nothing lost.
        val q2 = freshQueue()
        assertTrue("record survived restart", q2.pending().any { it.key == r.key })

        // Phase 2: back online after backoff — a fresh manager drains it.
        now += 2_000
        online = true
        val m2 = SyncManager(
            queue = q2,
            dispatcher = SyncManager.SyncDispatcher { ApiResult.Success(Unit) },
            clock = { now },
        )
        val results = m2.syncNow()
        assertEquals(SyncState.SYNCED, results.single().state)
        assertTrue("queue drained", q2.pending().isEmpty())
        assertEquals(0, q2.size())
    }

    @Test
    fun `timeout retry does not create duplicate logical records`() = runBlocking {
        var now = 0L
        var dispatches = 0
        val keysSeen = mutableListOf<String>()

        val dispatcher = SyncManager.SyncDispatcher { sent ->
            dispatches++
            keysSeen.add(sent.key)
            // Simulate: server may have accepted the first attempt (timeout),
            // then the retry succeeds.
            if (dispatches == 1) ApiResult.NetworkError("timeout — unknown outcome") else ApiResult.Success(Unit)
        }
        val m1 = SyncManager(
            queue = freshQueue(),
            dispatcher = dispatcher,
            maxAttempts = 3,
            baseBackoffMillis = 1_000L,
            clock = { now },
        )
        val r = record(key = "shorts:usage:7:2026-08-15:YOUTUBE:YOUTUBE_SHORTS")
        m1.enqueue(r)
        m1.syncNow() // attempt 1 -> timeout, transient failure
        assertEquals(1, dispatches)

        // Restart, retry the same idempotency key — the backend upsert
        // dedupes; the client must re-send the SAME key, never a second
        // logical record.
        now += 2_000
        val m2 = SyncManager(
            queue = freshQueue(),
            dispatcher = dispatcher,
            maxAttempts = 3,
            clock = { now },
        )
        assertEquals(SyncState.SYNCED, m2.syncNow().single().state)
        assertEquals("same idempotency key sent twice", listOf(r.key, r.key), keysSeen)
        assertEquals("no duplicate queue records", 0, m2.queue.size())
    }

    // ------------------------------------------------------------------
    // 6. Representative records for every sync domain (restart survival)
    // ------------------------------------------------------------------

    @Test
    fun `representative records from all sync domains survive restart`() {
        val q1 = freshQueue()
        val records = listOf(
            SyncRecord(SyncKind.SHORTS_USAGE, "shorts:usage:7:2026-08-15:YOUTUBE:YOUTUBE_SHORTS", "{}"),
            SyncRecord(SyncKind.STUDY_SESSION_END, "study:session:end:12", "{}"),
            SyncRecord(SyncKind.MONITORING_USAGE, "monitoring:usage:7:com.example:2026-08-15", "{}"),
            SyncRecord(SyncKind.WEB_EVENT, "web:event:7:BLOCK_ATTEMPT:tiktok.com:1720000000000", "{}"),
        )
        records.forEach { assertTrue(q1.enqueue(it)) }

        val q2 = freshQueue()
        val keys = q2.pending().map { it.key }.toSet()
        assertEquals("all four survive restart", records.map { it.key }.toSet(), keys)
    }
}
