package com.shortscap.app.sync

import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.AppUsageRecordDto
import com.shortscap.app.network.BackendApi
import com.shortscap.app.network.BlockedWebsiteDto
import com.shortscap.app.network.LeaderboardSettingsDto
import com.shortscap.app.network.MonitoringEventDto
import com.shortscap.app.network.MonitoringSettingsDto
import com.shortscap.app.network.NotificationPreferencesDto
import com.shortscap.app.network.ShortsEventDto
import com.shortscap.app.network.ShortsSettingsDto
import com.shortscap.app.network.ShortsUsageRecordDto
import com.shortscap.app.network.StudyScheduleDto
import com.shortscap.app.network.StudySessionEndDto
import com.shortscap.app.network.StudySessionStartDto
import com.shortscap.app.network.UserSettingsDto
import com.shortscap.app.network.WebEventDto

/**
 * Syncers — build [SyncRecord]s from local app data and route them to the
 * backend through [BackendApi]. One [RoutingDispatcher] implements
 * [SyncManager.SyncDispatcher] for EVERY [SyncKind], so the manager stays a
 * single generic loop and no per-feature retry systems exist.
 *
 * Conflict policy (Phase 16 §7/§21):
 *  - mutable settings: a local user change is authoritative immediately;
 *    the successful backend response confirms persistence. During initial
 *    sync, server values populate local state. A fresh local change is never
 *    silently overwritten by an older server value.
 *  - append/sync data (usage, events): backend idempotency + the record's
 *    idempotency key prevent duplicates; records are never silently dropped.
 */

// ---------------------------------------------------------------------------
// Record builders (local data -> SyncRecord)
// ---------------------------------------------------------------------------

object SettingsSyncer {

    fun userSettings(partial: Map<String, Any?>): SyncRecord = SyncRecord(
        kind = SyncKind.SETTINGS_USER,
        key = "settings:user",
        payload = SyncJson.objectOf(
            "theme" to partial["theme"],
            "language" to partial["language"],
            "notifications_enabled" to partial["notificationsEnabled"],
            "sound_enabled" to partial["soundEnabled"],
            "timezone" to partial["timezone"],
        ),
    )

    fun monitoringSettings(partial: Map<String, Any?>): SyncRecord = SyncRecord(
        kind = SyncKind.SETTINGS_MONITORING,
        key = "settings:monitoring",
        payload = SyncJson.objectOf(
            "device_monitoring_enabled" to partial["deviceMonitoringEnabled"],
            "monitoring_enabled" to partial["monitoringEnabled"],
            "strict_mode_enabled" to partial["strictModeEnabled"],
        ),
    )

    fun shortsSettings(partial: Map<String, Any?>): SyncRecord = SyncRecord(
        kind = SyncKind.SETTINGS_SHORTS,
        key = "settings:shorts",
        payload = SyncJson.objectOf(
            "daily_limit_minutes" to partial["dailyLimitMinutes"],
            "daily_limit_count" to partial["dailyLimitCount"],
            "warning_minutes" to partial["warningMinutes"],
            "warning_count" to partial["warningCount"],
            "strict_mode_enabled" to partial["strictModeEnabled"],
            "is_enabled" to partial["isEnabled"],
        ),
    )

    fun notificationPreferences(partial: Map<String, Any?>): SyncRecord = SyncRecord(
        kind = SyncKind.SETTINGS_NOTIFICATIONS,
        key = "settings:notifications",
        payload = SyncJson.objectOf(
            "study_notifications" to partial["studyNotifications"],
            "monitoring_notifications" to partial["monitoringNotifications"],
            "system_notifications" to partial["systemNotifications"],
        ),
    )

    fun leaderboardSettings(partial: Map<String, Any?>): SyncRecord = SyncRecord(
        kind = SyncKind.SETTINGS_LEADERBOARD,
        key = "settings:leaderboard",
        payload = SyncJson.objectOf(
            "is_enabled" to partial["isEnabled"],
            "display_name" to partial["displayName"],
            "is_opted_in" to partial["isOptedIn"],
        ),
    )
}

