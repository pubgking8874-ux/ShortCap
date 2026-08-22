package com.shortscap.app.shorts

/**
 * LinkedIn video adapter.
 *
 * LinkedIn hosts short-form video surfaces inside its feed (plus articles,
 * jobs, messaging, learning). The adapter combines the signals now
 * available: recognized platform (package) + scroll interaction in the
 * foreground context (TYPE_VIEW_SCROLLED) + the existing ≥3 second
 * engagement rule (aggregator). No scroll evidence → UNKNOWN, never counted.
 */
object LinkedInVideoAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.LINKEDIN
    override val packageNames: Set<String> = setOf("com.linkedin.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.LINKEDIN, ShortSurface.LINKEDIN_SHORT_VIDEO, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.LINKEDIN, 0.1f)
        }
}
