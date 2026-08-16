package com.shortscap.app.sync

import com.shortscap.app.network.ApiResult

/**
 * Phase 16 sync domain models — the smallest reusable synchronization
 * abstraction (one queue, one manager, one result type for every feature;
 * no per-feature retry systems).
 *
 * A [SyncRecord] is one unit of work waiting to be pushed to the backend.
 * Its [payload] is a JSON string matching the backend request schema for
 * [kind]. [key] is the IDEMPOTENCY key — re-enqueueing a record with the
 * same key (while an unsynced copy exists) is ignored, and the backend's own
 * upsert semantics (monitoring/shorts usage, permissions) prevent duplicate
 * rows even if a retry re-sends data.
 */

/** Lifecycle of one syncable record (Phase 16 §15). */
enum class SyncState {
    /** Waiting to be sent. */
    PENDING,
    /** Currently being sent. */
    SYNCING,
    /** Sent successfully (removed from the queue). */
    SYNCED,
    /** Permanent failure (4xx / validation) or retries exhausted. */
    FAILED,
}

/** What kind of backend write a [SyncRecord] represents. */
enum class SyncKind {
    SETTINGS_USER,
    SETTINGS_MONITORING,
    SETTINGS_SHORTS,
    SETTINGS_NOTIFICATIONS,
    SETTINGS_LEADERBOARD,
    SETTINGS_PERMISSIONS,
    STUDY_SCHEDULE_CREATE,
    STUDY_SCHEDULE_UPDATE,
    STUDY_SCHEDULE_DELETE,
    STUDY_SESSION_START,
    STUDY_SESSION_END,
    STUDY_BREAK_START,
    STUDY_BREAK_END,
    MONITORING_USAGE,
    MONITORING_EVENT,
    SHORTS_USAGE,
    SHORTS_EVENT,
    WEB_EVENT,
}

/**
 * One queued sync write. [key] is the idempotency key (e.g.
 * "shorts:2026-08-15:YOUTUBE:YOUTUBE_SHORTS"); [payload] is the JSON body.
 * [attempts] counts send attempts (used for bounded retry/backoff) and
 * [nextRetryAtMillis] gates retries until a future time (0 = retry now).
 * [lastAttemptAtMillis] records when the record was last claimed for sending
 * and [lastError] a SHORT sanitized error classification only (never full
 * payloads, tokens, headers or secrets — P1-2 STEP 19).
 */
data class SyncRecord(
    val kind: SyncKind,
    val key: String,
    val payload: String,
    val state: SyncState = SyncState.PENDING,
    val attempts: Int = 0,
    val nextRetryAtMillis: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastAttemptAtMillis: Long = 0L,
    val lastError: String? = null,
) {}

/** Records still awaiting a successful sync (PENDING or FAILED). */
fun SyncRecord.isOutstanding(): Boolean =
    state == SyncState.PENDING || state == SyncState.FAILED

/** Outcome of pushing one record — surfaced to callers for logging/UI. */
data class SyncResult(
    val key: String,
    val kind: SyncKind,
    val state: SyncState,
    val detail: String? = null,
) {
    companion object {
        fun success(key: String, kind: SyncKind) =
            SyncResult(key, kind, SyncState.SYNCED)
        fun failed(key: String, kind: SyncKind, detail: String? = null) =
            SyncResult(key, kind, SyncState.FAILED, detail)
        fun retrying(key: String, kind: SyncKind, detail: String? = null) =
            SyncResult(key, kind, SyncState.PENDING, detail)
    }
}

/** Maps an [ApiResult] to the right sync outcome (Phase 16 §13). */
fun ApiResult<*>.toSyncOutcome(key: String, kind: SyncKind): SyncResult = when (this) {
    is ApiResult.Success -> SyncResult.success(key, kind)
    is ApiResult.HttpError -> SyncResult.failed(key, kind, "HTTP $status: ${body ?: "error"}")
    is ApiResult.NetworkError -> SyncResult.retrying(key, kind, message)
}
