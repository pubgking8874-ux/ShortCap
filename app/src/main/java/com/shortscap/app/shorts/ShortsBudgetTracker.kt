package com.shortscap.app.shorts

/**
 * One GLOBAL Shorts budget for the user.
 *
 * Shorts from every platform contribute to the SAME combined total — e.g.
 * YouTube Shorts 10 min + Instagram Reels 8 min + TikTok 5 min +
 * Snapchat Spotlight 4 min = 27 min of global Shorts time — so the app's
 * Shorts limit applies to the combined short-form usage, never to
 * per-platform independent limits (unless a future product requirement
 * explicitly adds them).
 *
 * The per-platform breakdown is retained alongside the global totals so
 * future reporting can show platform/surface usage without re-deriving it —
 * platform/surface information is never thrown away after counting.
 *
 * This tracker only ACCUMULATES [ShortUsageUpdate]s produced by a
 * [ShortUsageAggregator]; it does not detect content, and it does not decide
 * limits (real-time enforcement and limit state remain Android-side /
 * settings-driven, and the backend persists the resulting summaries).
 */
class ShortsBudgetTracker {

    private var totalDurationMillis = 0L
    private var totalCount = 0L
    private val durationByPlatform = mutableMapOf<ShortPlatform, Long>()

    /** Combined Shorts duration across ALL platforms (ms). */
    val totalMillis: Long get() = totalDurationMillis

    /** Combined Shorts count across ALL platforms. */
    val totalShorts: Long get() = totalCount

    /** Apply one aggregator decision to the global budget. */
    fun apply(update: ShortUsageUpdate) {
        if (!update.counted) return
        totalDurationMillis += update.durationMillis
        totalCount += update.countDelta
        durationByPlatform[update.platform] =
            durationByPlatform.getOrDefault(update.platform, 0L) + update.durationMillis
    }

    /** Per-platform duration breakdown (ms) — for future platform-specific reports. */
    fun platformDurationMillis(): Map<ShortPlatform, Long> = durationByPlatform.toMap()
}
