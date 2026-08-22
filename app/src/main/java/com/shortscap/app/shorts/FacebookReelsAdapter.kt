package com.shortscap.app.shorts

/**
 * Facebook Reels adapter.
 *
 * Facebook is already part of the app's platform catalog
 * ([com.shortscap.app.model.ShortVideoPlatform] `facebook_reels`), so it
 * gets a first-class adapter rather than falling through to the generic
 * fallback. Reels is one surface inside the main Facebook app (Feed,
 * Stories, Watch, Groups, …). The adapter combines the signals now
 * available: recognized platform (package) + scroll interaction in the
 * foreground context (TYPE_VIEW_SCROLLED) + the existing ≥3 second
 * engagement rule (aggregator). No scroll evidence → UNKNOWN, never counted.
 */
object FacebookReelsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.FACEBOOK
    override val packageNames: Set<String> = setOf("com.facebook.katana")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.FACEBOOK, ShortSurface.FACEBOOK_REELS, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.FACEBOOK, 0.15f)
        }
}
