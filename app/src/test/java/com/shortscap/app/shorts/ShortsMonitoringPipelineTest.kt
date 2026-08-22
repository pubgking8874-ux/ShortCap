package com.shortscap.app.shorts

import com.shortscap.app.monitoring.WindowContentEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ShortsMonitoringPipeline] (Phase 11B): the monitoring →
 * detection → aggregation → budget → local-store flow, driven with a
 * controllable clock so the 3–5 second rule and transitions are deterministic.
 */
class ShortsMonitoringPipelineTest {

    private var now = 0L

    private val clock: () -> Long = { now }

    private val youtubeShortsClass = "com.google.android.apps.youtube.app.application.Shell\$ShortsActivity"
    private val youtubeHomeClass = "com.google.android.apps.youtube.app.application.Shell\$HomeActivity"
    private val youtubeMainActivity = "com.google.android.apps.youtube.app.watchwhile.MainActivity"

    /** Real registry + real adapters (YouTube surface-positive via class). */
    private fun pipeline(): ShortsMonitoringPipeline {
        val store = InMemoryShortsLocalStore()
        val budget = ShortsBudgetTracker()
        val pipeline = ShortsMonitoringPipeline(
            budget = budget,
            store = store,
            nowMillis = clock,
        )
        return pipeline
    }

    private fun ShortsMonitoringPipeline.enter(packageName: String, activityClassName: String?, at: Long) {
        now = at
        onForegroundAppChanged(packageName, activityClassName)
    }

    private fun ShortsMonitoringPipeline.leaveToNeutral(at: Long) {
        enter("com.android.launcher", null, at)
    }

