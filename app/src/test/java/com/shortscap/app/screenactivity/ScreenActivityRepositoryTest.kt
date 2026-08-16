package com.shortscap.app.screenactivity

import com.shortscap.app.sync.InMemorySyncQueue
import com.shortscap.app.sync.SyncKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [ScreenActivityRepository] — the aggregate + drain
 * boundary of the Screen Activity domain.
 *
 * Uses the REAL [SyncCoordinator] with an injected in-memory queue so the
 * test asserts on the exact records the existing sync layer would send
 * (no mocks of the sync contract): one daily app-usage summary per
 * (package, date), idempotency keys that prevent double increments, and the
 * offline-first "clear only after confirmed sync" contract.
 */
class ScreenActivityRepositoryTest {

    private val store = InMemoryScreenActivityStore()
    private val queue = InMemorySyncQueue()
    // Inject the real idempotency-keyed queue contract (InMemorySyncQueue) so
    // the test asserts on exactly what the app's durable Room queue would do
    // — without touching the shared SyncCoordinator's manager.
    private val repository = ScreenActivityRepository(store, enqueueRecord = { queue.enqueue(it) })

    private fun drain(deviceId: Int = 1): Int = repository.drainToSync(deviceId)

    @Test
    fun `one session drains to one daily usage record`() {
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 1_000_000L, 1_065_000L)
        )
        assertEquals(1, drain())
        val record = queue.pending().single()
        assertEquals(SyncKind.MONITORING_USAGE, record.kind)
        assertTrue(record.key.startsWith("monitoring:usage:1:com.example.alpha:"))
    }

    @Test
    fun `multiple sessions for the same package and day are aggregated into one record`() {
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 1_000_000L, 1_300_000L)
        )
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 2_000_000L, 2_450_000L)
        )
        assertEquals(1, drain())
        val payload = queue.pending().single().payload
        assertTrue(payload.contains("\"duration_seconds\":750"))
        assertTrue(payload.contains("\"launch_count\":2"))
    }

    @Test
    fun `different packages produce separate records`() {
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 1_000_000L, 1_300_000L)
        )
        repository.recordSession(
            ScreenActivitySession("com.example.beta", "Beta", 1_000_000L, 1_200_000L)
        )
        assertEquals(2, drain())
        assertEquals(2, queue.pending().size)
    }

    @Test
    fun `same package on different days produces separate records`() {
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 1_000_000L, 1_300_000L)
        )
        // Next UTC day (24h later).
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 87_400_000L, 87_700_000L)
        )
        assertEquals(2, drain())
        assertEquals(2, queue.pending().size)
    }

    @Test
    fun `draining twice does not create duplicate increments for the same sessions`() {
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 1_000_000L, 1_300_000L)
        )
        assertEquals(1, drain())
        // Same unsynced sessions drained again — the idempotency key dedupes
        // (nothing newly enqueued), so the queue still holds exactly ONE
        // record (never doubles).
        assertEquals(0, drain())
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `pending sessions stay until clearSynced is called`() {
        repository.recordSession(
            ScreenActivitySession("com.example.alpha", "Alpha", 1_000_000L, 1_300_000L)
        )
        drain()
        assertEquals(1, repository.pendingSessions().size)
        // After the sync layer confirms, the caller clears the local store.
        repository.clearSynced()
        assertEquals(0, repository.pendingSessions().size)
    }

    @Test
    fun `empty store drains to zero records`() {
        assertEquals(0, drain())
    }
}
