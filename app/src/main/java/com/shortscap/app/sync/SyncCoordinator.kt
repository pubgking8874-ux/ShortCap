package com.shortscap.app.sync

import com.shortscap.app.network.BackendApi
import com.shortscap.app.network.ShortsUsageRecordDto
import com.shortscap.app.network.WebEventDto
import com.shortscap.app.shorts.ShortPlatform
import com.shortscap.app.shorts.ShortSurface
import com.shortscap.app.shorts.ShortsLocalStore
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * SyncCoordinator — the app-wide Phase 16 wiring point.
 *
 * Owns the single [SyncQueue], [SyncManager] (with the [RoutingDispatcher]),
 * [ReadClients] and the per-domain record builders, so feature code just
 * calls e.g. `SyncCoordinator.enqueue(ShortsSyncer.usage(...))` or
 * `SyncCoordinator.drainShortsLocalStore(store, deviceId)` — no feature
 * builds its own HTTP client, retry loop or JSON.
 *
 * Android remains the real-time authority (study timer, monitoring, Shorts
 * detection, web blocking); the backend persists history. The coordinator
 * never runs timers — it only pushes already-collected data.
 */
object SyncCoordinator {

    /** Default API instance — the single real HTTP client. */
    val api: BackendApi by lazy { com.shortscap.app.network.HttpBackendApi() }

    val queue: SyncQueue = InMemorySyncQueue()

    val manager: SyncManager by lazy {
        SyncManager(
            queue = queue,
            dispatcher = RoutingDispatcher(api),
        )
    }

    val reads: ReadClients by lazy { ReadClients(api) }

    // ------------------------------------------------------------------
    // Enqueue helpers
    // ------------------------------------------------------------------

    /** Enqueues [record] (deduped by its idempotency key). */
    fun enqueue(record: SyncRecord): Boolean = manager.enqueue(record)

    /** Pushes every outstanding record now (returns per-record outcomes). */
    suspend fun syncNow(): List<SyncResult> = manager.syncNow()

    /**
     * Drains a [ShortsLocalStore] into Shorts usage/event sync records
     * (Phase 11B sync boundary — Phase 16 §10). Usage is aggregated per
     * (device, date, platform, surface); events map to SHORT_COUNTED.
     * Returns the number of records enqueued.
     */
    fun drainShortsLocalStore(store: ShortsLocalStore, deviceId: Int): Int {
        var enqueued = 0

        // Aggregate usage: sum duration/count per (date, platform, surface).
        val aggregated = mutableMapOf<Triple<String, ShortPlatform, ShortSurface>, Pair<Int, Long>>()
        store.usageSnapshot().forEach { usage ->
            val date = utcDateKey(usage.occurredAt)
            val key = Triple(date, usage.platform, usage.surface)
            val (count, millis) = aggregated.getOrElse(key) { 0 to 0L }
            aggregated[key] = (count + usage.countDelta) to (millis + usage.durationMillis)
        }
        aggregated.forEach { (key, totals) ->
            val (date, platform, surface) = key
            if (enqueue(
                    ShortsSyncer.usage(
                        ShortsUsageRecordDto(
                            deviceId = deviceId,
                            usageDate = date,
                            shortsCount = totals.first,
                            durationSeconds = (totals.second / 1000).toInt(),
                            platform = platform.name,
                            surface = surface.name,
                        )
                    )
                )
            ) {
                enqueued++
            }
        }

        store.eventSnapshot().forEach { event ->
            if (enqueue(
                    ShortsSyncer.event(
                        com.shortscap.app.network.ShortsEventDto(
                            deviceId = deviceId,
                            eventType = event.eventType,
                            occurredAt = Instant.ofEpochMilli(event.occurredAt).toString(),
                            durationSeconds = (event.durationMillis / 1000).toInt(),
                            metadataJson = mapOf(
                                "platform" to event.platform.name,
                                "surface" to event.surface.name,
                                "detection_method" to event.detectionMethod.name,
                                "confidence" to event.confidence,
                            ),
                        )
                    )
                )
            ) {
                enqueued++
            }
        }

        // Successfully queued -> the local records are now pending sync.
        // (The store is cleared by the caller only after a confirmed sync,
        // matching the offline-first contract.)
        return enqueued
    }

    /**
     * Enqueues one website event (BLOCK_ATTEMPT / BLOCKED / UNBLOCKED) —
     * Phase 16 §11. The Android web/blocking engine remains the real-time
     * authority; the backend only persists history.
     */
    fun enqueueWebEvent(event: WebEventDto): Boolean =
        enqueue(WebSyncer.event(event))
}

/**
 * UTC calendar date key (YYYY-MM-DD) for [epochMillis] — the bucket key used
 * when aggregating Shorts usage per (date, platform, surface).
 *
 * P1-1 compatibility fix: uses [Instant.atZone] + [ZonedDateTime.toLocalDate]
 * (java.time, available since API 26) instead of `LocalDate.ofInstant(...)`
 * which only exists from API 34 — the old call would throw
 * `NoSuchMethodError` on API 26–33 devices. Date/time behavior is unchanged:
 * the bucket is always the UTC calendar date of the occurrence, exactly as
 * before, so sync period computation and the midnight boundary are identical.
 */
internal fun utcDateKey(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
