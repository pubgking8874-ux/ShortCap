package com.shortscap.app.shorts

import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.BackendApi
import com.shortscap.app.network.ShortsControlDto
import com.shortscap.app.network.ShortsLimitCycleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shorts Limit page tests — pure, JVM-only coverage of the page's state
 * derivation, custom limit validation, progress/remaining/countdown math and
 * the best-effort backend sync mapping. The page itself renders the
 * authoritative [ShortsControlEngine] state (already covered by
 * ShortsControlEngineTest + RoomShortsLimitCycleStoreTest), so this suite
 * focuses on the page-level pure functions and the syncer seam.
 */
class ShortsLimitPageStateTest {

    // ------------------------------------------------------------------
    // Page state derivation
    // ------------------------------------------------------------------

    private fun state(
        status: ShortsLimitCycleStatus,
        currentCount: Int = 0,
        limitCount: Int = 200,
        warningTriggered: Boolean = false,
        limitReached: Boolean = false,
        hasCycle: Boolean = true,
    ): ShortsControlState {
        val cycle = if (hasCycle) ShortsLimitCycle(
            limitCount = limitCount,
            currentCount = currentCount,
            cycleStartedAt = 1_000_000L,
            cycleExpiresAt = 1_000_000L + 24L * 3_600_000L,
            status = status,
            warningTriggered = warningTriggered,
            limitReached = limitReached,
        ) else null
        return ShortsControlState(
        cycle = cycle,
        status = status,
        currentCount = currentCount,
        limitCount = limitCount,
        usageRatio = if (limitCount > 0) currentCount.toFloat() / limitCount else 0f,
        remainingCount = (limitCount - currentCount).coerceAtLeast(0),
        cycleStartedAt = null,
        cycleExpiresAt = null,
        remainingCycleMillis = 0L,
        enforcementState = when {
            status == ShortsLimitCycleStatus.LIMIT_REACHED || limitReached -> ShortsEnforcementState.LIMIT_REACHED
            warningTriggered -> ShortsEnforcementState.WARNING
            else -> ShortsEnforcementState.ALLOW
        },
        warningTriggered = warningTriggered,
        limitReached = limitReached,
    )
    }

    @Test
    fun `no cycle is first-time setup`() {
        assertEquals(
            ShortsLimitPageState.NO_LIMIT_CONFIGURED,
            deriveLimitPageState(state(ShortsLimitCycleStatus.DISABLED, hasCycle = false)),
        )
    }

    @Test
    fun `active cycle is ACTIVE`() {
        assertEquals(
            ShortsLimitPageState.ACTIVE,
            deriveLimitPageState(state(ShortsLimitCycleStatus.ACTIVE)),
        )
    }

    @Test
    fun `active cycle with warning latched is WARNING`() {
        assertEquals(
            ShortsLimitPageState.WARNING,
            deriveLimitPageState(state(ShortsLimitCycleStatus.ACTIVE, warningTriggered = true)),
        )
    }

    @Test
    fun `limit reached is LIMIT_REACHED`() {
        assertEquals(
            ShortsLimitPageState.LIMIT_REACHED,
            deriveLimitPageState(
                state(ShortsLimitCycleStatus.LIMIT_REACHED, currentCount = 200, limitCount = 200, limitReached = true),
            ),
        )
    }

    @Test
    fun `expired status is EXPIRED`() {
        assertEquals(
            ShortsLimitPageState.EXPIRED,
            deriveLimitPageState(state(ShortsLimitCycleStatus.EXPIRED, hasCycle = true)),
        )
    }

    @Test
    fun `disabled status maps back to setup - no toggle exists`() {
        // Final product rule: no enable/disable toggle — a DISABLED (or stale)
        // status always means "set a limit".
        assertEquals(
            ShortsLimitPageState.NO_LIMIT_CONFIGURED,
            deriveLimitPageState(state(ShortsLimitCycleStatus.DISABLED, hasCycle = true)),
        )
    }

    // ------------------------------------------------------------------
    // Custom limit input validation
    // ------------------------------------------------------------------

    @Test
    fun `empty input is Empty`() {
        assertEquals(LimitInputResult.Empty, parseLimitInput(""))
        assertEquals(LimitInputResult.Empty, parseLimitInput("   "))
    }

    @Test
    fun `valid positive integer parses`() {
        assertEquals(LimitInputResult.Valid(200), parseLimitInput("200"))
        assertEquals(LimitInputResult.Valid(50), parseLimitInput("50"))
        assertEquals(LimitInputResult.Valid(1), parseLimitInput("1"))
    }

