package com.shortscap.app.shorts

/**
 * ShareChat adapter.
 *
 * ShareChat (package `com.sharechat.android`) is a predominantly short-form
 * app, but it also hosts live streams, chats and other long-form content, so
 * even here "app is open" is not proof of a Short. Mirroring [MojAdapter],
 * the adapter identifies the platform with medium confidence and keeps the
 * surface UNKNOWN; a future interaction/UI signal source can confirm
 * SHARE_CHAT short video without changing the aggregator. Conservative by
 * design — the platform is recognized (so it appears in Short Applications
 * discovery), but no Short is ever counted from package identity alone.
 */
object ShareChatShortsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.SHARE_CHAT
    override val packageNames: Set<String> = setOf("com.sharechat.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.SHARE_CHAT,
            surface = ShortSurface.UNKNOWN,
            isShortForm = false,
            confidence = 0.3f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
        )
}
