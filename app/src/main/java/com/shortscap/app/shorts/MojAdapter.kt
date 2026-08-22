package com.shortscap.app.shorts

/**
 * Moj adapter.
 *
 * Moj (package `in.mohalla.video`) is a predominantly short-form app, but it
 * also hosts live streams and short dramas, so even here "app is open" is
 * not proof of a Short. The adapter combines the signals now available:
 * recognized platform (package) + scroll interaction in the foreground
 * context (TYPE_VIEW_SCROLLED) + the existing ≥3 second engagement rule
 * (aggregator). No scroll evidence → UNKNOWN, never counted.
 */
object MojAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.MOJ
    override val packageNames: Set<String> = setOf("in.mohalla.video")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.MOJ, ShortSurface.MOJ_SHORT_VIDEO, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.MOJ, 0.3f)
        }
}
