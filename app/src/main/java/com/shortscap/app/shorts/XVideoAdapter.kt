package com.shortscap.app.shorts

/**
 * X (Twitter) short-video adapter.
 *
 * X hosts short-form video surfaces inside a very broad app (timeline,
 * trending, live, Spaces, search). With package-only signals the adapter
 * only identifies the platform — the surface stays UNKNOWN and nothing is
 * counted as short-form yet. Both known package aliases are covered.
 */
object XVideoAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.X
    override val packageNames: Set<String> = setOf(
        "com.twitter.android",
        "com.twitter.android.lite",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.X,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.1f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
