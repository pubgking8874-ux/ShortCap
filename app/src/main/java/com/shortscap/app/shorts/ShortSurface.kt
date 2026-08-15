package com.shortscap.app.shorts

/**
 * A short-form CONTENT SURFACE inside a platform — the specific place where
 * short-form video is consumed (YouTube Shorts, Instagram Reels, TikTok's
 * short-video feed, Snapchat Spotlight, …).
 *
 * Kept strictly separate from [ShortPlatform]: not every platform has the
 * same surfaces, a platform may host several surfaces (only some short-form),
 * and the same surface concept never bleeds into another platform.
 * [UNKNOWN] is the conservative value when the surface cannot be determined.
 */
enum class ShortSurface {
    YOUTUBE_SHORTS,
    INSTAGRAM_REELS,
    FACEBOOK_REELS,
    TIKTOK_SHORT_FEED,
    SNAPCHAT_SPOTLIGHT,
    X_SHORT_VIDEO,
    LINKEDIN_SHORT_VIDEO,
    MOJ_SHORT_VIDEO,
    UNKNOWN,
}
