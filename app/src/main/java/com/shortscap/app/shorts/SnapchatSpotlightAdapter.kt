package com.shortscap.app.shorts

/**
 * Snapchat Spotlight adapter.
 *
 * Spotlight is one surface among many in Snapchat (chat, camera, stories,
 * maps, Discover). The adapter combines the signals now available:
 * recognized platform (package) + scroll interaction in the foreground
 * context (TYPE_VIEW_SCROLLED) + the existing ≥3 second engagement rule
 * (aggregator). No scroll evidence → UNKNOWN, never counted.
 */
object SnapchatSpotlightAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.SNAPCHAT
    override val packageNames: Set<String> = setOf("com.snapchat.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.SNAPCHAT, ShortSurface.SNAPCHAT_SPOTLIGHT, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.SNAPCHAT, 0.15f)
        }
}
