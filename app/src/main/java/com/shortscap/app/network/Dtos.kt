package com.shortscap.app.network

/**
 * DTOs for the Phase 16 synchronization layer — every payload maps 1:1 to a
 * backend FastAPI schema (see `backend/app/schemas/`). The Android client
 * serializes these to JSON for writes and parses them for reads; field names
 * must stay in sync with the backend schemas.
 *
 * Dates/times travel as ISO-8601 strings (e.g. `2026-08-15`,
 * `2026-08-15T09:30:00`), matching FastAPI's default datetime serialization.
 */

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

/** User settings — GET/PUT /settings. */
data class UserSettingsDto(
    val theme: String? = null,
    val language: String? = null,
    val notificationsEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val timezone: String? = null,
)

/** Monitoring settings — GET/PUT /settings/monitoring. */
data class MonitoringSettingsDto(
    val deviceMonitoringEnabled: Boolean? = null,
    val monitoringEnabled: Boolean? = null,
    val strictModeEnabled: Boolean? = null,
)

/** Shorts settings — GET/PUT /settings/shorts. */
data class ShortsSettingsDto(
    val dailyLimitMinutes: Int? = null,
    val dailyLimitCount: Int? = null,
    val warningMinutes: Int? = null,
    val warningCount: Int? = null,
    val strictModeEnabled: Boolean? = null,
    val isEnabled: Boolean? = null,
)

/** Notification preferences — GET/PUT /settings/notifications. */
data class NotificationPreferencesDto(
    val studyNotifications: Boolean? = null,
    val monitoringNotifications: Boolean? = null,
    val systemNotifications: Boolean? = null,
)

/** Leaderboard settings — GET/PUT /settings/leaderboard. */
data class LeaderboardSettingsDto(
    val isEnabled: Boolean? = null,
    val displayName: String? = null,
    val isOptedIn: Boolean? = null,
)

// ---------------------------------------------------------------------------
// Study
// ---------------------------------------------------------------------------

/** One study schedule — POST/GET/PUT /study/schedules (days_of_week is a
 * list of canonical 3-letter codes: "Mon", "Tue", …). */
data class StudyScheduleDto(
    val title: String,
    val subject: String? = null,
    val startTime: String? = null, // "HH:MM:SS"
    val durationMinutes: Int? = null,
    val daysOfWeek: List<String>? = null,
    val reminderMinutes: Int? = null,
    val isEnabled: Boolean = true,
)

/** Start a study session — POST /study/sessions/start. */
data class StudySessionStartDto(
    val scheduleId: Int? = null,
    val deviceId: Int? = null,
    val plannedDurationSeconds: Int? = null,
)

/** End/cancel a study session — POST /study/sessions/{id}/end. */
data class StudySessionEndDto(
    val cancelled: Boolean = false,
)

// ---------------------------------------------------------------------------
// Monitoring
// ---------------------------------------------------------------------------

/** One aggregated daily app-usage summary — POST /monitoring/app-usage/sync. */
data class AppUsageRecordDto(
    val deviceId: Int,
    val packageName: String,
    val appName: String? = null,
    val usageDate: String, // "YYYY-MM-DD"
    val durationSeconds: Int = 0,
    val launchCount: Int = 0,
)

/** One monitoring event — POST /monitoring/events. */
data class MonitoringEventDto(
    val deviceId: Int,
    val eventType: String,
    val appPackage: String? = null,
    val occurredAt: String? = null, // ISO-8601; null -> server now
    val metadataJson: Map<String, Any?>? = null,
)

// ---------------------------------------------------------------------------
// Shorts
// ---------------------------------------------------------------------------

/** One aggregated daily Shorts usage summary — POST /shorts/usage/sync.
 * `platform` / `surface` keep the cross-platform identity (Phase 11A). */
data class ShortsUsageRecordDto(
    val deviceId: Int,
    val usageDate: String, // "YYYY-MM-DD"
    val shortsCount: Int = 0,
    val durationSeconds: Int = 0,
    val warningTriggered: Boolean = false,
    val limitReached: Boolean = false,
    val platform: String? = null, // YOUTUBE / INSTAGRAM / ... / UNKNOWN
    val surface: String? = null, // YOUTUBE_SHORTS / ... / UNKNOWN
)

/** One Shorts event — POST /shorts/events. */
data class ShortsEventDto(
    val deviceId: Int,
    val eventType: String, // SHORT_STARTED / SHORT_COUNTED / SHORT_ENDED / WARNING_TRIGGERED / LIMIT_REACHED
    val occurredAt: String? = null,
    val durationSeconds: Int? = null,
    val metadataJson: Map<String, Any?>? = null,
)

// ---------------------------------------------------------------------------
// Web
// ---------------------------------------------------------------------------

/** One blocked website — POST/GET/PUT /websites/blocked. */
data class BlockedWebsiteDto(
    val domain: String,
    val isBlocked: Boolean = true,
    val verificationStatus: String? = null,
)

/** One website event — POST /web/events (BLOCK_ATTEMPT / BLOCKED / UNBLOCKED). */
data class WebEventDto(
    val deviceId: Int,
    val domain: String,
    val eventType: String,
    val blockedWebsiteId: Int? = null,
    val occurredAt: String? = null,
)

// ---------------------------------------------------------------------------
// Read-only retrieval (Reports / Score / Rank)
// ---------------------------------------------------------------------------

/** Parsed report envelope — GET /reports/{daily|weekly|monthly}. */
data class ReportDto(
    val period: String,
    val study: Map<String, Any?> = emptyMap(),
    val monitoring: Map<String, Any?> = emptyMap(),
    val shorts: Map<String, Any?> = emptyMap(),
    val web: Map<String, Any?> = emptyMap(),
)

/** Parsed score envelope — GET /score/{daily|weekly|monthly}. */
data class ScoreDto(
    val score: Int,
    val status: String,
    val components: List<Map<String, Any?>> = emptyList(),
    val explanation: Map<String, Any?> = emptyMap(),
)

/** One leaderboard row in the rank response. */
data class RankEntryDto(
    val rank: Int,
    val displayName: String,
    val score: Int,
    val userId: Int,
)

/** Parsed rank envelope — GET /rank/{weekly|monthly}. */
data class RankDto(
    val period: String,
    val yourRank: Int?,
    val yourScore: Int?,
    val yourScoreStatus: String?,
    val rankChange: Int?,
    val totalParticipants: Int,
    val winner: RankEntryDto?,
    val topThree: List<RankEntryDto>,
    val entries: List<RankEntryDto>,
)