object StudySyncer {

    fun createSchedule(schedule: StudyScheduleDto): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_SCHEDULE_CREATE,
        key = "study:schedule:create:${schedule.title.lowercase()}",
        payload = SyncJson.objectOf(
            "title" to schedule.title,
            "subject" to schedule.subject,
            "start_time" to schedule.startTime,
            "duration_minutes" to schedule.durationMinutes,
            "days_of_week" to schedule.daysOfWeek,
            "reminder_minutes" to schedule.reminderMinutes,
            "is_enabled" to schedule.isEnabled,
        ),
    )

    fun updateSchedule(scheduleId: Int, schedule: StudyScheduleDto): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_SCHEDULE_UPDATE,
        key = "study:schedule:update:$scheduleId",
        payload = SyncJson.objectOf(
            "id" to scheduleId,
            "title" to schedule.title,
            "subject" to schedule.subject,
            "start_time" to schedule.startTime,
            "duration_minutes" to schedule.durationMinutes,
            "days_of_week" to schedule.daysOfWeek,
            "reminder_minutes" to schedule.reminderMinutes,
            "is_enabled" to schedule.isEnabled,
        ),
    )

    fun deleteSchedule(scheduleId: Int): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_SCHEDULE_DELETE,
        key = "study:schedule:delete:$scheduleId",
        payload = SyncJson.objectOf("id" to scheduleId),
    )

    fun sessionStart(start: StudySessionStartDto): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_SESSION_START,
        key = "study:session:start:${start.scheduleId ?: "adhoc"}:${System.currentTimeMillis()}",
        payload = SyncJson.objectOf(
            "schedule_id" to start.scheduleId,
            "device_id" to start.deviceId,
            "planned_duration_seconds" to start.plannedDurationSeconds,
        ),
    )

    fun sessionEnd(sessionId: Int, cancelled: Boolean): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_SESSION_END,
        key = "study:session:end:$sessionId",
        payload = SyncJson.objectOf("id" to sessionId, "cancelled" to cancelled),
    )

    fun breakStart(sessionId: Int): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_BREAK_START,
        key = "study:break:start:$sessionId:${System.currentTimeMillis()}",
        payload = SyncJson.objectOf("study_session_id" to sessionId),
    )

    fun breakEnd(breakId: Int): SyncRecord = SyncRecord(
        kind = SyncKind.STUDY_BREAK_END,
        key = "study:break:end:$breakId",
        payload = SyncJson.objectOf("break_id" to breakId),
    )
}

object MonitoringSyncer {

    /** One record per usage summary; the backend batch endpoint is called
     * with a single-item batch per record (idempotent upsert per key). */
    fun usage(record: AppUsageRecordDto): SyncRecord = SyncRecord(
        kind = SyncKind.MONITORING_USAGE,
        key = "monitoring:usage:${record.deviceId}:${record.packageName}:${record.usageDate}",
        payload = SyncJson.objectOf(
            "device_id" to record.deviceId,
            "package_name" to record.packageName,
            "app_name" to record.appName,
            "usage_date" to record.usageDate,
            "duration_seconds" to record.durationSeconds,
            "launch_count" to record.launchCount,
        ),
    )

    fun event(event: MonitoringEventDto): SyncRecord = SyncRecord(
        kind = SyncKind.MONITORING_EVENT,
        key = "monitoring:event:${event.deviceId}:${event.eventType}:${event.occurredAt ?: System.currentTimeMillis()}",
        payload = SyncJson.objectOf(
            "device_id" to event.deviceId,
            "event_type" to event.eventType,
            "app_package" to event.appPackage,
            "occurred_at" to event.occurredAt,
            "metadata_json" to event.metadataJson,
        ),
    )
}

object ShortsSyncer {

