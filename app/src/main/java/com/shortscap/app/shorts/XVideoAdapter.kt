package com.shortscap.app.shorts

/**
 * X (Twitter) short-video adapter.
 *
 * X hosts short-form video surfaces inside a very broad app (timeline,
 * trending, live, Spaces, search). The adapter combines the signals now
 * available: recognized platform (package) + scroll interaction in the
 * foreground context (TYPE_VIEW_SCROLLED) + the existing ≥3 second
 * engagement rule (aggregator). No scroll evidence → UNKNOWN, never counted.
 * Both known package aliases are covered.
 */
object XVideoAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.X
    override val packageNames: Set<String> = setOf(
        "com.twitter.android",
        "com.twitter.android.lite",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.X, ShortSurface.X_SHORT_VIDEO, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.X, 0.1f)
        }
}
