package com.shortscap.app.screenactivity

import com.shortscap.app.network.AppUsageRecordDto
import com.shortscap.app.sync.MonitoringSyncer
import com.shortscap.app.sync.SyncCoordinator
import com.shortscap.app.sync.SyncRecord
import com.shortscap.app.sync.utcDateKey

/**
 * ScreenActivityRepository — the persistence + sync boundary of the Screen
 * Activity domain.
 *
 * Responsibilities:
 *  - persist every closed foreground session to the durable local store
 *    (survives app restart / process death / force stop);
 *  - drain the local sessions to the backend through the EXISTING
 *    [SyncCoordinator] (single HTTP client, durable queue, idempotent
 *    per-day upsert at `POST /monitoring/app-usage/sync`) — never a second
 *    sync system and never a second backend API.
 *
 * Aggregation: closed sessions are summed per (package, UTC date) into one
 * daily summary each (duration + launch count), mirroring the backend's
 * app_usage row shape. Re-syncing the same day OVERWRITES the backend row
 * (idempotent upsert, last sync wins) — never a duplicate row and never a
 * double increment, even after a retry, app restart or offline queue drain.
 *
 * The repository does NOT own Shorts detection / counting / limits / HUD and
 * is fully independent of the Shorts Control domain: turning Screen Activity
 * off stops collection here without touching Shorts, and vice versa.
 */
class ScreenActivityRepository(
    private val store: ScreenActivityStore,
    /**
     * The enqueue seam into the existing sync layer — defaults to the app's
     * single [SyncCoordinator] queue (durable, idempotency-keyed). Tests
     * inject a local queue so they assert on the real record contract
     * without touching the shared coordinator's lazily-bound manager.
     */
    private val enqueueRecord: (SyncRecord) -> Boolean = SyncCoordinator::enqueue,
) {

    /** Persists one closed session to the durable local store. */
    fun recordSession(session: ScreenActivitySession) {
        store.recordSession(session)
    }

    /**
     * Aggregates every unsynced local session per (package, date) and
     * enqueues one daily app-usage summary per (package, date) through the
     * existing sync layer. Returns the number of records enqueued.
     *
     * Local sessions are NOT cleared here: the caller clears them only after
     * the sync layer confirms, matching the offline-first contract (nothing
     * pending is discarded). See [clearSynced].
     */
    fun drainToSync(deviceId: Int): Int {
        var enqueued = 0

        // Aggregate per (package, UTC date): sum duration + launch count.
        val aggregated = mutableMapOf<Pair<String, String>, ScreenActivityAggregate>()
        store.sessionSnapshot().forEach { session ->
            val date = utcDateKey(session.startedAtMillis)
            val key = session.packageName to date
            val current = aggregated[key]
            val delta = ScreenActivityAggregate(
                packageName = session.packageName,
                appName = session.appName,
                usageDate = date,
                durationSeconds = (session.durationMillis / 1000).coerceAtLeast(0),
                launchCount = 1,
            )
            aggregated[key] = if (current == null) delta else current.merge(delta)
        }

        aggregated.values.forEach { aggregate ->
            if (enqueueRecord(
                    MonitoringSyncer.usage(
                        AppUsageRecordDto(
                            deviceId = deviceId,
                            packageName = aggregate.packageName,
                            appName = aggregate.appName,
                            usageDate = aggregate.usageDate,
                            durationSeconds = aggregate.durationSeconds.toInt(),
                            launchCount = aggregate.launchCount,
                        ),
                    ),
                )
            ) {
                enqueued++
            }
        }
        return enqueued
    }

    /** Clears the local sessions (call ONLY after the sync layer confirmed). */
    fun clearSynced() {
        store.clear()
    }

    /** Unsynced local sessions (read-only view for debugging/tests). */
    fun pendingSessions(): List<ScreenActivitySession> = store.sessionSnapshot()
}
