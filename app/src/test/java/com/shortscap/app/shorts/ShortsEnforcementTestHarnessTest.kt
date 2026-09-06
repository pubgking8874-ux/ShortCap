package com.shortscap.app.shorts

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Phase 4A.6 — DEBUG enforcement test harness process-restart safety.
 *
 * Verifies (Robolectric + real SharedPreferences) that the DEBUG test state
 * (testModeEnabled / testCount / testLimit / testEnforced) is persisted to a
 * dedicated DEBUG-only namespace, restored on process attach, that the count
 * listener is re-registered automatically + idempotently when the restored
 * state has the test enabled, that RESET/STOP clear + persist the disabled
 * state, and that no production enforcement state is ever touched.
 *
 * A "process restart" is simulated with the harness's internal
 * [ShortsEnforcementTestHarness.simulateProcessRestartForTest] seam, which
 * drops the in-memory half of the state (exactly what process death does)
 * while leaving the persisted half intact, so a following
 * [ShortsEnforcementTestHarness.attach] exercises the real restore path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native SQLite: the manifest Application (ShortsCapApplication) opens the
// Room database during onCreate, and Robolectric's legacy SQLite binds each
// connection to a single thread, breaking Room's multi-threaded pool.
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ShortsEnforcementTestHarnessTest {

    /** Must mirror the harness's dedicated namespace exactly. */
    private fun prefs(): SharedPreferences =
        context.getSharedPreferences("shorts_debug_enforcement_test", Context.MODE_PRIVATE)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Fresh baseline: no persisted DEBUG test state + memory reconciled.
        prefs().edit().clear().commit()
        ShortsEnforcementTestHarness.attach(context)
        assertFalse(ShortsEnforcementTestHarness.snapshot().enabled)
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)
    }

    @After
    fun tearDown() {
        // Leave a clean static state for other test classes in this JVM.
        ShortsEnforcementTestHarness.stopTest()
        prefs().edit().clear().commit()
    }

    // ------------------------------------------------------------------
    // State persistence
    // ------------------------------------------------------------------

    @Test
    fun `start test saves enabled state with limit and zero count`() {
        assertTrue(ShortsEnforcementTestHarness.startTest(5))

        val s = ShortsEnforcementTestHarness.snapshot()
        assertTrue(s.enabled)
        assertEquals(0, s.testCount)
        assertEquals(5, s.testLimit)
        assertFalse(s.enforced)

        // Persisted to the dedicated namespace.
        assertTrue(prefs().getBoolean("test_mode_enabled", false))
        assertEquals(0, prefs().getInt("test_count", -1))
        assertEquals(5, prefs().getInt("test_limit", -1))
        assertFalse(prefs().getBoolean("test_enforced", true))
    }

    @Test
    fun `start test rejects non-positive limit without enabling`() {
        assertFalse(ShortsEnforcementTestHarness.startTest(0))
        assertFalse(ShortsEnforcementTestHarness.startTest(-3))
        assertFalse(ShortsEnforcementTestHarness.snapshot().enabled)
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)
    }

    @Test
    fun `restored state enables test mode and preserves count and limit`() {
        ShortsEnforcementTestHarness.startTest(5)
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        assertEquals(3, ShortsEnforcementTestHarness.snapshot().testCount)

        // Process restart: in-memory state wiped, persisted state survives.
        ShortsEnforcementTestHarness.simulateProcessRestartForTest()
        assertFalse(ShortsEnforcementTestHarness.snapshot().enabled)
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)

        ShortsEnforcementTestHarness.attach(context)
        val s = ShortsEnforcementTestHarness.snapshot()
        assertTrue(s.enabled)
        assertEquals(3, s.testCount)
        assertEquals(5, s.testLimit)
        assertFalse(s.enforced)
    }

    @Test
    fun `restored enforced crossing is preserved after restart`() {
        ShortsEnforcementTestHarness.startTest(5)
        repeat(5) { ShortsEnforcementTestHarness.onValidatedShortEvent() }
        assertTrue(ShortsEnforcementTestHarness.snapshot().enforced)

        ShortsEnforcementTestHarness.simulateProcessRestartForTest()
        ShortsEnforcementTestHarness.attach(context)
        val s = ShortsEnforcementTestHarness.snapshot()
        assertTrue(s.enabled)
        assertTrue(s.enforced)
        assertEquals(5, s.testCount)
        assertTrue(prefs().getBoolean("test_enforced", false))
    }

    @Test
    fun `reset clears debug state and persists disabled state`() {
        ShortsEnforcementTestHarness.startTest(5)
        repeat(5) { ShortsEnforcementTestHarness.onValidatedShortEvent() }
        assertTrue(ShortsEnforcementTestHarness.snapshot().enforced)

        ShortsEnforcementTestHarness.resetTest()
        val s = ShortsEnforcementTestHarness.snapshot()
        assertFalse(s.enabled)
        assertEquals(0, s.testCount)
        assertFalse(s.enforced)
        // Limit kept as the existing/default UI value.
        assertEquals(5, s.testLimit)
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)
        assertFalse(prefs().getBoolean("test_mode_enabled", true))

        // A restart stays disabled (nothing to restore).
        ShortsEnforcementTestHarness.simulateProcessRestartForTest()
        ShortsEnforcementTestHarness.attach(context)
        assertFalse(ShortsEnforcementTestHarness.snapshot().enabled)
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)
    }

    @Test
    fun `stop disables debug state and unregisters`() {
        ShortsEnforcementTestHarness.startTest(5)
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        assertEquals(2, ShortsEnforcementTestHarness.snapshot().testCount)

        ShortsEnforcementTestHarness.stopTest()
        val s = ShortsEnforcementTestHarness.snapshot()
        assertFalse(s.enabled)
        assertEquals(0, s.testCount)
        assertFalse(s.enforced)
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)
        assertFalse(prefs().getBoolean("test_mode_enabled", true))
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    fun `restored enabled test automatically re-registers the listener`() {
        ShortsEnforcementTestHarness.startTest(5)
        assertTrue(ShortsEnforcementTestHarness.isListenerRegistered)

        ShortsEnforcementTestHarness.simulateProcessRestartForTest()
        assertFalse(ShortsEnforcementTestHarness.isListenerRegistered)

        ShortsEnforcementTestHarness.attach(context)
        assertTrue(ShortsEnforcementTestHarness.isListenerRegistered)
        assertTrue(ShortsEnforcementTestHarness.snapshot().enabled)
    }

    @Test
    fun `registration is idempotent across repeated attach and start`() {
        ShortsEnforcementTestHarness.startTest(5)
        assertTrue(ShortsEnforcementTestHarness.isListenerRegistered)

        // Repeated attach (restore) and START TEST must not double-register.
        ShortsEnforcementTestHarness.attach(context)
        ShortsEnforcementTestHarness.attach(context)
        ShortsEnforcementTestHarness.startTest(7)
        ShortsEnforcementTestHarness.attach(context)

        assertTrue(ShortsEnforcementTestHarness.isListenerRegistered)
        val s = ShortsEnforcementTestHarness.snapshot()
        assertTrue(s.enabled)
        assertEquals(0, s.testCount)
        assertEquals(7, s.testLimit)
    }

    @Test
    fun `disabled mode ignores validated events`() {
        // No START TEST — listener-less path still reports the event boundary
        // but must never increment or enforce.
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        val s = ShortsEnforcementTestHarness.snapshot()
        assertFalse(s.enabled)
        assertEquals(0, s.testCount)
        assertFalse(s.enforced)
    }

    // ------------------------------------------------------------------
    // Enforcement behavior
    // ------------------------------------------------------------------

    @Test
    fun `count increments only per validated event and crossing latches enforcement`() {
        ShortsEnforcementTestHarness.startTest(5)
        repeat(4) { ShortsEnforcementTestHarness.onValidatedShortEvent() }
        assertEquals(4, ShortsEnforcementTestHarness.snapshot().testCount)
        assertFalse(ShortsEnforcementTestHarness.snapshot().enforced)

        // 5th validated event crosses the limit exactly once.
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        assertEquals(5, ShortsEnforcementTestHarness.snapshot().testCount)
        assertTrue(ShortsEnforcementTestHarness.snapshot().enforced)

        // A 6th event never re-triggers (edge latch) — count still advances.
        ShortsEnforcementTestHarness.onValidatedShortEvent()
        assertEquals(6, ShortsEnforcementTestHarness.snapshot().testCount)
        assertTrue(ShortsEnforcementTestHarness.snapshot().enforced)
    }

    @Test
    fun `enforced flag is persisted before the enforcement trigger fires`() {
        ShortsEnforcementTestHarness.startTest(5)
        repeat(5) { ShortsEnforcementTestHarness.onValidatedShortEvent() }
        // After the crossing (which ran the one-shot trigger) the persisted
        // state already carries enforced=true — a crash/restart at the
        // trigger boundary cannot lose the latch.
        assertTrue(prefs().getBoolean("test_enforced", false))
        assertTrue(ShortsEnforcementTestHarness.isTestEnforced())
    }

    @Test
    fun `real debug enforcement seam is still invoked from debug builds`() {
        // The seam used by the harness crossing must be reachable and DEBUG —
        // never NOT_DEBUG — in this (debug) test environment. NO_CONTEXT is
        // expected because no accessibility service context exists in tests.
        val result = ShortsRestrictionEngine.debugTriggerEnforcement(testCondition = true)
        assertNotEquals("NOT_DEBUG", result)
        assertTrue(
            result in setOf("SHOWN", "SHOW_FAILED", "DECISION_FALSE", "NO_CONTEXT"),
        )
    }

    // ------------------------------------------------------------------
    // Production isolation
    // ------------------------------------------------------------------

    @Test
    fun `debug test reset never modifies production engine state`() {
        val engine = ShortsControlEngine(store = InMemoryShortsLimitCycleStore())
        engine.setLimit(50)
        engine.activate()
        val before = engine.currentState()

        ShortsEnforcementTestHarness.startTest(5)
        repeat(5) { ShortsEnforcementTestHarness.onValidatedShortEvent() }
        assertTrue(ShortsEnforcementTestHarness.snapshot().enforced)
        ShortsEnforcementTestHarness.resetTest()
        ShortsEnforcementTestHarness.stopTest()

        val after = engine.currentState()
        assertEquals(before.currentCount, after.currentCount)
        assertEquals(before.limitCount, after.limitCount)
        assertEquals(before.status, after.status)
        assertEquals(before.enforcementState, after.enforcementState)
        assertFalse(after.limitReached)
    }
}
