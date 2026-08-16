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
    /**
     * P1-5: the authoritative control engine fed with every VALID Short
     * (the aggregator already applied the 3–5 second rule). The engine owns
     * the 24-hour count/limit/warning/expiry lifecycle and persists it;
     * detection and counting stay separate. Null keeps the pipeline
     * standalone for tests.
     */
    private val controlEngine: ShortsControlEngine? = null,
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

    /** Surface-state listeners (e.g. the Shorts HUD — presentation only). */
    private val surfaceListeners = mutableListOf<ShortFormSurfaceListener>()

    companion object {
        /**
         * The store the shared pipeline records into. P1-2: defaults to the
         * in-memory store; [ShortsCapApp] installs the durable Room-backed
         * store at app start so pending Shorts usage/events survive process
         * death and are drained after restart.
         */
        @Volatile
        private var sharedStore: ShortsLocalStore = InMemoryShortsLocalStore()

        /**
         * P1-5: the app-wide control engine installed at startup. The shared
         * pipeline feeds every valid Short to it so the 24-hour cycle is the
         * authoritative count for the HUD and Short Control page.
         */
        @Volatile
        private var sharedControlEngine: ShortsControlEngine? = null

        /** Installs the durable store before the shared pipeline is first used. */
        fun installDurableStore(store: ShortsLocalStore) {
            sharedStore = store
        }

        /** P1-5: installs the authoritative control engine at app start. */
        fun installControlEngine(engine: ShortsControlEngine) {
            sharedControlEngine = engine
        }

        /**
         * The shared app-wide pipeline, subscribed to [MonitoringEventHub]
         * exactly once. Idempotent. Created lazily so the durable store
         * installed at app start is picked up.
         */
        private val shared by lazy {
            ShortsMonitoringPipeline(store = sharedStore, controlEngine = sharedControlEngine)
        }

        /**
         * The shared app-wide pipeline instance — the Shorts HUD (and any
         * other presentation layer) subscribes to its surface-state
         * notifications so it consumes the EXISTING detection results and
         * never detects Shorts itself.
         */
        val sharedInstance: ShortsMonitoringPipeline get() = shared

        /** Subscribe the shared pipeline to monitoring events (idempotent). */
        fun start() {
            MonitoringEventHub.subscribe(shared)
        }
    }

    /**
     * Registers [listener] for active short-form surface changes.
     * Notified with a [ShortFormSurfaceState] when the foreground context is
     * positively detected as short-form, and with `null` whenever the
     * foreground context is not (or no longer) short-form.
     */
    fun addSurfaceListener(listener: ShortFormSurfaceListener) {
        if (!surfaceListeners.contains(listener)) surfaceListeners.add(listener)
    }

    /** Removes a previously registered [listener]. */
    fun removeSurfaceListener(listener: ShortFormSurfaceListener) {
        surfaceListeners.remove(listener)
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
        notifySurfaceState()
    }

    /**
     * Broadcasts the CURRENT foreground context's short-form status to
     * surface listeners. Detection only — the 3–5 second counting rule is
     * applied separately by the aggregator on finalize; the HUD's visibility
     * follows the detection result exactly (isShortForm with sufficient
     * confidence), never the raw package list.
     */
    private fun notifySurfaceState() {
        val context = active ?: return
        val result = detect(
            ShortDetectionSignals(
                packageName = context.packageName,
                activityClassName = context.activityClassName,
                foregroundDurationMillis = 0L,
            )
        )
        val state = if (result.isShortForm && result.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            ShortFormSurfaceState(
                platform = result.platform,
                surface = result.surface,
                confidence = result.confidence,
            )
        } else {
            null
        }
        surfaceListeners.toList().forEach { it.onShortFormSurfaceChanged(state) }
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
            // P1-5: the authoritative cycle consumes every valid Short. The
            // candidate key is the context identity (package+class+start) so
            // the engine can dedupe repeated callbacks; the 3–5s rule was
            // already applied by the aggregator above.
            controlEngine?.onShortCounted(
                candidateKey = "${result.platform.name}:${result.surface.name}:${context.startedAt}",
                occurredAt = context.startedAt,
                durationMillis = update.durationMillis,
                now = now,
            )
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
