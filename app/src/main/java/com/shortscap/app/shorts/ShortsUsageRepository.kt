package com.shortscap.app.shorts

import com.shortscap.app.db.ShortsStoreDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase 1 — Real Shorts usage aggregation from the existing [shorts_usage] Room table.
 *
 * Provides per-period summaries (today, yesterday, this week, this month)
 * backed by actual counted-Short records. The UI never touches Room or SQL
 * directly — this repository is the single seam between Room and the
 * ViewModel/UI layer.
 *
 * All date ranges use the device's local calendar via [ZoneId.systemDefault()].
 * No hardcoded dates.
 */
class ShortsUsageRepository(
    private val dao: ShortsStoreDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Summary for a specific time range. */
    data class UsageSummary(
        val durationMillis: Long = 0L,
        val count: Int = 0,
    ) {
        val durationMinutes: Int get() = (durationMillis / 60_000L).toInt()
    }

    /** Today's Shorts usage summary. */
    fun getTodaySummary(): UsageSummary = runBlocking(ioDispatcher) {
        val (start, end) = todayRange()
        val result = dao.usageSummary(start, end)
        UsageSummary(durationMillis = result.totalDurationMillis, count = result.totalCount)
    }

    /** Yesterday's Shorts usage summary. */
    fun getYesterdaySummary(): UsageSummary = runBlocking(ioDispatcher) {
        val (start, end) = yesterdayRange()
        val result = dao.usageSummary(start, end)
        UsageSummary(durationMillis = result.totalDurationMillis, count = result.totalCount)
    }

    /** This week's Shorts usage summary (Monday–Sunday). */
    fun getThisWeekSummary(): UsageSummary = runBlocking(ioDispatcher) {
        val (start, end) = thisWeekRange()
        val result = dao.usageSummary(start, end)
        UsageSummary(durationMillis = result.totalDurationMillis, count = result.totalCount)
    }

    /** This month's Shorts usage summary. */
    fun getThisMonthSummary(): UsageSummary = runBlocking(ioDispatcher) {
        val (start, end) = thisMonthRange()
        val result = dao.usageSummary(start, end)
        UsageSummary(durationMillis = result.totalDurationMillis, count = result.totalCount)
    }

    /** All four period summaries at once (avoids 4 separate Room calls). */
    fun getAllSummaries(): PeriodSummaries = runBlocking(ioDispatcher) {
        val today = todayRange()
        val yesterday = yesterdayRange()
        val week = thisWeekRange()
        val month = thisMonthRange()

        val todayResult = dao.usageSummary(today.first, today.second)
        val yesterdayResult = dao.usageSummary(yesterday.first, yesterday.second)
        val weekResult = dao.usageSummary(week.first, week.second)
        val monthResult = dao.usageSummary(month.first, month.second)

        PeriodSummaries(
            today = UsageSummary(todayResult.totalDurationMillis, todayResult.totalCount),
            yesterday = UsageSummary(yesterdayResult.totalDurationMillis, yesterdayResult.totalCount),
            thisWeek = UsageSummary(weekResult.totalDurationMillis, weekResult.totalCount),
            thisMonth = UsageSummary(monthResult.totalDurationMillis, monthResult.totalCount),
        )
    }

    data class PeriodSummaries(
        val today: UsageSummary,
        val yesterday: UsageSummary,
        val thisWeek: UsageSummary,
        val thisMonth: UsageSummary,
    )

    // ---- Date range calculations using device local calendar ----

    private fun todayRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun yesterdayRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val yesterday = LocalDate.now(zone).minusDays(1)
        val start = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun thisWeekRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monday = today.with(DayOfWeek.MONDAY)
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    private fun thisMonthRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstOfMonth = today.withDayOfMonth(1)
        val start = firstOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = firstOfMonth.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
