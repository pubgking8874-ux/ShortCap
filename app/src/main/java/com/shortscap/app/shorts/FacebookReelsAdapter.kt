package com.shortscap.app.shorts

/**
 * Facebook Reels adapter.
 *
 * Facebook is already part of the app's platform catalog
 * ([com.shortscap.app.model.ShortVideoPlatform] `facebook_reels` and the
 * `SupportedShortVideoPackages` overlay set), so it gets a first-class
 * adapter rather than falling through to the generic fallback. Reels is one
 * surface inside the main Facebook app (Feed, Stories, Watch, Groups, …), so
 * detection is conservative: platform identified, surface UNKNOWN, nothing
 * counted as short-form yet.
 */
object FacebookReelsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.FACEBOOK
    override val packageNames: Set<String> = setOf("com.facebook.katana")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.FACEBOOK,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.15f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