    @Test
    fun `zero and negatives are rejected`() {
        assertEquals(LimitInputError.NOT_POSITIVE, (parseLimitInput("0") as LimitInputResult.Invalid).reason)
        assertEquals(LimitInputError.NOT_POSITIVE, (parseLimitInput("-5") as LimitInputResult.Invalid).reason)
    }

    @Test
    fun `non-numeric input is rejected`() {
        assertEquals(LimitInputError.NOT_A_NUMBER, (parseLimitInput("abc") as LimitInputResult.Invalid).reason)
        assertEquals(LimitInputError.NOT_A_NUMBER, (parseLimitInput("12.5") as LimitInputResult.Invalid).reason)
    }

    @Test
    fun `absurdly large values are rejected - never silently converted`() {
        assertEquals(LimitInputError.TOO_LARGE, (parseLimitInput("10001") as LimitInputResult.Invalid).reason)
        assertEquals(LimitInputError.TOO_LARGE, (parseLimitInput("99999999") as LimitInputResult.Invalid).reason)
        // The upper bound itself is accepted.
        assertEquals(LimitInputResult.Valid(10000), parseLimitInput("10000"))
    }

    // ------------------------------------------------------------------
    // Progress / remaining / countdown math
    // ------------------------------------------------------------------

    @Test
    fun `progress fraction is count over limit clamped between 0 and 1`() {
        assertEquals(0f, limitProgressFraction(0, 200), 1e-6f)
        assertEquals(0.5f, limitProgressFraction(100, 200), 1e-6f)
        assertEquals(0.635f, limitProgressFraction(127, 200), 1e-6f)
        assertEquals(1f, limitProgressFraction(200, 200), 1e-6f)
        assertEquals(1f, limitProgressFraction(250, 200), 1e-6f) // clamped, never > 1
    }

    @Test
    fun `progress never divides by zero`() {
        assertEquals(0f, limitProgressFraction(10, 0), 1e-6f)
        assertEquals(0f, limitProgressFraction(10, -5), 1e-6f)
    }

    @Test
    fun `remaining count is limit minus current, never negative`() {
        assertEquals(200, limitRemainingCount(0, 200))
        assertEquals(73, limitRemainingCount(127, 200))
        assertEquals(0, limitRemainingCount(200, 200))
        assertEquals(0, limitRemainingCount(250, 200))
    }

    @Test
    fun `countdown derives hours and minutes from remaining millis`() {
        assertEquals(18L to 42L, remainingHoursMinutes(18 * 3_600_000L + 42 * 60_000L))
        assertEquals(0L to 0L, remainingHoursMinutes(0L))
        assertEquals(23L to 59L, remainingHoursMinutes(24 * 3_600_000L - 60_000L))
        // Negative remaining (expired) clamps to zero — never negative display.
        assertEquals(0L to 0L, remainingHoursMinutes(-5_000L))
    }

    @Test
    fun `countdown formats as HH colon MM colon SS`() {
        assertEquals("24:00:00", remainingCountdownHms(24 * 3_600_000L))
        assertEquals("23:41:28", remainingCountdownHms(23 * 3_600_000L + 41 * 60_000L + 28_000L))
        assertEquals("00:00:01", remainingCountdownHms(1_000L))
        assertEquals("00:00:00", remainingCountdownHms(0L))
        // Expired clamps to 00:00:00, never negative.
        assertEquals("00:00:00", remainingCountdownHms(-5_000L))
        // Sub-second remainder rounds down (timer shows whole seconds).
        assertEquals("23:59:59", remainingCountdownHms(24 * 3_600_000L - 999L))
    }

    @Test
    fun `time progress is remaining over full cycle - full at start, zero at expiry`() {
        val cycle = 24 * 3_600_000L
        assertEquals(1f, timeProgressFraction(cycle, cycle), 1e-6f) // full circle at start
        assertEquals(0.5f, timeProgressFraction(cycle / 2, cycle), 1e-6f)
        assertEquals(0f, timeProgressFraction(0L, cycle), 1e-6f) // expired -> empty
        // Clamped: never > 1 or < 0, never divides by zero.
        assertEquals(1f, timeProgressFraction(cycle * 2, cycle), 1e-6f)
        assertEquals(0f, timeProgressFraction(cycle, 0L), 1e-6f)
    }

    @Test
    fun `time progress is independent from usage progress`() {
        // TIME clock and SHORTS USAGE fraction are separate concepts — a full
        // usage ratio with plenty of time left must NOT collapse the clock.
        assertEquals(1f, timeProgressFraction(24 * 3_600_000L, 24 * 3_600_000L), 1e-6f)
        assertEquals(1f, limitProgressFraction(200, 200), 1e-6f)
        // Half the time gone, full usage: clock at 0.5, usage at 1.0.
        assertEquals(0.5f, timeProgressFraction(12 * 3_600_000L, 24 * 3_600_000L), 1e-6f)
    }

