package com.shortscap.app.shorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-5 — focused tests for the authoritative [ShortsControlEngine] covering
 * the spec's 20 cases. Uses an in-memory store + a controllable clock so
 * cycle start/expiry, restart recovery and process-death recovery are
 * deterministic.
 */
class ShortsControlEngineTest {

    /** Controllable clock — simulates time passing without real waiting. */
    private class Clock {
        var now: Long = 1_000_000L
        fun advance(ms: Long) { now += ms }
    }

    private val HOUR = 3_600_000L
    private val DAY = 24 * HOUR

    private fun engine(
        store: ShortsLimitCycleStore = InMemoryShortsLimitCycleStore(),
        clock: Clock = Clock(),
        warningCount: Int? = null,
        warningMinutes: Int? = null,
    ) = ShortsControlEngine(
        store = store,
        warningCount = warningCount,
        warningMinutes = warningMinutes,
        nowMillis = { clock.now },
    )

    private fun count(engine: ShortsControlEngine, key: String = "youtube:shorts:${System.nanoTime()}", duration: Long = 4_000L) {
        engine.onShortCounted(candidateKey = key, occurredAt = 0L, durationMillis = duration)
    }

    // 0. SAVING a limit only CONFIGURES it — no cycle, no timer, no lock.
    @Test
    fun `saving a limit does not start the cycle`() {
        val clock = Clock()
        val e = engine(clock = clock)
        val state = e.setLimit(200)

        assertEquals(ShortsLimitCycleStatus.CONFIGURED, state.status)
        assertEquals(200, state.limitCount)
        assertNull(state.cycleStartedAt)
        assertNull(state.cycleExpiresAt)
        assertEquals(0L, state.remainingCycleMillis) // timer NOT started
        assertFalse(e.isLimitLocked()) // not locked before activation
        assertFalse(e.hasActiveCycle()) // no active cycle yet
    }

    // 0b. Editing the configured limit before activation is allowed.
    @Test
    fun `configured limit can be edited before activation`() {
        val e = engine()
        e.setLimit(200)
        e.setLimit(250)
        val state = e.currentState()
        assertEquals(ShortsLimitCycleStatus.CONFIGURED, state.status)
        assertEquals(250, state.limitCount)
        assertNull(state.cycleStartedAt)
    }

    // 1. ACTIVE starts the cycle from the configured limit.
    @Test
    fun `activate creates an active cycle with the configured limit`() {
        val clock = Clock()
        val e = engine(clock = clock)
        e.setLimit(200)
        val state = e.activate()

        assertEquals(ShortsLimitCycleStatus.ACTIVE, state.status)
        assertEquals(200, state.limitCount)
        assertNotNull(state.cycleStartedAt)
        assertNotNull(state.cycleExpiresAt)
        assertEquals(ShortsEnforcementState.ALLOW, state.enforcementState)
    }

    // 1b. Activating without a saved limit is a no-op.
    @Test
    fun `activate without a configured limit does nothing`() {
        val e = engine()
        val state = e.activate()
        assertEquals(ShortsLimitCycleStatus.DISABLED, state.status)
        assertNull(state.cycleStartedAt)
    }

    // 2. Cycle start is stored (only after activation).
    @Test
    fun `cycle start equals activation timestamp`() {
        val clock = Clock()
        val e = engine(clock = clock)
        e.setLimit(200)
        assertNull(e.currentState().cycleStartedAt) // not started on save
        e.activate()
        assertEquals(clock.now, e.currentState().cycleStartedAt)
    }

    // 3. Cycle expiry = start + 24h (only after activation).
    @Test
    fun `cycle expiry is exactly start plus 24 hours`() {
        val clock = Clock()
        val e = engine(clock = clock)
        e.setLimit(200)
        e.activate()
        val state = e.currentState()
        assertEquals(state.cycleStartedAt!! + DAY, state.cycleExpiresAt)
    }

    // 4. Current count starts at zero after activation.
    @Test
    fun `current count starts at zero`() {
        val e = engine()
        e.setLimit(200)
        val state = e.activate()
        assertEquals(0, state.currentCount)
        assertEquals(0f, state.usageRatio, 1e-6f)
    }

