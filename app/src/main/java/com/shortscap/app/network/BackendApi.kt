package com.shortscap.app.network

/**
 * BackendApi — the typed surface of the FastAPI backend (Phase 16).
 *
 * Every call returns [ApiResult] (never throws for HTTP/network failures) so
 * syncers, read clients and UI always handle an explicit state. The
 * implementation ([HttpBackendApi]) performs real HTTP; tests use fakes.
 *
 * Periods/reads use the backend's string conventions: `period` is
 * "daily" / "weekly" / "monthly"; `date` is "YYYY-MM-DD" (null = today).
 */
interface BackendApi {

    // ---- Settings -------------------------------------------------------

    suspend fun getUserSettings(): ApiResult<UserSettingsDto>
    suspend fun updateUserSettings(dto: UserSettingsDto): ApiResult<UserSettingsDto>

    suspend fun getMonitoringSettings(): ApiResult<MonitoringSettingsDto>
    suspend fun updateMonitoringSettings(dto: MonitoringSettingsDto): ApiResult<MonitoringSettingsDto>

    suspend fun getShortsSettings(): ApiResult<ShortsSettingsDto>
    suspend fun updateShortsSettings(dto: ShortsSettingsDto): ApiResult<ShortsSettingsDto>

    suspend fun getNotificationPreferences(): ApiResult<NotificationPreferencesDto>
    suspend fun updateNotificationPreferences(dto: NotificationPreferencesDto): ApiResult<NotificationPreferencesDto>

    suspend fun getLeaderboardSettings(): ApiResult<LeaderboardSettingsDto>
    suspend fun updateLeaderboardSettings(dto: LeaderboardSettingsDto): ApiResult<LeaderboardSettingsDto>

    /** Last-known permission states — PUT /settings/permissions (sync mirror). */
    suspend fun syncPermissions(states: List<Map<String, Any?>>): ApiResult<List<Map<String, Any?>>>

    // ---- Study ----------------------------------------------------------

    suspend fun listStudySchedules(): ApiResult<List<Map<String, Any?>>>
    suspend fun createStudySchedule(dto: StudyScheduleDto): ApiResult<Map<String, Any?>>
    suspend fun updateStudySchedule(scheduleId: Int, dto: StudyScheduleDto): ApiResult<Map<String, Any?>>
    suspend fun deleteStudySchedule(scheduleId: Int): ApiResult<Unit>

    suspend fun startStudySession(dto: StudySessionStartDto): ApiResult<Map<String, Any?>>
    suspend fun endStudySession(sessionId: Int, cancelled: Boolean): ApiResult<Map<String, Any?>>
    suspend fun listStudySessions(): ApiResult<List<Map<String, Any?>>>

    suspend fun startBreak(sessionId: Int): ApiResult<Map<String, Any?>>
    suspend fun endBreak(breakId: Int): ApiResult<Map<String, Any?>>

    // ---- Monitoring -----------------------------------------------------

    suspend fun syncAppUsage(records: List<AppUsageRecordDto>): ApiResult<List<Map<String, Any?>>>
    suspend fun createMonitoringEvent(dto: MonitoringEventDto): ApiResult<Map<String, Any?>>

    // ---- Shorts ---------------------------------------------------------

    suspend fun syncShortsUsage(records: List<ShortsUsageRecordDto>): ApiResult<List<Map<String, Any?>>>
    suspend fun createShortsEvent(dto: ShortsEventDto): ApiResult<Map<String, Any?>>

    // ---- Shorts Control (24-hour limit cycle + HUD preference) ----------

    /** GET /shorts/control — combined Shorts Control state. */
    suspend fun getShortsControl(): ApiResult<ShortsControlDto>

    /** GET /shorts/limit-cycle — the current 24-hour cycle (404 -> null). */
    suspend fun getShortsLimitCycle(): ApiResult<ShortsLimitCycleDto?>

    /** POST /shorts/limit-cycle/activate — start (or return) the 24-hour cycle. */
    suspend fun activateShortsLimitCycle(limitCount: Int): ApiResult<ShortsLimitCycleDto?>

    /** PUT /shorts/control — threshold-only limit edit (count + timer kept). */
    suspend fun updateShortsControl(limitCount: Int): ApiResult<ShortsControlDto>

    /** POST /shorts/limit-cycle/disable — disable Shorts control. */
    suspend fun disableShortsLimitCycle(): ApiResult<ShortsLimitCycleDto?>

    // ---- Web ------------------------------------------------------------

    suspend fun listBlockedWebsites(): ApiResult<List<Map<String, Any?>>>
    suspend fun createBlockedWebsite(dto: BlockedWebsiteDto): ApiResult<Map<String, Any?>>
    suspend fun createWebEvent(dto: WebEventDto): ApiResult<Map<String, Any?>>

    // ---- Read-only: Reports / Score / Rank ------------------------------

    suspend fun getReport(period: String, date: String?): ApiResult<ReportDto>
    suspend fun getScore(period: String, date: String?): ApiResult<ScoreDto>
    suspend fun getRank(period: String, date: String?, page: Int, pageSize: Int): ApiResult<RankDto>
}
