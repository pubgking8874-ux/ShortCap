package com.shortscap.app.activity

import com.shortscap.app.db.ScreenActivityUsageEntity
import com.shortscap.app.db.ShortsUsageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 1 reporting tests — the pure aggregation over REAL persisted rows
 * ([ActivityRepository.buildReport]). No Room/device needed: these assert the
 * exact rules the acceptance criteria require (real counts, real durations,
 * local-calendar-day boundary, no fake values).
 */
class ActivityRepositoryTest {

    private val zone = ZoneId.systemDefault()

    private fun dayStart(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun shorts(
        platform: String,
        occurredAt: Long,
        durationMillis: Long = 60_000L,
        countDelta: Int = 1,
    ) = ShortsUsageEntity(
        platform = platform,
        surface = "SHORTS",
        detectionMethod = "SCROLL",
        confidence = 0.8f,
        occurredAt = occurredAt,
        durationMillis = durationMillis,
        countDelta = countDelta,
    )

    private fun session(
        packageName: String,
        occurredAt: Long,
        durationSeconds: Long = 120L,
        appName: String? = null,
    ) = ScreenActivityUsageEntity(
        packageName = packageName,
        appName = appName,
        usageDate = "2026-01-01",
        durationSeconds = durationSeconds,
        launchCount = 1,
        occurredAt = occurredAt,
    )

    @Test
    fun `empty data reports zeros - no fake values`() {
        val report = ActivityRepository.buildReport(
            ActivityPeriod.DAILY,
            LocalDate.now(),
            emptyList(),
            emptyList(),
        )
        assertEquals(0, report.shortsCount)
        assertEquals(0, report.shortsMinutes)
        assertEquals(0, report.totalMinutes)
        assertEquals(0, report.unlockCount)
        assertEquals(0L, report.avgSessionSeconds)
        assertTrue(report.distribution.isEmpty())
        assertTrue(report.points.size == 24) // DAILY keeps the full 24-hour timeline
        assertTrue(report.points.all { it.minutes == 0 }) // and every hour is empty
    }

    @Test
    fun `five shorts today aggregate count, duration and platform breakdown`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val records = listOf(
            shorts(platform = "YOUTUBE", occurredAt = start + 1_000L, durationMillis = 40_000L),
            shorts(platform = "YOUTUBE", occurredAt = start + 5_000L, durationMillis = 35_000L),
            shorts(platform = "INSTAGRAM", occurredAt = start + 10_000L, durationMillis = 45_000L),
            shorts(platform = "INSTAGRAM", occurredAt = start + 20_000L, durationMillis = 20_000L),
            shorts(platform = "YOUTUBE", occurredAt = start + 30_000L, durationMillis = 10_000L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, records, emptyList())

        assertEquals(5, report.shortsCount)
        assertEquals(150_000L, records.sumOf { it.durationMillis }) // 2m 30s total
        assertEquals(3, report.shortsMinutes) // rounded to the nearest minute

        val byPlatform = report.shortsByPlatform.associate { it.platformName to it.count }
        assertEquals(3, byPlatform["YOUTUBE"])
        assertEquals(2, byPlatform["INSTAGRAM"])
    }

    @Test
    fun `distribution and stats come from persisted app sessions`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.instagram.android", start + 1_000L, durationSeconds = 600L),
            session("com.android.chrome", start + 2_000L, durationSeconds = 300L),
            session("com.whatsapp", start + 3_000L, durationSeconds = 60L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        val byId = report.distribution.associate { it.id to it.minutes }
        assertEquals(10, byId["instagram"]) // 600s
        assertEquals(5, byId["chrome"]) // 300s
        assertEquals(1, byId["com.whatsapp"]) // 60s → 1m
        assertEquals(16, report.totalMinutes)
        assertEquals(3, report.unlockCount)
        assertEquals(320L, report.avgSessionSeconds)
        // All three sessions started just after midnight → hour 0 holds the day.
        assertEquals(16, report.points[0].minutes)
    }

    @Test
    fun `today boundary excludes yesterday and tomorrow records`() {
        val today = LocalDate.now()
        val records = listOf(
            shorts(platform = "YOUTUBE", occurredAt = dayStart(today.minusDays(1)) + 1_000L),
            shorts(platform = "YOUTUBE", occurredAt = dayStart(today) + 1_000L),
            shorts(platform = "YOUTUBE", occurredAt = dayStart(today.plusDays(1)) + 1_000L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, records, emptyList())
        assertEquals(1, report.shortsCount)
        assertEquals(1, report.shortsMinutes)
    }

    @Test
    fun `weekly shorts cover the monday-to-sunday week only`() {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val records = listOf(
            shorts(platform = "YOUTUBE", occurredAt = dayStart(monday) + 1_000L),
            shorts(platform = "YOUTUBE", occurredAt = dayStart(monday.plusDays(6)) + 1_000L),
            // Sunday of the PREVIOUS week — outside the Monday-first week.
            shorts(platform = "YOUTUBE", occurredAt = dayStart(monday.minusDays(1)) + 1_000L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.WEEKLY, today, records, emptyList())
        assertEquals(2, report.shortsCount)
        assertEquals(7, report.points.size) // exactly Mon..Sun
    }

    @Test
    fun `daily trend compares today against yesterday`() {
        val today = LocalDate.now()
        val sessions = listOf(
            session("com.android.chrome", dayStart(today) + 1_000L, durationSeconds = 600L),
            session("com.android.chrome", dayStart(today.minusDays(1)) + 1_000L, durationSeconds = 300L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)
        // today 10m vs yesterday 5m → +100%
        assertEquals(100, report.trendPercent)
    }

    // ------------------------------------------------------------------
    // Phase 1.2 — system/IME/launcher/ShortsCap-internal packages are
    // filtered BEFORE aggregation, so totals, timeline, distribution and
    // "Other" all operate on the same reportable user-app dataset.
    // ------------------------------------------------------------------

    @Test
    fun `system UI sessions do not appear in distribution or totals`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.android.systemui", start + 1_000L, durationSeconds = 36_000L), // 10h idle
            session("com.google.android.youtube", start + 2_000L, durationSeconds = 1_800L), // 30m
            session("com.android.chrome", start + 3_000L, durationSeconds = 1_200L), // 20m
            session("com.instagram.android", start + 4_000L, durationSeconds = 600L), // 10m
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        val names = report.distribution.map { it.name }
        assertTrue("systemui must not appear: $names", names.none { it.contains("systemui") })
        assertEquals(setOf("YouTube", "Chrome", "Instagram"), names.toSet())
        // Total/timeline reflect only the 60m of user-app usage, not 10h30m.
        assertEquals(60, report.totalMinutes)
        assertEquals(60, report.points.sumOf { it.minutes })
    }

    @Test
    fun `Gboard and android system sessions are excluded from user reporting`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.google.android.inputmethod.latin", start + 1_000L, durationSeconds = 1_800L),
            session("android", start + 2_000L, durationSeconds = 1_600L),
            session("com.whatsapp", start + 3_000L, durationSeconds = 120L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        assertEquals(listOf("WhatsApp"), report.distribution.map { it.name })
        assertEquals(2, report.totalMinutes) // only WhatsApp's 2 minutes
        assertEquals(1, report.unlockCount) // system blips are not unlocks
        assertEquals(120L, report.avgSessionSeconds)
    }

    @Test
    fun `ShortsCap internal usage does not inflate most used apps or totals`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.shortscap.app", start + 1_000L, durationSeconds = 3_000L), // 50m
            session("com.google.android.youtube", start + 2_000L, durationSeconds = 600L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        assertEquals(listOf("YouTube"), report.distribution.map { it.name })
        assertEquals(10, report.totalMinutes)
    }

    @Test
    fun `filtering happens before top-N - system UI cannot crowd out user apps`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.android.systemui", start + 1_000L, durationSeconds = 36_000L), // 10h — would be #1 unfiltered
            session("com.google.android.youtube", start + 2_000L, durationSeconds = 1_800L),
            session("com.android.chrome", start + 3_000L, durationSeconds = 1_200L),
            session("com.instagram.android", start + 4_000L, durationSeconds = 600L),
            session("com.whatsapp", start + 5_000L, durationSeconds = 300L),
            session("com.facebook.katana", start + 6_000L, durationSeconds = 120L),
            session("com.snapchat.android", start + 7_000L, durationSeconds = 60L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        val names = report.distribution.map { it.name }
        assertTrue("systemui must not appear: $names", names.none { it.contains("systemui") })
        // Top-5 of the FILTERED set; the 6th real app folds into "Other".
        assertEquals(6, names.size)
        assertTrue(names.contains("Other"))
        // 30+20+10+5+2+1 = 68m of user apps — the 10h of system UI never counted.
        assertEquals(68, report.totalMinutes)
    }

    @Test
    fun `Other contains only reportable user apps - never system time`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.android.systemui", start + 1_000L, durationSeconds = 36_000L), // 10h system
            session("android", start + 2_000L, durationSeconds = 3_600L), // 1h system
            session("com.google.android.youtube", start + 3_000L, durationSeconds = 1_800L), // 30m
            session("com.android.chrome", start + 4_000L, durationSeconds = 1_200L), // 20m
            session("com.instagram.android", start + 5_000L, durationSeconds = 600L), // 10m
            session("com.whatsapp", start + 6_000L, durationSeconds = 300L), // 5m
            session("com.facebook.katana", start + 7_000L, durationSeconds = 120L), // 2m
            session("com.snapchat.android", start + 8_000L, durationSeconds = 60L), // 1m
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        val other = report.distribution.firstOrNull { it.id == "other" }
        assertNotNull("expected an Other slice", other)
        assertEquals(1, other?.minutes) // Snapchat (6th user app) only — no system time
        assertEquals(68, report.totalMinutes) // system 11h never counted
        assertEquals(67, report.distribution.filter { it.id != "other" }.sumOf { it.minutes })
    }

    @Test
    fun `timeline excludes system duration`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.android.systemui", start + 1_000L, durationSeconds = 36_000L), // 10h
            session("com.google.android.youtube", start + 2_000L, durationSeconds = 1_800L), // 30m
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        assertEquals(30, report.totalMinutes)
        assertEquals(30, report.points.sumOf { it.minutes })
        assertEquals(30, report.points[0].minutes) // both sessions started just after midnight
    }

    @Test
    fun `distribution uses exactly the reportable package set - same as Home`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val packages = listOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin",
            "com.google.android.googlequicksearchbox",
            "com.shortscap.app",
            "com.google.android.youtube",
            "com.android.chrome",
            "com.example.someapp",
        )
        val sessions = packages.mapIndexed { index, pkg ->
            session(pkg, start + (index + 1) * 1_000L, durationSeconds = 120L)
        }
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        // Home (AppViewModel) filters with the SAME PackageClassifier before
        // counting distinct packages; Activity must agree on that exact set.
        val expectedReportable = packages
            .filter { PackageClassifier.isReportable(PackageClassifier.classify(it)) }
            .map { ActivityAppNames.friendlyName(it) }
            .toSet()
        assertEquals(expectedReportable, report.distribution.map { it.name }.toSet())
        assertEquals(expectedReportable.size, report.unlockCount)
    }

    // ------------------------------------------------------------------
    // Phase 1.3 — Most Used Apps cleanup (launcher leak) + selected-hour
    // "Apps Used" breakdown.
    // ------------------------------------------------------------------

    @Test
    fun `launcher and system UI are excluded - user apps only in most used`() {
        val today = LocalDate.now()
        val start = dayStart(today)
        val sessions = listOf(
            session("com.android.systemui", start + 1_000L, durationSeconds = 36_000L), // 10h system UI
            session("com.android.launcher3", start + 2_000L, durationSeconds = 18_000L), // 5h launcher
            session("com.google.android.youtube", start + 3_000L, durationSeconds = 1_800L), // 30m
            session("com.instagram.android", start + 4_000L, durationSeconds = 1_200L), // 20m
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)

        val names = report.distribution.map { it.name }
        assertTrue("systemui must not appear: $names", names.none { it.contains("systemui") })
        assertTrue("launcher must not appear: $names", names.none { it.contains("launcher") })
        assertEquals(setOf("YouTube", "Instagram"), names.toSet())
        assertEquals(50, report.totalMinutes) // only the 50m of real user apps
        assertTrue(report.distribution.none { it.id == "other" }) // no hidden system time
    }

    @Test
    fun `selected hour returns a single app with its exact duration`() {
        val today = LocalDate.now()
        // YouTube 1:10 PM → 1:20 PM (hour 13, 10 minutes).
        val start = dayStart(today) + 13 * 3_600_000L + 10 * 60_000L
        val sessions = listOf(session("com.google.android.youtube", start, durationSeconds = 600L))

        val apps = ActivityRepository.hourAppsFromSessions(13, today, sessions)

        assertEquals(1, apps.size)
        assertEquals("com.google.android.youtube", apps[0].packageName)
        assertEquals("YouTube", apps[0].name)
        assertEquals(10, apps[0].minutes)
    }

    @Test
    fun `selected hour returns all apps sorted by duration descending`() {
        val today = LocalDate.now()
        val hourStart = dayStart(today) + 13 * 3_600_000L
        val sessions = listOf(
            session("com.google.android.youtube", hourStart, durationSeconds = 540L), // 9m
            session("com.instagram.android", hourStart + 9 * 60_000L, durationSeconds = 300L), // 5m
            session("com.android.chrome", hourStart + 14 * 60_000L, durationSeconds = 120L), // 2m
        )

        val apps = ActivityRepository.hourAppsFromSessions(13, today, sessions)

        assertEquals(listOf("YouTube", "Instagram", "Chrome"), apps.map { it.name })
        assertEquals(listOf(9, 5, 2), apps.map { it.minutes })
    }

    @Test
    fun `cross-hour session contributes only its overlap to the selected hour`() {
        val today = LocalDate.now()
        // YouTube 12:55 PM → 1:10 PM: hour 13 must get 10m, never 15m.
        val start = dayStart(today) + 12 * 3_600_000L + 55 * 60_000L
        val sessions = listOf(session("com.google.android.youtube", start, durationSeconds = 900L))

        val hour13 = ActivityRepository.hourAppsFromSessions(13, today, sessions)
        val hour12 = ActivityRepository.hourAppsFromSessions(12, today, sessions)

        assertEquals(10, hour13.single().minutes)
        assertEquals(5, hour12.single().minutes)
    }

    @Test
    fun `selected hour excludes system packages`() {
        val today = LocalDate.now()
        val hourStart = dayStart(today) + 13 * 3_600_000L
        val sessions = listOf(
            session("com.android.systemui", hourStart, durationSeconds = 1_800L), // 30m system
            session("com.google.android.youtube", hourStart + 30 * 60_000L, durationSeconds = 600L), // 10m
        )

        val apps = ActivityRepository.hourAppsFromSessions(13, today, sessions)

        assertEquals(listOf("YouTube"), apps.map { it.name })
        assertEquals(10, apps.single().minutes)
    }

    @Test
    fun `selected hour breakdown sums to the timeline hour total`() {
        val today = LocalDate.now()
        val hourStart = dayStart(today) + 13 * 3_600_000L
        val sessions = listOf(
            session("com.google.android.youtube", hourStart, durationSeconds = 540L),
            session("com.instagram.android", hourStart + 9 * 60_000L, durationSeconds = 300L),
        )
        val report = ActivityRepository.buildReport(ActivityPeriod.DAILY, today, emptyList(), sessions)
        val apps = ActivityRepository.hourAppsFromSessions(13, today, sessions)

        // Timeline hour 13 = 14m; per-app breakdown 9m + 5m = 14m.
        assertEquals(14, report.points[13].minutes)
        assertEquals(14, apps.sumOf { it.minutes })
    }
}