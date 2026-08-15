package com.shortscap.app.shorts

/**
 * Instagram Reels adapter.
 *
 * Reels is ONE surface inside the main Instagram app, alongside Feed,
 * Stories, DM, Explore and Live. With the current package-only signals the
 * surface cannot be separated, so the adapter is conservative: platform is
 * identified, surface stays UNKNOWN and nothing is classified as short-form
 * yet. A future interaction/UI signal source can raise this confidence
 * without changing the aggregator.
 */
object InstagramReelsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.INSTAGRAM
    override val packageNames: Set<String> = setOf("com.instagram.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.INSTAGRAM,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.15f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