    /** One daily usage summary per (device, platform, surface, date). */
    fun usage(record: ShortsUsageRecordDto): SyncRecord = SyncRecord(
        kind = SyncKind.SHORTS_USAGE,
        key = "shorts:usage:${record.deviceId}:${record.usageDate}:${record.platform ?: "UNKNOWN"}:${record.surface ?: "UNKNOWN"}",
        payload = SyncJson.objectOf(
            "device_id" to record.deviceId,
            "usage_date" to record.usageDate,
            "shorts_count" to record.shortsCount,
            "duration_seconds" to record.durationSeconds,
            "warning_triggered" to record.warningTriggered,
            "limit_reached" to record.limitReached,
            "platform" to record.platform,
            "surface" to record.surface,
        ),
    )

    fun event(event: ShortsEventDto): SyncRecord = SyncRecord(
        kind = SyncKind.SHORTS_EVENT,
        key = "shorts:event:${event.deviceId}:${event.eventType}:${event.occurredAt ?: System.currentTimeMillis()}",
        payload = SyncJson.objectOf(
            "device_id" to event.deviceId,
            "event_type" to event.eventType,
            "occurred_at" to event.occurredAt,
            "duration_seconds" to event.durationSeconds,
            "metadata_json" to event.metadataJson,
        ),
    )
}

object WebSyncer {

    fun blockedWebsite(website: BlockedWebsiteDto): SyncRecord = SyncRecord(
        kind = SyncKind.WEB_EVENT, // reuses the web-event route semantics below
        key = "web:blocked:${website.domain}",
        payload = SyncJson.objectOf(
            "domain" to website.domain,
            "is_blocked" to website.isBlocked,
            "verification_status" to website.verificationStatus,
        ),
    )

    fun event(event: WebEventDto): SyncRecord = SyncRecord(
        kind = SyncKind.WEB_EVENT,
        key = "web:event:${event.deviceId}:${event.eventType}:${event.domain}:${event.occurredAt ?: System.currentTimeMillis()}",
        payload = SyncJson.objectOf(
            "device_id" to event.deviceId,
            "domain" to event.domain,
            "event_type" to event.eventType,
            "blocked_website_id" to event.blockedWebsiteId,
            "occurred_at" to event.occurredAt,
        ),
    )
}

// ---------------------------------------------------------------------------
// Dispatcher: routes every SyncKind to the correct BackendApi call
// ---------------------------------------------------------------------------

/**
 * Routes queued records to the backend. Payloads are parsed back into DTOs
 * from the JSON the builders produced (they share the same field names as
 * the backend schemas).
 */
