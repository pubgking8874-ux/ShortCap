package com.shortscap.app.screenactivity

/**
 * Screen Activity — the GENERAL app/screen usage collection domain.
 *
 * STRICTLY INDEPENDENT of Shorts Control: this domain records \"which app was
 * active and for how long\" (generic device usage) and never touches Shorts
 * detection / counting / limit / HUD / enforcement. Both domains may observe
 * the same foreground-window event source
 * ([com.shortscap.app.monitoring.MonitoringEventHub]) but their business
 * logic, state and sync records are completely separate.
 *
 * Privacy boundary: only app identity + duration are recorded — never
 * message contents, typed text, passwords, screenshots or page contents.
 *
 * Everything in this file is a pure value type (no Android runtime) so the
 * session/aggregate logic is unit-testable.
 */

/**
 * One foreground usage session: the user was using [packageName] from
 * [startedAtMillis] until [endedAtMillis] (null while the session is still
 * active). [durationMillis] is derived — never stored as a continuously
 * decremented value.
 */
data class ScreenActivitySession(
    /** The Android application package that was in the foreground. */
    val packageName: String,
    /** Best-effort display label of the app (null when unresolvable). */
    val appName: String? = null,
    /** UTC epoch millis when the session started. */
    val startedAtMillis: Long,
    /** UTC epoch millis when the session ended (null = still active). */
    val endedAtMillis: Long? = null,
) {
    /** Whole-millisecond duration of the session (0 while active). */
    val durationMillis: Long
        get() = (endedAtMillis ?: startedAtMillis) - startedAtMillis
}

/**
 * One aggregated daily usage summary for a single package — the exact shape
 * the existing backend `app_usage` / `POST /monitoring/app-usage/sync`
 * contract persists (idempotent upsert per user + device + package + date).
 */
data class ScreenActivityAggregate(
    val packageName: String,
    val appName: String? = null,
    /** UTC calendar date key (YYYY-MM-DD) of the usage. */
    val usageDate: String,
    /** Total seconds the app was in the foreground that day. */
    val durationSeconds: Long,
    /** Number of separate foreground sessions that day. */
    val launchCount: Int,
)

/** Merges [other] into this aggregate (same package + date only). */
fun ScreenActivityAggregate.merge(other: ScreenActivityAggregate): ScreenActivityAggregate =
    ScreenActivityAggregate(
        packageName = packageName,
        appName = appName ?: other.appName,
        usageDate = usageDate,
        durationSeconds = durationSeconds + other.durationSeconds,
        launchCount = launchCount + other.launchCount,
    )
