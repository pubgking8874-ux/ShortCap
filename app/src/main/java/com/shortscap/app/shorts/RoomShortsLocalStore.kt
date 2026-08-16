package com.shortscap.app.shorts

import com.shortscap.app.db.ShortsEventEntity
import com.shortscap.app.db.ShortsStoreDao
import com.shortscap.app.db.ShortsUsageEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * P1-2 DURABLE ShortsLocalStore — Room-backed, survives process death.
 *
 * Same [ShortsLocalStore] contract as the in-memory store (so the
 * monitoring pipeline is untouched) but backed by [ShortsStoreDao]: Shorts
 * usage/events captured while the backend is unavailable are persisted to
 * SQLite immediately, so `drainShortsLocalStore` after an app restart finds
 * every record that was waiting for sync (Phase 11B sync boundary).
 *
 * Counting rules, detection and budget behavior are NOT touched — this only
 * makes the pending-sync boundary durable (P1-2 STEP 8).
 */
class RoomShortsLocalStore(
    private val dao: ShortsStoreDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShortsLocalStore {

    override fun recordUsage(usage: LocalShortsUsage) {
        ioBlocking { dao.insertUsage(usage.toEntity()) }
    }

    override fun recordEvent(event: LocalShortsEvent) {
        ioBlocking { dao.insertEvent(event.toEntity()) }
    }

    override fun usageSnapshot(): List<LocalShortsUsage> =
        ioBlocking { dao.usageSnapshot() }.map { it.toLocalUsage() }

    override fun eventSnapshot(): List<LocalShortsEvent> =
        ioBlocking { dao.eventSnapshot() }.map { it.toLocalEvent() }

    override fun clear() {
        ioBlocking {
            dao.clearUsage()
            dao.clearEvents()
        }
    }

    private fun <T> ioBlocking(block: suspend () -> T): T =
        runBlocking(ioDispatcher) { block() }
}

// ---------------------------------------------------------------------------
// Mapping between the domain model and the Room entities
// ---------------------------------------------------------------------------

private fun LocalShortsUsage.toEntity() = ShortsUsageEntity(
    platform = platform.name,
    surface = surface.name,
    detectionMethod = detectionMethod.name,
    confidence = confidence,
    occurredAt = occurredAt,
    durationMillis = durationMillis,
    countDelta = countDelta,
)

private fun LocalShortsEvent.toEntity() = ShortsEventEntity(
    eventType = eventType,
    platform = platform.name,
    surface = surface.name,
    detectionMethod = detectionMethod.name,
    confidence = confidence,
    occurredAt = occurredAt,
    durationMillis = durationMillis,
)

private fun ShortsUsageEntity.toLocalUsage() = LocalShortsUsage(
    platform = enumOrDefault(platform, ShortPlatform.UNKNOWN),
    surface = enumOrDefault(surface, ShortSurface.UNKNOWN),
    detectionMethod = enumOrDefault(detectionMethod, DetectionMethod.UNKNOWN),
    confidence = confidence,
    occurredAt = occurredAt,
    durationMillis = durationMillis,
    countDelta = countDelta,
)

private fun ShortsEventEntity.toLocalEvent() = LocalShortsEvent(
    eventType = eventType,
    platform = enumOrDefault(platform, ShortPlatform.UNKNOWN),
    surface = enumOrDefault(surface, ShortSurface.UNKNOWN),
    detectionMethod = enumOrDefault(detectionMethod, DetectionMethod.UNKNOWN),
    confidence = confidence,
    occurredAt = occurredAt,
    durationMillis = durationMillis,
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    try {
        enumValueOf(name)
    } catch (_: IllegalArgumentException) {
        fallback
    }
