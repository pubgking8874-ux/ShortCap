package com.shortscap.app.sync

import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.AppUsageRecordDto
import com.shortscap.app.network.BackendApi
import com.shortscap.app.network.BlockedWebsiteDto
import com.shortscap.app.network.LeaderboardSettingsDto
import com.shortscap.app.network.MonitoringEventDto
import com.shortscap.app.network.MonitoringSettingsDto
import com.shortscap.app.network.NotificationPreferencesDto
import com.shortscap.app.network.RankDto
import com.shortscap.app.network.RankEntryDto
import com.shortscap.app.network.ReportDto
import com.shortscap.app.network.ScoreDto
import com.shortscap.app.network.ShortsEventDto
import com.shortscap.app.network.ShortsSettingsDto
import com.shortscap.app.network.ShortsUsageRecordDto
import com.shortscap.app.network.StudyScheduleDto
import com.shortscap.app.network.StudySessionEndDto
import com.shortscap.app.network.StudySessionStartDto
import com.shortscap.app.network.UserSettingsDto
import com.shortscap.app.network.WebEventDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the Phase 16 sync layer ([SyncManager], [SyncQueue],
 * [RoutingDispatcher]) — pure Kotlin, no Android runtime, no network. A
 * controllable clock and scripted dispatcher make retry/backoff and
 * offline-first behavior deterministic (same style as
 * ShortsMonitoringPipelineTest).
 */
class SyncManagerTest {

    private var now = 0L

    private class ScriptedDispatcher(
        private val results: MutableList<() -> ApiResult<*>>,
    ) : SyncManager.SyncDispatcher {
        var calls = 0
        override suspend fun dispatch(record: SyncRecord): ApiResult<*> {
            calls++
            val next = if (results.size > 1) results.removeAt(0) else results.first()
            return next()
        }
    }

    private fun manager(
        dispatcher: SyncManager.SyncDispatcher,
        maxAttempts: Int = 3,
    ): SyncManager {
        now = 0L
        return SyncManager(
            queue = InMemorySyncQueue(),
            dispatcher = dispatcher,
            maxAttempts = maxAttempts,
            baseBackoffMillis = 1_000L,
            clock = { now },
        )
    }

    private fun record(kind: SyncKind = SyncKind.MONITORING_EVENT) = SyncRecord(
        kind = kind,
        key = "test:$kind:${System.nanoTime()}",
        payload = "{}",
    )

    // ------------------------------------------------------------------
    // States + dedupe
    // ------------------------------------------------------------------

    @Test
    fun `successful sync removes the record from the queue`() = runTest {
        val m = manager(ScriptedDispatcher(mutableListOf({ ApiResult.Success(Unit) })))
        val r = record()
        assertTrue("enqueued", m.enqueue(r))
        assertTrue("queued before sync", m.isQueued(r.key))

        val results = m.syncNow()
        assertEquals(SyncState.SYNCED, results.single().state)
        assertFalse("removed after sync", m.isQueued(r.key))
        assertEquals(0, m.queue.size())
    }

    @Test
    fun `enqueue is idempotent by key - no duplicate records`() {
        val queue = InMemorySyncQueue()
        val r1 = SyncRecord(SyncKind.MONITORING_EVENT, "key-1", "{}")
        val r2 = SyncRecord(SyncKind.MONITORING_EVENT, "key-1", "{}")
        assertTrue(queue.enqueue(r1))
        assertFalse("same key not enqueued twice", queue.enqueue(r2))
        assertEquals(1, queue.size())
        assertEquals("key-1", queue.pending().single().key)
    }

    @Test
    fun `different keys enqueue separately`() {
        val queue = InMemorySyncQueue()
        queue.enqueue(SyncRecord(SyncKind.SHORTS_USAGE, "shorts:a", "{}"))
        queue.enqueue(SyncRecord(SyncKind.SHORTS_USAGE, "shorts:b", "{}"))
        assertEquals(2, queue.size())
    }

    // ------------------------------------------------------------------
    // Retry / backoff (Phase 16 §13)
    // ------------------------------------------------------------------

    @Test
    fun `transient failure retries with backoff then succeeds`() = runTest {
        var attempts = 0
        val dispatcher = SyncManager.SyncDispatcher {
            attempts++
            if (attempts <= 2) ApiResult.NetworkError("offline") else ApiResult.Success(Unit)
        }
        val m = manager(dispatcher)
        val r = record()
        m.enqueue(r)

        // 1st attempt fails transiently -> retry scheduled in the future.
        var results = m.syncNow()
        assertEquals(SyncState.PENDING, results.single().state)
        assertTrue("backoff scheduled", m.queue.pending().single().nextRetryAtMillis > now)

        // Retry before the backoff elapses -> skipped.
        now += 500
        results = m.syncNow()
        assertEquals("skipped before backoff", 0, results.size)

        // Retry after backoff -> fails once more, then succeeds on the 3rd.
        now += 1_000
        results = m.syncNow()
        assertEquals(SyncState.PENDING, results.single().state)
        now += 2_000
        results = m.syncNow()
        assertEquals(SyncState.SYNCED, results.single().state)
        assertFalse(m.isQueued(r.key))
    }

