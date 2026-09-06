package com.shortscap.app.shorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests for the DEBUG-only [DebugEnforcementSimulation] bridge.
 *
 * Every assertion below verifies that the simulation feeds simulated input
 * through the REAL production decision path ([ShortsControlEngine]
 * derivation + [shouldRestrict]) while the real production engine's count,
 * limit and persisted state stay completely untouched.
 */
class DebugEnforcementSimulationTest {

    private class Clock {
        var now: Long = 1_000_000L
        fun advance(ms: Long) { now += ms }
    }

    /** A real engine over an in-memory store acting as the "production" state. */
    private class RealEngine(val store: ShortsLimitCycleStore = InMemoryShortsLimitCycleStore()) {
        val clock = Clock()
        val engine = ShortsControlEngine(store = store, nowMillis = { clock.now })
    }

    private fun sim(real: RealEngine) = DebugEnforcementSimulation(
        realEngineProvider = { real.engine },
        nowMillis = { real.clock.now },
    )

    // 1. Inert by default — simulation off, no enforcement, real state fine.
    @Test
    fun `simulation is off and inert by default`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)

        val snap = s.snapshot()
        assertFalse(snap.active)
        assertFalse(snap.restrictDecision)
        assertFalse(snap.limitReached)
        assertEquals(0, snap.simulatedCount)
        assertEquals(15, snap.realLimit)

        // Ops while off are no-ops.
        assertFalse(s.increment().active)
        assertFalse(s.setCount(9).active)
        assertFalse(s.reset().active)
        assertEquals(0, s.snapshot().simulatedCount)
    }

    // 2. Start reads the REAL configured limit when no override is given.
    @Test
    fun `start reads the real configured limit`() {
        val real = RealEngine()
        real.engine.setLimit(15) // CONFIGURED (saved), never activated — still the limit source
        val s = sim(real)

        val snap = s.start()
        assertTrue(snap.active)
        assertEquals(15, snap.simulatedLimit)
        assertEquals(15, snap.realLimit)
        assertEquals(0, snap.simulatedCount)
        assertEquals(ShortsEnforcementState.ALLOW, snap.enforcementState)
        assertFalse(snap.restrictDecision)
    }

    // 3. Below limit → NOT enforced (even with the surface active).
    @Test
    fun `below the limit is not enforced`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()
        s.setSurfaceActive(true)

        val snap = s.setCount(10)
        assertEquals(10, snap.simulatedCount)
        assertEquals(ShortsEnforcementState.ALLOW, snap.enforcementState)
        assertFalse(snap.limitReached)
        assertFalse(snap.restrictDecision)
        assertFalse(snap.blockerActive)
        assertEquals(0, snap.triggerCount)
    }

    // 4. Exactly at the limit → the real LIMIT_REACHED decision fires.
    @Test
    fun `exactly at the limit exercises the real limit reached decision`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()
        s.setSurfaceActive(true)

        val snap = s.setCount(15)
        assertEquals(15, snap.simulatedCount)
        assertEquals(ShortsLimitCycleStatus.LIMIT_REACHED, snap.status)
        assertEquals(ShortsEnforcementState.LIMIT_REACHED, snap.enforcementState)
        assertTrue(snap.limitReached)
        assertTrue(snap.restrictDecision)
        assertTrue(snap.blockerActive)
    }

    // 5. Above the limit → still the same real decision, count visible as > limit.
    // (Production does not distinguish "at" vs "over" — both are LIMIT_REACHED.)
    @Test
    fun `above the limit stays limit reached with the over-count visible`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()
        s.setSurfaceActive(true)

        val snap = s.setCount(16)
        assertEquals(16, snap.simulatedCount)
        assertEquals(ShortsEnforcementState.LIMIT_REACHED, snap.enforcementState)
        assertTrue(snap.restrictDecision)
    }

    // 6. Increment advances by exactly one; batch by N.
    @Test
    fun `increment advances by one and batch by n`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()

        var snap = s.increment()
        assertEquals(1, snap.simulatedCount)
        snap = s.increment()
        assertEquals(2, snap.simulatedCount)
        snap = s.incrementBy(5)
        assertEquals(7, snap.simulatedCount)
        snap = s.incrementBy(8) // 7 + 8 = 15
        assertEquals(15, snap.simulatedCount)
    }

    // 7. Enforcement is surface-gated exactly like production shouldRestrict.
    @Test
    fun `enforcement requires the simulated short-form surface`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()

        // Count at/over the limit but NO surface → the real decision says hide.
        val snap = s.setCount(15)
        assertTrue(snap.limitReached)
        assertFalse(snap.restrictDecision) // production: overlay needs a surface event
        assertFalse(snap.blockerActive)
        assertEquals(0, snap.triggerCount)
    }

    // 8. The enforcement edge fires exactly ONCE on the crossing transition.
    @Test
    fun `enforcement triggers exactly once on the crossing transition`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()
        s.setSurfaceActive(true)

        var snap = s.setCount(14)
        assertEquals(0, snap.triggerCount)
        snap = s.increment() // 15 — crossing transition
        assertEquals(1, snap.triggerCount)
        snap = s.increment() // 16 — still blocked
        assertEquals(1, snap.triggerCount)
        snap = s.setCount(50)
        assertEquals(1, snap.triggerCount)
        // Pure reads never re-trigger.
        snap = s.snapshot()
        assertEquals(1, snap.triggerCount)
    }

    // 9. Leaving the surface lifts the simulated blocker; returning re-triggers
    // (mirrors production re-showing on the next surface evaluation).
    @Test
    fun `surface leave lifts the blocker and return re-triggers`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()
        s.setSurfaceActive(true)
        s.setCount(15)
        assertEquals(1, s.snapshot().triggerCount)

        var snap = s.setSurfaceActive(false)
        assertFalse(snap.restrictDecision)
        assertFalse(snap.blockerActive)

        snap = s.setSurfaceActive(true)
        assertTrue(snap.restrictDecision)
        assertTrue(snap.blockerActive)
        assertEquals(2, snap.triggerCount) // a NEW enforcement action, like a re-show
    }

    // 10. Reset clears ONLY the simulation — real count/limit untouched.
    @Test
    fun `reset clears only the simulation and never touches the real engine`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        real.engine.activate()
        // Real "production" count of 5 via genuine counting.
        repeat(5) { real.engine.onShortCounted(candidateKey = "real$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }
        assertEquals(5, real.engine.currentState().currentCount)
        assertEquals(15, real.engine.currentState().limitCount)

        val s = sim(real)
        s.start()
        s.setSurfaceActive(true)
        s.setCount(15)
        assertTrue(s.snapshot().restrictDecision)

        val snap = s.reset()
        assertEquals(0, snap.simulatedCount)
        assertFalse(snap.restrictDecision)
        assertFalse(snap.blockerActive)
        assertEquals(0, snap.triggerCount)
        assertTrue(snap.active) // the simulation itself stays usable

        // Real state completely untouched.
        assertEquals(5, real.engine.currentState().currentCount)
        assertEquals(15, real.engine.currentState().limitCount)
        assertEquals(5, real.store.currentCycle()?.currentCount)
    }

    // 11. Simulation never modifies the real production count or limit.
    @Test
    fun `simulation leaves the real production state untouched`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        real.engine.activate()
        repeat(3) { real.engine.onShortCounted(candidateKey = "real$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }
        assertEquals(3, real.engine.currentState().currentCount)

        val s = sim(real)
        s.start(limitOverride = 50)
        s.setCount(49)
        s.incrementBy(50)
        s.reset()

        // Real engine: count 3, limit 15 — identical before/after all sim work.
        assertEquals(3, real.engine.currentState().currentCount)
        assertEquals(15, real.engine.currentState().limitCount)
        assertEquals(1, real.store.history().size) // only the real cycle row
    }

    // 12. Non-positive limits follow the existing engine behavior (no cycle).
    @Test
    fun `start with a non-positive limit stays off like the engine rejects it`() {
        val real = RealEngine() // real configured limit is 0 — nothing configured
        val s = sim(real)

        assertFalse(s.start(limitOverride = 0).active)
        assertFalse(s.start(limitOverride = -5).active)
        // No override and no real limit → also stays off.
        assertFalse(s.start().active)
        // Zero-limit scenario: nothing invented, nothing enforced.
        assertFalse(s.snapshot().restrictDecision)
    }

    // 13. Very small limit scenario (limit = 1): 0 → 1 → 2.
    @Test
    fun `small limit scenario walks zero to one to over`() {
        val real = RealEngine()
        val s = sim(real)
        s.start(limitOverride = 1)
        s.setSurfaceActive(true)

        var snap = s.snapshot()
        assertEquals(0, snap.simulatedCount)
        assertFalse(snap.restrictDecision)

        snap = s.increment() // 1 = limit reached
        assertEquals(1, snap.simulatedCount)
        assertTrue(snap.restrictDecision)
        assertEquals(1, snap.triggerCount)

        snap = s.increment() // 2 = over limit
        assertEquals(2, snap.simulatedCount)
        assertTrue(snap.restrictDecision)
        assertEquals(1, snap.triggerCount) // no duplicate trigger
    }

    // 14. setLimit follows the real engine (positive applies, non-positive rejected).
    @Test
    fun `simulated limit edits follow the engine semantics`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()

        var snap = s.setLimit(1)
        assertEquals(1, snap.simulatedLimit)
        // Crossing by threshold change alone is re-derived immediately.
        s.setSurfaceActive(true)
        snap = s.increment() // count 1 = limit 1 → LIMIT_REACHED
        assertTrue(snap.restrictDecision)

        snap = s.setLimit(0) // rejected by the engine — nothing changes
        assertEquals(1, snap.simulatedLimit)
        assertTrue(snap.restrictDecision) // unchanged block state
    }

    // 15. Stop returns to the inert state; real state stays intact.
    @Test
    fun `stop returns the simulation to inert`() {
        val real = RealEngine()
        real.engine.setLimit(15)
        val s = sim(real)
        s.start()
        s.setCount(12)
        assertTrue(s.snapshot().active)

        val snap = s.stop()
        assertFalse(snap.active)
        assertFalse(s.snapshot().active)
        assertEquals(0, s.snapshot().simulatedCount)
        assertEquals(15, real.engine.currentState().limitCount)
    }
}