    // ------------------------------------------------------------------
    // End-to-end: engine drives the page states (activation -> edit -> restart)
    // ------------------------------------------------------------------

    private class Clock {
        var now: Long = 1_000_000L
        fun advance(ms: Long) { now += ms }
    }

    private val HOUR = 3_600_000L
    private val DAY = 24 * HOUR

    private fun engine(
        store: ShortsLimitCycleStore = InMemoryShortsLimitCycleStore(),
        clock: Clock = Clock(),
        allowEditWhileActive: Boolean = true,
    ) = ShortsControlEngine(store = store, nowMillis = { clock.now }, allowEditWhileActive = allowEditWhileActive)

    @Test
    fun `activate then reopen shows an active cycle with a fresh 24h window`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e1 = engine(store = store, clock = clock)
        e1.setLimit(200)

        // App restart: brand-new engine over the same store.
        val e2 = engine(store = store, clock = clock)
        val state = e2.currentState()
        assertEquals(ShortsLimitPageState.ACTIVE, deriveLimitPageState(state))
        assertEquals(0, state.currentCount)
        assertEquals(200, state.limitCount)
        assertEquals(clock.now + DAY, state.cycleExpiresAt)
    }

    @Test
    fun `first save activates immediately - no separate toggle`() {
        val clock = Clock()
        val e = engine(clock = clock)
        // The page's Save Limit path is exactly engine.setLimit: the cycle
        // becomes ACTIVE immediately (start = now, expires = +24h, count 0).
        val state = e.setLimit(17)
        assertEquals(ShortsLimitPageState.ACTIVE, deriveLimitPageState(state))
        assertEquals(0, state.currentCount)
        assertEquals(17, state.limitCount)
        assertEquals(clock.now, state.cycleStartedAt)
        assertEquals(clock.now + DAY, state.cycleExpiresAt)
        assertEquals(17, state.remainingCount)
    }

    @Test
    fun `editing the active limit preserves count and the 24h timer - debug seam`() {
        val clock = Clock()
        val e = engine(store = InMemoryShortsLimitCycleStore(), clock = clock, allowEditWhileActive = true)
        e.setLimit(200)
        e.onShortCounted(candidateKey = "k1", occurredAt = 0L, durationMillis = 4_000L)
        e.onShortCounted(candidateKey = "k2", occurredAt = 1_000L, durationMillis = 4_000L)
        clock.advance(5 * HOUR)

        // DEBUG test seam: the 24-hour lock may be bypassed for testing.
        assertFalse(e.isLimitLocked())
        e.setLimit(250) // 200 -> 250

        val state = e.currentState()
        assertEquals(2, state.currentCount)
        assertEquals(250, state.limitCount)
        assertEquals(ShortsLimitPageState.ACTIVE, deriveLimitPageState(state))
        // 24-hour window untouched: 19h remaining after 5h elapsed.
        assertEquals(19 * HOUR, state.remainingCycleMillis)
    }

    @Test
    fun `production lock rejects editing during an active cycle`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock, allowEditWhileActive = false)
        e.setLimit(200)
        e.onShortCounted(candidateKey = "k1", occurredAt = 0L, durationMillis = 4_000L)
        clock.advance(HOUR)

        assertTrue(e.isLimitLocked())
        // The edit is rejected — the saved limit stays, the cycle is untouched.
        val state = e.setLimit(250)
        assertEquals(200, state.limitCount)
        assertEquals(1, state.currentCount)
        // 1h of the window elapsed: 23h remain (expires - now).
        assertEquals(DAY - HOUR, state.remainingCycleMillis)
        // Still one cycle — nothing new created, nothing reset.
        assertEquals(1, store.history().size)
    }

    @Test
    fun `production lock releases once the cycle expires`() {
        val clock = Clock()
        val e = engine(store = InMemoryShortsLimitCycleStore(), clock = clock, allowEditWhileActive = false)
        e.setLimit(200)
        assertTrue(e.isLimitLocked())

        clock.advance(DAY + 1)
        // After expiry the engine rolls a fresh window; the old one is history
        // and the new active window is editable only via its own lifecycle.
        val fresh = e.currentState()
        assertEquals(ShortsLimitPageState.ACTIVE, deriveLimitPageState(fresh))
        assertEquals(0, fresh.currentCount)
    }

    @Test
    fun `reaching the limit flips the page to LIMIT_REACHED and survives restart`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e1 = engine(store = store, clock = clock)
        e1.setLimit(10)
        repeat(10) { e1.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }

        val s1 = e1.currentState()
        assertEquals(ShortsLimitPageState.LIMIT_REACHED, deriveLimitPageState(s1))
        assertEquals(10, s1.currentCount)
        assertEquals(0, s1.remainingCount)
        assertEquals(1f, s1.usageRatio, 1e-6f)

        // Force-stop + reopen: LIMIT_REACHED persists (never reset by restart).
        val e2 = engine(store = store, clock = clock)
        val s2 = e2.currentState()
        assertEquals(ShortsLimitPageState.LIMIT_REACHED, deriveLimitPageState(s2))
        assertEquals(10, s2.currentCount)
    }

    @Test
    fun `stale disabled status keeps history but the page shows setup`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        e.onShortCounted(candidateKey = "k1", occurredAt = 0L, durationMillis = 4_000L)
        e.disable()

        val state = e.currentState()
        // No enable/disable toggle in the final product: no active cycle means
        // the setup screen, while history is preserved.
        assertEquals(ShortsLimitPageState.NO_LIMIT_CONFIGURED, deriveLimitPageState(state))
        assertTrue(e.hasHistory())
        assertEquals(1, store.history().size) // history preserved
    }

    @Test
    fun `expired cycle rolls a fresh window - page returns to ACTIVE`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(10)
        repeat(10) { e.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }
        assertEquals(ShortsLimitPageState.LIMIT_REACHED, deriveLimitPageState(e.currentState()))

        // 24 hours pass -> the engine marks the old window EXPIRED and
        // initializes the next one (same limit, fresh count).
        clock.advance(DAY + 1)
        val fresh = e.currentState()
        assertEquals(ShortsLimitPageState.ACTIVE, deriveLimitPageState(fresh))
        assertEquals(0, fresh.currentCount)
        assertEquals(10, fresh.limitCount)
        assertEquals(clock.now, fresh.cycleStartedAt)
    }

    @Test
    fun `offline - durable local state survives while the backend is unreachable`() {
        val store = InMemoryShortsLimitCycleStore()
        val clock = Clock()
        val e = engine(store = store, clock = clock)
        e.setLimit(200)
        repeat(5) { e.onShortCounted(candidateKey = "k$it", occurredAt = it * 1_000L, durationMillis = 4_000L) }

        // Network is irrelevant to the durable local state: the engine still
        // reports the count, limit, expiry and remaining time.
        val state = e.currentState()
        assertEquals(5, state.currentCount)
        assertEquals(200, state.limitCount)
        assertEquals(DAY, state.remainingCycleMillis)
        assertEquals(ShortsLimitPageState.ACTIVE, deriveLimitPageState(state))
    }
}

