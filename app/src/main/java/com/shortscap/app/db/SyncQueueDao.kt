package com.shortscap.app.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * P1-2 durable sync queue storage.
 *
 * One row per pending [SyncRecord]: the idempotency [key] is the primary key
 * (matches [com.shortscap.app.sync.InMemorySyncQueue]'s keyed-dedupe
 * semantics — re-enqueueing an unsynced record with the same key is
 * ignored), [state] stores the [com.shortscap.app.sync.SyncState] name and
 * [kind] the [com.shortscap.app.sync.SyncKind] name. [lastError] holds a
 * SHORT sanitized error classification only (never full payloads, tokens,
 * headers or secrets — Phase 19 §19 of the security audit).
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val key: String,
    val kind: String,
    val payload: String,
    val state: String,
    val attempts: Int,
    val nextRetryAtMillis: Long,
    val createdAtMillis: Long,
    val lastAttemptAtMillis: Long,
    val lastError: String?,
)

@Dao
interface SyncQueueDao {

    /**
     * Inserts a pending record. Returns -1 when a row with the same [key]
     * already exists (idempotent enqueue — the existing record is kept).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: SyncQueueEntity): Long

    /** All records not yet SYNCED (PENDING + FAILED), oldest first. */
    @Query(
        "SELECT * FROM sync_queue WHERE state IN ('PENDING', 'FAILED') " +
            "ORDER BY createdAtMillis ASC"
    )
    suspend fun pending(): List<SyncQueueEntity>

    /**
     * Atomically claims [key] for sending: transitions PENDING/FAILED ->
     * SYNCING, increments attempts, records the attempt time. Returns the
     * number of rows updated (1 = this worker won the claim, 0 = another
     * worker already claimed it or the record is gone) — the guard against
     * concurrent processing by multiple workers (P1-2 STEP 15).
     */
    @Query(
        "UPDATE sync_queue SET state = 'SYNCING', " +
            "attempts = attempts + 1, lastAttemptAtMillis = :attemptedAt " +
            "WHERE key = :key AND state IN ('PENDING', 'FAILED')"
    )
    suspend fun claimSyncing(key: String, attemptedAt: Long): Int

    /** Removes a successfully synced record. */
    @Query("DELETE FROM sync_queue WHERE key = :key")
    suspend fun markSynced(key: String)

    /** Marks [key] FAILED with the next retry time and a sanitized error. */
    @Query(
        "UPDATE sync_queue SET state = 'FAILED', " +
            "nextRetryAtMillis = :nextRetryAtMillis, lastError = :lastError " +
            "WHERE key = :key"
    )
    suspend fun markFailed(key: String, nextRetryAtMillis: Long, lastError: String?)

    /**
     * Process-restart recovery: any record interrupted mid-send (left in
     * SYNCING by a process death) is returned to the retryable PENDING
     * state. Called once when the durable queue is (re)created at app start.
     */
    @Query("UPDATE sync_queue SET state = 'PENDING', nextRetryAtMillis = 0 WHERE state = 'SYNCING'")
    suspend fun recoverInterrupted(): Int

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}
