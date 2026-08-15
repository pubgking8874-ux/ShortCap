package com.shortscap.app.shorts

/**
 * Moj adapter.
 *
 * Moj (package `in.mohalla.video`) is a predominantly short-form app, but it
 * also hosts live streams and short dramas, so even here "app is open" is
 * not proof of a Short. The adapter identifies the platform with medium
 * confidence and keeps the surface UNKNOWN; a future interaction/UI signal
 * source can confirm MOJ_SHORT_VIDEO without changing the aggregator.
 */
object MojAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.MOJ
    override val packageNames: Set<String> = setOf("in.mohalla.video")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.MOJ,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.3f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
