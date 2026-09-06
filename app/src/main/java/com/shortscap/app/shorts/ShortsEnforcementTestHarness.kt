package com.shortscap.app.shorts

import android.content.Context
import android.util.Log
import com.shortscap.app.BuildConfig
import com.shortscap.app.hud.ShortsHudTriggerEngine

/**
 * Phase 4A.1 / 4A.6 — DEBUG-only controlled enforcement test mode.
 *
 * THE LOOP (no 24-hour wait, ever):
 *
 *   START TEST (limit = 5)
 *     → real Shorts are detected/validated/counted by the EXISTING pipeline
 *     → every validated production Short-count event → testCount++
 *     → process restart at any point: the DEBUG test state
 *       (testModeEnabled/testCount/testLimit/testEnforced) is restored from a
 *       dedicated DEBUG-only SharedPreferences namespace
 *       (shorts_debug_enforcement_test) and the count listener is
 *       re-registered automatically — START TEST once, the running test
 *       survives process death
 *     → testCount >= testLimit (once, edge-guarded)
 *     → testEnforced=true (persisted BEFORE the trigger) → TEST_LIMIT_REACHED
 *     → REAL ShortsRestrictionEngine enforcement action
 *     → REAL ShortsRestrictionOverlayManager (full-screen touch-blocking overlay)
 *     → RESET TEST / STOP TEST end the run (persisted disabled) → the panel
 *       returns to START TEST for the next run
 *
 * WHAT IS TEST-SPECIFIC (all in-memory + a dedicated DEBUG-only preference
 * namespace, all BuildConfig.DEBUG-gated):
 *   - testModeEnabled / testLimit / testCount / testEnforced (this object,
 *     mirrored to SharedPreferences so an in-flight test survives process
 *     recreation)
 *   - the DEBUG enforcement seam on [ShortsRestrictionEngine]
 *     ([ShortsRestrictionEngine.debugTriggerEnforcement]) — it bypasses ONLY
 *     the production limitReached DECISION INPUT, never the action
 *   - the DEBUG test indicator in the existing HUD (ShortsHudUiState fields)
 *   - reset/stop state
 *
 * PROCESS-RESTART SAFETY (Phase 4A.6):
 *   The harness state is per-process memory, so process death alone would
 *   silently end a running test. [attach] is called from
 *   ShortsCapApplication.onCreate BEFORE normal monitoring begins: in DEBUG
 *   builds it restores the persisted test state and, when the restored state
 *   says the test is enabled, re-registers the count listener with the
 *   current [ShortsMonitoringPipeline.sharedInstance]. Registration is
 *   idempotent (pipeline dedup + a local guard), so repeated attach / START
 *   TEST calls never double-register. Release builds are inert: nothing is
 *   persisted, restored or registered, and [startTest] keeps being rejected.
 *
 * WHAT IS REAL:
 *   - Short detection / validation / qualification / dedup / persistence
 *     (untouched — the harness only RECEIVES the same validated count event
 *     the production enforcement seam receives, via the EXISTING
 *     [ShortsMonitoringPipeline.CountChangeListener]; arbitrary accessibility
 *     callbacks are never counted)
 *   - the enforcement action: the same [ShortsRestrictionOverlayManager]
 *     show() path production uses — same overlay, same WindowManager flags,
 *     same touch blocking, same permission gate
 *
 * PRODUCTION ISOLATION:
 *   - Nothing is ever written to the production Room cycle, daily count,
 *     daily reset, limit, settings, history or auth state. The harness has
 *     no reference to any store; its SharedPreferences namespace is
 *     exclusively for DEBUG test state.
 *   - The production limitReached decision path is untouched; the test seam
 *     is a separate DEBUG entry point that never runs in release builds.
 *   - When test mode is OFF the HUD and enforcement behave exactly as before.
 */
object ShortsEnforcementTestHarness {

    /** Dedicated DEBUG-only log tag for the test path. */
    const val TAG = "SHORTS_TEST"

    // ------------------------------------------------------------------
    // DEBUG test-state persistence — dedicated namespace, never production
    // ------------------------------------------------------------------

    /** Dedicated SharedPreferences namespace for the DEBUG enforcement test. */
    private const val PREFS_NAME = "shorts_debug_enforcement_test"
    private const val KEY_ENABLED = "test_mode_enabled"
    private const val KEY_COUNT = "test_count"
    private const val KEY_LIMIT = "test_limit"
    private const val KEY_ENFORCED = "test_enforced"

