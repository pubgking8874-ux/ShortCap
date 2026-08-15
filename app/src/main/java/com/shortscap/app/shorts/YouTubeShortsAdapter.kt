package com.shortscap.app.shorts

/**
 * YouTube Shorts adapter.
 *
 * Platform identity is package-based (high confidence). Surface detection
 * uses the YouTube Shorts player activity class name from window-state
 * events when available — currently the only signal that reliably separates
 * the Shorts surface from YouTube's long-form / live / Home / Stories
 * surfaces. Without that signal the surface stays UNKNOWN: YouTube is far
 * more than Shorts, so the app must never assume "YouTube is open = Shorts
 * are being watched".
 */
object YouTubeShortsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.YOUTUBE
    override val packageNames: Set<String> = setOf("com.google.android.youtube")

    /** YouTube's Shorts player window class (from accessibility window-state events). */
    private val shortsActivityClasses = setOf(
        "com.google.android.apps.youtube.app.application.Shell\$ShortsActivity",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName
        if (className != null && className in shortsActivityClasses) {
            return ShortDetectionResult(
                platform = ShortPlatform.YOUTUBE,
                surface = ShortSurface.YOUTUBE_SHORTS,
                isShortForm = true,
                confidence = 0.85f,
                detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
                metadata = mapOf("surfaceSignal" to "activity_class"),
            )
        }
        // Package is YouTube but the surface is not confirmable — do not guess.
        return ShortDetectionResult(
            platform = ShortPlatform.YOUTUBE,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.2f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
    }
}
