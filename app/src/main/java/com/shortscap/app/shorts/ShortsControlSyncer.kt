package com.shortscap.app.shorts

import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.BackendApi

/**
 * Best-effort backend sync for the Shorts Control state (24-hour limit
 * cycle). Uses the EXISTING [BackendApi] client — no second HTTP client, no
 * second sync queue.
 *
 * The Android app remains the real-time authority: the local
 * [ShortsControlEngine] + Room store is the single source of truth and is
 * NEVER reset or overwritten by this syncer. The backend is a mirror that
 * receives the activated / edited / disabled cycle and returns the sync
 * status the Shorts Limit page surfaces as an offline/error notice.
 *
 * Control commands are pushed directly (best-effort) rather than enqueued —
 * the durable SyncQueue is for data records (usage/events), not control
 * commands. A failure never blocks or resets the local state; when the
 * network returns, the next user action (or page refresh) re-pushes.
 */
class ShortsControlSyncer(
    private val api: BackendApi,
) {

    /** Pushes an activation (or returns the existing cycle). */
    suspend fun syncActivate(limitCount: Int): ShortsSyncStatus =
        when (api.activateShortsLimitCycle(limitCount)) {
            is ApiResult.Success -> ShortsSyncStatus.SYNCED
            is ApiResult.NetworkError -> ShortsSyncStatus.OFFLINE
            is ApiResult.HttpError -> ShortsSyncStatus.ERROR
        }

    /** Pushes a threshold-only limit edit (count + 24-hour timer preserved). */
    suspend fun syncEditLimit(limitCount: Int): ShortsSyncStatus =
        when (api.updateShortsControl(limitCount)) {
            is ApiResult.Success -> ShortsSyncStatus.SYNCED
            is ApiResult.NetworkError -> ShortsSyncStatus.OFFLINE
            is ApiResult.HttpError -> ShortsSyncStatus.ERROR
        }

    /** Pushes a disable of Shorts control. */
    suspend fun syncDisable(): ShortsSyncStatus =
        when (api.disableShortsLimitCycle()) {
            is ApiResult.Success -> ShortsSyncStatus.SYNCED
            is ApiResult.NetworkError -> ShortsSyncStatus.OFFLINE
            is ApiResult.HttpError -> ShortsSyncStatus.ERROR
        }
}
