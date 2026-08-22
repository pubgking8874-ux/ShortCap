package com.shortscap.app.shorts

/**
 * TikTok adapter.
 *
 * TikTok's primary surface IS its short-video feed, but the app also hosts
 * LIVE, stories, search and other screens. The adapter combines the signals
 * now available: recognized platform (package) + scroll interaction in the
 * foreground context (TYPE_VIEW_SCROLLED — the short-video feed scrolls
 * between videos) + the existing ≥3 second engagement rule (aggregator).
 *
 * No scroll evidence → UNKNOWN, never counted (a session that only watches
 * a single video without browsing the feed is not counted — documented
 * limitation). Both known package aliases (aweme + older musically) are
 * covered.
 */
object TikTokAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.TIKTOK
    override val packageNames: Set<String> = setOf(
        "com.ss.android.ugc.aweme",
        "com.zhiliaoapp.musically",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.TIKTOK, ShortSurface.TIKTOK_SHORT_FEED, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.TIKTOK, 0.35f)
        }
}
