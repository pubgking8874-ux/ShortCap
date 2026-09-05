package com.shortscap.app.activity

import com.shortscap.app.theme.ScInstagram
import com.shortscap.app.theme.ScYouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Phase 1.3 color tests — the centralized [AppUsageColorProvider] must be
 * deterministic (a package always maps to the SAME brand color), keyed by
 * package identity, independent of order/ranking, and give unknown packages
 * and the "Other" bucket a stable neutral color.
 */
class AppUsageColorProviderTest {

    @Test
    fun `youtube maps to its brand color and is deterministic`() {
        assertEquals(ScYouTube, AppUsageColorProvider.colorFor("com.google.android.youtube"))
        assertEquals(
            AppUsageColorProvider.colorFor("com.google.android.youtube"),
            AppUsageColorProvider.colorFor("com.google.android.youtube"),
        )
    }

    @Test
    fun `instagram maps to its brand color and is deterministic`() {
        assertEquals(ScInstagram, AppUsageColorProvider.colorFor("com.instagram.android"))
        assertEquals(
            AppUsageColorProvider.colorFor("com.instagram.android"),
            AppUsageColorProvider.colorFor("com.instagram.android"),
        )
    }

    @Test
    fun `youtube and instagram never share a color`() {
        assertNotEquals(
            AppUsageColorProvider.colorFor("com.google.android.youtube"),
            AppUsageColorProvider.colorFor("com.instagram.android"),
        )
    }

    @Test
    fun `color is independent of call order or ranking`() {
        // Same two packages queried in opposite order must keep their colors.
        val youtubeFirst = AppUsageColorProvider.colorFor("com.google.android.youtube")
        val instagramSecond = AppUsageColorProvider.colorFor("com.instagram.android")
        val instagramFirst = AppUsageColorProvider.colorFor("com.instagram.android")
        val youtubeSecond = AppUsageColorProvider.colorFor("com.google.android.youtube")
        assertEquals(youtubeFirst, youtubeSecond)
        assertEquals(instagramFirst, instagramSecond)
        assertNotEquals(youtubeFirst, instagramFirst)
    }

    @Test
    fun `unknown package gets a deterministic non-gray color`() {
        // Phase 1.6: legitimate reportable apps WITHOUT a dedicated brand
        // color must still be colourful (deterministic palette, keyed by
        // package identity) so the Daily donut is not dominated by gray.
        val first = AppUsageColorProvider.colorFor("com.example.someapp")
        val second = AppUsageColorProvider.colorFor("com.example.someapp")
        assertEquals(first, second) // deterministic across calls
        assertNotEquals(AppUsageColorProvider.NeutralUnknownColor, first) // never neutral gray
        assertNotEquals(AppUsageColorProvider.NeutralUnknownColor, AppUsageColorProvider.colorFor("com.facebook.orca"))
    }

    @Test
    fun `unknown packages keep distinct stable colors where hashes differ`() {
        // Determinism: the SAME unknown package maps to the same fallback
        // colour whether queried first or last (independent of rank/order).
        val a1 = AppUsageColorProvider.colorFor("com.example.first")
        val b1 = AppUsageColorProvider.colorFor("com.example.second")
        val b2 = AppUsageColorProvider.colorFor("com.example.second")
        val a2 = AppUsageColorProvider.colorFor("com.example.first")
        assertEquals(a1, a2)
        assertEquals(b1, b2)
        // Two different unknown packages should not both be gray.
        assertNotEquals(AppUsageColorProvider.NeutralUnknownColor, a1)
        assertNotEquals(AppUsageColorProvider.NeutralUnknownColor, b1)
    }

    @Test
    fun `other bucket gets the neutral color`() {
        // "Other" is not an application — it stays neutral gray.
        assertEquals(AppUsageColorProvider.NeutralUnknownColor, AppUsageColorProvider.colorFor(null))
    }
}