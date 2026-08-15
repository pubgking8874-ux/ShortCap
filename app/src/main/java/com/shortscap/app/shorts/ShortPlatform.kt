package com.shortscap.app.shorts

/**
 * A top-level short-form content PLATFORM — the application that hosts the
 * content (YouTube, Instagram, TikTok, …).
 *
 * This is deliberately distinct from [ShortSurface]: a single platform can
 * contain short-form, long-form, live, chat, stories and many other screens,
 * so "this app is running" must NEVER by itself mean "a Short is being
 * watched". Platform identity is package-based and centralized here so
 * package-name checks never spread through the monitoring/accessibility code.
 *
 * Values cover the app's existing platform catalog (the
 * [com.shortscap.app.model.ShortVideoPlatform] list and the
 * `SupportedShortVideoPackages` overlay set) plus future-ready entries.
 * [UNKNOWN] is the conservative value for anything not yet recognized.
 */
enum class ShortPlatform {
    YOUTUBE,
    INSTAGRAM,
    TIKTOK,
    SNAPCHAT,
    FACEBOOK,
    MOJ,
    X,
    LINKEDIN,
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps an Android package name to a platform. Package-based and
         * centralized here. Unknown packages return [UNKNOWN] — the caller
         * must not guess.
         */
        fun fromPackageName(packageName: String?): ShortPlatform = when (packageName) {
            "com.google.android.youtube" -> YOUTUBE
            "com.instagram.android" -> INSTAGRAM
            "com.ss.android.ugc.aweme", "com.zhiliaoapp.musically" -> TIKTOK
            "com.snapchat.android" -> SNAPCHAT
            "com.facebook.katana" -> FACEBOOK
            "in.mohalla.video" -> MOJ
            "com.twitter.android", "com.twitter.android.lite" -> X
            "com.linkedin.android" -> LINKEDIN
            else -> UNKNOWN
        }
    }
}
