package com.shortscap.app.activity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * ActivityRepository — the single data seam for the Activity section.
 *
 * Today [seedUsageRecords] returns deterministic RAW usage records (the same
 * values the Activity page has always shown: the reference week's exact
 * Mon–Sun series, today's hourly profile, and a full previous + current
 * month so every monthly date range and trend has real data). [reportFor]
 * then AGGREGATES those same records dynamically according to the selected
 * period:
 *   - DAILY   → grouped by hour of the reference day (24-point timeline,
 *               12 AM – 11 PM);
 *   - WEEKLY  → grouped by weekday, Monday–Sunday (exactly 7 points);
 *   - MONTHLY → grouped into 7-day date ranges of the current month
 *               (Aug 1–7, Aug 8–14, …; the count adapts to the month's days).
 * There are NO separate fake datasets per period — one raw record set feeds
 * every chart, and [rangeReportFor] drills into one range's per-day usage.
 *
 * Tomorrow [seedUsageRecords] is replaced by a backend API / database fetch
 * behind the exact same [ActivityRecord] / [ActivityReport] shapes — no UI,
 * chart or navigation changes required.
 *
 * Chart style is NOT part of this layer: it is a presentation preference
 * (Settings → Appearance → Chart) and never appears in activity data.
 */
object ActivityRepository {

    // ---- Seed data (used only until real tracking / backend data) ----

    /** Fixed reference "today" so every aggregation is deterministic. */
    private val referenceDate: LocalDate = LocalDate.of(2026, 8, 7)

    /** Base minutes per weekday — the familiar weekly series (Sun..Sat). */
    private val baseMinutesByDay = mapOf(
        DayOfWeek.SUNDAY to 190,
        DayOfWeek.MONDAY to 210,
        DayOfWeek.TUESDAY to 185,
        DayOfWeek.WEDNESDAY to 260,
        DayOfWeek.THURSDAY to 150,
        DayOfWeek.FRIDAY to 300,
        DayOfWeek.SATURDAY to 340,
    )

    /** Deterministic day-level offsets so ranges/months aggregate differently. */
    private val dayVariation = intArrayOf(0, 12, -8, 5, -15, 10, -5, 18, -12, 7, -3, 14, -9, 6)

    /**
     * Today's hourly usage SHAPE (weights, deterministic). The shape is
     * scaled in [scaledHourlyProfile] so today's hourly total equals the
     * reference day's base value — the daily total therefore matches the
     * Friday bar in the weekly chart, and the weekly total stays exactly at
     * the established Mon–Sun series (27h 15m).
     */
    private val todayHourlyShape = listOf(
        7 to 15, 8 to 40, 9 to 55, 10 to 35, 11 to 60, 12 to 75, 13 to 50,
        14 to 45, 15 to 70, 16 to 30, 17 to 85, 18 to 110, 19 to 95,
        20 to 80, 21 to 65, 22 to 40,
    )

    /** App distribution — the values the Activity page has always shown. */
    private val distribution = listOf(
        ActivitySlice(id = "instagram", name = "Instagram", percent = 42),
        ActivitySlice(id = "youtube", name = "YouTube", percent = 27),
        ActivitySlice(id = "chrome", name = "Chrome", percent = 18),
        ActivitySlice(id = "other", name = "Other", percent = 13),
    )

    /** Share of usage spent on Shorts — seed heuristic until real tracking. */
    private const val SHORTS_SHARE = 0.38f
    /** Average Shorts length in minutes — seed heuristic until real tracking. */
    private const val AVG_SHORTS_MINUTES = 2

    /**
     * Deterministic raw usage records: one day-level record per day of the
     * previous month (for trend comparisons) through the end of the current
     * month (so every monthly date range has data), plus today's hourly
     * profile. The reference week keeps the exact base series (weekly total
     * stays 27h 15m; today's daily total equals its Friday bar). Day-level
     * records sit in the 0-hour bucket.
     */
    fun seedUsageRecords(): List<ActivityRecord> {
        val records = mutableListOf<ActivityRecord>()
        val first = referenceDate.withDayOfMonth(1).minusMonths(1)
        val last = referenceDate.withDayOfMonth(1).plusMonths(1).minusDays(1)
        val refWeekStart = referenceDate.with(DayOfWeek.MONDAY)
        val refWeekEnd = refWeekStart.plusDays(6)
        var cursor = first
        var dayIndex = 0
        while (!cursor.isAfter(last)) {
            val inReferenceWeek = !cursor.isBefore(refWeekStart) && !cursor.isAfter(refWeekEnd)
            val variation = if (inReferenceWeek) 0 else dayVariation[dayIndex % dayVariation.size]
            val minutes = (baseMinutesByDay.getValue(cursor.dayOfWeek) + variation).coerceAtLeast(20)
            records += ActivityRecord(date = cursor, hour = 0, minutes = minutes)
            cursor = cursor.plusDays(1)
            dayIndex++
        }
        // Replace the reference day's flat record with its hourly profile,
        // scaled to the day's base total so daily and weekly stay consistent.
        records.removeAll { it.date == referenceDate }
        scaledHourlyProfile(baseMinutesByDay.getValue(referenceDate.dayOfWeek))
            .forEach { (hour, minutes) ->
                records += ActivityRecord(date = referenceDate, hour = hour, minutes = minutes)
            }
        return records
    }

    /**
     * Scales the hourly shape so its sum equals [target] minutes exactly
     * (integer math; rounding drift is distributed one minute at a time).
     */
    private fun scaledHourlyProfile(target: Int): List<Pair<Int, Int>> {
        val shapeSum = todayHourlyShape.sumOf { it.second }
        val scaled = todayHourlyShape.map { (hour, weight) -> hour to weight * target / shapeSum }
        var diff = target - scaled.sumOf { it.second }
        val result = scaled.toMutableList()
        var i = 0
        while (diff > 0) {
            val (hour, minutes) = result[i]
            result[i] = hour to minutes + 1
            diff--
            i = (i + 1) % result.size
        }
        return result
    }

    /**
     * Structured activity data for [period]. Pure and deterministic — the UI
     * only renders this; it never mutates or recalculates it.
     */
    fun reportFor(period: ActivityPeriod): ActivityReport {
        val records = seedUsageRecords()
        val points = when (period) {
            ActivityPeriod.DAILY -> aggregateByHour(records, referenceDate)
            ActivityPeriod.WEEKLY -> aggregateByWeekday(records, referenceDate)
            ActivityPeriod.MONTHLY -> aggregateByMonthRanges(records, referenceDate)
        }
        val total = points.sumOf { it.minutes }
        val shortsMinutes = (total * SHORTS_SHARE).toInt()
        return ActivityReport(
            period = period,
            totalMinutes = total,
            points = points,
            distribution = distributionWithMinutes(total),
            shortsMinutes = shortsMinutes,
            shortsCount = shortsMinutes / AVG_SHORTS_MINUTES,
            busiestLabel = points.maxByOrNull { it.minutes }?.label.orEmpty(),
            trendPercent = trendFor(period, records),
        )
    }

    // ---- Aggregation (pure functions over the same raw records) ----

    /** DAILY → all 24 hours of the reference day; zero hours stay empty. */
    private fun aggregateByHour(records: List<ActivityRecord>, date: LocalDate): List<ActivityPoint> {
        val byHour = records
            .filter { it.date == date }
            .groupBy { it.hour }
            .mapValues { (_, rs) -> rs.sumOf { it.minutes } }
        return (0..23).map { hour ->
            ActivityPoint(
                label = hourLabel(hour),
                minutes = byHour[hour] ?: 0,
                detailTitle = fullDateLabel(date),
                timeRange = hourRangeLabel(hour),
            )
        }
    }

    /** WEEKLY → exactly 7 points, Monday-first, short day + actual date labels. */
    private fun aggregateByWeekday(records: List<ActivityRecord>, date: LocalDate): List<ActivityPoint> {
        val weekStart = date.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)
        val byDay = records
            .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
            .groupBy { it.date.dayOfWeek }
            .mapValues { (_, rs) -> rs.sumOf { it.minutes } }
        val order = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )
        return order.mapIndexed { index, day ->
            val dayDate = weekStart.plusDays(index.toLong())
            ActivityPoint(
                label = shortDayDateLabel(dayDate),
                minutes = byDay[day] ?: 0,
                detailTitle = fullDateLabel(dayDate),
            )
        }
    }

    /**
     * MONTHLY → the current month split into 7-day date ranges
     * (Aug 1–7, Aug 8–14, …). The final range holds the remaining days, so
     * the count adapts to the actual number of days in the month.
     */
    private fun aggregateByMonthRanges(records: List<ActivityRecord>, date: LocalDate): List<ActivityPoint> =
        monthlyRanges(date).map { range ->
            val minutes = records
                .filter { !it.date.isBefore(range.from) && !it.date.isAfter(range.to) }
                .sumOf { it.minutes }
            ActivityPoint(label = range.label, minutes = minutes, detailTitle = range.label)
        }

    /**
     * The current month's 7-day date ranges (public so the UI can map a
     * tapped monthly bar back to its range and open the per-range detail).
     */
    fun monthlyRanges(date: LocalDate = referenceDate): List<ActivityRange> {
        val first = date.withDayOfMonth(1)
        val last = date.withDayOfMonth(1).plusMonths(1).minusDays(1)
        val monthShort = first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val ranges = mutableListOf<ActivityRange>()
        var start = first
        while (!start.isAfter(last)) {
            val end = if (start.plusDays(6).isAfter(last)) last else start.plusDays(6)
            ranges += ActivityRange(
                from = start,
                to = end,
                label = "$monthShort ${start.dayOfMonth}–${end.dayOfMonth}",
            )
            start = end.plusDays(1)
        }
        return ranges
    }

    /**
     * Per-day detail for one date range (opened by tapping a monthly bar):
     * one point per day, full weekday labels, trend vs the previous
     * equal-length window. The same raw records feed this as every other view.
     */
    fun rangeReportFor(range: ActivityRange): ActivityReport {
        val records = seedUsageRecords()
        val days = (ChronoUnit.DAYS.between(range.from, range.to) + 1).toInt()
        val dayPoints = (0 until days).map { i ->
            val date = range.from.plusDays(i.toLong())
            val minutes = records.filter { it.date == date }.sumOf { it.minutes }
            ActivityPoint(
                label = shortDayDateLabel(date),
                minutes = minutes,
                detailTitle = fullDateLabel(date),
            )
        }
        val total = dayPoints.sumOf { it.minutes }
        val prevFrom = range.from.minusDays(days.toLong())
        val prevTotal = records
            .filter { !it.date.isBefore(prevFrom) && it.date.isBefore(range.from) }
            .sumOf { it.minutes }
        val shortsMinutes = (total * SHORTS_SHARE).toInt()
        return ActivityReport(
            period = ActivityPeriod.MONTHLY,
            totalMinutes = total,
            points = dayPoints,
            distribution = distributionWithMinutes(total),
            shortsMinutes = shortsMinutes,
            shortsCount = shortsMinutes / AVG_SHORTS_MINUTES,
            busiestLabel = dayPoints.maxByOrNull { it.minutes }?.label.orEmpty(),
            trendPercent = percentChange(total, prevTotal),
        )
    }

    /**
     * The app distribution for one report — per-app MINUTES derived from the
     * period's aggregated total, so the displayed hours/minutes are real usage
     * data (and sum EXACTLY to the period total; rounding remainder is
     * distributed one minute at a time). [percent] stays the proportional
     * source that drives the charts. A future backend provides minutes
     * directly behind the same shape.
     */
    private fun distributionWithMinutes(total: Int): List<ActivitySlice> {
        if (total <= 0) return distribution
        val minutes = distribution.map { total * it.percent / 100 }.toMutableList()
        var remainder = total - minutes.sum()
        var i = 0
        while (remainder > 0) {
            minutes[i] = minutes[i] + 1
            remainder--
            i = (i + 1) % minutes.size
        }
        return distribution.mapIndexed { index, slice -> slice.copy(minutes = minutes[index]) }
    }

    /** Trend % vs the previous comparable period, derived from the records. */
    private fun trendFor(period: ActivityPeriod, records: List<ActivityRecord>): Int = when (period) {
        ActivityPeriod.DAILY -> {
            val today = records.filter { it.date == referenceDate }.sumOf { it.minutes }
            val yesterday = records.filter { it.date == referenceDate.minusDays(1) }.sumOf { it.minutes }
            percentChange(today, yesterday)
        }
        ActivityPeriod.WEEKLY -> {
            percentChange(
                weekTotal(records, referenceDate),
                weekTotal(records, referenceDate.minusWeeks(1)),
            )
        }
        ActivityPeriod.MONTHLY -> {
            percentChange(
                monthTotal(records, YearMonth.from(referenceDate)),
                monthTotal(records, YearMonth.from(referenceDate).minusMonths(1)),
            )
        }
    }

    private fun weekTotal(records: List<ActivityRecord>, date: LocalDate): Int {
        val start = date.with(DayOfWeek.MONDAY)
        return records
            .filter { !it.date.isBefore(start) && !it.date.isAfter(start.plusDays(6)) }
            .sumOf { it.minutes }
    }

    private fun monthTotal(records: List<ActivityRecord>, month: YearMonth): Int =
        records.filter { YearMonth.from(it.date) == month }.sumOf { it.minutes }

    private fun percentChange(current: Int, previous: Int): Int =
        if (previous <= 0) 0 else (current - previous) * 100 / previous

    private fun hourLabel(hour: Int): String = when (hour) {
        0 -> "12 AM"
        12 -> "12 PM"
        else -> if (hour < 12) "${hour} AM" else "${hour - 12} PM"
    }

    /** "2:00 PM – 3:00 PM" — the exact clock window for one hourly point. */
    private fun hourRangeLabel(hour: Int): String {
        fun clock(h: Int): String = when (val x = h % 24) {
            0 -> "12:00 AM"
            12 -> "12:00 PM"
            else -> if (x < 12) "$x:00 AM" else "${x - 12}:00 PM"
        }
        return "${clock(hour)} – ${clock(hour + 1)}"
    }

    /** "Mon Aug 4" — compact day + date used as weekly / range axis labels. */
    private fun shortDayDateLabel(date: LocalDate): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} " +
            "${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${date.dayOfMonth}"

    /** "Tuesday, August 5" — full date used in tooltips and captions. */
    private fun fullDateLabel(date: LocalDate): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, " +
            "${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.dayOfMonth}"

    /**
     * "Friday, August 7" / "Aug 3 – Aug 9" / "August 2026" — the exact
     * calendar span the selected period covers, shown near the chart.
     */
    fun periodDateCaption(period: ActivityPeriod): String = when (period) {
        ActivityPeriod.DAILY -> fullDateLabel(referenceDate)
        ActivityPeriod.WEEKLY -> {
            val start = referenceDate.with(DayOfWeek.MONDAY)
            val end = start.plusDays(6)
            "${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${start.dayOfMonth} – " +
                "${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${end.dayOfMonth}"
        }
        ActivityPeriod.MONTHLY ->
            "${referenceDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${referenceDate.year}"
    }

    // ---- Future backend seams (placeholders only — not implemented) ----

    /** FUTURE: GET /activity/summary?period=… — real usage from the backend. */
    suspend fun fetchReportFromBackend(period: ActivityPeriod): ActivityReport {
        // TODO: backend / database call; keep the same ActivityReport shape.
        return reportFor(period)
    }
}
