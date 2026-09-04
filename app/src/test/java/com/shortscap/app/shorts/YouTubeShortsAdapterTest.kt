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

    // ====================================================================
    // detectUserAdvance() tests
    // ====================================================================

    /** Base Shorts player evidence with specific creator and caption. */
    private fun shortEvidence(
        creator: String,
        caption: String,
        extraDescs: List<String> = emptyList(),
        extraIds: List<String> = emptyList(),
    ): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf(
            "com.google.android.apps.youtube.app.ui.ReelPlayerView",
            "androidx.recyclerview.widget.RecyclerView",
        ),
        nodeViewIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            *extraIds.toTypedArray(),
        ),
        nodeContentDescriptions = listOf(
            creator,
            caption,
            "Like",
            "Share",
            "Comment",
            "Subscribe",
            "Remix this Short",
            "See more videos using this sound",
            *extraDescs.toTypedArray(),
        ),
    )

    /** Shorts evidence with overlay panel open (comments, share, etc.). */
    private fun shortEvidenceWithOverlay(
        creator: String,
        caption: String,
        overlayDescs: List<String>,
        overlayIds: List<String> = emptyList(),
    ): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf(
            "com.google.android.apps.youtube.app.ui.ReelPlayerView",
            "androidx.recyclerview.widget.RecyclerView",
            "com.google.android.material.bottomsheet.BottomSheetBehavior",
        ),
        nodeViewIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            *overlayIds.toTypedArray(),
        ),
        nodeContentDescriptions = listOf(
            creator,
            caption,
            "Like",
            "Share",
            "Comment",
            "Subscribe",
            "Remix this Short",
            "See more videos using this sound",
            *overlayDescs.toTypedArray(),
        ),
    )

    /** Sparse Shorts evidence with no meaningful content descriptions. */
    private fun sparseShortEvidence(
        extraDescs: List<String> = emptyList(),
    ): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf(
            "com.google.android.apps.youtube.app.ui.ReelPlayerView",
            "androidx.recyclerview.widget.RecyclerView",
        ),
        nodeViewIds = listOf("com.google.android.youtube:id/reel_recycler"),
        nodeContentDescriptions = listOf(
            "Like",
            "Share",
            "Comment",
            "Subscribe",
            *extraDescs.toTypedArray(),
        ),
    )

    private fun advance(prev: WindowContentEvidence, curr: WindowContentEvidence): Boolean =
        YouTubeShortsAdapter.detectUserAdvance(prev, curr)

    // TEST 1: Normal YouTube swipe — different creator + different caption
    @Test
    fun `normal swipe with different creator and caption detects advance`() {
        val shortA = shortEvidence("@creator_alpha", "Amazing sunset timelapse")
        val shortB = shortEvidence("@creator_beta", "Cooking pasta from scratch")
        assertTrue("Expected ADVANCE for different creator+caption", advance(shortA, shortB))
    }

    // TEST 2: Same creator, different Short — different caption
    @Test
    fun `same creator with different caption still detects advance`() {
        val shortA = shortEvidence("@samecreator", "Video A about travel")
        val shortB = shortEvidence("@samecreator", "Video B about cooking")
        assertTrue("Expected ADVANCE for same creator but different caption", advance(shortA, shortB))
    }

    // TEST 3: Similar captions — still different enough content
    @Test
    fun `similar but non-identical captions detect advance`() {
        val shortA = shortEvidence("@creator1", "Amazing sunset over the ocean today")
        val shortB = shortEvidence("@creator1", "Beautiful sunset at the beach view")
        assertTrue("Expected ADVANCE for similar but non-identical captions", advance(shortA, shortB))
    }

    // TEST 4: Comments open/close on same Short — NO advance
    @Test
    fun `comments open and close does not create false advance`() {
        val shortA = shortEvidence("@creator1", "Travel vlog Episode 1")
        val shortAWithComments = shortEvidenceWithOverlay(
            "@creator1",
            "Travel vlog Episode 1",
            overlayDescs = listOf("Add a comment", "Reply"),
            overlayIds = listOf("com.google.android.youtube:id/comment_input"),
        )
        assertFalse("Expected NO ADVANCE for comments overlay", advance(shortA, shortAWithComments))
        assertFalse("Expected NO ADVANCE for comments overlay (reverse)", advance(shortAWithComments, shortA))
    }

    // TEST 5: Share open/close on same Short — NO advance
    @Test
    fun `share sheet open and close does not create false advance`() {
        val shortA = shortEvidence("@creator1", "Travel vlog Episode 1")
        val shortAWithShare = shortEvidenceWithOverlay(
            "@creator1",
            "Travel vlog Episode 1",
            overlayDescs = listOf("Send to", "Share this"),
            overlayIds = listOf("com.google.android.youtube:id/share_sheet"),
        )
        assertFalse("Expected NO ADVANCE for share overlay", advance(shortA, shortAWithShare))
        assertFalse("Expected NO ADVANCE for share overlay (reverse)", advance(shortAWithShare, shortA))
    }

    // TEST 6: Like/comment/share control state changes — NO advance
    @Test
    fun `control state changes on same Short do not create false advance`() {
        val shortA = shortEvidence("@creator1", "Travel vlog Episode 1")
        // Same Short but with different control state (liked vs unliked)
        val shortAUnliked = WindowContentEvidence(
            nodeClasses = listOf(
                "com.google.android.apps.youtube.app.ui.ReelPlayerView",
                "androidx.recyclerview.widget.RecyclerView",
            ),
            nodeViewIds = listOf("com.google.android.youtube:id/reel_recycler"),
            nodeContentDescriptions = listOf(
                "@creator1",
                "Travel vlog Episode 1",
                "Like",
                "Share",
                "Comment",
                "Subscribe",
                "Remix this Short",
                "See more videos using this sound",
            ),
        )
        assertFalse("Expected NO ADVANCE for control state change", advance(shortA, shortAUnliked))
    }

    // TEST 7: One swipe producing multiple observations — ONE advance only
    @Test
    fun `multiple observations of same transition produce at most one advance`() {
        val shortA = shortEvidence("@creator_alpha", "Amazing sunset timelapse")
        val shortB1 = shortEvidence("@creator_beta", "Cooking pasta from scratch")
        val shortB2 = shortEvidence("@creator_beta", "Cooking pasta from scratch")

        // First observation of Short B — should detect advance
        assertTrue("Expected ADVANCE on first observation", advance(shortA, shortB1))
        // Second observation of same Short B — should NOT detect advance
        // (content identity is now identical)
        assertFalse("Expected NO ADVANCE on second observation of same Short", advance(shortB1, shortB2))
    }

    // TEST 8: TYPE_VIEW_SCROLLED available — existing path works
    // (This is tested via ShortsMonitoringPipeline tests, not adapter tests)
    // Here we verify detectUserAdvance is compatible — it should NOT interfere
    @Test
    fun `detectUserAdvance does not interfere with TYPE_VIEW_SCROLLED path`() {
        // detectUserAdvance should still work correctly — it's an additional
        // signal, not a replacement for TYPE_VIEW_SCROLLED
        val shortA = shortEvidence("@creator_alpha", "Amazing sunset timelapse")
        val shortB = shortEvidence("@creator_beta", "Cooking pasta from scratch")
        assertTrue(advance(shortA, shortB))
    }

    // TEST 9: TYPE_VIEW_SCROLLED absent — content-based detection still works
    @Test
    fun `content-based advance detection works when TYPE_VIEW_SCROLLED is absent`() {
        // This is the core fix scenario: TYPE_VIEW_SCROLLED doesn't fire,
        // but detectUserAdvance() can still detect the transition via
        // content fingerprint comparison.
        val shortA = shortEvidence("@creator_alpha", "Amazing sunset timelapse")
        val shortB = shortEvidence("@creator_beta", "Cooking pasta from scratch")
        assertTrue("Expected ADVANCE via content fingerprint", advance(shortA, shortB))
    }

    // TEST 10: Facebook regression — verify adapter is YouTube-only
    @Test
    fun `YouTubeShortsAdapter only handles YouTube package`() {
        assertEquals(setOf("com.google.android.youtube"), YouTubeShortsAdapter.packageNames)
        assertEquals(ShortPlatform.YOUTUBE, YouTubeShortsAdapter.platform)
    }

    // Additional edge case: sparse content descriptions with structural fallback
    @Test
    fun `sparse content descriptions with structural delta detects advance`() {
        val sparseA = sparseShortEvidence()
        val sparseB = WindowContentEvidence(
            nodeClasses = listOf(
                "com.google.android.apps.youtube.app.ui.ReelPlayerView",
                "androidx.recyclerview.widget.RecyclerView",
                "com.google.android.apps.youtube.app.ui.NewPlayerContainer",
            ),
            nodeViewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
            ),
            nodeContentDescriptions = listOf(
                "Like",
                "Share",
                "Comment",
                "Subscribe",
                "Remix this Short",
            ),
        )
        assertTrue("Expected ADVANCE via structural delta", advance(sparseA, sparseB))
    }

    // Additional edge case: new content descriptions appearing
    @Test
    fun `new content descriptions appearing detects advance`() {
        val shortA = shortEvidence("@creator_alpha", "Video about nothing special")
        // Short B has a new content description that wasn't in Short A
        val shortB = shortEvidence("@creator_beta", "Completely different video topic")
        assertTrue("Expected ADVANCE for new content descriptions", advance(shortA, shortB))
    }

    // Edge case: same creator with identical captions (very sparse)
    @Test
    fun `same creator with identical sparse captions and no structural delta rejects`() {
        // Both Shorts have identical fingerprints (same creator, same caption,
        // same structural elements) — this should be rejected as SAME_SHORT
        val shortA = shortEvidence("@creator", "Same caption text")
        val shortB = shortEvidence("@creator", "Same caption text")
        assertFalse("Expected REJECT for identical content", advance(shortA, shortB))
    }
}