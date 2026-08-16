package com.shortscap.app.sync

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SyncQueue — the local queue of pending backend writes (Phase 16 §14).
 *
 * Offline-first contract: records captured while the backend is unavailable
 * stay in the queue (PENDING) and are pushed on the next successful drain —
 * locally captured data is never discarded just because the network was
 * down. [enqueue] is idempotent by [SyncRecord.key]: an unsynced record with
 * the same key is not duplicated (Phase 16 §12).
 *
 * P1-2 makes the queue DURABLE: the default implementation is Room-backed
 * ([com.shortscap.app.sync.RoomSyncQueue]), so pending records survive
 * process death and app restart. The interface itself is unchanged so the
 * manager and syncers are untouched.
 */
interface SyncQueue {

    /**
     * Adds [record]. Returns true when newly enqueued; false when an
     * unsynced record with the same [SyncRecord.key] already exists (or the
     * record is already SYNCED/being removed).
     */
    fun enqueue(record: SyncRecord): Boolean

    /** All records not yet SYNCED (PENDING + FAILED), oldest first. */
    fun pending(): List<SyncRecord>

    /**
     * Atomically claims [key] for sending (PENDING/FAILED -> SYNCING).
     * Returns true when THIS caller won the claim — the concurrent-worker
     * guard: a second worker claiming the same key gets false and must not
     * dispatch it (P1-2 STEP 15).
     */
    fun markSyncing(key: String): Boolean

    /** Marks [key] as successfully synced and removes it from the queue. */
    fun markSynced(key: String)

    /**
     * Marks [key] as failed, optionally scheduling a retry time and storing
     * a SHORT sanitized error classification ([lastError] — never payloads,
     * tokens, headers or secrets, P1-2 STEP 19).
     */
    fun markFailed(key: String, nextRetryAtMillis: Long = 0L, lastError: String? = null)

    /** Number of queued records (debug / UI badge). */
    fun size(): Int

    /** Removes every record (used by tests and full reset). */
    fun clear()
}

/**
 * Thread-safe in-memory queue (default / test queue). Records are keyed by
 * idempotency key; the pending list preserves insertion order. A record is
 * kept as FAILED with its retry time until [markSynced] removes it — a
 * permanent 4xx failure stays visible for inspection without retrying
 * (bounded by the manager's attempt cap).
 *
 * P1-2: [markSyncing] now actually transitions to SYNCING (matching the
 * durable Room queue) so a second worker can never re-dispatch a record
 * that is in flight.
 */
class InMemorySyncQueue : SyncQueue {

    private val records = ConcurrentHashMap<String, SyncRecord>()
    private val order = CopyOnWriteArrayList<String>()

    override fun enqueue(record: SyncRecord): Boolean {
        val previous = records.putIfAbsent(record.key, record)
        if (previous == null) {
            order.add(record.key)
            return true
        }
        // Same key already queued (PENDING or FAILED): keep the existing one.
        return false
    }

    override fun pending(): List<SyncRecord> =
        order.mapNotNull { records[it] }
            .filter { it.isOutstanding() }
            .sortedBy { it.createdAtMillis }

    override fun markSyncing(key: String): Boolean {
        var claimed = false
        records.computeIfPresent(key) { _, record ->
            if (record.isOutstanding()) {
                claimed = true
                record.copy(
                    state = SyncState.SYNCING,
                    attempts = record.attempts + 1,
                    lastAttemptAtMillis = System.currentTimeMillis(),
                )
            } else {
                record
            }
        }
        return claimed
    }

    override fun markSynced(key: String) {
        records.remove(key)
        order.remove(key)
    }

    override fun markFailed(key: String, nextRetryAtMillis: Long, lastError: String?) {
        records.computeIfPresent(key) { _, record ->
            record.copy(
                state = SyncState.FAILED,
                nextRetryAtMillis = nextRetryAtMillis,
                lastError = lastError,
            )
        }
    }

    override fun size(): Int = records.size

    override fun clear() {
        records.clear()
        order.clear()
    }
}