    /** Structural evidence proving the Shorts PLAYER is in the active window. */
    private fun shortsPlayerEvidence(): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf("com.google.android.apps.youtube.app.ui.ReelPlayerView"),
        nodeViewIds = listOf("com.google.android.youtube:id/reel_recycler"),
    )

    // 1. Known platform + known short surface -> counted.
    @Test
    fun `known platform with short surface is counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(4_000L, p.currentBudget().totalMillis)
        val usage = p.localStore().usageSnapshot()
        assertEquals(1, usage.size)
        assertEquals(ShortPlatform.YOUTUBE, usage[0].platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, usage[0].surface)
        assertEquals(DetectionMethod.PLATFORM_ADAPTER, usage[0].detectionMethod)
        assertEquals(1, p.localStore().eventSnapshot().size)
    }

    // 2. Known platform + non-short content -> NOT counted.
    @Test
    fun `known platform with non-short surface is not counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeHomeClass, 0L)
        p.leaveToNeutral(10_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 3. Unknown platform -> NOT counted.
    @Test
    fun `unknown platform is never counted`() {
        val p = pipeline()
        p.enter("com.example.unknownapp", null, 0L)
        p.leaveToNeutral(10_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 4. Known platform + unknown surface -> NOT counted (conservative).
    @Test
    fun `known platform with unknown surface is not counted`() {
        val p = pipeline()
        p.enter("com.instagram.android", null, 0L)
        p.leaveToNeutral(5_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 5. Swipe/change before the ~2s threshold -> NOT counted.
    @Test
    fun `context left before swipe rule threshold is not counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.leaveToNeutral(1_000L) // < 2000ms

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 6. Engagement beyond the 3-5s threshold -> counted once, full duration.
    @Test
    fun `engagement beyond threshold is counted once with full duration`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.leaveToNeutral(60_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(60_000L, p.currentBudget().totalMillis)
        assertEquals(60_000L, p.localStore().usageSnapshot()[0].durationMillis)
    }

    // 7. Platform switching must NOT reset the global budget.
    @Test
    fun `platform switching keeps accumulating the global budget`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.enter("com.instagram.android", null, 4_000L) // counted YouTube context ends
        p.enter("com.snapchat.android", null, 9_000L) // Instagram (uncounted) ends
        p.leaveToNeutral(15_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(4_000L, p.currentBudget().totalMillis)
        assertEquals(mapOf(ShortPlatform.YOUTUBE to 4_000L), p.currentBudget().platformDurationMillis())
    }

    // 8. Global budget accumulates ACROSS platforms (injected multi-platform detection).
    @Test
    fun `global budget accumulates across platforms`() {
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = { signals ->
            when (signals.packageName) {
                "com.google.android.youtube" -> ShortDetectionResult(
                    ShortPlatform.YOUTUBE, ShortSurface.YOUTUBE_SHORTS,
                    isShortForm = true, confidence = 0.9f, DetectionMethod.PLATFORM_ADAPTER,
                )
                "com.instagram.android" -> ShortDetectionResult(
                    ShortPlatform.INSTAGRAM, ShortSurface.INSTAGRAM_REELS,
                    isShortForm = true, confidence = 0.9f, DetectionMethod.PLATFORM_ADAPTER,
                )
                else -> ShortDetectionResult.UNKNOWN
            }
        }
        val store = InMemoryShortsLocalStore()
        val budget = ShortsBudgetTracker()
        val p = ShortsMonitoringPipeline(detect = detect, budget = budget, store = store, nowMillis = clock)

        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.enter("com.instagram.android", null, 4_000L) // YouTube counted (4s)
        p.leaveToNeutral(8_000L) // Instagram counted (4s)

        assertEquals(2, budget.totalShorts)
        assertEquals(8_000L, budget.totalMillis)
        assertEquals(
            mapOf(ShortPlatform.YOUTUBE to 4_000L, ShortPlatform.INSTAGRAM to 4_000L),
            budget.platformDurationMillis(),
        )
        assertEquals(2, store.usageSnapshot().size)
    }

    // 9. Duplicate events for the same surface are NOT re-counted.
    @Test
    fun `duplicate window events do not double count`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.enter("com.google.android.youtube", youtubeShortsClass, 2_000L) // same surface -> ignored
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(1, p.localStore().usageSnapshot().size)
    }

    // 10. Insufficient confidence -> NOT counted.
    @Test
    fun `insufficient confidence is never counted`() {
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = {
            ShortDetectionResult(
                ShortPlatform.YOUTUBE, ShortSurface.YOUTUBE_SHORTS,
                isShortForm = true, confidence = 0.2f, DetectionMethod.PLATFORM_ADAPTER,
            )
        }
        val store = InMemoryShortsLocalStore()
        val budget = ShortsBudgetTracker()
        val p = ShortsMonitoringPipeline(detect = detect, budget = budget, store = store, nowMillis = clock)

        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.leaveToNeutral(10_000L)

        assertFalse(store.usageSnapshot().isNotEmpty())
        assertEquals(0, budget.totalShorts)
    }

    // 11. Shorts HUD surface-state broadcasts: positively detected
    //     short-form surface -> non-null state; normal content -> null.
    @Test
    fun `surface listener reports active short-form surface`() {
        val p = pipeline()
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)
        p.leaveToNeutral(1_000L)

        assertEquals(2, states.size)
        val active = states[0]
        assertEquals(ShortPlatform.YOUTUBE, active?.platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, active?.surface)
        assertEquals(0.85f, active?.confidence ?: 0f, 1e-6f)
        // Leaving to the launcher -> not short-form -> null.
        assertNull(states[1])
    }

    // 12. Normal (long-form) content -> null surface state (HUD must stay hidden).
    @Test
    fun `surface listener reports null for non-short content`() {
        val p = pipeline()
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.google.android.youtube", youtubeHomeClass, 0L)
        p.leaveToNeutral(5_000L)

        assertEquals(2, states.size)
        assertNull(states[0])
        assertNull(states[1])
    }

    // 13. Insufficient-confidence detections -> null surface state (HUD
    //     must NOT guess).
    @Test
    fun `surface listener stays null on insufficient confidence`() {
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = {
            ShortDetectionResult(
                ShortPlatform.YOUTUBE, ShortSurface.YOUTUBE_SHORTS,
                isShortForm = true, confidence = 0.2f, DetectionMethod.PLATFORM_ADAPTER,
            )
        }
        val p = ShortsMonitoringPipeline(detect = detect, store = InMemoryShortsLocalStore(), nowMillis = clock)
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)

        assertNull(states.single())
    }

    // 14. Scroll-capable platform (Instagram Reels) with scroll evidence +
    //     engagement >= 3s -> counted (scroll interaction signal).
    @Test
    fun `reels session with scroll evidence and engagement is counted`() {
        val p = pipeline()
        p.enter("com.instagram.android", null, 0L)
        p.onForegroundScrolled("com.instagram.android")
        p.onForegroundScrolled("com.instagram.android")
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(4_000L, p.currentBudget().totalMillis)
        val usage = p.localStore().usageSnapshot()
        assertEquals(1, usage.size)
        assertEquals(ShortPlatform.INSTAGRAM, usage[0].platform)
        assertEquals(ShortSurface.INSTAGRAM_REELS, usage[0].surface)
    }

    // 15. Scroll evidence is NOT enough alone — the 3–5s rule still gates.
    @Test
    fun `scroll evidence without engagement is not counted`() {
        val p = pipeline()
        p.enter("com.instagram.android", null, 0L)
        p.onForegroundScrolled("com.instagram.android")
        p.leaveToNeutral(1_000L) // < 2000ms swipe rule

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 16. No scroll evidence -> never counted blindly, even with long duration.
    @Test
    fun `long session without scroll evidence is not counted`() {
        val p = pipeline()
        p.enter("com.instagram.android", null, 0L)
        p.leaveToNeutral(60_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 17. Scrolls from ANOTHER package are ignored (only the active context's
    //     package counts as interaction evidence).
    @Test
    fun `scrolls from other packages are ignored`() {
        val p = pipeline()
        p.enter("com.instagram.android", null, 0L)
        p.onForegroundScrolled("com.android.chrome")
        p.leaveToNeutral(5_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 18. YouTube Shorts-named fallback class (currency gap) is counted.
    @Test
    fun `youtube shorts fallback class is counted`() {
        val p = pipeline()
        val fallbackClass = "com.google.android.apps.youtube.app.application.Shell\$ShortsPlayerActivity"
        p.enter("com.google.android.youtube", fallbackClass, 0L)
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(1, p.localStore().usageSnapshot().size)
        val usage = p.localStore().usageSnapshot()[0]
        assertEquals(ShortPlatform.YOUTUBE, usage.platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, usage.surface)
        assertEquals(0.7f, usage.confidence, 1e-6f)
    }

    // 19. TikTok feed with scroll evidence + engagement -> counted.
    @Test
    fun `tiktok feed with scroll evidence is counted`() {
        val p = pipeline()
        p.enter("com.ss.android.ugc.aweme", null, 0L)
        p.onForegroundScrolled("com.ss.android.ugc.aweme")
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        val usage = p.localStore().usageSnapshot()
        assertEquals(ShortPlatform.TIKTOK, usage[0].platform)
        assertEquals(ShortSurface.TIKTOK_SHORT_FEED, usage[0].surface)
    }

    // ---- Phase 13.2: MainActivity + structural Shorts-player evidence ----

    // 20. Actual watchwhile.MainActivity + Shorts player content evidence
    //     (NO scrolls — the vivo device delivers none) + engagement >= 3s
    //     -> counted exactly once.
    @Test
    fun `mainActivity with player content evidence and engagement is counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())
        p.leaveToNeutral(5_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(5_000L, p.currentBudget().totalMillis)
        val usage = p.localStore().usageSnapshot()
        assertEquals(1, usage.size)
        assertEquals(ShortPlatform.YOUTUBE, usage[0].platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, usage[0].surface)
        assertEquals(0.75f, usage[0].confidence, 1e-6f)
        assertEquals(1, p.localStore().eventSnapshot().size)
    }

    // 21. Shorts + < 2s engagement -> NOT counted (swipe rule preserved).
    @Test
    fun `mainActivity with player content evidence but under two seconds is not counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())
        p.leaveToNeutral(1_000L) // < 2000ms swipe rule

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 22. Multiple accessibility/evidence events -> ONE count (no duplicates).
    @Test
    fun `repeated content evidence events do not double count`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
        assertEquals(1, p.localStore().usageSnapshot().size)
    }

    // 23. MainActivity WITHOUT Shorts player evidence -> never counted, even
    //     after a long engagement (Home / Watch / Live / Search stay excluded).
    @Test
    fun `mainActivity without player evidence is not counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        p.leaveToNeutral(60_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 24. Content evidence from ANOTHER package is ignored.
    @Test
    fun `content evidence from other packages is ignored`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        p.onForegroundContentObserved("com.android.chrome", shortsPlayerEvidence())
        p.leaveToNeutral(5_000L)

        assertEquals(0, p.currentBudget().totalShorts)
        assertTrue(p.localStore().usageSnapshot().isEmpty())
    }

    // 25. A session that STARTS as Home and then shows Shorts-player evidence
    //     inside the SAME MainActivity window is counted (surface change
    //     inside the shared activity is handled).
    @Test
    fun `mainActivity confirmed as shorts after evidence arrives is counted`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        // Same context, evidence arrives (throttled content walk) -> surface
        // flips to Shorts; engagement from context start is still >= 3s.
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())
        p.leaveToNeutral(4_000L)

        assertEquals(1, p.currentBudget().totalShorts)
    }
}