    // 5. Increment count (only inside an ACTIVE cycle).
    @Test
    fun `count increments on a valid short`() {
        val e = engine()
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "k1", occurredAt = 0L, durationMillis = 4_000L)
        assertEquals(1, e.currentState().currentCount)
        e.onShortCounted(candidateKey = "k2", occurredAt = 1_000L, durationMillis = 4_000L)
        assertEquals(2, e.currentState().currentCount)
    }

    // 5b. Counting is ignored while the limit is only CONFIGURED (not active).
    @Test
    fun `shorts are not counted before activation`() {
        val e = engine()
        e.setLimit(200)
        e.onShortCounted(candidateKey = "k1", occurredAt = 0L, durationMillis = 4_000L)
        assertEquals(0, e.currentState().currentCount)
    }

    // 6. Duplicate candidate is NOT counted twice.
    @Test
    fun `duplicate candidate is not counted twice`() {
        val e = engine()
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "dup", occurredAt = 0L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "dup", occurredAt = 0L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "dup", occurredAt = 0L, durationMillis = 4_000L)
        assertEquals(1, e.currentState().currentCount)
    }

    // 7. Warning state (count-based).
    @Test
    fun `count-based warning triggers and latches`() {
        val e = engine(warningCount = 150)
        e.setLimit(200)
        e.activate()
        repeat(149) { e.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }
        assertFalse(e.currentState().warningTriggered)
        e.onShortCounted(candidateKey = "k150", occurredAt = 150_000L, durationMillis = 4_000L)
        assertTrue(e.currentState().warningTriggered)
        assertEquals(ShortsEnforcementState.WARNING, e.currentState().enforcementState)
    }

    // 7b. Time-based warning (minutes).
    @Test
    fun `time-based warning uses duration tracking`() {
        val e = engine(warningMinutes = 5)
        e.setLimit(200)
        e.activate()
        // 4 minutes of counted duration -> no warning.
        repeat(60) { e.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }
        assertFalse(e.currentState().warningTriggered)
        // Another minute of duration -> 5 minutes reached.
        repeat(15) { e.onShortCounted(candidateKey = "m$it", occurredAt = 1_000_000L + it, durationMillis = 4_000L) }
        assertTrue(e.currentState().warningTriggered)
    }

    // 8. Limit reached.
    @Test
    fun `limit reached when count hits the limit`() {
        val e = engine()
        e.setLimit(2)
        e.activate()
        e.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)
        assertEquals(ShortsEnforcementState.ALLOW, e.currentState().enforcementState)
        e.onShortCounted(candidateKey = "b", occurredAt = 1_000L, durationMillis = 4_000L)
        val state = e.currentState()
        assertEquals(ShortsLimitCycleStatus.LIMIT_REACHED, state.status)
        assertTrue(state.limitReached)
        assertEquals(ShortsEnforcementState.LIMIT_REACHED, state.enforcementState)
    }

    // 9. Limit reached survives restart.
    @Test
    fun `limit reached survives restart`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e1 = engine(store = store, clock = clock)
        e1.setLimit(2)
        e1.activate()
        e1.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)
        e1.onShortCounted(candidateKey = "b", occurredAt = 1_000L, durationMillis = 4_000L)
        assertEquals(ShortsLimitCycleStatus.LIMIT_REACHED, e1.currentState().status)

        // New engine over the SAME store = process restart.
        val e2 = engine(store = store, clock = clock)
        val state = e2.currentState()
        assertEquals(ShortsLimitCycleStatus.LIMIT_REACHED, state.status)
        assertTrue(state.limitReached)
        assertEquals(2, state.currentCount)
    }

    // 10. Active cycle survives restart.
    @Test
    fun `active cycle survives restart`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e1 = engine(store = store, clock = clock)
        e1.setLimit(200)
        e1.activate()
        e1.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)

        val e2 = engine(store = store, clock = clock)
        val state = e2.currentState()
        assertEquals(ShortsLimitCycleStatus.ACTIVE, state.status)
        assertEquals(1, state.currentCount)
        assertEquals(e1.currentState().cycleStartedAt, state.cycleStartedAt)
        assertEquals(e1.currentState().cycleExpiresAt, state.cycleExpiresAt)
    }

    // 10b. Saved-but-not-activated limit survives restart as CONFIGURED.
    @Test
    fun `configured limit survives restart as ready`() {
        val store = InMemoryShortsLimitCycleStore()
        val e1 = engine(store = store)
        e1.setLimit(180)
        assertFalse(e1.hasActiveCycle())

        val e2 = engine(store = store)
        val state = e2.currentState()
        assertEquals(ShortsLimitCycleStatus.CONFIGURED, state.status)
        assertEquals(180, state.limitCount)
        assertNull(state.cycleStartedAt) // timer still NOT started
        assertFalse(e2.hasActiveCycle())
    }

    // 11. Count survives restart.
    @Test
    fun `count survives restart`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e1 = engine(store = store, clock = clock)
        e1.setLimit(200)
        e1.activate()
        repeat(7) { e1.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }

        val e2 = engine(store = store, clock = clock)
        assertEquals(7, e2.currentState().currentCount)
    }

    // 12. Limit change while active preserves count + cycle timing (debug seam).
    @Test
    fun `limit change preserves count and timers`() {
        val clock = Clock()
        val e = engine(clock = clock)
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "b", occurredAt = 1_000L, durationMillis = 4_000L)
        val before = e.currentState()

        clock.advance(5 * HOUR)
        e.setLimit(250) // 200 -> 250 (debug seam allows it)

        val after = e.currentState()
        assertEquals(250, after.limitCount)
        assertEquals(2, after.currentCount) // count preserved
        assertEquals(before.cycleStartedAt, after.cycleStartedAt) // timer preserved
        assertEquals(before.cycleExpiresAt, after.cycleExpiresAt)
        assertEquals(ShortsEnforcementState.ALLOW, after.enforcementState)
    }

    // 13. Cycle expiry does NOT auto-roll — the user re-activates.
    @Test
    fun `expired cycle does not auto-start a new cycle`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)

        clock.advance(DAY + 1) // cycle now expired
        val state = e.currentState()

        assertEquals(ShortsLimitCycleStatus.EXPIRED, state.status)
        // The expired window keeps its own timestamps; NO new window was
        // auto-created (remaining time is 0, not 24h).
        assertNotNull(state.cycleStartedAt)
        assertEquals(0L, state.remainingCycleMillis)
        assertFalse(e.hasActiveCycle())
        assertFalse(e.isLimitLocked()) // editing available again

        // Re-activating starts the NEXT cycle from the same configured limit.
        clock.advance(1_000L)
        val fresh = e.activate()
        assertEquals(ShortsLimitCycleStatus.ACTIVE, fresh.status)
        assertEquals(0, fresh.currentCount)
        assertEquals(200, fresh.limitCount)
        assertEquals(clock.now, fresh.cycleStartedAt)
        assertEquals(clock.now + DAY, fresh.cycleExpiresAt)
    }

    // 14. 0 / invalid limit is safe.
    @Test
    fun `invalid limit is handled safely`() {
        val e = engine()
        val state = e.setLimit(0)
        assertEquals(0f, state.usageRatio, 1e-6f)
        assertEquals(0, state.remainingCount)
        // No division by zero, no crash, control effectively off.
        assertTrue(state.limitCount <= 0 || state.status != ShortsLimitCycleStatus.ACTIVE)
    }

    // 15. Cross-platform counts use ONE global counter.
    @Test
    fun `cross-platform shorts share one global count`() {
        val e = engine()
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "youtube:1", occurredAt = 0L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "instagram:1", occurredAt = 1_000L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "tiktok:1", occurredAt = 2_000L, durationMillis = 4_000L)
        val state = e.currentState()
        assertEquals(3, state.currentCount)
        assertEquals(197, state.remainingCount)
        assertEquals(3f / 200f, state.usageRatio, 1e-6f)
    }

    // 16. HUD state derived correctly (ratio + remaining + cycle time).
    @Test
    fun `hud state fields are derived correctly`() {
        val clock = Clock()
        val e = engine(clock = clock)
        e.setLimit(200)
        e.activate()
        repeat(127) { e.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }

        clock.advance(HOUR) // 1h into the cycle
        val state = e.currentState()
        assertEquals(127, state.currentCount)
        assertEquals(200, state.limitCount)
        assertEquals(127f / 200f, state.usageRatio, 1e-6f)
        assertEquals(73, state.remainingCount)
        assertEquals(23 * HOUR, state.remainingCycleMillis) // 23h left
        assertEquals(ShortsEnforcementState.ALLOW, state.enforcementState)
    }

    // 17. Disabling control preserves history and stops counting.
    @Test
    fun `disabling control preserves history and stops counting`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)

        val state = e.disable()
        assertEquals(ShortsLimitCycleStatus.DISABLED, state.status)
        assertEquals(1, store.history().size) // history NOT deleted

        // Counting is ignored while disabled.
        e.onShortCounted(candidateKey = "b", occurredAt = 1_000L, durationMillis = 4_000L)
        assertEquals(1, e.currentState().currentCount.coerceAtLeast(store.history().first().currentCount))
    }

    // 18. Re-enable starts a fresh cycle (setLimit then activate).
    @Test
    fun `re-enabling starts a fresh cycle`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        e.activate()
        e.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)
        e.disable()
        clock.advance(HOUR)

        val state = e.setLimit(200)
        assertEquals(ShortsLimitCycleStatus.CONFIGURED, state.status) // ready first
        val active = e.activate()
        assertEquals(ShortsLimitCycleStatus.ACTIVE, active.status)
        assertEquals(0, active.currentCount)
        assertEquals(2, store.history().size)
    }

    // 19. No second active cycle is ever created while one is active.
    @Test
    fun `setting limit on an active cycle never creates a second cycle`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        e.activate()
        e.setLimit(250) // debug seam: threshold-only on the SAME window
        e.setLimit(100)
        assertEquals(1, store.history().size) // still ONE cycle
        assertEquals(100, e.currentState().limitCount)
    }

    // 19b. ACTIVE on an already-active cycle never creates a second cycle.
    @Test
    fun `activating an already active cycle does nothing`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        e.activate()
        val firstStart = e.currentState().cycleStartedAt

        e.activate()
        e.activate()
        assertEquals(1, store.history().size) // still ONE cycle
        assertEquals(firstStart, e.currentState().cycleStartedAt) // untouched
    }

    // 20. Process-death recovery: same store, new engine instance.
    @Test
    fun `process death recovery restores exact state`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e1 = engine(store = store, clock = clock)
        e1.setLimit(200)
        e1.activate()
        repeat(12) { e1.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }
        e1.disable()

        // Process death: brand-new engine over the SAME persisted store.
        val e2 = engine(store = store, clock = clock)
        assertEquals(ShortsLimitCycleStatus.DISABLED, e2.currentState().status)
        val history = store.history()
        assertEquals(1, history.size)
        assertEquals(12, history.first().currentCount)
    }

    // 21. Retry-safe persistence: no duplicate cycles from repeated saves.
    @Test
    fun `repeated state reads never create duplicate cycles`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        repeat(50) { e.currentState() }
        assertEquals(1, store.history().size) // only the CONFIGURED row
    }

    // 22. Limit reached persists until the cycle expires (no reset before).
    @Test
    fun `limit reached state survives until expiry`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(2)
        e.activate()
        e.onShortCounted(candidateKey = "a", occurredAt = 0L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "b", occurredAt = 1_000L, durationMillis = 4_000L)
        assertTrue(e.currentState().limitReached)

        clock.advance(DAY + 1)
        val expired = e.currentState()
        assertEquals(ShortsLimitCycleStatus.EXPIRED, expired.status) // no auto-roll
        assertFalse(expired.limitReached)
    }
}
