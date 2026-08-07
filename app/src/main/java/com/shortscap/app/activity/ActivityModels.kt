package com.shortscap.app.activity

import java.time.LocalDate

/** Period selector for the Activity page and its report screens. */
enum class ActivityPeriod { DAILY, WEEKLY, MONTHLY }

/**
 * One date range of the current month used by the Monthly view
 * (e.g. Aug 1–7, Aug 8–14, …). Tapping a monthly bar opens the detailed
 * per-day usage for exactly this range.
 */
data class ActivityRange(
    val from: LocalDate,
    val to: LocalDate,
    val label: String,
)

/**
 * One RAW usage record (seeded today; backend/database later).
 *
 * [date] is the calendar day, [hour] the hour-of-day bucket (0 for
 * day-level records) and [minutes] the usage during that bucket. Every
 * period aggregates THESE same records dynamically — there are no separate
 * fake datasets for Daily / Weekly / Monthly.
 */
data class ActivityRecord(val date: LocalDate, val hour: Int, val minutes: Int)

/**
 * One point in a time-series chart — an hour, a weekday or a date range.
 *
 * [label] is the axis label ("12 AM", "Mon Aug 4", "Aug 1–7"). [detailTitle]
 * and [timeRange] carry the DATE/TIME information for tooltips so every
 * chart style (bar / donut / line) can show exactly WHEN usage happened:
 *   - Daily   → detailTitle = "Tuesday, August 5", timeRange = "2:00 PM – 3:00 PM"
 *   - Weekly  → detailTitle = "Tuesday, August 5", timeRange = null
 *   - Monthly → detailTitle = "Aug 1–7", timeRange = null
 */
data class ActivityPoint(
    val label: String,
    val minutes: Int,
    val detailTitle: String? = null,
    val timeRange: String? = null,
)

/**
 * One app slice of the usage distribution. [id] is a stable key (the
 * display [name] is data, and the UI localizes the "Other" entry).
 *
 * [percent] is the proportional share (the seed value, also what the charts
 * render). [minutes] is the REAL usage duration for the current period —
 * derived from the period's aggregated total by the repository so every
 * app row can show "4h 35m" instead of "42%". A future backend provides
 * minutes directly; the chart/UI shapes do not change.
 */
data class ActivitySlice(
    val id: String,
    val name: String,
    val percent: Int,
    val minutes: Int = 0,
)

/**
 * Structured activity/report data consumed by the Activity page and the
 * dedicated Weekly / Monthly report screens.
 *
 * Today [ActivityRepository] seeds deterministic values; tomorrow the exact
 * same shape is filled by a backend API / database — the UI, chart
 * rendering and navigation never change.
 *
 * Deliberately NO presentation fields: chart style is a user preference
 * (Settings → Appearance → Chart) and lives outside this layer.
 */
data class ActivityReport(
    val period: ActivityPeriod,
    val totalMinutes: Int,
    val points: List<ActivityPoint>,
    val distribution: List<ActivitySlice>,
    val shortsMinutes: Int,
    val shortsCount: Int,
    val busiestLabel: String,
    val trendPercent: Int,
)
