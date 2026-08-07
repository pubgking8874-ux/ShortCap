package com.shortscap.app.web

/**
 * Web section — models.
 *
 * Two logically separated concerns, ready to be backed by a real backend /
 * database in the future:
 *
 *  1. **Website rules** ([WebRule]) — the user's block/allow list. Fields map
 *     1:1 to a future `web_rules` table (domain, display name, status,
 *     created/updated timestamps).
 *  2. **Web usage analytics** ([WebUsageRecord] + [WebAnalyticsSummary]) —
 *     per-website usage durations aggregated by period (Today / Week / Month).
 *     Fields map 1:1 to a future usage-tracking data source (domain, usage
 *     duration, date, aggregation period).
 *
 * No UI code hardcodes website data — everything flows through
 * [WebRepository] (seed data today, backend calls later).
 */

/** Block/allow state of a website rule. */
enum class WebRuleStatus { BLOCKED, ALLOWED }

/**
 * A website rule in the user's block/allow list. [createdAt] / [updatedAt]
 * are epoch-millis timestamps (future-ready; not rendered today).
 */
data class WebRule(
    val id: String,
    val domain: String,
    val displayName: String,
    val status: WebRuleStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Analytics aggregation periods offered on the Web Usage Analytics screen. */
enum class WebAnalyticsPeriod { TODAY, WEEK, MONTH }

/**
 * One raw website-usage record: how long a website was used on a given day.
 * [dateEpochDay] is the epoch day (see [java.time.LocalDate.toEpochDay]) of
 * the usage. A future tracking mechanism (browser / VPN / accessibility)
 * would insert records of this exact shape — no UI changes required.
 */
data class WebUsageRecord(
    val domain: String,
    val displayName: String,
    val durationMinutes: Int,
    val dateEpochDay: Long,
)

/** One aggregated website slice of a [WebAnalyticsSummary] (percentage 0–100). */
data class WebUsageItem(
    val domain: String,
    val displayName: String,
    val durationMinutes: Int,
    val percentage: Int,
)

/** One bar of the trend chart (label + usage duration). */
data class WebTrendPoint(
    val label: String,
    val durationMinutes: Int,
)

/**
 * Fully aggregated analytics for one [WebAnalyticsPeriod]: the total usage,
 * the website-wise breakdown (each website's share of the circle) and the
 * trend bars shown below the donut chart.
 */
data class WebAnalyticsSummary(
    val period: WebAnalyticsPeriod,
    val totalMinutes: Int,
    val items: List<WebUsageItem>,
    val trend: List<WebTrendPoint>,
)
