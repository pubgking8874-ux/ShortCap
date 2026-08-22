package com.shortscap.app.shorts

/**
 * ShareChat adapter.
 *
 * ShareChat (package `com.sharechat.android`) is a predominantly short-form
 * app, but it also hosts live streams, chats and other long-form content, so
 * even here "app is open" is not proof of a Short. The adapter combines the
 * signals now available: recognized platform (package) + scroll interaction
 * in the foreground context (TYPE_VIEW_SCROLLED) + the existing ≥3 second
 * engagement rule (aggregator). No scroll evidence → UNKNOWN, never counted.
 */
object ShareChatShortsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.SHARE_CHAT
    override val packageNames: Set<String> = setOf("com.sharechat.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult =
        if (scrollInteractionConfidence(signals.interactionCount) >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            scrollDetectedResult(ShortPlatform.SHARE_CHAT, ShortSurface.SHARE_CHAT_SHORT_VIDEO, signals.interactionCount)
        } else {
            unconfirmedResult(ShortPlatform.SHARE_CHAT, 0.3f)
        }
}
