package com.shortscap.app.shorts

/**
 * Instagram Reels adapter.
 *
 * Reels is ONE surface inside the main Instagram app (Feed, Stories, DM,
 * Explore, Live), so package identity alone never proves a Reel is being
 * watched. The adapter combines the signals now available:
 *
 *  - recognized platform (package, always true here);
 *  - scroll interaction observed in the foreground context
 *    (TYPE_VIEW_SCROLLED — the Reels feed is a vertical scroll surface);
 *  - the existing ≥3 second engagement rule (applied by the aggregator).
 *
 * No scroll evidence → UNKNOWN, never counted (watching a single video
 * without browsing the feed is not counted — documented limitation).
 */
object InstagramReelsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.INSTAGRAM
    override val packageNames: Set<String> = setOf("com.instagram.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.INSTAGRAM, ShortSurface.INSTAGRAM_REELS, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.INSTAGRAM, 0.15f)
        }
}