    @Test
    fun `transient failure exhausts retries into permanent FAILED`() = runTest {
        val dispatcher = SyncManager.SyncDispatcher { ApiResult.NetworkError("down") }
        val m = manager(dispatcher, maxAttempts = 2)
        val r = record()
        m.enqueue(r)

        m.syncNow() // attempt 1 -> retry
        now += 2_000
        m.syncNow() // attempt 2 -> retry
        now += 4_000
        val results = m.syncNow() // attempt 3 -> permanent FAILED
        assertEquals(SyncState.FAILED, results.single().state)

        // Never retried again (nextRetryAtMillis = Long.MAX_VALUE).
        now += 1_000_000
        val after = m.syncNow()
        assertEquals("no endless retry", 0, after.size)
        assertEquals(1, m.queue.size())
    }

    @Test
    fun `permanent 4xx failure is FAILED immediately and never retried`() = runTest {
        val dispatcher = SyncManager.SyncDispatcher { ApiResult.HttpError(422, "bad payload") }
        val m = manager(dispatcher)
        val r = record()
        m.enqueue(r)

        val results = m.syncNow()
        assertEquals(SyncState.FAILED, results.single().state)
        now += 10_000
        assertEquals("4xx never retried", 0, m.syncNow().size)
    }

    @Test
    fun `5xx is transient and retried`() = runTest {
        var attempts = 0
        val dispatcher = SyncManager.SyncDispatcher {
            attempts++
            if (attempts == 1) ApiResult.HttpError(503, "unavailable") else ApiResult.Success(Unit)
        }
        val m = manager(dispatcher)
        m.enqueue(record())
        assertEquals(SyncState.PENDING, m.syncNow().single().state)
        now += 2_000
        assertEquals(SyncState.SYNCED, m.syncNow().single().state)
    }

    // ------------------------------------------------------------------
    // Offline-first (Phase 16 §12)
    // ------------------------------------------------------------------

    @Test
    fun `records captured while offline are kept and synced when back online`() = runTest {
        var online = false
        val dispatcher = SyncManager.SyncDispatcher {
            if (online) ApiResult.Success(Unit) else ApiResult.NetworkError("offline")
        }
        val m = manager(dispatcher)
        m.enqueue(record(SyncKind.MONITORING_USAGE))
        m.enqueue(record(SyncKind.SHORTS_USAGE))
        m.enqueue(record(SyncKind.WEB_EVENT))

        // Offline: 3 records captured, none lost.
        assertEquals(3, m.queue.size())
        m.syncNow() // all fail transiently
        assertEquals("nothing discarded while offline", 3, m.queue.size())

        // Back online after backoff: everything syncs.
        now += 2_000
        online = true
        val results = m.syncNow()
        assertEquals(3, results.size)
        assertTrue(results.all { it.state == SyncState.SYNCED })
        assertEquals(0, m.queue.size())
    }

    // ------------------------------------------------------------------
    // RoutingDispatcher payload round-trips (builders -> parsers)
    // ------------------------------------------------------------------

    @Test
    fun `settings payloads round-trip through the dispatcher`() = runTest {
        var received: UserSettingsDto? = null
        var receivedMonitoring: MonitoringSettingsDto? = null
        val api = RecordingBackendApi().apply {
            onUserSettings = { received = it }
            onMonitoringSettings = { receivedMonitoring = it }
        }
        val dispatcher = RoutingDispatcher(api)

        dispatcher.dispatch(
            SettingsSyncer.userSettings(
                mapOf("theme" to "dark", "language" to "en", "soundEnabled" to true)
            )
        )
        dispatcher.dispatch(
            SettingsSyncer.monitoringSettings(
                mapOf("monitoringEnabled" to true, "strictModeEnabled" to true)
            )
        )
        assertEquals("dark", received?.theme)
        assertEquals("en", received?.language)
        assertEquals(true, received?.soundEnabled)
        assertEquals(true, receivedMonitoring?.monitoringEnabled)
        assertEquals(true, receivedMonitoring?.strictModeEnabled)
    }

    @Test
    fun `shorts and web payloads round-trip through the dispatcher`() = runTest {
        var shorts: ShortsUsageRecordDto? = null
        var web: WebEventDto? = null
        val api = RecordingBackendApi().apply {
            onShortsUsage = { shorts = it.single() }
            onWebEvent = { web = it }
        }
        val dispatcher = RoutingDispatcher(api)

        dispatcher.dispatch(
            ShortsSyncer.usage(
                ShortsUsageRecordDto(
                    deviceId = 7,
                    usageDate = "2026-08-15",
                    shortsCount = 3,
                    durationSeconds = 120,
                    platform = "YOUTUBE",
                    surface = "YOUTUBE_SHORTS",
                )
            )
        )
        dispatcher.dispatch(
            WebSyncer.event(
                WebEventDto(deviceId = 7, domain = "tiktok.com", eventType = "BLOCK_ATTEMPT")
            )
        )
        assertEquals(7, shorts?.deviceId)
        assertEquals("2026-08-15", shorts?.usageDate)
        assertEquals("YOUTUBE", shorts?.platform)
        assertEquals("YOUTUBE_SHORTS", shorts?.surface)
        assertEquals("tiktok.com", web?.domain)
        assertEquals("BLOCK_ATTEMPT", web?.eventType)
    }

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}

