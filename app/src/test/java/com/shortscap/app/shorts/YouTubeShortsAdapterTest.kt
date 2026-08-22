package com.shortscap.app.shorts

import com.shortscap.app.monitoring.WindowContentEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests for [YouTubeShortsAdapter] — the confirmed
 * `watchwhile.MainActivity` Shorts case (Phase 13.2: vivo device evidence +
 * structural content evidence), the `watchwhile.InternalMainActivity` case and
 * the YouTube Home / Watch / Live / search exclusions. Only the YouTube
 * package ever reaches this adapter (the registry selects it by package), so
 * all tests use the YouTube package.
 */
class YouTubeShortsAdapterTest {

    private val youtube = "com.google.android.youtube"
    private val mainActivity = "com.google.android.apps.youtube.app.watchwhile.MainActivity"
    private val watchWhileShorts =
        "com.google.android.apps.youtube.app.watchwhile.InternalMainActivity"
    private val homeActivity = "com.google.android.apps.youtube.app.application.Shell\$HomeActivity"
    private val exactShortsClass = "com.google.android.apps.youtube.app.application.Shell\$ShortsActivity"

    private fun detect(
        className: String?,
        interactionCount: Int = 0,
        evidence: WindowContentEvidence = WindowContentEvidence(),
    ): ShortDetectionResult =
        YouTubeShortsAdapter.detect(
            ShortDetectionSignals(
                packageName = youtube,
                activityClassName = className,
                interactionCount = interactionCount,
                contentEvidence = evidence,
            )
        )

