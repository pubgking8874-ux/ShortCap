package com.shortscap.app.shorts

/**
 * TikTok adapter.
 *
 * TikTok's primary surface IS its short-video feed, but the app also hosts
 * LIVE, stories, search and other screens. Package identity alone therefore
 * does not prove a Short is being watched. With the current signals the
 * adapter identifies the platform and keeps the surface UNKNOWN with medium
 * confidence — conservative, never counting an open TikTok as a Short by
 * default. Both known package aliases (aweme + older musically) are covered.
 */
object TikTokAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.TIKTOK
    override val packageNames: Set<String> = setOf(
        "com.ss.android.ugc.aweme",
        "com.zhiliaoapp.musically",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.TIKTOK,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.35f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
