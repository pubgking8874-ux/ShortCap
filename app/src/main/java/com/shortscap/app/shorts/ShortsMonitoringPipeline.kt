package com.shortscap.app.shorts

import com.shortscap.app.monitoring.MonitoringEventHub

/**
 * Connects the existing Android monitoring pipeline to the cross-platform
 * Shorts detection architecture (Phase 11B).
 *
 * Flow:
 *
 *   MonitoringEventHub
 *     -> ShortPlatformRegistry (package -> adapter)
 *     -> ShortPlatformAdapter.detect()
 *     -> ShortDetectionResult
 *     -> ShortUsageAggregator (3–5 second rule)
 *     -> ShortsBudgetTracker (ONE global budget across platforms)
 *     -> ShortsLocalStore (local usage/event records -> future sync layer)
 *
 * It is a PASSIVE subscriber: the accessibility service keeps observing
 * window-state events (package + window-class metadata only, no content) and
 * this pipeline classifies them. Monitoring, detection and aggregation stay
 * separate responsibilities — MonitoringService never owns Shorts counting,
 * and the detector never owns the counter.
 *
 * Honest limitations: with the current signal set, only the YouTube Shorts
 * surface is positively detected (via its window class); all other platforms
 * report UNKNOWN and are never counted. Each continuous foreground session
 * on a short-form surface counts as ONE Short (window-state events cannot
 * see individual swipes), so duration-based usage stays accurate while
 * per-short counts are session-level. The 3–5 second rule still applies:
 * a context left before ~2s is never counted.
 */
class ShortsMonitoringPipeline(
    private val registry: ShortPlatformRegistry = ShortPlatformRegistry,
    private val aggregator: ShortUsageAggregator = DefaultShortUsageAggregator(),
    private val budget: ShortsBudgetTracker = ShortsBudgetTracker(),
    private val store: ShortsLocalStore = InMemoryShortsLocalStore(),
    private val detect: (ShortDetectionSignals) -> ShortDetectionResult = { signals ->
        registry.detect(signals)
    },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : MonitoringEventHub.MonitoringEventListener {

    /** The window context currently in the foreground (surface-level). */
    private data class ActiveContext(
        val packageName: String,
        val activityClassName: String?,
        val startedAt: Long,
    )

    private var active: ActiveContext? = null

    companion object {
        /**
         * The shared app-wide pipeline, subscribed to [MonitoringEventHub]
         * exactly once. Idempotent.
         */
        private val shared = ShortsMonitoringPipeline()

        /** Subscribe the shared pipeline to monitoring events (idempotent). */
        fun start() {
            MonitoringEventHub.subscribe(shared)
        }
    }

    override fun onForegroundAppChanged(packageName: String, activityClassName: String?) {
        val now = nowMillis()
        val previous = active

        // Same surface still in the foreground (e.g. a repeated window-state
        // event): extend the context WITHOUT re-evaluating — each context is
        // finalized exactly once, preventing duplicate counting.
        if (previous != null &&
            previous.packageName == packageName &&
            previous.activityClassName == activityClassName
        ) {
            return
        }

        if (previous != null) {
            finalize(previous, now)
        }
        active = ActiveContext(
            packageName = packageName,
            activityClassName = activityClassName,
            startedAt = now,
        )
    }

    /**
     * Classify the context that just ended: detect -> aggregate -> budget ->
     * local record. Called exactly once per context (on transition).
     */
    private fun finalize(context: ActiveContext, now: Long) {
        val elapsed = (now - context.startedAt).coerceAtLeast(0L)
        val signals = ShortDetectionSignals(
            packageName = context.packageName,
            activityClassName = context.activityClassName,
            foregroundDurationMillis = elapsed,
        )
        val result = detect(signals)
        val update = aggregator.evaluate(result, signals)
        budget.apply(update)
        if (update.counted) {
            store.recordUsage(
                LocalShortsUsage(
                    platform = update.platform,
                    surface = update.surface,
                    detectionMethod = update.detectionMethod,
                    confidence = result.confidence,
                    occurredAt = context.startedAt,
                    durationMillis = update.durationMillis,
                    countDelta = update.countDelta,
                ),
            )
            store.recordEvent(
                LocalShortsEvent(
                    eventType = "SHORT_COUNTED",
                    platform = update.platform,
                    surface = update.surface,
                    detectionMethod = update.detectionMethod,
                    confidence = result.confidence,
                    occurredAt = context.startedAt,
                    durationMillis = update.durationMillis,
                ),
            )
        }
    }

    /** Current global budget totals (read-only view for the UI/debugging). */
    fun currentBudget(): ShortsBudgetTracker = budget

    /** Local records pending sync (read-only view). */
    fun localStore(): ShortsLocalStore = store

    /**
     * Phase 16 — drains the local Shorts store into the backend sync queue
     * (POST /shorts/usage/sync + /shorts/events) and returns the number of
     * records enqueued. Called by the app when the network is available; the
     * local records stay until a sync is confirmed (offline-first — nothing
     * is discarded). [deviceId] must reference the user's backend device.
     */
    fun drainToSync(deviceId: Int): Int =
        com.shortscap.app.sync.SyncCoordinator.drainShortsLocalStore(store, deviceId)
}
