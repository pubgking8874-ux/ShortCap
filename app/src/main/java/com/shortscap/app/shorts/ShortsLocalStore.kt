package com.shortscap.app.shorts

/**
 * One counted Shorts usage record kept locally until it is synced.
 *
 * Platform/surface/detection-method/confidence are preserved so future
 * reports can break usage down per platform without re-deriving it — the
 * cross-platform identity is never thrown away after counting.
 */
data class LocalShortsUsage(
    val platform: ShortPlatform,
    val surface: ShortSurface,
    val detectionMethod: DetectionMethod,
    val confidence: Float,
    /** Epoch millis when the short-form context started. */
    val occurredAt: Long,
    val durationMillis: Long,
    val countDelta: Int,
)

/** One local Shorts event (a counted short), mirroring the backend event shape. */
data class LocalShortsEvent(
    val eventType: String,
    val platform: ShortPlatform,
    val surface: ShortSurface,
    val detectionMethod: DetectionMethod,
    val confidence: Float,
    val occurredAt: Long,
    val durationMillis: Long,
)

/**
 * The LOCAL persistence/sync boundary for Shorts usage.
 *
 * The detector → aggregator pipeline writes here; a future sync layer drains
 * [usageSnapshot] / [eventSnapshot] to the backend (Phase 10 `shorts_usage` /
 * `shorts_events` APIs). The detector itself NEVER talks to the backend.
 * Swap the in-memory implementation for Room/DataStore persistence later
 * without touching the pipeline.
 */
interface ShortsLocalStore {

    fun recordUsage(usage: LocalShortsUsage)

    fun recordEvent(event: LocalShortsEvent)

    /** Snapshot of unsynced usage records (in insertion order). */
    fun usageSnapshot(): List<LocalShortsUsage>

    /** Snapshot of unsynced events (in insertion order). */
    fun eventSnapshot(): List<LocalShortsEvent>

    /** Clear local records (e.g. after a successful sync). */
    fun clear()
}

/**
 * In-memory local store. Thread-safe enough for the single accessibility
 * thread the hub dispatches on. Replace with persistent storage later.
 */
class InMemoryShortsLocalStore : ShortsLocalStore {

    private val usage = mutableListOf<LocalShortsUsage>()
    private val events = mutableListOf<LocalShortsEvent>()

    override fun recordUsage(usage: LocalShortsUsage) {
        this.usage += usage
    }

    override fun recordEvent(event: LocalShortsEvent) {
        this.events += event
    }

    override fun usageSnapshot(): List<LocalShortsUsage> = usage.toList()

    override fun eventSnapshot(): List<LocalShortsEvent> = events.toList()

    override fun clear() {
        usage.clear()
        events.clear()
    }
}
