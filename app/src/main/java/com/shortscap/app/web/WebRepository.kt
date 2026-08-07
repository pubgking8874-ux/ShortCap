package com.shortscap.app.web

import com.shortscap.app.favicon.FaviconRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * WebRepository — the single data seam for the Web section.
 *
 * Today it seeds demo website rules and deterministic demo usage records so
 * every Web screen is fully functional. The analytics aggregation is a pure
 * function ([analyticsSummary]) with no UI/backend coupling.
 *
 * Future-ready: rules and analytics stay logically separated, and both map
 * 1:1 to a future backend / database. When a real data source connects, the
 * seed calls are swapped for repository calls — the UI consumes only
 * [WebRule] / [WebUsageRecord] / [WebAnalyticsSummary] and never changes.
 */
object WebRepository {

    private data class SeedSite(val domain: String, val name: String, val baseMinutes: Int)

    private val seedSites = listOf(
        SeedSite("youtube.com", "YouTube", 45),
        SeedSite("instagram.com", "Instagram", 38),
        SeedSite("tiktok.com", "TikTok", 30),
        SeedSite("netflix.com", "Netflix", 35),
        SeedSite("reddit.com", "Reddit", 22),
        SeedSite("x.com", "X", 18),
        SeedSite("wikipedia.org", "Wikipedia", 25),
        SeedSite("coursera.org", "Coursera", 20),
    )

    /** Demo block/allow list — replaced by a backend `web_rules` fetch later. */
    fun seedRules(now: Long = System.currentTimeMillis()): List<WebRule> = listOf(
        seedRule("tiktok.com", "TikTok", WebRuleStatus.BLOCKED, now),
        seedRule("instagram.com", "Instagram", WebRuleStatus.BLOCKED, now),
        seedRule("x.com", "X", WebRuleStatus.BLOCKED, now),
        seedRule("reddit.com", "Reddit", WebRuleStatus.BLOCKED, now),
        seedRule("netflix.com", "Netflix", WebRuleStatus.BLOCKED, now),
        seedRule("youtube.com", "YouTube", WebRuleStatus.ALLOWED, now),
        seedRule("wikipedia.org", "Wikipedia", WebRuleStatus.ALLOWED, now),
        seedRule("coursera.org", "Coursera", WebRuleStatus.ALLOWED, now),
    )

    /**
     * Builds a seed rule with its website identity — [faviconUrl] is the
     * primary official favicon candidate and [localIconPath] the favicon
     * cache key; the favicon pixels themselves are resolved + cached at
     * render time by [FaviconRepository] (never stored inline).
     */
    private fun seedRule(domain: String, name: String, status: WebRuleStatus, now: Long): WebRule =
        WebRule(
            id = domain,
            domain = domain,
            displayName = name,
            status = status,
            createdAt = now,
            updatedAt = now,
            faviconUrl = FaviconRepository.faviconUrl(domain),
            localIconPath = FaviconRepository.cacheKey(domain),
        )

    /**
     * Deterministic demo website usage for the last 30 days. This is NOT
     * real detection — no fake YouTube-Shorts detection is claimed. A future
     * tracking mechanism (browser / VPN / accessibility-based) will insert
     * real [WebUsageRecord]s of this exact shape; the screens never change.
     */
    fun seedUsageRecords(todayEpochDay: Long = LocalDate.now().toEpochDay()): List<WebUsageRecord> {
        val records = mutableListOf<WebUsageRecord>()
        seedSites.forEachIndexed { siteIndex, site ->
            for (dayOffset in 29 downTo 0) {
                val date = LocalDate.ofEpochDay(todayEpochDay - dayOffset)
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                // Stable pseudo-random wobble (no Random) so every render is consistent.
                val wobble = 1.0 + 0.16 * (((dayOffset + siteIndex * 2) % 5) - 2)
                val factor = if (weekend) wobble * 1.25 else wobble
                val minutes = (site.baseMinutes * factor).toInt()
                records += WebUsageRecord(site.domain, site.name, minutes, date.toEpochDay())
            }
        }
        return records
    }