    /** A Shorts PLAYER-shaped evidence snapshot (node class + reel view id). */
    private fun playerEvidence(): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf(
            "com.google.android.apps.youtube.app.ui.ReelPlayerView",
            "androidx.recyclerview.widget.RecyclerView",
        ),
        nodeViewIds = listOf("com.google.android.youtube:id/reel_recycler"),
    )

    // ---- Phase 13.2: watchwhile.MainActivity + structural evidence ----

    // 1. Actual observed MainActivity + reliable Shorts signal → Shorts.
    @Test
    fun `MainActivity with Shorts player content evidence is Shorts without any scrolls`() {
        val result = detect(mainActivity, evidence = playerEvidence())
        assertTrue(result.isShortForm)
        assertEquals(ShortPlatform.YOUTUBE, result.platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, result.surface)
        assertTrue(result.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD)
        assertEquals(0.75f, result.confidence, 1e-6f)
    }

    // View-id-only evidence also counts (class obfuscation gap).
    @Test
    fun `MainActivity with reel view id evidence alone counts as Shorts`() {
        val result = detect(
            mainActivity,
            evidence = WindowContentEvidence(nodeViewIds = listOf("com.google.android.youtube:id/reel_watch_fragment_root")),
        )
        assertTrue(result.isShortForm)
        assertEquals(0.75f, result.confidence, 1e-6f)
    }

    // 2. MainActivity normal Watch → NOT Shorts.
    @Test
    fun `MainActivity normal Watch is never Shorts`() {
        val results = buildList {
            add(detect(mainActivity))                                // no evidence at all
            add(
                detect(
                    mainActivity,
                    evidence = WindowContentEvidence(
                        nodeClasses = listOf("android.widget.FrameLayout", "com.google.android.exoplayer2.ui.PlayerView"),
                        nodeViewIds = listOf("com.google.android.youtube:id/watch_while_layout"),
                    ),
                ),
            )
            add(detect(mainActivity, interactionCount = 5))          // scrolls alone are not Shorts evidence
        }
        results.forEach {
            assertFalse("expected not Shorts, got ${it.surface}", it.isShortForm)
            assertEquals(ShortSurface.UNKNOWN, it.surface)
        }
    }

    // 3. MainActivity Live → NOT Shorts.
    @Test
    fun `MainActivity Live is never Shorts`() {
        val result = detect(
            mainActivity,
            evidence = WindowContentEvidence(
                nodeClasses = listOf("android.widget.FrameLayout", "com.google.android.exoplayer2.ui.PlayerView"),
                nodeViewIds = listOf("com.google.android.youtube:id/chat_recycler"),
            ),
        )
        assertFalse(result.isShortForm)
        assertEquals(ShortSurface.UNKNOWN, result.surface)
    }

    // 4. MainActivity Search → NOT Shorts.
    @Test
    fun `MainActivity Search results are never Shorts`() {
        val result = detect(
            mainActivity,
            evidence = WindowContentEvidence(
                nodeClasses = listOf("com.google.android.apps.youtube.app.uhq.SearchResultsFragmentContainer"),
                nodeViewIds = listOf("com.google.android.youtube:id/results_recycler"),
            ),
        )
        assertFalse(result.isShortForm)
        assertEquals(ShortSurface.UNKNOWN, result.surface)
    }

    // 5. MainActivity without sufficient Shorts evidence → NOT Shorts.
    @Test
    fun `MainActivity without Shorts player evidence is never Shorts`() {
        val result = detect(mainActivity, interactionCount = 0)
        assertFalse(result.isShortForm)
        assertEquals(ShortSurface.UNKNOWN, result.surface)
    }

    // False-positive guards: Home Shorts shelf + bottom-nav Shorts tab are NOT
    // Shorts players.
    @Test
    fun `Home Shorts shelf preview nodes are not treated as a Shorts player`() {
        val result = detect(
            homeActivity,
            evidence = WindowContentEvidence(
                nodeClasses = listOf("com.google.android.apps.youtube.app.ui.ReelShelfVideoItemView"),
                nodeViewIds = listOf("com.google.android.youtube:id/shorts_shelf_recycler"),
            ),
        )
        assertFalse(result.isShortForm)
        assertEquals(ShortSurface.UNKNOWN, result.surface)
    }

    @Test
    fun `bottom nav Shorts tab node is not treated as a Shorts player`() {
        val result = detect(
            mainActivity,
            evidence = WindowContentEvidence(
                nodeClasses = listOf("com.google.android.apps.youtube.app.application.LegacyShortsTabIndicatorView"),
                nodeViewIds = listOf("com.google.android.youtube:id/shorts_tab"),
            ),
        )
        assertFalse(result.isShortForm)
        assertEquals(ShortSurface.UNKNOWN, result.surface)
    }

    @Test
    fun `null window class with player content evidence is Shorts`() {
        val result = detect(null, evidence = playerEvidence())
        assertTrue(result.isShortForm)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, result.surface)
    }

    // ---- watchwhile.InternalMainActivity (existing Shorts host) ----

    @Test
    fun `watchwhile class with scroll evidence is recognized as Shorts`() {
        val result = detect(watchWhileShorts, interactionCount = 2)
        assertTrue(result.isShortForm)
        assertEquals(ShortPlatform.YOUTUBE, result.platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, result.surface)
        assertTrue(result.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD)
        assertEquals(0.6f, result.confidence, 1e-6f)
    }

    @Test
    fun `watchwhile class WITHOUT scroll evidence is not Shorts (long-form Watch)`() {
        // The watchwhile activity also hosts normal videos — a bare class
        // match must never count them.
        val result = detect(watchWhileShorts, interactionCount = 0)
        assertFalse(result.isShortForm)
        assertEquals(ShortSurface.UNKNOWN, result.surface)
    }

    // ---- YouTube exclusions stay intact ----

    @Test
    fun `YouTube Home is never Shorts even with heavy scroll evidence`() {
        val result = detect(homeActivity, interactionCount = 10)
        assertFalse(result.isShortForm)
    }

    @Test
    fun `null window class is never Shorts without content evidence`() {
        val result = detect(null, interactionCount = 10)
        assertFalse(result.isShortForm)
    }

    @Test
    fun `live and search window classes are never Shorts`() {
        // Live/search host in other (or watchwhile-without-scroll) windows.
        assertFalse(detect("com.google.android.apps.youtube.app.watchwhile.InternalLiveActivity", interactionCount = 3).isShortForm)
        assertFalse(detect("com.google.android.apps.youtube.app.search.SearchActivity", interactionCount = 3).isShortForm)
    }

    // ---- existing rules remain intact ----

    @Test
    fun `exact Shorts class is still detected without scroll evidence`() {
        val result = detect(exactShortsClass, interactionCount = 0)
        assertTrue(result.isShortForm)
        assertEquals(0.85f, result.confidence, 1e-6f)
    }

    @Test
    fun `Shorts-named fallback class is still detected`() {
        val result = detect("com.google.android.apps.youtube.app.application.Shell\$ShortsPlayerActivity")
        assertTrue(result.isShortForm)
        assertEquals(0.7f, result.confidence, 1e-6f)
    }
}