package com.shortscap.app.shorts

/**
 * The CONSERVATIVE fallback for unknown / future applications that have no
 * platform-specific adapter yet.
 *
 * It may use generic signals (foreground duration, interaction count,
 * visible descriptors) as future signal sources are added, but it NEVER
 * assumes an app is short-form just because it is an app. With today's
 * signal set (package name only) it returns UNKNOWN — and because the
 * aggregator only counts when [ShortDetectionResult.isShortForm] is true,
 * an unknown classification is never falsely counted.
 */
object GenericShortVideoAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.UNKNOWN
    override val packageNames: Set<String> = emptySet()

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        // No platform rules and no reliable generic signal yet — classify as
        // UNKNOWN rather than guessing. Future interaction/UI signal sources
        // can raise confidence here without touching the aggregator.
        ShortDetectionResult(
            platform = ShortPlatform.UNKNOWN,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0f,
            detectionMethod = DetectionMethod.GENERIC_UI_SIGNAL,
        )
}
