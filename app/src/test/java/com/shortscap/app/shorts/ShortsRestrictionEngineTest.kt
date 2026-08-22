package com.shortscap.app.shorts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Shorts restriction decision rule ([shouldRestrict]) —
 * the pure show/hide logic of [ShortsRestrictionEngine]: the overlay shows
 * ONLY when a short-form surface is active AND the authoritative control
 * state reports the limit reached; every other state (no surface, EXPIRED,
 * DISABLED, ALLOW) hides automatically.
 */
class ShortsRestrictionEngineTest {

    private fun controlState(
        status: ShortsLimitCycleStatus,
        limitReached: Boolean = status == ShortsLimitCycleStatus.LIMIT_REACHED,
    ): ShortsControlState = ShortsControlState(
        cycle = null,
        status = status,
        currentCount = 0,
        limitCount = 0,
        usageRatio = 0f,
        remainingCount = 0,
        cycleStartedAt = null,
        cycleExpiresAt = null,
        remainingCycleMillis = 0L,
        enforcementState = if (limitReached) ShortsEnforcementState.LIMIT_REACHED else ShortsEnforcementState.ALLOW,
        warningTriggered = false,
        limitReached = limitReached,
    )

    @Test
    fun `shows only when short-form surface is active and limit reached`() {
        assertTrue(shouldRestrict(true, controlState(ShortsLimitCycleStatus.LIMIT_REACHED, limitReached = true)))
    }

    @Test
    fun `hidden when no short-form surface is active`() {
        assertFalse(shouldRestrict(false, controlState(ShortsLimitCycleStatus.LIMIT_REACHED, limitReached = true)))
    }

    @Test
    fun `hidden when the 24-hour cycle is expired`() {
        // Expired windows report limitReached == false — the overlay lifts
        // automatically on the next evaluation (no timer needed).
        assertFalse(shouldRestrict(true, controlState(ShortsLimitCycleStatus.EXPIRED)))
        assertFalse(shouldRestrict(false, controlState(ShortsLimitCycleStatus.EXPIRED)))
    }

    @Test
    fun `hidden when control is disabled`() {
        assertFalse(shouldRestrict(true, controlState(ShortsLimitCycleStatus.DISABLED)))
    }

    @Test
    fun `hidden while the limit is not reached`() {
        assertFalse(shouldRestrict(true, controlState(ShortsLimitCycleStatus.ACTIVE)))
        assertFalse(shouldRestrict(true, controlState(ShortsLimitCycleStatus.CONFIGURED)))
    }
}
