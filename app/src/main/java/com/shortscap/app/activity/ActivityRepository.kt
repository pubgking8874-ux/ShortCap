package com.shortscap.app.activity

import com.shortscap.app.db.ScreenActivityDao
import com.shortscap.app.db.ScreenActivityUsageEntity
import com.shortscap.app.db.ShortsStoreDao
import com.shortscap.app.db.ShortsUsageEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * ActivityRepository — the reporting seam for the Activity section.
 *
 * Phase 1 — every displayed value is aggregated from REAL persisted data:
 *  - app usage (timeline, distribution, unlock/avg-session stats) comes from
 *    `screen_activity_usage` (ScreenActivityEngine's closed foreground
 *    sessions);
 *  - Shorts count / watch time / per-platform breakdown comes from
 *    `shorts_usage` (the counting pipeline's persisted counted-Short rows).
 *
 * "Today" is ALWAYS the device's local calendar day
 * ([ZoneId.systemDefault()]) — never the 24-hour enforcement cycle. The
 * enforcement cycle ([com.shortscap.app.shorts.ShortsControlEngine]) is
 * untouched; reporting and enforcement are separate concepts.
 *
 * [DataSource] is installed at app start ([installDataSource]). Before
 * install the empty source reports zeros — never fabricated values.
 */
object ActivityRepository {

    /** Raw persisted reporting rows the repository aggregates. */
    interface DataSource {
        /** Counted Shorts usage rows with occurredAt in [startMillis, endMillis). */
        suspend fun shortsUsage(startMillis: Long, endMillis: Long): List<ShortsUsageEntity>

        /** Closed app-usage sessions with occurredAt in [startMillis, endMillis). */
        suspend fun appSessions(startMillis: Long, endMillis: Long): List<ScreenActivityUsageEntity>
    }

    /** Before install: report zeros, never fake numbers. */
    private object EmptyDataSource : DataSource {
        override suspend fun shortsUsage(startMillis: Long, endMillis: Long): List<ShortsUsageEntity> = emptyList()
        override suspend fun appSessions(startMillis: Long, endMillis: Long): List<ScreenActivityUsageEntity> = emptyList()
    }

    @Volatile
    private var source: DataSource = EmptyDataSource

    /** Device launcher + enabled IME packages excluded by the pure report path
     *  (installed at app start — see [installDeviceNonReportablePackages]). */
    @Volatile
    private var deviceNonReportablePackages: Set<String> = emptySet()

    /**
     * Phase 1.7 — the device-local USER-APP catalog (installed at app start
     * from [UserAppCatalogResolver]). null = catalog not installed (unit-test
     * / pre-install state: the classifier rules alone decide eligibility); a
     * non-null set makes catalog membership the SINGLE reporting gate — a
     * recorded foreground package is reportable only when Android reports it
     * as an installed, launchable, user-facing application.
     */
    @Volatile
    private var userAppCatalog: Set<String>? = null

    @Volatile
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Installs the real Room-backed source (called from [com.shortscap.app.ShortsCapApplication]). */
    fun installDataSource(dataSource: DataSource) {
        source = dataSource
    }

    /**
     * Phase 1.7: installs the device's user-facing application catalog as the
     * eligibility gate for Activity reporting (resolved once at app start via
     * [UserAppCatalogResolver] — Android PackageManager metadata, never a
     * hardcoded blacklist). Raw `screen_activity_usage` rows are untouched;
     * only reporting eligibility changes. Idempotent. Passing null (the
     * default state) restores the classifier-only behavior used by tests and
     * pre-install code.
     */
    fun installUserAppCatalog(catalogPackages: Set<String>?) {
        userAppCatalog = catalogPackages
    }

    /**
     * Phase 1.4: installs the DEVICE-SPECIFIC non-reportable packages (the
     * device's launcher + every enabled input method, resolved once at app
     * start by [com.shortscap.app.activity.PackageClassifier]). The pure
     * aggregation in this repository is package-name based (no Context), so
     * this set lets it exclude exactly the same device packages as Home's
     * context-aware classifier — package identity, never display labels.
     * Idempotent; defaults to empty so tests and pre-install state are clean.
     */
    fun installDeviceNonReportablePackages(packages: Set<String>) {
        deviceNonReportablePackages = packages
    }

    /** Room-backed source over the two existing tables (read-only). */
    class RoomDataSource(
        private val shortsDao: ShortsStoreDao,
        private val screenActivityDao: ScreenActivityDao,
    ) : DataSource {
        override suspend fun shortsUsage(startMillis: Long, endMillis: Long): List<ShortsUsageEntity> =
            shortsDao.usageInRange(startMillis, endMillis)

        override suspend fun appSessions(startMillis: Long, endMillis: Long): List<ScreenActivityUsageEntity> =
            screenActivityDao.usageInRange(startMillis, endMillis)
    }

    /** Max real app slices shown in the distribution; the rest folds into "Other". */
    private const val MAX_DISTRIBUTION_SLICES = 5

    // ------------------------------------------------------------------
    // Public API — same signatures as before (the UI is unchanged).
    // ------------------------------------------------------------------

    /** Structured activity data for [period], aggregated from persisted rows. */
    fun reportFor(period: ActivityPeriod, today: LocalDate = LocalDate.now()): ActivityReport {
        val (from, to) = periodSpan(period, today)
        val (prevFrom, _) = previousSpan(period, today)
        val shorts = loadShorts(minOf(from, prevFrom), to)
        val sessions = loadSessions(minOf(from, prevFrom), to)
        return buildReport(period, today, shorts, sessions)
    }

    /**
     * Per-day detail for one date range (opened by tapping a monthly bar):
     * one point per day, full weekday labels, trend vs the previous
     * equal-length window. The same persisted rows feed this as every other
     * view.
     */
    fun rangeReportFor(range: ActivityRange): ActivityReport {
        val from = range.from
        val to = range.to
        val spanDays = (ChronoUnit.DAYS.between(from, to) + 1)
        val prevTo = from.minusDays(1)
        val prevFrom = prevTo.minusDays(spanDays - 1)
        val shorts = loadShorts(minOf(from, prevFrom), to)
        // Phase 1.2: filter system/IME/launcher/ShortsCap-internal packages
        // BEFORE aggregation so every metric below (day points, prev window,
        // distribution, unlock/avg) uses the same reportable user-app set.
        val sessions = reportableSessions(loadSessions(minOf(from, prevFrom), to))
        val zone = ZoneId.systemDefault()

        val days = spanDays.toInt()
        val dayPoints = (0 until days).map { i ->
            val date = from.plusDays(i.toLong())
            val minutes = sessions
                .filter { inRange(it.occurredAt, date, date, zone) }
                .sumOf { minutesOf(it.durationSeconds * 1000L) }
            ActivityPoint(
                label = shortDayDateLabel(date),
                minutes = minutes,
                detailTitle = fullDateLabel(date),
            )
        }
        val total = dayPoints.sumOf { it.minutes }
        val prevTotal = sessions
            .filter { inRange(it.occurredAt, prevFrom, prevTo, zone) }
            .sumOf { minutesOf(it.durationSeconds * 1000L) }

        return buildReportForWindow(
            period = ActivityPeriod.MONTHLY,
            from = from,
            to = to,
            total = total,
            points = dayPoints,
            shorts = shorts.filter { inRange(it.occurredAt, from, to, zone) },
            sessions = sessions,
            zone = zone,
            trendPercent = percentChange(total, prevTotal),
        )
    }

    /**
     * The current month's 7-day date ranges (public so the UI can map a
     * tapped monthly bar back to its range and open the per-range detail).
     */
    fun monthlyRanges(date: LocalDate = LocalDate.now()): List<ActivityRange> {
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
     * "Friday, August 7" / "Aug 3 – Aug 9" / "August 2026" — the exact
     * calendar span the selected period covers, shown near the chart.
     */
    fun periodDateCaption(period: ActivityPeriod, today: LocalDate = LocalDate.now()): String = when (period) {
        ActivityPeriod.DAILY -> fullDateLabel(today)
        ActivityPeriod.WEEKLY -> {
            val start = today.with(DayOfWeek.MONDAY)
            val end = start.plusDays(6)
            "${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${start.dayOfMonth} – " +
                "${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${end.dayOfMonth}"
        }
        ActivityPeriod.MONTHLY ->
            "${today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${today.year}"
    }

    /** FUTURE: GET /activity/summary?period=… — same shape, backend source. */
    suspend fun fetchReportFromBackend(period: ActivityPeriod): ActivityReport =
        reportFor(period)

    // ------------------------------------------------------------------
    // Pure aggregation over persisted rows (unit-testable).
    // ------------------------------------------------------------------

    /** Aggregates [shorts] + [sessions] into a full report for the period. */
    internal fun buildReport(
        period: ActivityPeriod,
        today: LocalDate,
        shorts: List<ShortsUsageEntity>,
        sessions: List<ScreenActivityUsageEntity>,
    ): ActivityReport {
        val zone = ZoneId.systemDefault()
        val (from, to) = periodSpan(period, today)
        val (prevFrom, prevTo) = previousSpan(period, today)

        // Phase 1.2: filter BEFORE aggregation — totals, timeline, distribution
        // and percentages must all operate on the same reportable user-app set
        // (system UI / IME / launcher / ShortsCap-internal never reach the
        // report; raw screen_activity_usage rows are untouched).
        val reportable = reportableSessions(sessions)

        val points = when (period) {
            ActivityPeriod.DAILY -> aggregateByHour(reportable, today, zone)
            ActivityPeriod.WEEKLY -> aggregateByWeekday(reportable, from, to, zone)
            ActivityPeriod.MONTHLY -> aggregateByDateRanges(reportable, today, zone)
        }
        val total = points.sumOf { it.minutes }
        val prevTotal = reportable
            .filter { inRange(it.occurredAt, prevFrom, prevTo, zone) }
            .sumOf { minutesOf(it.durationSeconds * 1000L) }

        return buildReportForWindow(
            period = period,
            from = from,
            to = to,
            total = total,
            points = points,
            shorts = shorts.filter { inRange(it.occurredAt, from, to, zone) },
            sessions = reportable,
            zone = zone,
            trendPercent = percentChange(total, prevTotal),
        )
    }

    /**
     * Phase 1.2/1.4/1.7: keeps only sessions whose package is a REAL
     * user-facing application — the single eligibility gate for Activity
     * reporting:
     *  1. catalog membership — when the Phase 1.7 user-app catalog is
     *     installed (app start), the package must be an installed, launchable,
     *     user-facing application per Android's PackageManager metadata
     *     ([UserAppCatalogResolver]); otherwise it is ignored for reporting
     *     (android/app/system services/launchers/IMEs can never be members).
     *  2. not a device non-reportable package (launcher / enabled IMEs).
     *  3. [PackageClassifier] considers it reportable (still excludes known
     *     internals such as ShortsCap or the quick-search surface).
     * Filtering happens HERE, before any aggregation, so totals, timeline,
     * distribution, percentages and "Other" all operate on the same
     * user-app dataset. Raw `screen_activity_usage` rows are untouched.
     */
    private fun reportableSessions(sessions: List<ScreenActivityUsageEntity>): List<ScreenActivityUsageEntity> {
        // Local snapshot — the catalog is a @Volatile installable property and
        // must not change mid-filter.
        val catalog = userAppCatalog
        return sessions.filter {
            val pkg = it.packageName
            (catalog == null || pkg in catalog) &&
                pkg !in deviceNonReportablePackages &&
                PackageClassifier.isReportable(PackageClassifier.classify(pkg))
        }
    }

    /** Shared window aggregation for a report (used by reportFor + rangeReportFor). */
    private fun buildReportForWindow(
        period: ActivityPeriod,
        from: LocalDate,
        to: LocalDate,
        total: Int,
        points: List<ActivityPoint>,
        shorts: List<ShortsUsageEntity>,
        sessions: List<ScreenActivityUsageEntity>,
        zone: ZoneId,
        trendPercent: Int,
    ): ActivityReport {
        val shortsMinutes = (shorts.sumOf { it.durationMillis } / 60_000.0).roundToInt()
        val shortsCount = shorts.sumOf { it.countDelta }
        val unlockCount = sessions.count { inRange(it.occurredAt, from, to, zone) }
        val avgSessionSeconds = averageSessionSeconds(sessions, from, to, zone)
        return ActivityReport(
            period = period,
            totalMinutes = total,
            points = points,
            distribution = distributionFor(sessions, from, to, zone),
            shortsMinutes = shortsMinutes,
            shortsCount = shortsCount,
            shortsByPlatform = shortsByPlatform(shorts),
            busiestLabel = points.maxByOrNull { it.minutes }?.label.orEmpty(),
            trendPercent = trendPercent,
            unlockCount = unlockCount,
            avgSessionSeconds = avgSessionSeconds,
        )
    }

    // ---- App-usage aggregation (screen_activity_usage) ----

    /** DAILY → all 24 hours of [date]; sessions are split across the hours
     *  they actually spanned, so a session crossing an hour boundary shows
     *  up in every hour it covered. */
    private fun aggregateByHour(sessions: List<ScreenActivityUsageEntity>, date: LocalDate, zone: ZoneId): List<ActivityPoint> {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val byHour = mutableMapOf<Int, Long>()
        sessions
            .filter { it.occurredAt >= dayStart && it.occurredAt < dayEnd }
            .forEach { session ->
                splitByHour(session.occurredAt, session.durationSeconds * 1000L, zone)
                    .forEach { (hour, millis) -> byHour.merge(hour, millis, Long::plus) }
            }
        return (0..23).map { hour ->
            ActivityPoint(
                label = hourLabel(hour),
                minutes = minutesOf(byHour[hour] ?: 0L),
                detailTitle = fullDateLabel(date),
                timeRange = hourRangeLabel(hour),
            )
        }
    }

    /** WEEKLY → exactly 7 points, Monday-first, short day + actual date labels. */
    private fun aggregateByWeekday(
        sessions: List<ScreenActivityUsageEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<ActivityPoint> {
        val byDay = sessions
            .filter { inRange(it.occurredAt, from, to, zone) }
            .groupBy { dateOf(it.occurredAt, zone).dayOfWeek }
            .mapValues { (_, rows) -> rows.sumOf { minutesOf(it.durationSeconds * 1000L) } }
        return listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        ).mapIndexed { index, day ->
            val dayDate = from.plusDays(index.toLong())
            ActivityPoint(
                label = shortDayDateLabel(dayDate),
                minutes = byDay[day] ?: 0,
                detailTitle = fullDateLabel(dayDate),
            )
        }
    }

    /** MONTHLY → the current month split into 7-day date ranges. */
    private fun aggregateByDateRanges(sessions: List<ScreenActivityUsageEntity>, today: LocalDate, zone: ZoneId): List<ActivityPoint> =
        monthlyRanges(today).map { range ->
            val minutes = sessions
                .filter { inRange(it.occurredAt, range.from, range.to, zone) }
                .sumOf { minutesOf(it.durationSeconds * 1000L) }
            ActivityPoint(label = range.label, minutes = minutes, detailTitle = range.label)
        }

    /** Real per-app distribution for the window — minutes + percent from the
     *  persisted sessions; the longest apps are shown, the tail folds into a
     *  single localized "Other" slice. */
    private fun distributionFor(
        sessions: List<ScreenActivityUsageEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): List<ActivitySlice> {
        val inWindow = sessions.filter { inRange(it.occurredAt, from, to, zone) }
        if (inWindow.isEmpty()) return emptyList()
        val byPackage = inWindow
            .groupBy { it.packageName }
            .mapValues { (_, rows) ->
                rows.sumOf { it.durationSeconds } to (rows.firstNotNullOfOrNull { it.appName })
            }
            .entries
            .sortedByDescending { it.value.first }
        val totalSeconds = byPackage.sumOf { it.value.first }
        if (totalSeconds <= 0) return emptyList()

        val visible = byPackage.take(MAX_DISTRIBUTION_SLICES)
        val slices = visible.map { (pkg, value) ->
            val (seconds, appName) = value
            ActivitySlice(
                id = sliceIdFor(pkg),
                name = ActivityAppNames.friendlyName(pkg, appName),
                percent = ((seconds * 100) / totalSeconds).toInt().coerceIn(0, 100),
                minutes = minutesOf(seconds * 1000L),
                // Package identity travels with the slice so the UI colors it
                // through AppUsageColorProvider (never by display label).
                packageName = pkg,
            )
        }
        val remainderSeconds = totalSeconds - visible.sumOf { it.value.first }
        return if (remainderSeconds > 0) {
            slices + ActivitySlice(
                id = "other",
                name = "Other",
                percent = ((remainderSeconds * 100) / totalSeconds).toInt().coerceIn(0, 100),
                minutes = minutesOf(remainderSeconds * 1000L),
            )
        } else {
            slices
        }
    }

    /**
     * Phase 1.3: the reportable applications active during [hour] of [date]
     * (local 24-hour clock, 0..23) and their exact durations — the data
     * behind a selected hour's "Apps Used" breakdown in the Activity detail
     * card. Sessions are split across hour boundaries with the same
     * [splitByHour] logic the timeline uses, so a session crossing into
     * [hour] contributes only its overlap (e.g. 12:55 → 1:10 PM contributes
     * 10m to the 1 PM hour, never 15m). Durations are grouped per package
     * and sorted longest-first.
     */
    fun hourApps(hour: Int, date: LocalDate = LocalDate.now()): List<ActivityAppUsage> =
        hourAppsFromSessions(hour, date, loadSessions(date, date))

    /** Pure aggregation behind [hourApps] (unit-testable, no Room needed). */
    internal fun hourAppsFromSessions(
        hour: Int,
        date: LocalDate,
        sessions: List<ScreenActivityUsageEntity>,
    ): List<ActivityAppUsage> {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val hourStart = dayStart + hour * 3_600_000L
        val millisByPackage = mutableMapOf<String, Long>()
        val appNameByPackage = mutableMapOf<String, String?>()
        reportableSessions(sessions).forEach { session ->
            val overlapMillis = splitByHour(session.occurredAt, session.durationSeconds * 1000L, zone)[hour] ?: 0L
            if (overlapMillis <= 0L) return@forEach
            millisByPackage.merge(session.packageName, overlapMillis, Long::plus)
            appNameByPackage.putIfAbsent(session.packageName, session.appName)
        }
        return millisByPackage.entries
            .sortedByDescending { it.value }
            .map { (pkg, millis) ->
                ActivityAppUsage(
                    packageName = pkg,
                    name = ActivityAppNames.friendlyName(pkg, appNameByPackage[pkg]),
                    minutes = minutesOf(millis),
                )
            }
            .filter { it.minutes > 0 }
    }

    /**
     * Phase 1.8 — Home Recent Activity consolidation: collapses raw closed
     * foreground-session rows into ONE item per application, keyed by the
     * stable Android `packageName` (never a display label). For each package
     * the item carries the SUM of every session's duration and the timestamp
     * of its LATEST session; the result is sorted newest-first by that latest
     * activity so Home's existing top-N can be applied AFTER consolidation
     * (duplicate sessions can never consume multiple Recent Activity rows).
     *
     * Callers pass the already-filtered reportable sessions (the
     * reportable-app rules live with the classifier / catalog); this is pure
     * aggregation — raw `screen_activity_usage` rows and every other report
     * (Activity timeline, distribution, selected-hour apps) are untouched.
     */
    internal fun recentActivityFromSessions(
        sessions: List<ScreenActivityUsageEntity>,
    ): List<RecentActivityItem> =
        sessions
            .groupBy { it.packageName }
            .map { (packageName, rows) ->
                val latest = rows.maxByOrNull { it.occurredAt } ?: rows.first()
                RecentActivityItem(
                    packageName = packageName,
                    appName = latest.appName,
                    totalDurationSeconds = rows.sumOf { it.durationSeconds },
                    latestOccurredAt = latest.occurredAt,
                )
            }
            .sortedByDescending { it.latestOccurredAt }

    /** Number of closed foreground sessions + their average length in the window. */
    private fun averageSessionSeconds(
        sessions: List<ScreenActivityUsageEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): Long {
        val inWindow = sessions.filter { inRange(it.occurredAt, from, to, zone) }
        if (inWindow.isEmpty()) return 0L
        return inWindow.sumOf { it.durationSeconds } / inWindow.size
    }

    // ---- Shorts aggregation (shorts_usage) ----

    /** Per-platform Shorts totals, using the persisted platform value exactly. */
    private fun shortsByPlatform(shorts: List<ShortsUsageEntity>): List<PlatformShortsSlice> =
        shorts
            .groupBy { it.platform }
            .map { (platform, rows) ->
                PlatformShortsSlice(
                    platformName = platform,
                    count = rows.sumOf { it.countDelta },
                    minutes = (rows.sumOf { it.durationMillis } / 60_000.0).roundToInt(),
                )
            }
            .sortedByDescending { it.count }

    // ---- Loading + date helpers ----

    private fun loadShorts(from: LocalDate, to: LocalDate): List<ShortsUsageEntity> {
        val zone = ZoneId.systemDefault()
        return runBlocking(ioDispatcher) {
            source.shortsUsage(
                from.atStartOfDay(zone).toInstant().toEpochMilli(),
                to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }

    private fun loadSessions(from: LocalDate, to: LocalDate): List<ScreenActivityUsageEntity> {
        val zone = ZoneId.systemDefault()
        return runBlocking(ioDispatcher) {
            source.appSessions(
                from.atStartOfDay(zone).toInstant().toEpochMilli(),
                to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }

    /** Inclusive date span of [period] around [today]. */
    private fun periodSpan(period: ActivityPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        ActivityPeriod.DAILY -> today to today
        ActivityPeriod.WEEKLY -> {
            val monday = today.with(DayOfWeek.MONDAY)
            monday to monday.plusDays(6)
        }
        ActivityPeriod.MONTHLY -> {
            val first = today.withDayOfMonth(1)
            first to first.plusMonths(1).minusDays(1)
        }
    }

    /** Inclusive date span of the previous equal-length window. */
    private fun previousSpan(period: ActivityPeriod, today: LocalDate): Pair<LocalDate, LocalDate> {
        val (from, to) = periodSpan(period, today)
        val spanDays = ChronoUnit.DAYS.between(from, to) + 1
        val prevTo = from.minusDays(1)
        return (prevTo.minusDays(spanDays - 1)) to prevTo
    }

    private fun inRange(epochMillis: Long, from: LocalDate, to: LocalDate, zone: ZoneId): Boolean {
        val date = dateOf(epochMillis, zone)
        return !date.isBefore(from) && !date.isAfter(to)
    }

    private fun dateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Splits [durationMillis] starting at [startMillis] across local hour
     *  buckets (handles sessions spanning hour boundaries and midnight). */
    private fun splitByHour(startMillis: Long, durationMillis: Long, zone: ZoneId): Map<Int, Long> {
        if (durationMillis <= 0L) return emptyMap()
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val end = start.plus(durationMillis, ChronoUnit.MILLIS)
        val result = mutableMapOf<Int, Long>()
        var cursor = start
        while (cursor.isBefore(end)) {
            val nextHour = cursor.toLocalDate().atTime(cursor.hour + 1, 0).atZone(zone)
            val sliceEnd = if (nextHour.isAfter(end)) end else nextHour
            val millis = ChronoUnit.MILLIS.between(cursor, sliceEnd).coerceAtLeast(0L)
            result.merge(cursor.hour, millis, Long::plus)
            cursor = sliceEnd
        }
        return result
    }

    /** Whole minutes for a duration, rounded to the nearest minute. */
    private fun minutesOf(millis: Long): Int = (millis / 60_000.0).roundToInt()

    /** Stable distribution slice id for a package (keeps themed colors). */
    private fun sliceIdFor(packageName: String): String = when (packageName) {
        "com.instagram.android" -> "instagram"
        "com.google.android.youtube" -> "youtube"
        "com.android.chrome" -> "chrome"
        else -> packageName
    }

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
}

/**
 * Friendly display names for packages recorded by Screen Activity — shared by
 * Activity's distribution and Home's Recent Activity. Falls back to the last
 * segment of the package name so unknown apps are still readable.
 */
object ActivityAppNames {

    /**
     * Phase 1.7 — the authoritative DISPLAY label per package, resolved from
     * the device at app start ([UserAppCatalogResolver], Android
     * PackageManager.loadLabel). Installed by [installDeviceLabels]; empty in
     * tests / pre-install state. Device labels take priority over any
     * persisted/backend `appName` so a package is always shown as Android
     * itself names it (e.g. `org.telegram.messenger` → "Telegram", never a
     * stale "Messenger" from backend sync).
     */
    @Volatile
    private var deviceLabels: Map<String, String> = emptyMap()

    /** Installs the device label map (called at app start). */
    fun installDeviceLabels(labels: Map<String, String>) {
        deviceLabels = labels
    }

    private val known = mapOf(
        "com.google.android.youtube" to "YouTube",
        "com.instagram.android" to "Instagram",
        "com.ss.android.ugc.aweme" to "TikTok",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.snapchat.android" to "Snapchat",
        "com.facebook.katana" to "Facebook",
        "in.mohalla.video" to "Moj",
        "com.twitter.android" to "X",
        "com.twitter.android.lite" to "X",
        "com.linkedin.android" to "LinkedIn",
        "com.sharechat.android" to "ShareChat",
        "com.android.chrome" to "Chrome",
        "com.whatsapp" to "WhatsApp",
        "com.shortscap.app" to "ShortsCap",
    )

    /**
     * Best-effort readable app name — Android's own application label first
     * (Phase 1.7 device catalog), then the persisted/backend `appName`, then
     * the known-name map, then the package's last segment. Never crashes for
     * an unknown or uninstalled package.
     */
    fun friendlyName(packageName: String, appName: String? = null): String {
        deviceLabels[packageName]?.let { return it }
        if (!appName.isNullOrBlank()) return appName
        known[packageName]?.let { return it }
        val segment = packageName.substringAfterLast('.')
        return if (segment.isBlank()) packageName else segment
    }
}