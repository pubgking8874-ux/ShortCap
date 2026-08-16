package com.shortscap.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * HttpBackendApi — real HTTP implementation of [BackendApi].
 *
 * Uses `HttpURLConnection` + `org.json` — the same networking primitives the
 * existing Android code already uses (FaviconRepository / DomainVerifier),
 * so no new HTTP/JSON dependency is introduced. All calls run on
 * [Dispatchers.IO]; every failure is mapped to an [ApiResult] (never
 * thrown).
 *
 * Centralized here:
 *  - base URL from [BackendConfig.baseUrl] (emulator -> 10.0.2.2 for local dev)
 *  - timeouts from [BackendConfig]
 *  - the TEMPORARY development identity header (X-Dev-User-Id) — the single
 *    place Android sends it; Cognito replaces it later without touching
 *    syncers/UI
 *  - JSON serialization/parsing and error mapping
 *
 * No credentials, tokens or secrets are ever logged or exposed.
 */
class HttpBackendApi(
    private val config: BackendConfig = BackendConfig,
) : BackendApi {

    // ------------------------------------------------------------------
    // Generic HTTP plumbing
    // ------------------------------------------------------------------

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        query: Map<String, String> = emptyMap(),
    ): ApiResult<String?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = buildUrl(path, query)
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = config.CONNECT_TIMEOUT_MS
                readTimeout = config.READ_TIMEOUT_MS
                // TEMPORARY DEVELOPMENT IDENTITY (Cognito replaces this later).
                // Sent ONLY in debug builds — release builds fail closed and
                // never send the dev identity header (see BackendConfig).
                if (config.devIdentityEnabled) {
                    setRequestProperty(config.DEV_USER_ID_HEADER, config.devUserId)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status in 200..299) {
                ApiResult.Success(if (raw.isBlank()) null else raw)
            } else {
                ApiResult.HttpError(status, sanitize(raw))
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.NetworkError("Request timed out")
        } catch (e: java.net.ConnectException) {
            ApiResult.NetworkError("Backend unreachable (${e.message ?: "connection refused"})")
        } catch (e: java.net.UnknownHostException) {
            ApiResult.NetworkError("Backend host not found")
        } catch (e: Exception) {
            ApiResult.NetworkError(e.message ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }

    /** Builds "baseUrl + path" with percent-encoded query params. */
    private fun buildUrl(path: String, query: Map<String, String>): String {
        val base = config.baseUrl.trimEnd('/')
        if (query.isEmpty()) return "$base$path"
        val encoded = query.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$base$path?$encoded"
    }

    /** Strip anything that could be a credential/URL/stack detail from an
     * error body before it reaches the UI. */
    private fun sanitize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // FastAPI error bodies are JSON: keep only the "detail" text.
        return try {
            JSONObject(trimmed).optString("detail").takeIf { it.isNotBlank() } ?: trimmed.take(200)
        } catch (_: Exception) {
            trimmed.take(200)
        }
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    private fun parseObject(raw: String?): Map<String, Any?> =
        if (raw.isNullOrBlank()) emptyMap()
        else try {
            jsonToMap(JSONObject(raw))
        } catch (_: Exception) {
            emptyMap()
        }

    private fun parseList(raw: String?): List<Map<String, Any?>> =
        if (raw.isNullOrBlank()) emptyList()
        else try {
            val array = JSONArray(raw)
            (0 until array.length()).map { jsonToMap(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }

    private fun jsonToMap(obj: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = when (val value = obj.opt(key)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> (0 until value.length()).map { idx ->
                    val item = value.opt(idx)
                    if (item is JSONObject) jsonToMap(item) else item
                }
                else -> value
            }
        }
        return map
    }

    private fun body(vararg pairs: Pair<String, Any?>): String {
        val obj = JSONObject()
        pairs.forEach { (k, v) -> if (v != null) obj.put(k, v) }
        return obj.toString()
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    override suspend fun getUserSettings(): ApiResult<UserSettingsDto> {
        val result = request("GET", "/settings")
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    UserSettingsDto(
                        theme = m["theme"] as? String,
                        language = m["language"] as? String,
                        notificationsEnabled = m["notifications_enabled"] as? Boolean,
                        soundEnabled = m["sound_enabled"] as? Boolean,
                        timezone = m["timezone"] as? String,
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun updateUserSettings(dto: UserSettingsDto): ApiResult<UserSettingsDto> {
        val result = request(
            "PUT", "/settings",
            body(
                "theme" to dto.theme,
                "language" to dto.language,
                "notifications_enabled" to dto.notificationsEnabled,
                "sound_enabled" to dto.soundEnabled,
                "timezone" to dto.timezone,
            ),
        )
        return when (result) {
            is ApiResult.Success -> getUserSettings() // server returns the full payload
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun getMonitoringSettings(): ApiResult<MonitoringSettingsDto> {
        val result = request("GET", "/settings/monitoring")
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    MonitoringSettingsDto(
                        deviceMonitoringEnabled = m["device_monitoring_enabled"] as? Boolean,
                        monitoringEnabled = m["monitoring_enabled"] as? Boolean,
                        strictModeEnabled = m["strict_mode_enabled"] as? Boolean,
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun updateMonitoringSettings(dto: MonitoringSettingsDto): ApiResult<MonitoringSettingsDto> {
        val result = request(
            "PUT", "/settings/monitoring",
            body(
                "device_monitoring_enabled" to dto.deviceMonitoringEnabled,
                "monitoring_enabled" to dto.monitoringEnabled,
                "strict_mode_enabled" to dto.strictModeEnabled,
            ),
        )
        return when (result) {
            is ApiResult.Success -> getMonitoringSettings()
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun getShortsSettings(): ApiResult<ShortsSettingsDto> {
        val result = request("GET", "/settings/shorts")
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    ShortsSettingsDto(
                        dailyLimitMinutes = m["daily_limit_minutes"] as? Int,
                        dailyLimitCount = m["daily_limit_count"] as? Int,
                        warningMinutes = m["warning_minutes"] as? Int,
                        warningCount = m["warning_count"] as? Int,
                        strictModeEnabled = m["strict_mode_enabled"] as? Boolean,
                        isEnabled = m["is_enabled"] as? Boolean,
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun updateShortsSettings(dto: ShortsSettingsDto): ApiResult<ShortsSettingsDto> {
        val result = request(
            "PUT", "/settings/shorts",
            body(
                "daily_limit_minutes" to dto.dailyLimitMinutes,
                "daily_limit_count" to dto.dailyLimitCount,
                "warning_minutes" to dto.warningMinutes,
                "warning_count" to dto.warningCount,
                "strict_mode_enabled" to dto.strictModeEnabled,
                "is_enabled" to dto.isEnabled,
            ),
        )
        return when (result) {
            is ApiResult.Success -> getShortsSettings()
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun getNotificationPreferences(): ApiResult<NotificationPreferencesDto> {
        val result = request("GET", "/settings/notifications")
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    NotificationPreferencesDto(
                        studyNotifications = m["study_notifications"] as? Boolean,
                        monitoringNotifications = m["monitoring_notifications"] as? Boolean,
                        systemNotifications = m["system_notifications"] as? Boolean,
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun updateNotificationPreferences(dto: NotificationPreferencesDto): ApiResult<NotificationPreferencesDto> {
        val result = request(
            "PUT", "/settings/notifications",
            body(
                "study_notifications" to dto.studyNotifications,
                "monitoring_notifications" to dto.monitoringNotifications,
                "system_notifications" to dto.systemNotifications,
            ),
        )
        return when (result) {
            is ApiResult.Success -> getNotificationPreferences()
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun getLeaderboardSettings(): ApiResult<LeaderboardSettingsDto> {
        val result = request("GET", "/settings/leaderboard")
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    LeaderboardSettingsDto(
                        isEnabled = m["is_enabled"] as? Boolean,
                        displayName = m["display_name"] as? String,
                        isOptedIn = m["is_opted_in"] as? Boolean,
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun updateLeaderboardSettings(dto: LeaderboardSettingsDto): ApiResult<LeaderboardSettingsDto> {
        val result = request(
            "PUT", "/settings/leaderboard",
            body(
                "is_enabled" to dto.isEnabled,
                "display_name" to dto.displayName,
                "is_opted_in" to dto.isOptedIn,
            ),
        )
        return when (result) {
            is ApiResult.Success -> getLeaderboardSettings()
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun syncPermissions(states: List<Map<String, Any?>>): ApiResult<List<Map<String, Any?>>> {
        val array = JSONArray()
        states.forEach { state ->
            val obj = JSONObject()
            state.forEach { (k, v) -> if (v != null) obj.put(k, v) }
            array.put(obj)
        }
        val result = request("PUT", "/settings/permissions", array.toString())
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseList(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    // ------------------------------------------------------------------
    // Study
    // ------------------------------------------------------------------

    override suspend fun listStudySchedules(): ApiResult<List<Map<String, Any?>>> {
        val result = request("GET", "/study/schedules")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseList(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun createStudySchedule(dto: StudyScheduleDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/study/schedules",
            body(
                "title" to dto.title,
                "subject" to dto.subject,
                "start_time" to dto.startTime,
                "duration_minutes" to dto.durationMinutes,
                "days_of_week" to dto.daysOfWeek,
                "reminder_minutes" to dto.reminderMinutes,
                "is_enabled" to dto.isEnabled,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun updateStudySchedule(scheduleId: Int, dto: StudyScheduleDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "PUT", "/study/schedules/$scheduleId",
            body(
                "title" to dto.title,
                "subject" to dto.subject,
                "start_time" to dto.startTime,
                "duration_minutes" to dto.durationMinutes,
                "days_of_week" to dto.daysOfWeek,
                "reminder_minutes" to dto.reminderMinutes,
                "is_enabled" to dto.isEnabled,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun deleteStudySchedule(scheduleId: Int): ApiResult<Unit> {
        val result = request("DELETE", "/study/schedules/$scheduleId")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun startStudySession(dto: StudySessionStartDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/study/sessions/start",
            body(
                "schedule_id" to dto.scheduleId,
                "device_id" to dto.deviceId,
                "planned_duration_seconds" to dto.plannedDurationSeconds,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun endStudySession(sessionId: Int, cancelled: Boolean): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/study/sessions/$sessionId/end",
            body("cancelled" to cancelled),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun listStudySessions(): ApiResult<List<Map<String, Any?>>> {
        val result = request("GET", "/study/sessions")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseList(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun startBreak(sessionId: Int): ApiResult<Map<String, Any?>> {
        val result = request("POST", "/study/sessions/$sessionId/breaks/start")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun endBreak(breakId: Int): ApiResult<Map<String, Any?>> {
        val result = request("POST", "/study/breaks/$breakId/end")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    // ------------------------------------------------------------------
    // Monitoring
    // ------------------------------------------------------------------

    override suspend fun syncAppUsage(records: List<AppUsageRecordDto>): ApiResult<List<Map<String, Any?>>> {
        if (records.isEmpty()) return ApiResult.Success(emptyList())
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject()
                    .put("device_id", r.deviceId)
                    .put("package_name", r.packageName)
                    .put("app_name", r.appName)
                    .put("usage_date", r.usageDate)
                    .put("duration_seconds", r.durationSeconds)
                    .put("launch_count", r.launchCount),
            )
        }
        val payload = if (records.size == 1) array.getJSONObject(0).toString() else array.toString()
        val result = request("POST", "/monitoring/app-usage/sync", payload)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseList(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun createMonitoringEvent(dto: MonitoringEventDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/monitoring/events",
            body(
                "device_id" to dto.deviceId,
                "event_type" to dto.eventType,
                "app_package" to dto.appPackage,
                "occurred_at" to dto.occurredAt,
                "metadata_json" to dto.metadataJson,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    // ------------------------------------------------------------------
    // Shorts
    // ------------------------------------------------------------------

    override suspend fun syncShortsUsage(records: List<ShortsUsageRecordDto>): ApiResult<List<Map<String, Any?>>> {
        if (records.isEmpty()) return ApiResult.Success(emptyList())
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject()
                    .put("device_id", r.deviceId)
                    .put("usage_date", r.usageDate)
                    .put("shorts_count", r.shortsCount)
                    .put("duration_seconds", r.durationSeconds)
                    .put("warning_triggered", r.warningTriggered)
                    .put("limit_reached", r.limitReached)
                    .put("platform", r.platform)
                    .put("surface", r.surface),
            )
        }
        val payload = if (records.size == 1) array.getJSONObject(0).toString() else array.toString()
        val result = request("POST", "/shorts/usage/sync", payload)
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseList(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun createShortsEvent(dto: ShortsEventDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/shorts/events",
            body(
                "device_id" to dto.deviceId,
                "event_type" to dto.eventType,
                "occurred_at" to dto.occurredAt,
                "duration_seconds" to dto.durationSeconds,
                "metadata_json" to dto.metadataJson,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    // ------------------------------------------------------------------
    // Web
    // ------------------------------------------------------------------

    override suspend fun listBlockedWebsites(): ApiResult<List<Map<String, Any?>>> {
        val result = request("GET", "/websites/blocked")
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseList(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun createBlockedWebsite(dto: BlockedWebsiteDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/websites/blocked",
            body(
                "domain" to dto.domain,
                "is_blocked" to dto.isBlocked,
                "verification_status" to dto.verificationStatus,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun createWebEvent(dto: WebEventDto): ApiResult<Map<String, Any?>> {
        val result = request(
            "POST", "/web/events",
            body(
                "device_id" to dto.deviceId,
                "domain" to dto.domain,
                "event_type" to dto.eventType,
                "blocked_website_id" to dto.blockedWebsiteId,
                "occurred_at" to dto.occurredAt,
            ),
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(parseObject(result.data))
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    // ------------------------------------------------------------------
    // Read-only: Reports / Score / Rank
    // ------------------------------------------------------------------

    override suspend fun getReport(period: String, date: String?): ApiResult<ReportDto> {
        val result = request("GET", "/reports/$period", query = date?.let { mapOf("date" to it) } ?: emptyMap())
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    ReportDto(
                        period = period,
                        study = m["study"] as? Map<String, Any?> ?: emptyMap(),
                        monitoring = m["monitoring"] as? Map<String, Any?> ?: emptyMap(),
                        shorts = m["shorts"] as? Map<String, Any?> ?: emptyMap(),
                        web = m["web"] as? Map<String, Any?> ?: emptyMap(),
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun getScore(period: String, date: String?): ApiResult<ScoreDto> {
        val result = request("GET", "/score/$period", query = date?.let { mapOf("date" to it) } ?: emptyMap())
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                ApiResult.Success(
                    ScoreDto(
                        score = (m["score"] as? Number)?.toInt() ?: 0,
                        status = m["status"] as? String ?: "",
                        components = m["components"] as? List<Map<String, Any?>> ?: emptyList(),
                        explanation = m["explanation"] as? Map<String, Any?> ?: emptyMap(),
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }

    override suspend fun getRank(period: String, date: String?, page: Int, pageSize: Int): ApiResult<RankDto> {
        val query = buildMap {
            date?.let { put("date", it) }
            put("page", page.toString())
            put("page_size", pageSize.toString())
        }
        val result = request("GET", "/rank/$period", query = query)
        return when (result) {
            is ApiResult.Success -> {
                val m = parseObject(result.data)
                fun entry(raw: Any?): RankEntryDto? {
                    val e = raw as? Map<String, Any?> ?: return null
                    return RankEntryDto(
                        rank = (e["rank"] as? Number)?.toInt() ?: 0,
                        displayName = e["display_name"] as? String ?: "",
                        score = (e["score"] as? Number)?.toInt() ?: 0,
                        userId = (e["user_id"] as? Number)?.toInt() ?: 0,
                    )
                }
                ApiResult.Success(
                    RankDto(
                        period = period,
                        yourRank = (m["your_rank"] as? Number)?.toInt(),
                        yourScore = (m["your_score"] as? Number)?.toInt(),
                        yourScoreStatus = m["your_score_status"] as? String,
                        rankChange = (m["rank_change"] as? Number)?.toInt(),
                        totalParticipants = (m["total_participants"] as? Number)?.toInt() ?: 0,
                        winner = entry(m["winner"]),
                        topThree = (m["top_three"] as? List<*>)?.mapNotNull { entry(it) } ?: emptyList(),
                        entries = (m["entries"] as? List<*>)?.mapNotNull { entry(it) } ?: emptyList(),
                    )
                )
            }
            is ApiResult.HttpError -> result
            is ApiResult.NetworkError -> result
        }
    }
}
