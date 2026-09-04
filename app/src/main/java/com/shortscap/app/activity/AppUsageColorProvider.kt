package com.shortscap.app.activity

import androidx.compose.ui.graphics.Color
import com.shortscap.app.theme.ScChrome
import com.shortscap.app.theme.ScColors
import com.shortscap.app.theme.ScFacebook
import com.shortscap.app.theme.ScInstagram
import com.shortscap.app.theme.ScLinkedIn
import com.shortscap.app.theme.ScReddit
import com.shortscap.app.theme.ScSnapchat
import com.shortscap.app.theme.ScTelegram
import com.shortscap.app.theme.ScTikTok
import com.shortscap.app.theme.ScWhatsApp
import com.shortscap.app.theme.ScX
import com.shortscap.app.theme.ScYouTube

/**
 * AppUsageColorProvider — the single source of truth for the color of a
 * REPORTABLE application in the Activity / Home reporting UI (Phase 1.3).
 *
 * Deterministic by design: a package always maps to the SAME brand color
 * regardless of duration, ranking, sort order, selected hour or screen.
 * Colors are keyed by PACKAGE IDENTITY (never the display label), so apps
 * with similar labels can never collide. Unknown reportable packages and
 * the "Other" bucket get a stable neutral color — never random, never
 * position-dependent.
 */
object AppUsageColorProvider {

    /**
     * Recognizable brand colors per known reportable application. Packages
     * not listed here fall back to the neutral color. Kept deliberately
     * focused on real user apps; ShortsCap / system packages are filtered
     * out before reporting anyway.
     */
    private val brandColors: Map<String, Color> = mapOf(
        "com.google.android.youtube" to ScYouTube,
        "com.instagram.android" to ScInstagram,
        "com.facebook.katana" to ScFacebook,
        "com.whatsapp" to ScWhatsApp,
        "com.android.chrome" to ScChrome,
        "com.snapchat.android" to ScSnapchat,
        "com.ss.android.ugc.aweme" to ScTikTok,
        "com.zhiliaoapp.musically" to ScTikTok,
        "com.twitter.android" to ScX,
        "com.twitter.android.lite" to ScX,
        "com.linkedin.android" to ScLinkedIn,
        "org.telegram.messenger" to ScTelegram,
        "com.reddit.frontpage" to ScReddit,
    )

    /** Fixed neutral color for unknown reportable apps and the "Other" slice. */
    val NeutralUnknownColor: Color = Color(0xFF9CA3AF)

    /**
     * Theme-aware color for a recorded package. [packageName] is null for the
     * "Other" bucket → the theme's neutral [ScColors.PieOther]; unknown
     * reportable packages get the same neutral (deterministic).
     */
    fun colorFor(packageName: String?, colors: ScColors): Color =
        brandColors[packageName] ?: colors.PieOther

    /**
     * Standalone variant (no composition theme) used by non-composable
     * layers such as AppViewModel's Home Recent Activity rows — unknown
     * packages and "Other" get [NeutralUnknownColor].
     */
    fun colorFor(packageName: String?): Color =
        brandColors[packageName] ?: NeutralUnknownColor
}