    /**
     * Pure aggregation — builds the [WebAnalyticsSummary] for [period] from
     * raw [WebUsageRecord]s. Independent of the data source, so it works
     * identically with seed data and future backend records.
     */
    fun analyticsSummary(
        records: List<WebUsageRecord>,
        period: WebAnalyticsPeriod,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): WebAnalyticsSummary {
        val startDay = when (period) {
            WebAnalyticsPeriod.TODAY -> todayEpochDay
            WebAnalyticsPeriod.WEEK -> todayEpochDay - 6
            WebAnalyticsPeriod.MONTH -> todayEpochDay - 29
        }
        val inRange = records.filter { it.dateEpochDay in startDay..todayEpochDay }
        val total = inRange.sumOf { it.durationMinutes }

        val items = inRange
            .groupBy { it.domain.lowercase() }
            .map { (_, recs) ->
                val first = recs.first()
                val minutes = recs.sumOf { it.durationMinutes }
                WebUsageItem(
                    domain = first.domain,
                    displayName = first.displayName,
                    durationMinutes = minutes,
                    percentage = if (total > 0) (minutes * 100f / total).toInt() else 0,
                )
            }
            .sortedByDescending { it.durationMinutes }

        val trend = when (period) {
            WebAnalyticsPeriod.MONTH -> monthlyTrend(records, todayEpochDay)
            else -> weeklyTrend(records, todayEpochDay)
        }

        return WebAnalyticsSummary(period = period, totalMinutes = total, items = items, trend = trend)
    }

    /** Daily bars for the last 7 days (used by Today and Week). */
    private fun weeklyTrend(records: List<WebUsageRecord>, todayEpochDay: Long): List<WebTrendPoint> =
        (6 downTo 0).map { offset ->
            val day = LocalDate.ofEpochDay(todayEpochDay - offset)
            val minutes = records.filter { it.dateEpochDay == day.toEpochDay() }.sumOf { it.durationMinutes }
            WebTrendPoint(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), minutes)
        }

    /** Four weekly buckets over the last 28 days (W4 = most recent week). */
    private fun monthlyTrend(records: List<WebUsageRecord>, todayEpochDay: Long): List<WebTrendPoint> =
        (3 downTo 0).map { bucket ->
            val startDay = todayEpochDay - (bucket * 7 + 6)
            val endDay = todayEpochDay - bucket * 7
            val minutes = records.filter { it.dateEpochDay in startDay..endDay }.sumOf { it.durationMinutes }
            WebTrendPoint("W${4 - bucket}", minutes)
        }

    // ---- Website display-name identification ----
    // Display-name hints for very common domains (metadata only — favicons are
    // NEVER hardcoded; the favicon system resolves them automatically for any
    // domain). Unknown domains fall back to derivation from the domain.
    private val knownDisplayNames = mapOf(
        "youtube.com" to "YouTube",
        "google.com" to "Google",
        "google.co.in" to "Google",
        "x.com" to "X",
        "twitter.com" to "Twitter",
        "reddit.com" to "Reddit",
        "instagram.com" to "Instagram",
        "facebook.com" to "Facebook",
        "tiktok.com" to "TikTok",
        "netflix.com" to "Netflix",
        "wikipedia.org" to "Wikipedia",
        "coursera.org" to "Coursera",
        "linkedin.com" to "LinkedIn",
        "github.com" to "GitHub",
        "amazon.com" to "Amazon",
        "whatsapp.com" to "WhatsApp",
        "spotify.com" to "Spotify",
        "discord.com" to "Discord",
        "twitch.tv" to "Twitch",
        "docs.google.com" to "Google Docs",
        "drive.google.com" to "Google Drive",
        "gmail.com" to "Gmail",
    )

    /**
     * Identifies a human-friendly website name for [domain]: known-domain
     * hint first (also matched by root when a subdomain is given), then
     * derived from the domain. Pure function — no context or IO.
     */
    fun displayNameFor(domain: String): String {
        val d = domain.trim().lowercase()
        knownDisplayNames[d]?.let { return it }
        val parts = d.split(".")
        if (parts.size > 2) {
            knownDisplayNames[parts.takeLast(2).joinToString(".")]?.let { return it }
        }
        return d.removePrefix("www.").substringBefore(".").replaceFirstChar { it.uppercase() }
    }

    // ---- Future backend / tracking seams (documented only — not implemented) ----
    // Once a real data source connects (backend API, database, or a
    // browser/VPN/accessibility-based tracking mechanism), swap the seeds
    // above for:
    //
    //   suspend fun fetchRulesFromBackend(): List<WebRule>
    //   suspend fun syncRuleToCloud(rule: WebRule)
    //   suspend fun fetchUsageFromBackend(fromEpochDay: Long, toEpochDay: Long): List<WebUsageRecord>
    //   fun trackWebsiteVisit(domain: String, durationMinutes: Int)
    //
    // The UI only consumes WebRule / WebUsageRecord / WebAnalyticsSummary,
    // so replacing the data source requires zero screen changes.
}