/** Records the last DTO received per endpoint for round-trip assertions. */
private class RecordingBackendApi : BackendApi {

    var onUserSettings: (UserSettingsDto) -> Unit = {}
    var onMonitoringSettings: (MonitoringSettingsDto) -> Unit = {}
    var onShortsUsage: (List<ShortsUsageRecordDto>) -> Unit = {}
    var onWebEvent: (WebEventDto) -> Unit = {}

    override suspend fun getUserSettings(): ApiResult<UserSettingsDto> =
        ApiResult.Success(UserSettingsDto())
    override suspend fun updateUserSettings(dto: UserSettingsDto): ApiResult<UserSettingsDto> {
        onUserSettings(dto)
        return ApiResult.Success(dto)
    }
    override suspend fun getMonitoringSettings(): ApiResult<MonitoringSettingsDto> =
        ApiResult.Success(MonitoringSettingsDto())
    override suspend fun updateMonitoringSettings(dto: MonitoringSettingsDto): ApiResult<MonitoringSettingsDto> {
        onMonitoringSettings(dto)
        return ApiResult.Success(dto)
    }
    override suspend fun getShortsSettings(): ApiResult<ShortsSettingsDto> =
        ApiResult.Success(ShortsSettingsDto())
    override suspend fun updateShortsSettings(dto: ShortsSettingsDto): ApiResult<ShortsSettingsDto> =
        ApiResult.Success(dto)
    override suspend fun getNotificationPreferences(): ApiResult<NotificationPreferencesDto> =
        ApiResult.Success(NotificationPreferencesDto())
    override suspend fun updateNotificationPreferences(dto: NotificationPreferencesDto): ApiResult<NotificationPreferencesDto> =
        ApiResult.Success(dto)
    override suspend fun getLeaderboardSettings(): ApiResult<LeaderboardSettingsDto> =
        ApiResult.Success(LeaderboardSettingsDto())
    override suspend fun updateLeaderboardSettings(dto: LeaderboardSettingsDto): ApiResult<LeaderboardSettingsDto> =
        ApiResult.Success(dto)
    override suspend fun syncPermissions(states: List<Map<String, Any?>>): ApiResult<List<Map<String, Any?>>> =
        ApiResult.Success(states)
    override suspend fun listStudySchedules(): ApiResult<List<Map<String, Any?>>> =
        ApiResult.Success(emptyList())
    override suspend fun createStudySchedule(dto: StudyScheduleDto): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun updateStudySchedule(scheduleId: Int, dto: StudyScheduleDto): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun deleteStudySchedule(scheduleId: Int): ApiResult<Unit> =
        ApiResult.Success(Unit)
    override suspend fun startStudySession(dto: StudySessionStartDto): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun endStudySession(sessionId: Int, cancelled: Boolean): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun listStudySessions(): ApiResult<List<Map<String, Any?>>> =
        ApiResult.Success(emptyList())
    override suspend fun startBreak(sessionId: Int): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun endBreak(breakId: Int): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun syncAppUsage(records: List<AppUsageRecordDto>): ApiResult<List<Map<String, Any?>>> =
        ApiResult.Success(emptyList())
    override suspend fun createMonitoringEvent(dto: MonitoringEventDto): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun syncShortsUsage(records: List<ShortsUsageRecordDto>): ApiResult<List<Map<String, Any?>>> {
        onShortsUsage(records)
        return ApiResult.Success(emptyList())
    }
    override suspend fun createShortsEvent(dto: ShortsEventDto): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun listBlockedWebsites(): ApiResult<List<Map<String, Any?>>> =
        ApiResult.Success(emptyList())
    override suspend fun createBlockedWebsite(dto: BlockedWebsiteDto): ApiResult<Map<String, Any?>> =
        ApiResult.Success(emptyMap())
    override suspend fun createWebEvent(dto: WebEventDto): ApiResult<Map<String, Any?>> {
        onWebEvent(dto)
        return ApiResult.Success(emptyMap())
    }
    override suspend fun getReport(period: String, date: String?): ApiResult<ReportDto> =
        ApiResult.Success(ReportDto(period))
    override suspend fun getScore(period: String, date: String?): ApiResult<ScoreDto> =
        ApiResult.Success(ScoreDto(score = 0, status = "insufficient_data"))
    override suspend fun getRank(period: String, date: String?, page: Int, pageSize: Int): ApiResult<RankDto> =
        ApiResult.Success(RankDto(period, null, null, null, null, 0, null, emptyList(), emptyList()))
}
