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

    /**
     * Fixed neutral color for the "Other" slice and chart remainders ONLY.
     * Gray is never used for a reportable application — every app gets either
     * its brand color or a deterministic palette color (Phase 1.6), so the
     * Daily donut stays colorful instead of collapsing into gray.
     */
    val NeutralUnknownColor: Color = Color(0xFF9CA3AF)

    /**
     * Deterministic fallback palette for reportable apps without a dedicated
     * brand color (anything not in [brandColors]: Messenger, region apps,
     * games, …). Chosen by package identity so the SAME app always receives
     * the SAME color on every screen, refresh and sort order — never random,
     * never position-dependent, and never the neutral gray.
     */
    private val fallbackPalette = listOf(
        Color(0xFFF59E0B), // amber
        Color(0xFF22C55E), // green
        Color(0xFF3B82F6), // blue
        Color(0xFF8B5CF6), // violet
        Color(0xFFEC4899), // pink
        Color(0xFF14B8A6), // teal
        Color(0xFFF97316), // orange
        Color(0xFF6366F1), // indigo
    )

    /** Stable palette index for an unknown reportable package name. */
    private fun fallbackIndex(packageName: String): Int =
        (packageName.hashCode() and Int.MAX_VALUE) % fallbackPalette.size

    /**
     * Theme-aware color for a recorded package. [packageName] is null for the
     * "Other" bucket → the theme's neutral [ScColors.PieOther]; known apps
     * get their brand color; unknown reportable apps get a deterministic
     * fallback-palette color (see [fallbackPalette]).
     */
    fun colorFor(packageName: String?, colors: ScColors): Color {
        if (packageName == null) return colors.PieOther
        brandColors[packageName]?.let { return it }
        return fallbackPalette[fallbackIndex(packageName)]
    }

    /**
     * Standalone variant (no composition theme) used by non-composable
     * layers such as AppViewModel's Home Recent Activity rows — "Other"
     * (null) gets [NeutralUnknownColor]; unknown apps get the same
     * deterministic fallback-palette color as the composable variant.
     */
    fun colorFor(packageName: String?): Color {
        if (packageName == null) return NeutralUnknownColor
        brandColors[packageName]?.let { return it }
        return fallbackPalette[fallbackIndex(packageName)]
    }
}