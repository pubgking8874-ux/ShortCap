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
 * Evidence loss uses a grace period:
 *   WATCHING/QUALIFIED → EVIDENCE_LOST_PENDING → evidence returns → continue
 *                                             → grace expires → discard/count
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
        /**
         * Unique identifier for this Short session. Generated when a new
         * session starts (NO_SESSION → WATCHING). Used for correlating
         * lifecycle logs across scroll/content/count events.
         */
        val sessionId: String = "",
        val packageName: String,
        val activityClassName: String?,
        val startedAt: Long,
        val interactionCount: Int = 0,
        val contentEvidence: WindowContentEvidence = WindowContentEvidence(),
        val lastDetectionResult: ShortDetectionResult = ShortDetectionResult.UNKNOWN,
        val sessionState: SessionState = SessionState.NO_SESSION,
        val shortStartedAt: Long = 0L,
        val lastScrollAt: Long = 0L,
        /**
         * Timestamp of the last genuine user scroll that was processed (not
         * debounced or suppressed). Used to distinguish real user swipes from
         * residual accessibility/ViewPager scroll events.
         */
        val lastUserScrollAt: Long = 0L,
        /**
         * When evidence is temporarily lost during a UI transition, the session
         * is not destroyed immediately. Instead, evidenceLostAt records when the
         * loss was first detected. If evidence returns before the grace period
         * expires, the session continues. If the grace period expires, the
         * session is counted (QUALIFIED) or discarded (WATCHING).
         */
        val evidenceLostAt: Long = 0L,
        /**
         * The most recently ended session. Preserved so that if a new session
         * starts (content evidence) before the scroll handler processes the
         * previous session's scroll, we can still count the Short the user
         * was leaving. Reset when the previous session is counted or when
         * a new session starts.
         */
        val lastEndedSession: ActiveContext? = null,
    )

    private var active: ActiveContext? = null

    /** Monotonically increasing sequence number for each counted Short. */
    @Volatile private var countEventSeq = 0

    /** Monotonically increasing session ID counter for lifecycle diagnostics. */
    @Volatile private var sessionSeq = 0

    private val surfaceListeners = mutableListOf<ShortFormSurfaceListener>()
    private var lastBroadcastKey: String? = null
    private var lastBroadcastState: ShortFormSurfaceState? = null

    // ------------------------------------------------------------------
    // Count-change listeners — notified after every successful countShort()
    // so the HUD / Dashboard can update without waiting for the next
    // surface-state broadcast (which is deduplicated when the package,
    // activity, and startedAt haven't changed).
    // ------------------------------------------------------------------
    fun interface CountChangeListener {
        fun onShortCountChanged(newCount: Int, limitCount: Int)
    }
    private val countListeners = mutableListOf<CountChangeListener>()

    fun addCountListener(listener: CountChangeListener) {
        if (!countListeners.contains(listener)) countListeners.add(listener)
    }
    fun removeCountListener(listener: CountChangeListener) {
        countListeners.remove(listener)
    }

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

        /**
         * Grace period for evidence loss. When accessibility evidence is
         * temporarily lost during a UI transition (e.g. YouTube swipe),
         * the session is kept alive for this duration. If evidence returns
         * within the grace period, the session continues. If the grace
         * period expires, the session is counted (QUALIFIED) or discarded
         * (WATCHING).
         *
         * 1000ms is long enough to survive normal UI transitions but short
         * enough to not delay counting significantly.
         */
        const val EVIDENCE_LOST_GRACE_MILLIS = 1000L

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
            Log.i("SC_LIFECYCLE",
                "SC_LIFECYCLE ACCESSIBILITY_EVENT eventType=WINDOW_STATE_CHANGED " +
                    "pkg=$packageName className=$activityClassName " +
                    "decision=SAME_SURFACE_RETURN sessionId=${previous.sessionId} " +
                    "sessionState=${previous.sessionState} shortStartedAt=${previous.shortStartedAt}",
            )
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
                val elapsedSinceStart = now - previous.shortStartedAt
                Log.i("SC_SHORT",
                    "SC_SHORT onForegroundAppChanged CARRY_FORWARD pkg=$packageName " +
                        "cls=$activityClassName isOurOverlay=$isOurOverlay " +
                        "sessionState=${previous.sessionState} shortStartedAt=${previous.shortStartedAt}",
                )
                Log.i("SC_TRACE",
                    "SC_TRACE CARRY_FORWARD sessionId=${previous.shortStartedAt} " +
                        "pkg=$packageName sessionState=${previous.sessionState} " +
                        "elapsed=${elapsedSinceStart}ms timestamp=$now",
                )
                active = previous.copy(
                    activityClassName = activityClassName,
                    evidenceLostAt = 0L, // package/class change = definitive signal, clear pending loss
                )
                // Skip detect() for our own overlay — re-running detection with
                // "ComposeView" as the activity class would lose the Shorts
                // classification and trigger EVIDENCE_LOST. Content evidence
                // events will continue to call notifySurfaceState() normally.
                if (!isOurOverlay) {
                    notifySurfaceState()
                }
                return
            }

            // Platform change or leaving Shorts platform — destroy session
            if (previous.sessionState != SessionState.NO_SESSION) {
                val elapsedSinceStart = now - previous.shortStartedAt
                Log.i("SC_SHORT",
                    "SC_SHORT SHORT_SESSION_BOUNDARY pkg=${previous.packageName} " +
                        "reason=PLATFORM_CHANGE sessionState=${previous.sessionState}",
                )
                Log.i("SC_TRACE",
                    "SC_TRACE PLATFORM_CHANGE sessionId=${previous.shortStartedAt} " +
                        "pkg=${previous.packageName} sessionState=${previous.sessionState} " +
                        "elapsed=${elapsedSinceStart}ms timestamp=$now",
                )
                if (previous.sessionState == SessionState.QUALIFIED) {
                    Log.i("SC_TRACE",
                        "SC_TRACE COUNT_DECISION sessionId=${previous.shortStartedAt} " +
                            "sessionState=QUALIFIED elapsed=$elapsedSinceStart willCount=true " +
                            "reason=PLATFORM_CHANGE timestamp=$now",
                    )
                    Log.i("SC_LIFECYCLE",
                        "SC_LIFECYCLE COUNT_DECISION sessionId=${previous.sessionId} " +
                            "elapsed=${elapsedSinceStart}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                            "decision=COUNT reason=PLATFORM_CHANGE pkg=${previous.packageName}",
                    )
                    countShort(previous, now)
                } else {
                    Log.i("SC_SHORT",
                        "SC_SHORT DISCARDED pkg=${previous.packageName} " +
                            "reason=PLATFORM_CHANGE sessionState=${previous.sessionState}",
                    )
                    Log.i("SC_TRACE",
                        "SC_TRACE COUNT_DECISION sessionId=${previous.shortStartedAt} " +
                            "sessionState=${previous.sessionState} elapsed=$elapsedSinceStart willCount=false " +
                            "reason=PLATFORM_CHANGE_NOT_QUALIFIED timestamp=$now",
                    )
                    Log.i("SC_LIFECYCLE",
                        "SC_LIFECYCLE COUNT_DECISION sessionId=${previous.sessionId} " +
                            "elapsed=${elapsedSinceStart}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                            "decision=BELOW_MIN_TIME reason=PLATFORM_CHANGE_NOT_QUALIFIED " +
                            "sessionState=${previous.sessionState} pkg=${previous.packageName}",
                    )
                }
                Log.i("SC_LIFECYCLE",
                    "SC_LIFECYCLE SESSION_END sessionId=${previous.sessionId} " +
                        "reason=PLATFORM_CHANGE duration=${elapsedSinceStart}ms " +
                        "pkg=${previous.packageName} sessionState=${previous.sessionState}",
                )
            }
        }

        // ===== SC_LIFECYCLE: WINDOW_STATE_CHANGED — new surface detected =====
        Log.i("SC_LIFECYCLE",
            "SC_LIFECYCLE ACCESSIBILITY_EVENT eventType=WINDOW_STATE_CHANGED " +
                "pkg=$packageName className=$activityClassName " +
                "decision=NEW_SURFACE prevPkg=${previous?.packageName} " +
                "prevSession=${previous?.sessionState}",
        )

        Log.i("SC_SHORT",
            "SC_SHORT SHORT_SESSION_START pkg=$packageName cls=$activityClassName",
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

        val elapsedSinceStart = if (context.shortStartedAt > 0) now - context.shortStartedAt else 0L

        // ===== SC_LIFECYCLE: SCROLL_EVENT — every scroll with full context =====
        Log.i("SC_LIFECYCLE",
            "SC_LIFECYCLE SCROLL_EVENT sessionId=${context.sessionId} " +
                "eventTime=$now pkg=$packageName sessionState=${context.sessionState} " +
                "shortStartedAt=${context.shortStartedAt} lastScrollAt=${context.lastScrollAt} " +
                "lastUserScrollAt=${context.lastUserScrollAt} " +
                "elapsedSinceSessionStart=${elapsedSinceStart}ms " +
                "timeSinceLastScroll=${timeSinceLastScroll}ms " +
                "interactionCount=${context.interactionCount}",
        )

        Log.i("SC_SHORT",
            "SC_SHORT SCROLL_CHECK pkg=$packageName sessionState=${context.sessionState} " +
                "shortStartedAt=${context.shortStartedAt} lastScrollAt=${context.lastScrollAt} " +
                "timeSinceLastScroll=${timeSinceLastScroll}ms elapsedSinceStart=${elapsedSinceStart}ms",
        )
        Log.i("SC_TRACE",
            "SC_TRACE SCROLL_RECEIVED sessionId=${context.shortStartedAt} " +
                "pkg=$packageName sessionState=${context.sessionState} " +
                "elapsed=${elapsedSinceStart}ms timestamp=$now",
        )

        // ---- Global debounce: absorb rapid-fire scrolls within 500ms ----
        if (context.lastScrollAt > 0 && timeSinceLastScroll < SCROLL_DEBOUNCE_MILLIS) {
            Log.i("SC_LIFECYCLE",
                "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                    "elapsed=${timeSinceLastScroll}ms threshold=${SCROLL_DEBOUNCE_MILLIS}ms " +
                    "decision=DEBOUNCED sessionState=${context.sessionState} " +
                    "shortStartedAt=${context.shortStartedAt} pkg=$packageName",
            )
            Log.i("SC_SHORT",
                "SC_SHORT SCROLL_DEBOUNCED elapsed=${timeSinceLastScroll}ms " +
                    "sessionState=${context.sessionState} pkg=$packageName",
            )
            Log.i("SC_TRACE",
                "SC_TRACE SCROLL_DEBOUNCED sessionId=${context.shortStartedAt} " +
                    "elapsed=${timeSinceLastScroll}ms sessionState=${context.sessionState} " +
                    "timestamp=$now",
            )
            active = context.copy(interactionCount = context.interactionCount + 1)
            return
        }

        Log.i("SC_SHORT",
            "SC_SHORT SCROLL pkg=$packageName sessionState=${context.sessionState} " +
                "interactionCount=${context.interactionCount + 1}",
        )

        // ---- Session-start protection window ----
        // If the session just started and elapsed < SHORT_MIN_ENGAGEMENT_MILLIS,
        // this scroll may be a residual accessibility/ViewPager scroll from the
        // swipe that transitioned INTO this Short. HOWEVER, it may also be the
        // FIRST scroll that should count the PREVIOUS Short (if content evidence
        // created this new session before the scroll handler ran).
        //
        // Strategy: if current session is too new, check lastEndedSession.
        // If the previous session was eligible (QUALIFIED or WATCHING with
        // sufficient elapsed time), count it now. Otherwise ignore the scroll.
        if (context.sessionState == SessionState.WATCHING &&
            context.shortStartedAt > 0 &&
            elapsedSinceStart < SHORT_MIN_ENGAGEMENT_MILLIS
        ) {
            val prev = context.lastEndedSession
            val prevElapsed = if (prev != null && prev.shortStartedAt > 0) {
                now - prev.shortStartedAt
            } else 0L

            if (prev != null && prevElapsed >= SHORT_MIN_ENGAGEMENT_MILLIS) {
                // Previous Short was watched long enough — count it now.
                // This scroll belongs to the transition FROM the previous Short.
                Log.i("SC_LIFECYCLE",
                    "SC_LIFECYCLE COUNT_DECISION sessionId=${prev.sessionId} " +
                        "elapsed=${prevElapsed}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                        "decision=COUNT reason=USER_SCROLL_PREVIOUS " +
                        "prevSessionState=${prev.sessionState} pkg=$packageName",
                )
                Log.i("SC_SHORT",
                    "SC_SHORT COUNTING_PREVIOUS pkg=$packageName " +
                        "prevSessionId=${prev.sessionId} elapsed=$prevElapsed",
                )
                countShort(prev, now)
                // Clear the counted previous session
                active = context.copy(lastEndedSession = null)
                Log.i("SC_LIFECYCLE",
                    "SC_LIFECYCLE SESSION_END sessionId=${prev.sessionId} " +
                        "reason=COUNTED_ON_SCROLL duration=${prevElapsed}ms " +
                        "pkg=$packageName countedFrom=PROTECTION_WINDOW_FALLBACK",
                )
                // Current session continues — timer, state, interactionCount all preserved
                return
            }

            // No previous session to count, or it wasn't eligible.
            // Pure residual scroll — ignore it.
            Log.i("SC_LIFECYCLE",
                "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                    "elapsed=${elapsedSinceStart}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                    "decision=RESIDUAL_SCROLL_IGNORED sessionState=WATCHING " +
                    "pkg=$packageName hasPrevSession=${prev != null} " +
                    "prevElapsed=${prevElapsed}ms",
            )
            Log.i("SC_SHORT",
                "SC_SHORT RESIDUAL_SCROLL_IGNORED pkg=$packageName " +
                    "elapsed=${elapsedSinceStart}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms",
            )
            // Session continues unchanged — timer, state, interactionCount all preserved
            return
        }

        // Clear any pending evidence loss — scroll is a definitive session boundary
        val evidenceLostAt = 0L

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
                        "source=SCROLL_CHECK reason=USER_SCROLL",
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
                Log.i("SC_LIFECYCLE",
                    "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                        "elapsed=${elapsed}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                        "decision=COUNT reason=USER_SCROLL " +
                        "sessionState=QUALIFIED pkg=$packageName",
                )
                Log.i("SC_SHORT",
                    "SC_COUNT COUNTING pkg=$packageName shortStartedAt=${context.shortStartedAt} elapsed=$elapsed",
                )
                Log.i("SC_TRACE",
                    "SC_TRACE COUNT_DECISION sessionId=${context.shortStartedAt} " +
                        "sessionState=QUALIFIED elapsed=$elapsed willCount=true " +
                        "reason=SCROLL_CHECK timestamp=$now",
                )
                countShort(context, now)
            }
            SessionState.WATCHING -> {
                val elapsed = now - context.shortStartedAt
                Log.i("SC_LIFECYCLE",
                    "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                        "elapsed=${elapsed}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                        "decision=BELOW_MIN_TIME_IGNORED reason=USER_SCROLL_EARLY " +
                        "sessionState=WATCHING pkg=$packageName",
                )
                Log.i("SC_SHORT",
                    "SC_SHORT DISCARDED pkg=$packageName " +
                        "reason=BELOW_MIN_TIME elapsed=$elapsed",
                )
                Log.i("SC_TRACE",
                    "SC_TRACE COUNT_DECISION sessionId=${context.shortStartedAt} " +
                        "sessionState=WATCHING elapsed=$elapsed willCount=false " +
                        "reason=BELOW_MIN_TIME threshold=${SHORT_MIN_ENGAGEMENT_MILLIS} " +
                        "timestamp=$now",
                )
            }
            SessionState.NO_SESSION -> {
                Log.i("SC_LIFECYCLE",
                    "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                        "elapsed=0ms decision=SKIPPED sessionState=NO_SESSION pkg=$packageName",
                )
                Log.i("SC_TRACE",
                    "SC_TRACE COUNT_DECISION sessionId=${context.shortStartedAt} " +
                        "sessionState=NO_SESSION elapsed=0 willCount=false " +
                        "reason=NO_SESSION timestamp=$now",
                )
            }
        }

        // ===== SC_LIFECYCLE: SESSION_END — session destroyed by scroll =====
        val sessionDuration = if (context.shortStartedAt > 0) now - context.shortStartedAt else 0L
        val endReason = when (effectiveState) {
            SessionState.QUALIFIED -> "COUNTED_ON_SCROLL"
            SessionState.WATCHING -> "DISCARDED_ON_SCROLL"
            SessionState.NO_SESSION -> "NO_SESSION_SCROLLED"
        }
        Log.i("SC_LIFECYCLE",
            "SC_LIFECYCLE SESSION_END sessionId=${context.sessionId} " +
                "reason=$endReason duration=${sessionDuration}ms " +
                "pkg=$packageName sessionState=${context.sessionState} " +
                "shortStartedAt=${context.shortStartedAt} now=$now",
        )

        active = context.copy(
            interactionCount = context.interactionCount + 1,
            sessionState = SessionState.NO_SESSION,
            shortStartedAt = 0L,
            // Clear lastScrollAt so debounce does not carry over into the next
            // session. The session-start protection window (elapsed < 3000ms)
            // handles residual scrolls for the new session.
            lastScrollAt = 0L,
            lastUserScrollAt = now,
            evidenceLostAt = evidenceLostAt,
        )
    }

    // =========================================================================
    // Content evidence
    // =========================================================================

    override fun onForegroundContentObserved(packageName: String, evidence: WindowContentEvidence) {
        val context = active ?: return
        if (context.packageName == packageName) {
            // ===== SC_LIFECYCLE: ACCESSIBILITY_EVENT — content evidence received =====
            val elapsedSinceStart = if (context.shortStartedAt > 0) nowMillis() - context.shortStartedAt else 0L
            Log.i("SC_LIFECYCLE",
                "SC_LIFECYCLE ACCESSIBILITY_EVENT eventType=WINDOW_CONTENT_CHANGED " +
                    "pkg=$packageName sessionId=${context.sessionId} " +
                    "sessionState=${context.sessionState} shortStartedAt=${context.shortStartedAt} " +
                    "elapsedSinceSessionStart=${elapsedSinceStart}ms " +
                    "classes=${evidence.nodeClasses.size} ids=${evidence.nodeViewIds.size}",
            )
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
                "evidenceIds=${context.contentEvidence.nodeViewIds.size} " +
                "evidenceLostAt=${context.evidenceLostAt}",
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

        // ===== PLATFORM_OBSERVATION: diagnostic logging for all platforms =====
        val observationClasses = context.contentEvidence.nodeClasses.take(30).joinToString(",")
        val observationIds = context.contentEvidence.nodeViewIds.take(30).joinToString(",")
        val observationDescs = context.contentEvidence.nodeContentDescriptions.take(20).joinToString(",")
        val rejectionReason = if (!shortsDetected) {
            if (!result.isShortForm) "isShortForm=false" else "confidence=${result.confidence} < threshold"
        } else null
        Log.i("SC_PLATFORM_OBS",
            "SC_SHORT PLATFORM_OBSERVATION pkg=${context.packageName} " +
                "activity=${context.activityClassName} platform=${result.platform} " +
                "surface=${result.surface} confidence=${result.confidence} " +
                "isShortForm=${result.isShortForm} interactionCount=${context.interactionCount} " +
                "classes=[${observationClasses}] ids=[${observationIds}] " +
                "descs=[${observationDescs}] reason=$rejectionReason"
        )
        // ===== end PLATFORM_OBSERVATION =====

        val now = nowMillis()
        var newState = context.sessionState
        var newShortStartedAt = context.shortStartedAt
        var newEvidenceLostAt = context.evidenceLostAt
        var newSessionId = context.sessionId
        var newLastEndedSession: ActiveContext? = context.lastEndedSession

        if (shortsDetected) {
            // === Evidence IS present ===

            // If evidence was previously lost and has now returned, clear the
            // pending loss. The session continues — this might be the same Short
            // (UI tree updated) or a new Short (user swiped). We cannot
            // distinguish these without content IDs, so we continue the session.
            if (newEvidenceLostAt > 0) {
                Log.i("SC_SHORT",
                    "SC_SHORT CONFIRMED_SURFACE_RETURN pkg=${context.packageName} " +
                        "sessionState=${context.sessionState} " +
                        "evidenceLostDuration=${now - newEvidenceLostAt}ms",
                )
                newEvidenceLostAt = 0L
            }

            when (context.sessionState) {
                SessionState.NO_SESSION -> {
                    newShortStartedAt = now
                    newState = SessionState.WATCHING
                    newSessionId = "SID-${++sessionSeq}"
                    // Store the ended session so scroll handler can count it
                    // even if a new session already started via content evidence.
                    if (context.shortStartedAt > 0 &&
                        (context.sessionState == SessionState.WATCHING ||
                            context.sessionState == SessionState.QUALIFIED)
                    ) {
                        newLastEndedSession = context
                    }
                    // ===== SC_LIFECYCLE: SESSION_START — new Short timer begins =====
                    Log.i("SC_LIFECYCLE",
                        "SC_LIFECYCLE SESSION_START sessionId=$newSessionId " +
                            "pkg=${context.packageName} surface=${result.surface} " +
                            "startedAt=$now startedAfterScrollAt=${context.lastScrollAt} " +
                            "confidence=${result.confidence} " +
                            "platform=${result.platform} " +
                            "interactionCount=${context.interactionCount}",
                    )
                    Log.i("SC_SHORT",
                        "SC_SHORT NEW_SHORT_DETECTED pkg=${context.packageName} " +
                            "platform=${result.platform} surface=${result.surface}",
                    )
                    Log.i("SC_SHORT",
                        "SC_SHORT TIMER_STARTED pkg=${context.packageName} " +
                            "shortStartedAt=$now",
                    )
                    Log.i("SC_TRACE",
                        "SC_TRACE SHORT_DETECTED sessionId=$now " +
                            "pkg=${context.packageName} platform=${result.platform} " +
                            "surface=${result.surface} timestamp=$now",
                    )
                    Log.i("SC_TRACE",
                        "SC_TRACE TIMER_STARTED sessionId=$now " +
                            "startedAt=$now timestamp=$now",
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
                        Log.i("SC_TRACE",
                            "SC_TRACE QUALIFIED sessionId=${context.shortStartedAt} " +
                                "elapsed=$elapsed threshold=${SHORT_MIN_ENGAGEMENT_MILLIS} " +
                                "source=NOTIFY_CHECK timestamp=$now",
                        )
                    }
                }
                SessionState.QUALIFIED -> {
                    // Session is already QUALIFIED and evidence is still present.
                    // This means the user is still on a Shorts surface. Do NOT
                    // count here — wait for a scroll event (primary boundary) or
                    // evidence loss with grace period expiry (fallback boundary).
                    // Counting on every content evidence update would count the
                    // same Short multiple times.
                }
            }
        } else {
            // === Evidence is LOST ===

            when (context.sessionState) {
                SessionState.WATCHING -> {
                    if (newEvidenceLostAt == 0L) {
                        // First evidence loss → start grace period
                        newEvidenceLostAt = now
                        Log.i("SC_SHORT",
                            "SC_SHORT TEMPORARY_EVIDENCE_LOST pkg=${context.packageName} " +
                                "sessionState=WATCHING elapsed=${now - context.shortStartedAt}ms " +
                                "gracePeriod=${EVIDENCE_LOST_GRACE_MILLIS}ms",
                        )
                        Log.i("SC_TRACE",
                            "SC_TRACE EVIDENCE_LOST sessionId=${context.shortStartedAt} " +
                                "sessionState=WATCHING elapsed=${now - context.shortStartedAt}ms " +
                                "gracePeriod=${EVIDENCE_LOST_GRACE_MILLIS}ms timestamp=$now",
                        )
                        // Don't destroy session — keep alive during grace period
                    } else if (now - newEvidenceLostAt > EVIDENCE_LOST_GRACE_MILLIS) {
                        // Grace period expired → genuine exit from Shorts
                        val totalElapsed = now - context.shortStartedAt
                        Log.i("SC_LIFECYCLE",
                            "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                                "elapsed=${totalElapsed}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                                "decision=SKIPPED reason=EVIDENCE_LOST_EXPIRED " +
                                "sessionState=WATCHING pkg=${context.packageName}",
                        )
                        Log.i("SC_LIFECYCLE",
                            "SC_LIFECYCLE SESSION_END sessionId=${context.sessionId} " +
                                "reason=EVIDENCE_LOST_DISCARDED duration=${totalElapsed}ms " +
                                "pkg=${context.packageName} evidenceLostFor=${now - newEvidenceLostAt}ms",
                        )
                        Log.i("SC_SHORT",
                            "SC_SHORT CONFIRMED_SURFACE_EXIT pkg=${context.packageName} " +
                                "sessionState=WATCHING elapsed=$totalElapsed " +
                                "evidenceLostFor=${now - newEvidenceLostAt}ms",
                        )
                        Log.i("SC_SHORT",
                            "SC_SHORT DISCARDED pkg=${context.packageName} " +
                                "reason=EVIDENCE_LOST_EXPIRED elapsed=$totalElapsed",
                        )
                        Log.i("SC_TRACE",
                            "SC_TRACE EVIDENCE_LOST_EXPIRED sessionId=${context.shortStartedAt} " +
                                "sessionState=WATCHING elapsed=$totalElapsed " +
                                "evidenceLostFor=${now - newEvidenceLostAt}ms action=DISCARD " +
                                "timestamp=$now",
                        )
                        newState = SessionState.NO_SESSION
                        newShortStartedAt = 0L
                        newEvidenceLostAt = 0L
                    } else {
                        // Grace period not expired → continue waiting
                        Log.i("SC_SHORT",
                            "SC_SHORT EVIDENCE_LOST_PENDING pkg=${context.packageName} " +
                                "sessionState=WATCHING " +
                                "evidenceLostFor=${now - newEvidenceLostAt}ms " +
                                "remaining=${EVIDENCE_LOST_GRACE_MILLIS - (now - newEvidenceLostAt)}ms",
                        )
                    }
                }
                SessionState.QUALIFIED -> {
                    if (newEvidenceLostAt == 0L) {
                        // First evidence loss → start grace period
                        newEvidenceLostAt = now
                        Log.i("SC_SHORT",
                            "SC_SHORT TEMPORARY_EVIDENCE_LOST pkg=${context.packageName} " +
                                "sessionState=QUALIFIED elapsed=${now - context.shortStartedAt}ms " +
                                "gracePeriod=${EVIDENCE_LOST_GRACE_MILLIS}ms",
                        )
                        Log.i("SC_TRACE",
                            "SC_TRACE EVIDENCE_LOST sessionId=${context.shortStartedAt} " +
                                "sessionState=QUALIFIED elapsed=${now - context.shortStartedAt}ms " +
                                "gracePeriod=${EVIDENCE_LOST_GRACE_MILLIS}ms timestamp=$now",
                        )
                        // Don't destroy session — keep alive during grace period
                    } else if (now - newEvidenceLostAt > EVIDENCE_LOST_GRACE_MILLIS) {
                        // Grace period expired → count the session
                        val totalElapsed = now - context.shortStartedAt
                        Log.i("SC_LIFECYCLE",
                            "SC_LIFECYCLE COUNT_DECISION sessionId=${context.sessionId} " +
                                "elapsed=${totalElapsed}ms threshold=${SHORT_MIN_ENGAGEMENT_MILLIS}ms " +
                                "decision=COUNT reason=EVIDENCE_LOST_EXPIRED " +
                                "sessionState=QUALIFIED pkg=${context.packageName}",
                        )
                        Log.i("SC_LIFECYCLE",
                            "SC_LIFECYCLE SESSION_END sessionId=${context.sessionId} " +
                                "reason=COUNTED_ON_EVIDENCE_LOST duration=${totalElapsed}ms " +
                                "pkg=${context.packageName} evidenceLostFor=${now - newEvidenceLostAt}ms",
                        )
                        Log.i("SC_SHORT",
                            "SC_SHORT CONFIRMED_SURFACE_EXIT pkg=${context.packageName} " +
                                "sessionState=QUALIFIED elapsed=$totalElapsed " +
                                "evidenceLostFor=${now - newEvidenceLostAt}ms",
                        )
                        Log.i("SC_SHORT",
                            "SC_SHORT SCROLL pkg=${context.packageName} " +
                                "sessionState=QUALIFIED reason=EVIDENCE_LOST_EXPIRED",
                        )
                        Log.i("SC_TRACE",
                            "SC_TRACE COUNT_DECISION sessionId=${context.shortStartedAt} " +
                                "sessionState=QUALIFIED elapsed=$totalElapsed willCount=true " +
                                "reason=EVIDENCE_LOST_EXPIRED timestamp=$now",
                        )
                        countShort(context, now)
                        newState = SessionState.NO_SESSION
                        newShortStartedAt = 0L
                        newEvidenceLostAt = 0L
                    } else {
                        // Grace period not expired → continue waiting
                        Log.i("SC_SHORT",
                            "SC_SHORT EVIDENCE_LOST_PENDING pkg=${context.packageName} " +
                                "sessionState=QUALIFIED " +
                                "evidenceLostFor=${now - newEvidenceLostAt}ms " +
                                "remaining=${EVIDENCE_LOST_GRACE_MILLIS - (now - newEvidenceLostAt)}ms",
                        )
                    }
                }
                else -> { /* NO_SESSION */ }
            }
        }

        // Broadcast state: keep the HUD visible during evidence loss grace
        // period to prevent visual flickering. Only hide when evidence loss
        // is confirmed (grace expired) or there was never a Shorts session.
        val broadcastState = if (shortsDetected) {
            ShortFormSurfaceState(
                platform = result.platform,
                surface = result.surface,
                confidence = result.confidence,
            )
        } else if (newEvidenceLostAt > 0 &&
            (context.sessionState == SessionState.WATCHING ||
                context.sessionState == SessionState.QUALIFIED)
        ) {
            // Evidence lost but session still active (grace period) → keep HUD visible
            lastBroadcastState
        } else {
            null
        }

        // Preserve the strongest confirmed detection result. Once a session
        // is confirmed as Shorts (e.g. SNAPCHAT_SPOTLIGHT / YOUTUBE_SHORTS),
        // transient evidence loss during swipe transitions must not overwrite
        // the confirmed result with UNKNOWN. This ensures countShort() uses
        // the correct surface and confidence.
        val preservedResult = if (result.isShortForm &&
            result.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD
        ) {
            result  // new result is strong enough, use it
        } else if (context.lastDetectionResult.isShortForm &&
            context.lastDetectionResult.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD
        ) {
            context.lastDetectionResult  // keep the old confirmed result
        } else {
            result  // neither is confirmed, use new result
        }

        active = context.copy(
            sessionId = newSessionId,
            lastDetectionResult = preservedResult,
            sessionState = newState,
            shortStartedAt = newShortStartedAt,
            evidenceLostAt = newEvidenceLostAt,
            lastEndedSession = newLastEndedSession,
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
        val id = ++countEventSeq
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

        val countBefore = controlEngine?.currentState()?.currentCount ?: 0
        Log.i("SC_RT", "SC_RT COUNT_EVENT id=$id")
        Log.i("SC_RT", "SC_RT COUNT_SHORT_ENTER id=$id countBefore=$countBefore candidateKey=${result.platform.name}:${result.surface.name}:${sessionStart} sessionStart=$sessionStart")

        Log.i("SC_SHORT",
            "SC_SHORT COUNTED pkg=${context.packageName} " +
                "platform=${result.platform} surface=${result.surface} " +
                "duration=${elapsed}ms confidence=${result.confidence}",
        )

        Log.i("SC_RT", "SC_RT ENGINE_CALL id=$id countBefore=$countBefore candidateKey=${result.platform.name}:${result.surface.name}:${sessionStart}")
        controlEngine?.onShortCounted(
            candidateKey = "${result.platform.name}:${result.surface.name}:${sessionStart}",
            occurredAt = sessionStart,
            durationMillis = elapsed,
            now = now,
        )

        // --- COUNT_PERSISTED diagnostic ---
        val engineState = controlEngine?.currentState()
        if (engineState != null) {
            Log.i("SC_RT", "SC_RT ENGINE_RESULT id=$id countAfter=${engineState.currentCount} limit=${engineState.limitCount} status=${engineState.status}")
            Log.i("SC_COUNT",
                "SC_COUNT COUNT_PERSISTED platform=${result.platform} " +
                    "surface=${result.surface} count=${engineState.currentCount} " +
                    "limit=${engineState.limitCount} reached=${engineState.limitReached} " +
                    "remaining=${engineState.remainingCount}",
            )

            // --- COUNT_STATE_UPDATED: push to UI listeners ---
            Log.i("SC_RT", "SC_RT LISTENER_NOTIFY id=$id count=${engineState.currentCount} listenerCount=${countListeners.size}")
            countListeners.forEach {
                it.onShortCountChanged(engineState.currentCount, engineState.limitCount)
            }
            Log.i("SC_RT", "SC_RT LISTENER_NOTIFY_COMPLETE id=$id count=${engineState.currentCount}")
            Log.i("SC_COUNT",
                "SC_COUNT COUNT_STATE_UPDATED count=${engineState.currentCount} " +
                    "limit=${engineState.limitCount}",
            )
        } else {
            Log.w("SC_RT", "SC_RT ENGINE_RESULT id=$id controlEngine=NULL — count NOT persisted")
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