    /** Application context used for the DEBUG-only persistence (set by [attach]). */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var testModeEnabled = false

    @Volatile
    private var testLimit = 0

    /** Independent DEBUG test counter — increments per validated Short event. */
    @Volatile
    private var testCount = 0

    /** Edge gate: prevents duplicate enforcement from duplicate callbacks. */
    @Volatile
    private var testEnforced = false

    /** Local idempotency guard — the listener is added/removed at most once. */
    @Volatile
    private var listenerRegistered = false

    private val countListener = ShortsMonitoringPipeline.CountChangeListener { _, _ ->
        onValidatedShortEvent()
    }

    // ------------------------------------------------------------------
    // Lifecycle — process attach / restore / START TEST / RESET TEST / STOP TEST
    // ------------------------------------------------------------------

    /**
     * Phase 4A.6 — called from ShortsCapApplication.onCreate at process start,
     * BEFORE normal Shorts monitoring begins. In DEBUG builds this restores
     * any in-flight controlled test (persisted in the dedicated DEBUG-only
     * preference namespace) and, when the restored state has the test enabled,
     * re-registers the count listener automatically — the user does NOT need
     * to press START TEST again after a process restart. Idempotent: repeated
     * attach calls re-read the same persisted state and never duplicate the
     * registration. Release builds are inert.
     */
    fun attach(context: Context) {
        if (!BuildConfig.DEBUG) return
        appContext = context.applicationContext
        restoreTestState()
    }

    /**
     * START TEST: enables DEBUG test mode, resets ONLY test state
     * (count=0, enforced=false), sets the test limit, persists the enabled
     * state and registers the validated-event listener (idempotent — repeated
     * START TEST never duplicates the registration, the pipeline's
     * addCountListener already deduplicates and the local guard enforces it).
     * Production state is never read for writing, never modified, never reset.
     */
    fun startTest(limit: Int): Boolean {
        if (!BuildConfig.DEBUG) {
            // Observable in release builds (log only) so a release-build
            // install can be distinguished from "START TEST never pressed".
            Log.w(TAG, "TEST_START_REJECTED reason=NOT_DEBUG_BUILD")
            return false
        }
        if (limit <= 0) {
            log("TEST_START_REJECTED limit=$limit reason=NOT_POSITIVE")
            return false
        }
        testLimit = limit
        testCount = 0
        testEnforced = false
        testModeEnabled = true
        saveState()
        registerListener()
        log("TEST_START limit=$testLimit count=0")
        // Phase 4A.2 — DEBUG-only diagnostic trace.
        Log.i(TAG, "SHORTS_DIAG: TEST_STARTED limit=$testLimit")
        publishTestStateToHud()
        return true
    }

    /**
     * RESET TEST: clears the DEBUG test run (mode → off, count → 0,
     * enforced → false; the configured test limit is preserved as the
     * existing/default UI value), persists the disabled state and unregisters
     * the listener. The panel returns to START TEST for the next run.
     * Production currentCount, dailyShortsCount, limit, cycleStartedAt,
     * cycleExpiresAt, Room data and settings are NOT touched — the harness
     * holds no reference to any of them.
     */
    fun resetTest() {
        if (!BuildConfig.DEBUG) return
        testModeEnabled = false
        testCount = 0
        testEnforced = false
        // testLimit intentionally preserved (existing/default UI value).
        saveState()
        unregisterListener()
        log("TEST_RESET")
        log("TEST_COUNT=0")
        Log.i(TAG, "SHORTS_DIAG: TEST_RESET count=0 enforced=false")
        publishTestStateToHud()
    }

    /**
     * STOP TEST: disables test mode, persists the disabled state and
     * unregisters the listener. All production state untouched; normal
     * production behavior continues (the HUD indicator clears).
     */
    fun stopTest() {
        if (!BuildConfig.DEBUG) return
        testModeEnabled = false
        testCount = 0
        testEnforced = false
        saveState()
        unregisterListener()
        log("TEST_STOP")
        publishTestStateToHud()
    }

    // ------------------------------------------------------------------
    // Persistence + registration internals (DEBUG-only)
    // ------------------------------------------------------------------

