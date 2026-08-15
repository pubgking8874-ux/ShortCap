package com.shortscap.app.shorts

/**
 * LinkedIn video adapter.
 *
 * LinkedIn hosts short-form video surfaces inside its feed (plus articles,
 * jobs, messaging, learning). With package-only signals the adapter only
 * identifies the platform — the surface stays UNKNOWN and nothing is counted
 * as short-form yet.
 */
object LinkedInVideoAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.LINKEDIN
    override val packageNames: Set<String> = setOf("com.linkedin.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.LINKEDIN,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.1f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
