package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.MonitoringEventHub
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * Shorts monitoring pipeline with a simplified per-Short session state machine.
 *
 * States:
 *   NO_SESSION → WATCHING → QUALIFIED → (counted on scroll) → NO_SESSION
 *                                 ↑
 *                          (scroll before qualify → discard → NO_SESSION)
 *
 * The fundamental unit is ONE INDIVIDUAL SHORT/REEL.
 * The timer belongs to the individual Short, not the application session.
 * One scroll = one Short boundary. Content changes are harmless.
 */
class ShortsMonitoringPipeline(
    private val registry: ShortPlatformRegistry = ShortPlatformRegistry,
    private val aggregator: ShortUsageAggregator = DefaultShortUsageAggregator(),
    private val budget: ShortsBudgetTracker = ShortsBudgetTracker(),
    private val store: ShortsLocalStore = InMemoryShortsLocalStore(),
    private val controlEngine: ShortsControlEngine? = null,
    private val detect: (ShortDetectionSignals) -> ShortDetectionResult = { signals ->
        registry.detect(signals)
    },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : MonitoringEventHub.MonitoringEventListener {

    private enum class SessionState {
        NO_SESSION,
        WATCHING,
        QUALIFIED,
    }

    private data class ActiveContext(
        val packageName: String,
        val activityClassName: String?,
        val startedAt: Long,
        val interactionCount: Int = 0,
        val contentEvidence: WindowContentEvidence = WindowContentEvidence(),
        val lastDetectionResult: ShortDetectionResult = ShortDetectionResult.UNKNOWN,
        val sessionState: SessionState = SessionState.NO_SESSION,
        val shortStartedAt: Long = 0L,
        val lastScrollAt: Long = 0L,
    )

    private var active: ActiveContext? = null

    private val surfaceListeners = mutableListOf<ShortFormSurfaceListener>()
    private var lastBroadcastKey: String? = null
    private var lastBroadcastState: ShortFormSurfaceState? = null

    companion object {
        @Volatile
        private var sharedStore: ShortsLocalStore = InMemoryShortsLocalStore()
        @Volatile
        private var sharedControlEngine: ShortsControlEngine? = null

        fun installDurableStore(store: ShortsLocalStore) { sharedStore = store }
        fun installControlEngine(engine: ShortsControlEngine) { sharedControlEngine = engine }

        private val shared by lazy {
            ShortsMonitoringPipeline(store = sharedStore, controlEngine = sharedControlEngine)
        }
        val sharedInstance: ShortsMonitoringPipeline get() = shared
        fun start() { MonitoringEventHub.subscribe(shared) }

        private const val SCROLL_DEBOUNCE_MILLIS = 500L

        /** Our own package — overlay foreground events from this package are ignored. */
        private const val OUR_PACKAGE_NAME = "com.shortscap.app"
    }

    fun addSurfaceListener(listener: ShortFormSurfaceListener) {
        if (!surfaceListeners.contains(listener)) surfaceListeners.add(listener)
    }
    fun removeSurfaceListener(listener: ShortFormSurfaceListener) {
        surfaceListeners.remove(listener)
    }

    // =========================================================================
    // Foreground app change
    // =========================================================================

    override fun onForegroundAppChanged(packageName: String, activityClassName: String?) {
        val now = nowMillis()
        val previous = active

        Log.i("SC_SHORT",
            "SC_SHORT onForegroundAppChanged pkg=$packageName cls=$activityClassName " +
                "prevPkg=${previous?.packageName} prevCls=${previous?.activityClassName} " +
                "prevSession=${previous?.sessionState} prevShortStarted=${previous?.shortStartedAt}",
        )

        if (previous != null &&
            previous.packageName == packageName &&
            previous.activityClassName == activityClassName
        ) {
            Log.i("SC_SHORT", "SC_SHORT onForegroundAppChanged SAME_SURFACE_RETURN")
            return
        }

        if (previous != null) {
            val samePackage = previous.packageName == packageName
            val isShortsPlatform = registry.adapterFor(packageName).platform != ShortPlatform.UNKNOWN
            val sessionInProgress = previous.sessionState == SessionState.WATCHING ||
                previous.sessionState == SessionState.QUALIFIED

            Log.i("SC_SHORT",
                "SC_SHORT onForegroundAppChanged samePkg=$samePackage " +
                    "isShortsPlatform=$isShortsPlatform sessionInProgress=$sessionInProgress " +
                    "prevSession=${previous.sessionState}",
            )

            // Our own HUD overlay (TYPE_APPLICATION_OVERLAY) causes the accessibility
            // service to report com.shortscap.app as the foreground package. This must
            // NOT be treated as the user leaving the Shorts platform.
            val isOurOverlay = packageName == OUR_PACKAGE_NAME &&
                activityClassName?.contains("ComposeView") == true

            if (sessionInProgress && (samePackage && isShortsPlatform || isOurOverlay)) {
                Log.i("SC_SHORT",
                    "SC_SHORT onForegroundAppChanged CARRY_FORWARD pkg=$packageName " +
                        "cls=$activityClassName isOurOverlay=$isOurOverlay " +
                        "sessionState=${previous.sessionState} shortStartedAt=${previous.shortStartedAt}",
                )
                active = previous.copy(activityClassName = activityClassName)
                // Skip detect() for our own overlay — re-running detection with
                // "ComposeView" as the activity class would lose the Shorts
                // classification and trigger EVIDENCE_LOST. Content evidence
                // events will continue to call notifySurfaceState() normally.
                if (!isOurOverlay) {
                    notifySurfaceState()
                }
                return
            }

            if (previous.sessionState != SessionState.NO_SESSION) {
                Log.i("SC_SHORT",
                    "SC_SHORT DISCARDED pkg=${previous.packageName} " +
                        "reason=LEFT_PLATFORM sessionState=${previous.sessionState}",
                )
            }
        }

        Log.i("SC_SHORT",
            "SC_SHORT onForegroundAppChanged NEW_CONTEXT pkg=$packageName cls=$activityClassName",
        )
        active = ActiveContext(
            packageName = packageName,
            activityClassName = activityClassName,
            startedAt = now,
        )
        notifySurfaceState()
    }

    // =========================================================================
    // Scroll
    // =========================================================================

    override fun onForegroundScrolled(packageName: String) {
        val context = active ?: return
        if (context.packageName != packageName) return

        val now = nowMillis()
        val timeSinceLastScroll = now - context.lastScrollAt

        Log.i("SC_SHORT",
            "SC_SHORT SCROLL_CHECK pkg=$packageName sessionState=${context.sessionState} " +
                "shortStartedAt=${context.shortStartedAt} lastScrollAt=${context.lastScrollAt} " +
                "timeSinceLastScroll=${timeSinceLastScroll}ms",
        )

        if (context.lastScrollAt > 0 && timeSinceLastScroll < SCROLL_DEBOUNCE_MILLIS) {
            Log.i("SC_SHORT",
                "SC_SHORT SCROLL_DEBOUNCED elapsed=${timeSinceLastScroll}ms " +
                    "sessionState=${context.sessionState} pkg=$packageName",
            )
            active = context.copy(interactionCount = context.interactionCount + 1)
            return
        }

        Log.i("SC_SHORT",
            "SC_SHORT SCROLL pkg=$packageName sessionState=${context.sessionState} " +
                "interactionCount=${context.interactionCount + 1}",
        )

        // Qualification check on scroll
        var effectiveState = context.sessionState
        if (effectiveState == SessionState.WATCHING && context.shortStartedAt > 0) {
            val elapsed = now - context.shortStartedAt
            Log.i("SC_SHORT",
                "SC_SHORT ELAPSED elapsed=${elapsed}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                    "pkg=$packageName shortStartedAt=${context.shortStartedAt} now=$now",
            )
            if (elapsed >= SHORT_MIN_ENGAGEMENT_MILLIS) {
                effectiveState = SessionState.QUALIFIED
                Log.i("SC_SHORT",
                    "SC_SHORT QUALIFIED pkg=$packageName " +
                        "shortStartedAt=${context.shortStartedAt} elapsed=$elapsed " +
                        "source=SCROLL_CHECK",
                )
            } else {
                Log.i("SC_SHORT",
                    "SC_SHORT QUALIFICATION_CHECK result=NOT_QUALIFIED elapsed=$elapsed " +
                        "threshold=${SHORT_MIN_ENGAGEMENT_MILLIS} pkg=$packageName",
                )
            }
        } else {
            Log.i("SC_SHORT",
                "SC_SHORT QUALIFICATION_CHECK result=SKIPPED state=$effectiveState " +
                    "shortStartedAt=${context.shortStartedAt} pkg=$packageName",
            )
        }

        when (effectiveState) {
            SessionState.QUALIFIED -> {
                val elapsed = now - context.shortStartedAt
                Log.i("SC_SHORT",
                    "SC_COUNT COUNTING pkg=$packageName shortStartedAt=${context.shortStartedAt} elapsed=$elapsed",
                )
                countShort(context, now)
            }
            SessionState.WATCHING -> {
                val elapsed = now - context.shortStartedAt
                Log.i("SC_SHORT",
                    "SC_SHORT DISCARDED pkg=$packageName " +
                        "reason=BELOW_MIN_TIME elapsed=$elapsed",
                )
            }
            SessionState.NO_SESSION -> { /* nothing */ }
        }

        active = context.copy(
            interactionCount = context.interactionCount + 1,
            sessionState = SessionState.NO_SESSION,
            shortStartedAt = 0L,
            lastScrollAt = now,
        )
    }

    // =========================================================================
    // Content evidence
    // =========================================================================

    override fun onForegroundContentObserved(packageName: String, evidence: WindowContentEvidence) {
        val context = active ?: return
        if (context.packageName == packageName) {
            Log.i("SC_SHORT",
                "SC_SHORT onForegroundContentObserved pkg=$packageName " +
                    "sessionState=${context.sessionState} shortStartedAt=${context.shortStartedAt} " +
                    "classes=${evidence.nodeClasses.size} ids=${evidence.nodeViewIds.size}",
            )
            active = context.copy(contentEvidence = evidence)
            notifySurfaceState()
        }
    }

    // =========================================================================
    // Detection + state machine
    // =========================================================================

    private fun notifySurfaceState() {
        val context = active ?: return

        Log.i("SC_SHORT",
            "SC_SHORT notifySurfaceState pkg=${context.packageName} " +
                "sessionState=${context.sessionState} shortStartedAt=${context.shortStartedAt} " +
                "evidenceClasses=${context.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${context.contentEvidence.nodeViewIds.size}",
        )

        val result = detect(
            ShortDetectionSignals(
                packageName = context.packageName,
                activityClassName = context.activityClassName,
                foregroundDurationMillis = 0L,
                interactionCount = context.interactionCount,
                contentEvidence = context.contentEvidence,
            )
        )

        val shortsDetected = result.isShortForm &&
            result.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD

        val now = nowMillis()
        var newState = context.sessionState
        var newShortStartedAt = context.shortStartedAt

        if (shortsDetected) {
            when (context.sessionState) {
                SessionState.NO_SESSION -> {
                    newShortStartedAt = now
                    newState = SessionState.WATCHING
                    Log.i("SC_SHORT",
                        "SC_SHORT DETECTED pkg=${context.packageName} " +
                            "platform=${result.platform} surface=${result.surface}",
                    )
                    Log.i("SC_SHORT",
                        "SC_SHORT TIMER_STARTED pkg=${context.packageName} " +
                            "shortStartedAt=$now",
                    )
                }
                SessionState.WATCHING -> {
                    val elapsed = now - context.shortStartedAt
                    Log.i("SC_SHORT",
                        "SC_SHORT notifySurfaceState WATCHING_CHECK elapsed=$elapsed " +
                            "threshold=${SHORT_MIN_ENGAGEMENT_MILLIS} pkg=${context.packageName}",
                    )
                    if (elapsed >= SHORT_MIN_ENGAGEMENT_MILLIS &&
                        newState != SessionState.QUALIFIED
                    ) {
                        newState = SessionState.QUALIFIED
                        Log.i("SC_SHORT",
                            "SC_SHORT QUALIFIED pkg=${context.packageName} " +
                                "shortStartedAt=${context.shortStartedAt} elapsed=$elapsed " +
                                "source=NOTIFY_CHECK",
                        )
                    }
                }
                SessionState.QUALIFIED -> { /* no-op */ }
            }
        } else {
            when (context.sessionState) {
                SessionState.WATCHING -> {
                    val elapsed = now - context.shortStartedAt
                    Log.i("SC_SHORT",
                        "SC_SHORT DISCARDED pkg=${context.packageName} " +
                            "reason=EVIDENCE_LOST elapsed=$elapsed",
                    )
                    newState = SessionState.NO_SESSION
                    newShortStartedAt = 0L
                }
                SessionState.QUALIFIED -> {
                    Log.i("SC_SHORT",
                        "SC_SHORT SCROLL pkg=${context.packageName} " +
                            "sessionState=QUALIFIED reason=EVIDENCE_LOST",
                    )
                    countShort(context, now)
                    newState = SessionState.NO_SESSION
                    newShortStartedAt = 0L
                }
                else -> { /* NO_SESSION */ }
            }
        }

        val broadcastState = if (shortsDetected) {
            ShortFormSurfaceState(
                platform = result.platform,
                surface = result.surface,
                confidence = result.confidence,
            )
        } else null

        active = context.copy(
            lastDetectionResult = result,
            sessionState = newState,
            shortStartedAt = newShortStartedAt,
        )

        val key = "${context.packageName}|${context.activityClassName}|${context.startedAt}"
        if (broadcastState == lastBroadcastState && key == lastBroadcastKey) return
        lastBroadcastKey = key
        lastBroadcastState = broadcastState
        surfaceListeners.toList().forEach { it.onShortFormSurfaceChanged(broadcastState) }
    }

    // =========================================================================
    // Count
    // =========================================================================

    private fun countShort(context: ActiveContext, now: Long) {
        val sessionStart = if (context.shortStartedAt > 0) context.shortStartedAt else context.startedAt
        val elapsed = (now - sessionStart).coerceAtLeast(0L)

        // Use the already-confirmed detection result from the active session.
        // Re-running detect() at count time can return UNKNOWN / low confidence
        // if content evidence is stale or temporarily missing (e.g. during scroll
        // transition). The session was already confirmed as Shorts when it
        // transitioned to WATCHING — preserve that metadata.
        val result = if (context.lastDetectionResult.isShortForm &&
            context.lastDetectionResult.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD
        ) {
            context.lastDetectionResult
        } else {
            // Fallback: only re-detect if no confirmed result was ever stored
            detect(
                ShortDetectionSignals(
                    packageName = context.packageName,
                    activityClassName = context.activityClassName,
                    foregroundDurationMillis = elapsed,
                    interactionCount = context.interactionCount,
                    contentEvidence = context.contentEvidence,
                ),
            )
        }

        Log.i("SC_SHORT",
            "SC_SHORT COUNTED pkg=${context.packageName} " +
                "platform=${result.platform} surface=${result.surface} " +
                "duration=${elapsed}ms confidence=${result.confidence}",
        )

        controlEngine?.onShortCounted(
            candidateKey = "${result.platform.name}:${result.surface.name}:${sessionStart}",
            occurredAt = sessionStart,
            durationMillis = elapsed,
            now = now,
        )

        val engineState = controlEngine?.currentState()
        if (engineState != null) {
            Log.i("SC_SHORT",
                "SC_SHORT LIMIT_CHECK count=${engineState.currentCount} " +
                    "limit=${engineState.limitCount} reached=${engineState.limitReached} " +
                    "remaining=${engineState.remainingCount}",
            )
        }

        store.recordUsage(
            LocalShortsUsage(
                platform = result.platform,
                surface = result.surface,
                detectionMethod = result.detectionMethod,
                confidence = result.confidence,
                occurredAt = sessionStart,
                durationMillis = elapsed,
                countDelta = 1,
            ),
        )
        store.recordEvent(
            LocalShortsEvent(
                eventType = "SHORT_COUNTED",
                platform = result.platform,
                surface = result.surface,
                detectionMethod = result.detectionMethod,
                confidence = result.confidence,
                occurredAt = sessionStart,
                durationMillis = elapsed,
            ),
        )
    }

    fun currentBudget(): ShortsBudgetTracker = budget
    fun localStore(): ShortsLocalStore = store

    fun drainToSync(deviceId: Int): Int =
        com.shortscap.app.sync.SyncCoordinator.drainShortsLocalStore(store, deviceId)
}
