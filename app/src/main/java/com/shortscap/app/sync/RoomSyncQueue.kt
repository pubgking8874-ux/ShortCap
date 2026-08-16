package com.shortscap.app.sync

import com.shortscap.app.db.SyncQueueDao
import com.shortscap.app.db.SyncQueueEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * P1-2 DURABLE SyncQueue — Room-backed, survives process death and restart.
 *
 * Implements the exact [SyncQueue] contract (so [SyncManager] and the
 * syncers are untouched) on top of [SyncQueueDao]. Every record is written
 * to SQLite BEFORE any network attempt ([SyncManager.enqueue] -> this ->
 * disk), making the persistent queue the source of truth for unsynchronized
 * work (P1-2 STEP 4).
 *
 * Restart recovery (P1-2 STEP 5): construction returns any record left in
 * SYNCING by a process death back to the retryable PENDING state, so an
 * interrupted send is retried instead of lost or stuck.
 *
 * Concurrency (P1-2 STEP 15): [markSyncing] is an atomic conditional UPDATE
 * — only the winning worker transitions PENDING/FAILED -> SYNCING and gets
 * `true`; a second worker gets `false` and must not dispatch the record.
 *
 * Threading: DB work runs on [ioDispatcher] (Dispatchers.IO); the interface
 * stays synchronous so no caller changes are needed. Ops are single-row
 * writes/reads — no heavy work is ever performed on the main thread
 * (P1-2 STEP 16).
 */
class RoomSyncQueue(
    private val dao: SyncQueueDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncQueue {

    init {
        // Process-restart recovery: interrupted sends (SYNCING) -> PENDING.
        ioBlocking { dao.recoverInterrupted() }
    }

    override fun enqueue(record: SyncRecord): Boolean {
        // INSERT OR IGNORE keyed on the idempotency key: returns -1 when an
        // unsynced record with the same key already exists (dedupe, matching
        // InMemorySyncQueue.enqueue).
        val rowId = ioBlocking { dao.insert(record.toEntity()) }
        return rowId != -1L
    }

    override fun pending(): List<SyncRecord> =
        ioBlocking { dao.pending() }.map { it.toSyncRecord() }

    override fun markSyncing(key: String): Boolean =
        ioBlocking { dao.claimSyncing(key, System.currentTimeMillis()) } > 0

    override fun markSynced(key: String) {
        ioBlocking { dao.markSynced(key) }
    }

    override fun markFailed(key: String, nextRetryAtMillis: Long, lastError: String?) {
        ioBlocking { dao.markFailed(key, nextRetryAtMillis, lastError) }
    }

    override fun size(): Int = ioBlocking { dao.count() }

    override fun clear() {
        ioBlocking { dao.clear() }
    }

    private fun <T> ioBlocking(block: suspend () -> T): T =
        runBlocking(ioDispatcher) { block() }
}

// ---------------------------------------------------------------------------
// Mapping between the sync domain model and the Room entity
// ---------------------------------------------------------------------------

private fun SyncRecord.toEntity() = SyncQueueEntity(
    key = key,
    kind = kind.name,
    payload = payload,
    state = state.name,
    attempts = attempts,
    nextRetryAtMillis = nextRetryAtMillis,
    createdAtMillis = createdAtMillis,
    lastAttemptAtMillis = lastAttemptAtMillis,
    lastError = lastError,
)

private fun SyncQueueEntity.toSyncRecord() = SyncRecord(
    kind = SyncKind.valueOf(kind),
    key = key,
    payload = payload,
    state = SyncState.valueOf(state),
    attempts = attempts,
    nextRetryAtMillis = nextRetryAtMillis,
    createdAtMillis = createdAtMillis,
    lastAttemptAtMillis = lastAttemptAtMillis,
    lastError = lastError,
)