/** Scriptable backend for the Shorts Control syncer mapping tests. */
private class ScriptedControlApi : BackendApi {
    var activateResult: ApiResult<ShortsLimitCycleDto?> = ApiResult.Success(null)
    var editResult: ApiResult<ShortsControlDto> = ApiResult.Success(ShortsControlDto())
    var disableResult: ApiResult<ShortsLimitCycleDto?> = ApiResult.Success(null)
    var activateCalled = false
    var editLimit: Int? = null

    override suspend fun activateShortsLimitCycle(limitCount: Int): ApiResult<ShortsLimitCycleDto?> {
        activateCalled = true
        return activateResult
    }
    override suspend fun updateShortsControl(limitCount: Int): ApiResult<ShortsControlDto> {
        editLimit = limitCount
        return editResult
    }
    override suspend fun disableShortsLimitCycle(): ApiResult<ShortsLimitCycleDto?> = disableResult

    // Unused by the syncer — default stubs keep the interface implementation trivial.
    override suspend fun getUserSettings() = ApiResult.Success(com.shortscap.app.network.UserSettingsDto())
    override suspend fun updateUserSettings(dto: com.shortscap.app.network.UserSettingsDto) = ApiResult.Success(dto)
    override suspend fun getMonitoringSettings() = ApiResult.Success(com.shortscap.app.network.MonitoringSettingsDto())
    override suspend fun updateMonitoringSettings(dto: com.shortscap.app.network.MonitoringSettingsDto) = ApiResult.Success(dto)
    override suspend fun getShortsSettings() = ApiResult.Success(com.shortscap.app.network.ShortsSettingsDto())
    override suspend fun updateShortsSettings(dto: com.shortscap.app.network.ShortsSettingsDto) = ApiResult.Success(dto)
    override suspend fun getNotificationPreferences() = ApiResult.Success(com.shortscap.app.network.NotificationPreferencesDto())
    override suspend fun updateNotificationPreferences(dto: com.shortscap.app.network.NotificationPreferencesDto) = ApiResult.Success(dto)
    override suspend fun getLeaderboardSettings() = ApiResult.Success(com.shortscap.app.network.LeaderboardSettingsDto())
    override suspend fun updateLeaderboardSettings(dto: com.shortscap.app.network.LeaderboardSettingsDto) = ApiResult.Success(dto)
    override suspend fun syncPermissions(states: List<Map<String, Any?>>) = ApiResult.Success(states)
    override suspend fun listStudySchedules() = ApiResult.Success(emptyList<Map<String, Any?>>())
    override suspend fun createStudySchedule(dto: com.shortscap.app.network.StudyScheduleDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun updateStudySchedule(scheduleId: Int, dto: com.shortscap.app.network.StudyScheduleDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun deleteStudySchedule(scheduleId: Int) = ApiResult.Success(Unit)
    override suspend fun startStudySession(dto: com.shortscap.app.network.StudySessionStartDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun endStudySession(sessionId: Int, cancelled: Boolean) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun listStudySessions() = ApiResult.Success(emptyList<Map<String, Any?>>())
    override suspend fun startBreak(sessionId: Int) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun endBreak(breakId: Int) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun syncAppUsage(records: List<com.shortscap.app.network.AppUsageRecordDto>) = ApiResult.Success(emptyList<Map<String, Any?>>())
    override suspend fun createMonitoringEvent(dto: com.shortscap.app.network.MonitoringEventDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun syncShortsUsage(records: List<com.shortscap.app.network.ShortsUsageRecordDto>) = ApiResult.Success(emptyList<Map<String, Any?>>())
    override suspend fun createShortsEvent(dto: com.shortscap.app.network.ShortsEventDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun getShortsControl() = ApiResult.Success(com.shortscap.app.network.ShortsControlDto())
    override suspend fun getShortsLimitCycle() = ApiResult.Success(null)
    override suspend fun listBlockedWebsites() = ApiResult.Success(emptyList<Map<String, Any?>>())
    override suspend fun createBlockedWebsite(dto: com.shortscap.app.network.BlockedWebsiteDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun createWebEvent(dto: com.shortscap.app.network.WebEventDto) = ApiResult.Success(emptyMap<String, Any?>())
    override suspend fun getReport(period: String, date: String?) = ApiResult.Success(com.shortscap.app.network.ReportDto(period))
    override suspend fun getScore(period: String, date: String?) = ApiResult.Success(com.shortscap.app.network.ScoreDto(score = 0, status = "insufficient_data"))
    override suspend fun getRank(period: String, date: String?, page: Int, pageSize: Int) =
        ApiResult.Success(com.shortscap.app.network.RankDto(period, null, null, null, null, 0, null, emptyList(), emptyList()))
}

class ShortsControlSyncerTest {

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    @Test
    fun `successful activate maps to SYNCED`() = runTest {
        val api = ScriptedControlApi()
        val syncer = ShortsControlSyncer(api)
        assertEquals(ShortsSyncStatus.SYNCED, syncer.syncActivate(200))
        assertTrue(api.activateCalled)
    }

    @Test
    fun `network failure maps to OFFLINE and never throws`() = runTest {
        val api = ScriptedControlApi().apply {
            activateResult = ApiResult.NetworkError("offline")
        }
        val syncer = ShortsControlSyncer(api)
        assertEquals(ShortsSyncStatus.OFFLINE, syncer.syncActivate(200))
    }

    @Test
    fun `server error maps to ERROR`() = runTest {
        val api = ScriptedControlApi().apply {
            activateResult = ApiResult.HttpError(500, "boom")
        }
        val syncer = ShortsControlSyncer(api)
        assertEquals(ShortsSyncStatus.ERROR, syncer.syncActivate(200))
    }

    @Test
    fun `edit limit pushes the threshold and keeps the count untouched locally`() = runTest {
        val api = ScriptedControlApi()
        val syncer = ShortsControlSyncer(api)
        assertEquals(ShortsSyncStatus.SYNCED, syncer.syncEditLimit(250))
        assertEquals(250, api.editLimit)
    }

    @Test
    fun `disable maps to SYNCED`() = runTest {
        val api = ScriptedControlApi()
        val syncer = ShortsControlSyncer(api)
        assertEquals(ShortsSyncStatus.SYNCED, syncer.syncDisable())
    }
}
