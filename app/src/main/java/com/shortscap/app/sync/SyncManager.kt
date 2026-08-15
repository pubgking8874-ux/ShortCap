package com.shortscap.app.sync

import com.shortscap.app.network.ApiResult

/**
 * SyncManager — the single reusable synchronization loop (Phase 16 §13/§14).
 *
 * One [SyncQueue] + one [SyncDispatcher] + bounded retry/backoff serve every
 * feature: settings, study, monitoring, shorts and web writes all flow
 * through here. No per-feature retry systems exist.
 *
 * Behavior:
 *  - [syncNow] drains outstanding records (PENDING, and FAILED whose retry
 *    time has passed).
 *  - Each record is dispatched to [SyncDispatcher.dispatch] — the caller
 *    (syncer) performs the real API call and returns the [ApiResult].
 *  - Transient failures (network / 5xx) are retried with exponential
 *    backoff up to [maxAttempts] attempts. Permanent failures (4xx) and
 *    exhausted retries are marked FAILED and NOT retried again — never an
 *    endless retry loop (Phase 16 §13).
 *  - Dedupe is handled by the queue's idempotency keys plus the backend's
 *    own upsert semantics for usage/events — re-sending after a lost ack
 *    cannot create duplicate rows (Phase 16 §12/§14).
 *
 * Deterministic and test-friendly: [queue], [dispatcher], [clock],
 * [maxAttempts] and [baseBackoffMillis] are injectable.
 */
class SyncManager(
    val queue: SyncQueue,
    private val dispatcher: SyncDispatcher,
    private val maxAttempts: Int = 3,
    private val baseBackoffMillis: Long = 2_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Per-record API call adapter — implemented by the syncers. */
    fun interface SyncDispatcher {
        /** Push [record] to the backend; return the [ApiResult]. */
        suspend fun dispatch(record: SyncRecord): ApiResult<*>
    }

    /** True when [key] is still queued (not yet successfully synced). */
    fun isQueued(key: String): Boolean =
        queue.pending().any { it.key == key }

    /** Adds [record] (deduped by key). Returns true when newly enqueued. */
    fun enqueue(record: SyncRecord): Boolean = queue.enqueue(record)

    /** Pushes every outstanding record; returns per-record outcomes. */
    suspend fun syncNow(): List<SyncResult> {
        val results = mutableListOf<SyncResult>()
        for (record in queue.pending()) {
            if (!record.isOutstanding()) continue
            if (record.state == SyncState.FAILED && record.nextRetryAtMillis > clock()) continue
            results.add(syncOne(record))
        }
        return results
    }

    private suspend fun syncOne(record: SyncRecord): SyncResult {
        queue.markSyncing(record.key)
        val result = try {
            dispatcher.dispatch(record)
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Unexpected sync error")
        }

        return when {
            result is ApiResult.Success -> {
                queue.markSynced(record.key)
                SyncResult.success(record.key, record.kind)
            }
            // Transient: network or 5xx. Retry with backoff until the cap.
            result.isTransient && record.attempts < maxAttempts -> {
                val backoff = baseBackoffMillis shl (record.attempts.coerceAtMost(5))
                queue.markFailed(record.key, clock() + backoff)
                SyncResult.retrying(record.key, record.kind, detail(result))
            }
            else -> {
                // Permanent (4xx) or retries exhausted -> FAILED, no retry.
                queue.markFailed(record.key, Long.MAX_VALUE)
                SyncResult.failed(record.key, record.kind, detail(result))
            }
        }
    }

    private fun detail(result: ApiResult<*>): String = when (result) {
        is ApiResult.Success -> ""
        is ApiResult.HttpError -> "HTTP ${result.status}: ${result.body ?: "error"}"
        is ApiResult.NetworkError -> result.message
    }
}
