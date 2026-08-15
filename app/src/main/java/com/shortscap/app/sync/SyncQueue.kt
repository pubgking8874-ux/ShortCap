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
 * The interface keeps the queue swappable (in-memory today; SharedPreferences
 * / Room persistence is a documented future seam) without touching the
 * manager or syncers.
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

    /** Marks [key] as being sent right now. */
    fun markSyncing(key: String)

    /** Marks [key] as successfully synced and removes it from the queue. */
    fun markSynced(key: String)

    /** Marks [key] as failed, optionally scheduling a retry time. */
    fun markFailed(key: String, nextRetryAtMillis: Long = 0L)

    /** Number of queued records (debug / UI badge). */
    fun size(): Int

    /** Removes every record (used by tests and full reset). */
    fun clear()
}

/**
 * Thread-safe in-memory queue. Records are keyed by idempotency key; the
 * pending list preserves insertion order. A record is kept as FAILED with
 * its retry time until [markSynced] removes it — a permanent 4xx failure
 * stays visible for inspection without retrying (bounded by the manager's
 * attempt cap).
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
            .sortedBy { it.createdAtMillis }

    override fun markSyncing(key: String) {
        records.computeIfPresent(key) { _, record ->
            record.copy(attempts = record.attempts + 1)
        }
    }

    override fun markSynced(key: String) {
        records.remove(key)
        order.remove(key)
    }

    override fun markFailed(key: String, nextRetryAtMillis: Long) {
        records.computeIfPresent(key) { _, record ->
            record.copy(
                state = SyncState.FAILED,
                nextRetryAtMillis = nextRetryAtMillis,
            )
        }
    }

    override fun size(): Int = records.size

    override fun clear() {
        records.clear()
        order.clear()
    }
}
