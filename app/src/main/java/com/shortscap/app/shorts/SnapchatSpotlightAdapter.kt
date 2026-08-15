package com.shortscap.app.shorts

/**
 * Snapchat Spotlight adapter.
 *
 * Spotlight is one surface among many in Snapchat (chat, camera, stories,
 * maps, Discover). With package-only signals the surface cannot be
 * separated, so the adapter is conservative: platform identified, surface
 * UNKNOWN, nothing counted as short-form yet.
 */
object SnapchatSpotlightAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.SNAPCHAT
    override val packageNames: Set<String> = setOf("com.snapchat.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.SNAPCHAT,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.15f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