    /**
     * Persists the current DEBUG test state to the dedicated preference
     * namespace. No-op when the process has not attached a context yet or in
     * release builds (this method is only reachable from DEBUG-gated callers).
     */
    private fun saveState() {
        if (!BuildConfig.DEBUG) return
        val context = appContext ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, testModeEnabled)
            .putInt(KEY_COUNT, testCount)
            .putInt(KEY_LIMIT, testLimit)
            .putBoolean(KEY_ENFORCED, testEnforced)
            .apply()
    }

    /**
     * Restores the persisted DEBUG test state (process-start defaults when no
     * state exists) and reconciles registration: an enabled test re-registers
     * its count listener with the current pipeline instance; a disabled test
     * ensures no stale registration survives.
     */
    private fun restoreTestState() {
        if (!BuildConfig.DEBUG) return
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        testModeEnabled = prefs.getBoolean(KEY_ENABLED, false)
        testCount = prefs.getInt(KEY_COUNT, 0)
        testLimit = prefs.getInt(KEY_LIMIT, 0)
        testEnforced = prefs.getBoolean(KEY_ENFORCED, false)
        if (testModeEnabled) {
            registerListener()
        } else {
            unregisterListener()
        }
        Log.i(TAG,
            "TEST_STATE_RESTORED testModeEnabled=$testModeEnabled " +
                "testCount=$testCount testLimit=$testLimit testEnforced=$testEnforced")
    }

    /** Idempotent listener registration with the shared pipeline. */
    private fun registerListener() {
        if (listenerRegistered) return
        ShortsMonitoringPipeline.sharedInstance.addCountListener(countListener)
        listenerRegistered = true
        Log.i(TAG, "TEST_LISTENER_REGISTERED")
    }

    /** Idempotent listener removal from the shared pipeline. */
    private fun unregisterListener() {
        if (!listenerRegistered) return
        ShortsMonitoringPipeline.sharedInstance.removeCountListener(countListener)
        listenerRegistered = false
        Log.i(TAG, "TEST_LISTENER_UNREGISTERED")
    }

    // ------------------------------------------------------------------
    // Counting — one call per VALIDATED production Short-count event
    // ------------------------------------------------------------------

    /**
     * Called after every successful production countShort() + persistence
     * (the pipeline notifies listeners ONLY for fully validated Shorts —
     * detection, 3s qualification and dedup already happened upstream).
     * Independent DEBUG counter: testCount++ per validated event, persisted
     * on every increment so an in-flight run survives process death.
     * `internal` (rather than private) so unit tests can drive the exact
     * validated-event path directly.
     */
    internal fun onValidatedShortEvent() {
        // TEST_COUNT_EVENT_RECEIVED is emitted BEFORE the mode guard so a
        // device run can prove whether validated count events reach the
        // harness listener at all (the Phase 4A.4 execution boundary).
        if (!BuildConfig.DEBUG) return
        Log.i(TAG,
            "TEST_COUNT_EVENT_RECEIVED testModeEnabled=$testModeEnabled " +
                "testCount=$testCount testLimit=$testLimit testEnforced=$testEnforced")
        if (!testModeEnabled) return
        testCount++
        Log.i(TAG,
            "TEST_COUNT_INCREMENT testCount=$testCount testLimit=$testLimit")
        log("TEST_SHORT_ACCEPTED count=$testCount limit=$testLimit")
        // Phase 4A.2 — DEBUG-only diagnostic trace (log only, no behavior
        // change): marks each validated event reaching the harness.
        Log.i(TAG, "SHORTS_DIAG: VALIDATED_SHORT_EVENT testCount=$testCount testLimit=$testLimit")
        publishTestStateToHud()
        saveState()

        if (testCount >= testLimit && !testEnforced) {
            // Edge gate: duplicate callbacks after the crossing never
            // re-trigger enforcement (testEnforced latches until RESET).
            testEnforced = true
            // Phase 4A.3 ordering preserved: testEnforced=true is persisted
            // BEFORE the one-shot enforcement trigger so no callback can
            // observe a stale false — even across a process restart.
            saveState()
            Log.i(TAG,
                "TEST_ENFORCED_SET testEnforced=true testCount=$testCount testLimit=$testLimit")
            log("TEST_LIMIT_REACHED count=$testCount limit=$testLimit")
            log("TEST_ENFORCEMENT_TRIGGERED")
            Log.i(TAG, "SHORTS_DIAG: TEST_LIMIT_REACHED testCount=$testCount testLimit=$testLimit")
            triggerRealEnforcement()
            publishTestStateToHud()
        }
    }

    /**
     * Invokes the REAL enforcement engine through the DEBUG seam, providing
     * the TEST enforcement condition. The seam bypasses ONLY the production
     * limitReached decision input — the enforcement ACTION executed is the
     * exact production path (ShortsRestrictionOverlayManager.show → real
     * full-screen touch-blocking overlay).
     */
    private fun triggerRealEnforcement() {
        val result = ShortsRestrictionEngine.debugTriggerEnforcement(testCondition = true)
        log("TEST_ENFORCEMENT_RESULT result=$result")
    }

    // ------------------------------------------------------------------
    // HUD integration — the EXISTING HUD renders the test progress
    // ------------------------------------------------------------------

    /**
     * Publishes the test state into the EXISTING HUD's shared uiState
     * ([ShortsHudUiState] DEBUG test fields). The HUD shows the clearly
     * identifiable indicator ("TEST MODE · n / limit") only while test mode
     * is active; with test mode OFF all test fields are false/zero and the
     * production HUD is byte-identical to before. Values are written as
     * Compose snapshot state (thread-safe), so the overlay recomposes live.
     */
    private fun publishTestStateToHud() {
        if (!BuildConfig.DEBUG) return
        ShortsHudTriggerEngine.uiState.testModeActive = testModeEnabled
        ShortsHudTriggerEngine.uiState.testCount = testCount
        ShortsHudTriggerEngine.uiState.testLimit = testLimit
    }

    private fun log(event: String) {
        Log.i(TAG, event)
    }

    // ------------------------------------------------------------------
    // Persistent test-enforcement condition (Phase 4A.3)
    // ------------------------------------------------------------------

    /**
     * Phase 4A.3 — read-only DEBUG query consumed by the production
     * enforcement evaluation paths ([ShortsRestrictionEngine]). True only
     * while the controlled test remains enforced:
     *
     *   BuildConfig.DEBUG AND testModeEnabled AND testEnforced
     *
     * When true, the engine's surface/count evaluations treat the test
     * condition as `limitReached = true` (via a local state copy) so the
     * real overlay shown at the test-limit crossing SURVIVES subsequent
     * re-evaluations instead of being hidden by the untouched production
     * state (the Phase 4A.2 root cause). The engine holds a READ-ONLY
     * dependency on this query — it never writes test state. Lifecycle:
     * set by the test-limit crossing (before the one-shot trigger fires,
     * so no callback can observe a stale false), cleared by RESET TEST and
     * STOP TEST, and always false in release builds.
     */
    fun isTestEnforced(): Boolean =
        BuildConfig.DEBUG && testModeEnabled && testEnforced

    /** Snapshot for the debug UI / diagnostics (zeroed when inert). */
    fun snapshot(): ShortsEnforcementTestSnapshot =
        ShortsEnforcementTestSnapshot(
            available = BuildConfig.DEBUG,
            enabled = testModeEnabled && BuildConfig.DEBUG,
            testLimit = if (BuildConfig.DEBUG) testLimit else 0,
            testCount = if (BuildConfig.DEBUG) testCount else 0,
            enforced = if (BuildConfig.DEBUG) testEnforced else false,
        )

    // ------------------------------------------------------------------
    // Test seams (internal — same module, never part of the app API)
    // ------------------------------------------------------------------

    /** Whether the count listener is currently registered (idempotency guard). */
    internal val isListenerRegistered: Boolean
        get() = listenerRegistered

    /**
     * TEST-ONLY seam: simulates the in-memory half of a process restart —
     * fields return to their class-load defaults and the listener is dropped
     * from the pipeline — while leaving the persisted DEBUG test state
     * untouched, so a subsequent [attach] exercises the real restore +
     * re-registration path.
     */
    internal fun simulateProcessRestartForTest() {
        unregisterListener()
        testModeEnabled = false
        testCount = 0
        testLimit = 0
        testEnforced = false
    }
}

/** Read-only snapshot of the DEBUG test-mode state. */
data class ShortsEnforcementTestSnapshot(
    /** True only in DEBUG builds. */
    val available: Boolean,
    /** True while test mode is running. */
    val enabled: Boolean,
    /** The developer-set test limit (independent of the production limit). */
    val testLimit: Int,
    /** Independent DEBUG test counter (per validated Short event). */
    val testCount: Int,
    /** True once the test limit crossing has been enforced (edge latch). */
    val enforced: Boolean,
)