class RoutingDispatcher(
    private val api: BackendApi,
) : SyncManager.SyncDispatcher {

    override suspend fun dispatch(record: SyncRecord): ApiResult<*> = when (record.kind) {
        SyncKind.SETTINGS_USER -> api.updateUserSettings(parseUserSettings(record.payload))
        SyncKind.SETTINGS_MONITORING -> api.updateMonitoringSettings(parseMonitoringSettings(record.payload))
        SyncKind.SETTINGS_SHORTS -> api.updateShortsSettings(parseShortsSettings(record.payload))
        SyncKind.SETTINGS_NOTIFICATIONS -> api.updateNotificationPreferences(parseNotifications(record.payload))
        SyncKind.SETTINGS_LEADERBOARD -> api.updateLeaderboardSettings(parseLeaderboard(record.payload))
        SyncKind.STUDY_SCHEDULE_CREATE -> api.createStudySchedule(parseSchedule(record.payload))
        SyncKind.STUDY_SCHEDULE_UPDATE -> api.updateStudySchedule(intField(record.payload, "id"), parseSchedule(record.payload))
        SyncKind.STUDY_SCHEDULE_DELETE -> api.deleteStudySchedule(intField(record.payload, "id"))
        SyncKind.SETTINGS_PERMISSIONS -> api.syncPermissions(parsePermissionStates(record.payload))
        SyncKind.STUDY_SESSION_START -> api.startStudySession(parseSessionStart(record.payload))
        SyncKind.STUDY_SESSION_END -> api.endStudySession(
            intField(record.payload, "id"),
            (field(record.payload, "cancelled") as? Boolean) ?: false,
        )
        SyncKind.STUDY_BREAK_START -> api.startBreak(intField(record.payload, "study_session_id"))
        SyncKind.STUDY_BREAK_END -> api.endBreak(intField(record.payload, "break_id"))
        SyncKind.MONITORING_USAGE -> api.syncAppUsage(listOf(parseAppUsage(record.payload)))
        SyncKind.MONITORING_EVENT -> api.createMonitoringEvent(parseMonitoringEvent(record.payload))
        SyncKind.SHORTS_USAGE -> api.syncShortsUsage(listOf(parseShortsUsage(record.payload)))
        SyncKind.SHORTS_EVENT -> api.createShortsEvent(parseShortsEvent(record.payload))
        SyncKind.WEB_EVENT -> api.createWebEvent(parseWebEvent(record.payload))
    }

    // ---- payload parsers (mirror of the builders above) ----

    private fun parseUserSettings(p: String) = UserSettingsDto(
        theme = field(p, "theme") as? String,
        language = field(p, "language") as? String,
        notificationsEnabled = field(p, "notifications_enabled") as? Boolean,
        soundEnabled = field(p, "sound_enabled") as? Boolean,
        timezone = field(p, "timezone") as? String,
    )

    private fun parseMonitoringSettings(p: String) = MonitoringSettingsDto(
        deviceMonitoringEnabled = field(p, "device_monitoring_enabled") as? Boolean,
        monitoringEnabled = field(p, "monitoring_enabled") as? Boolean,
        strictModeEnabled = field(p, "strict_mode_enabled") as? Boolean,
    )

    private fun parseShortsSettings(p: String) = ShortsSettingsDto(
        dailyLimitMinutes = (field(p, "daily_limit_minutes") as? Number)?.toInt(),
        dailyLimitCount = (field(p, "daily_limit_count") as? Number)?.toInt(),
        warningMinutes = (field(p, "warning_minutes") as? Number)?.toInt(),
        warningCount = (field(p, "warning_count") as? Number)?.toInt(),
        strictModeEnabled = field(p, "strict_mode_enabled") as? Boolean,
        isEnabled = field(p, "is_enabled") as? Boolean,
    )

    private fun parseNotifications(p: String) = NotificationPreferencesDto(
        studyNotifications = field(p, "study_notifications") as? Boolean,
        monitoringNotifications = field(p, "monitoring_notifications") as? Boolean,
        systemNotifications = field(p, "system_notifications") as? Boolean,
    )

    private fun parseLeaderboard(p: String) = LeaderboardSettingsDto(
        isEnabled = field(p, "is_enabled") as? Boolean,
        displayName = field(p, "display_name") as? String,
        isOptedIn = field(p, "is_opted_in") as? Boolean,
    )

    private fun parseSchedule(p: String) = StudyScheduleDto(
        title = (field(p, "title") as? String).orEmpty(),
        subject = field(p, "subject") as? String,
        startTime = field(p, "start_time") as? String,
        durationMinutes = (field(p, "duration_minutes") as? Number)?.toInt(),
        daysOfWeek = (field(p, "days_of_week") as? List<*>?)?.map { it.toString() },
        reminderMinutes = (field(p, "reminder_minutes") as? Number)?.toInt(),
        isEnabled = (field(p, "is_enabled") as? Boolean) ?: true,
    )

    private fun parseSessionStart(p: String) = StudySessionStartDto(
        scheduleId = (field(p, "schedule_id") as? Number)?.toInt(),
        deviceId = (field(p, "device_id") as? Number)?.toInt(),
        plannedDurationSeconds = (field(p, "planned_duration_seconds") as? Number)?.toInt(),
    )

    private fun parseEnd(p: String) = StudySessionEndDto(
        cancelled = (field(p, "cancelled") as? Boolean) ?: false,
    )

    private fun parseAppUsage(p: String) = AppUsageRecordDto(
        deviceId = (field(p, "device_id") as? Number)?.toInt() ?: 0,
        packageName = (field(p, "package_name") as? String).orEmpty(),
        appName = field(p, "app_name") as? String,
        usageDate = (field(p, "usage_date") as? String).orEmpty(),
        durationSeconds = (field(p, "duration_seconds") as? Number)?.toInt() ?: 0,
        launchCount = (field(p, "launch_count") as? Number)?.toInt() ?: 0,
    )

    private fun parseMonitoringEvent(p: String) = MonitoringEventDto(
        deviceId = (field(p, "device_id") as? Number)?.toInt() ?: 0,
        eventType = (field(p, "event_type") as? String).orEmpty(),
        appPackage = field(p, "app_package") as? String,
        occurredAt = field(p, "occurred_at") as? String,
        metadataJson = field(p, "metadata_json") as? Map<String, Any?>,
    )

    private fun parseShortsUsage(p: String) = ShortsUsageRecordDto(
        deviceId = (field(p, "device_id") as? Number)?.toInt() ?: 0,
        usageDate = (field(p, "usage_date") as? String).orEmpty(),
        shortsCount = (field(p, "shorts_count") as? Number)?.toInt() ?: 0,
        durationSeconds = (field(p, "duration_seconds") as? Number)?.toInt() ?: 0,
        warningTriggered = (field(p, "warning_triggered") as? Boolean) ?: false,
        limitReached = (field(p, "limit_reached") as? Boolean) ?: false,
        platform = field(p, "platform") as? String,
        surface = field(p, "surface") as? String,
    )

    private fun parseShortsEvent(p: String) = ShortsEventDto(
        deviceId = (field(p, "device_id") as? Number)?.toInt() ?: 0,
        eventType = (field(p, "event_type") as? String).orEmpty(),
        occurredAt = field(p, "occurred_at") as? String,
        durationSeconds = (field(p, "duration_seconds") as? Number)?.toInt(),
        metadataJson = field(p, "metadata_json") as? Map<String, Any?>,
    )

    private fun parsePermissionStates(p: String): List<Map<String, Any?>> =
        listOf(mapOf("permission_key" to (field(p, "permission_key") as? String).orEmpty()))

    private fun parseWebEvent(p: String) = WebEventDto(
        deviceId = (field(p, "device_id") as? Number)?.toInt() ?: 0,
        domain = (field(p, "domain") as? String).orEmpty(),
        eventType = (field(p, "event_type") as? String).orEmpty(),
        blockedWebsiteId = (field(p, "blocked_website_id") as? Number)?.toInt(),
        occurredAt = field(p, "occurred_at") as? String,
    )

    private fun intField(payload: String, name: String): Int =
        (field(payload, name) as? Number)?.toInt() ?: 0

    /** Extract one field from the record's JSON payload. */
    private fun field(payload: String, name: String): Any? {
        val marker = "\"$name\":"
        val index = payload.indexOf(marker)
        if (index < 0) return null
        val start = index + marker.length
        var i = start
        // Skip whitespace
        while (i < payload.length && payload[i].isWhitespace()) i++
        if (i >= payload.length) return null
        return when (val c = payload[i]) {
            '"' -> {
                val sb = StringBuilder()
                var j = i + 1
                while (j < payload.length) {
                    val ch = payload[j]
                    if (ch == '\\' && j + 1 < payload.length) {
                        sb.append(payload[j + 1])
                        j += 2
                        continue
                    }
                    if (ch == '"') break
                    sb.append(ch)
                    j++
                }
                sb.toString()
            }
            't' -> true
            'f' -> false
            'n' -> null
            '[' -> {
                val end = payload.indexOf(']', i)
                if (end < 0) null else payload.substring(i + 1, end)
                    .split(",")
                    .map { it.trim().trim('"') }
            }
            else -> {
                var j = i
                while (j < payload.length && (payload[j].isDigit() || payload[j] == '-' || payload[j] == '.')) j++
                payload.substring(i, j).toDoubleOrNull()
            }
        }
    }
}
