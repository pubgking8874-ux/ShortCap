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

    // ====================================================================
    // TRANSIENT_UI gate regression tests (Phase: cross-platform fix)
    // ====================================================================
    //
    // NOTE: These tests verify the surface listener (HUD state) and session
    // state transitions — NOT the budget/count (which requires
    // ShortsControlEngine.shared and is a pre-existing test-infrastructure gap
    // affecting all tests 1–25).

    /** Evidence that looks like transient UI (has like/share/comment labels). */
    private fun transientUiEvidence(): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf("android.widget.FrameLayout"),
        nodeViewIds = listOf("com.instagram.android:id/comment_button"),
        nodeContentDescriptions = listOf("like", "share", "comment on this post"),
    )

    /** Evidence that is clearly Shorts player (structural + transient mixed). */
    private fun shortsPlayerWithTransientEvidence(): WindowContentEvidence = WindowContentEvidence(
        nodeClasses = listOf(
            "com.instagram.android.widget.ReelPlayerView",
            "android.widget.FrameLayout",
        ),
        nodeViewIds = listOf(
            "com.instagram.android:id/reel_recycler",
            "com.instagram.android:id/comment_button",
        ),
        nodeContentDescriptions = listOf(
            "like",
            "share",
            "watch reels",
        ),
    )

    // 26. NO_SESSION + TRANSIENT_UI: detection must proceed via scroll fallback.
    //     The surface listener must receive a non-null state (HUD shown)
    //     when the adapter detects Shorts despite transient UI labels.
    @Test
    fun `transient UI evidence does not block initial detection with scroll`() {
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = { signals ->
            if (signals.interactionCount >= 1) {
                ShortDetectionResult(
                    ShortPlatform.INSTAGRAM, ShortSurface.INSTAGRAM_REELS,
                    isShortForm = true, confidence = 0.65f, DetectionMethod.PLATFORM_ADAPTER,
                )
            } else {
                ShortDetectionResult(
                    ShortPlatform.INSTAGRAM, ShortSurface.UNKNOWN,
                    isShortForm = false, confidence = 0.15f, DetectionMethod.PLATFORM_ADAPTER,
                )
            }
        }
        val store = InMemoryShortsLocalStore()
        val p = ShortsMonitoringPipeline(
            detect = detect, store = store, nowMillis = clock,
        )
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.instagram.android", null, 0L)
        now = 500L
        p.onForegroundContentObserved("com.instagram.android", transientUiEvidence())
        p.onForegroundScrolled("com.instagram.android")
        now = 1_000L
        p.onForegroundContentObserved("com.instagram.android", transientUiEvidence())

        // The surface listener must have received a non-null state
        // (HUD shown) — the scroll fallback detected Reels despite transient UI.
        assertTrue(
            "Expected at least one non-null surface state (HUD shown), got: $states",
            states.any { it != null },
        )
        val activeState = states.firstNotNullOfOrNull { it }
        assertEquals(ShortPlatform.INSTAGRAM, activeState?.platform)
        assertEquals(ShortSurface.INSTAGRAM_REELS, activeState?.surface)
    }

    // 27. NO_SESSION + TRANSIENT_UI + non-short adapter: HUD must stay hidden.
    @Test
    fun `transient UI evidence with non-short adapter result does not create session`() {
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = {
            ShortDetectionResult(
                ShortPlatform.FACEBOOK, ShortSurface.UNKNOWN,
                isShortForm = false, confidence = 0.15f, DetectionMethod.PLATFORM_ADAPTER,
            )
        }
        val p = ShortsMonitoringPipeline(
            detect = detect, store = InMemoryShortsLocalStore(), nowMillis = clock,
        )
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.facebook.katana", null, 0L)
        now = 500L
        p.onForegroundContentObserved("com.facebook.katana", transientUiEvidence())
        now = 1_000L
        p.onForegroundContentObserved("com.facebook.katana", transientUiEvidence())
        p.leaveToNeutral(60_000L)

        // No surface state should ever be non-null.
        assertTrue(
            "Expected all surface states to be null, got: $states",
            states.all { it == null },
        )
    }

    // 28. WATCHING session + TRANSIENT_UI: session must be preserved
    //     and the surface listener must keep receiving non-null state.
    @Test
    fun `transient UI during watching session preserves session`() {
        val p = pipeline()
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)

        // Content evidence with transient UI — session must be preserved.
        now = 1_000L
        p.onForegroundContentObserved("com.google.android.youtube", transientUiEvidence())

        // The surface listener must have received a non-null state
        // (HUD shown) and the transient UI must NOT have caused a null.
        assertTrue(
            "Expected non-null surface state (HUD shown), got: $states",
            states.any { it != null },
        )
        // The last state before leaving must be non-null (HUD still visible).
        assertTrue(
            "HUD must still be visible after transient UI during WATCHING",
            states.last() != null,
        )
    }

    // 29. QUALIFIED session + TRANSIENT_UI: session must be preserved.
    @Test
    fun `transient UI during qualified session preserves qualification`() {
        val p = pipeline()
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })
        p.enter("com.google.android.youtube", youtubeShortsClass, 0L)

        // Advance to QUALIFIED (>= 3s)
        now = 4_000L
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerEvidence())

        // Transient UI arrives — must NOT destroy the qualified session.
        now = 5_000L
        p.onForegroundContentObserved("com.google.android.youtube", transientUiEvidence())

        // HUD must still be visible after transient UI during QUALIFIED.
        assertTrue(
            "HUD must still be visible after transient UI during QUALIFIED",
            states.last() != null,
        )
    }

    // 30. YouTube Shorts player evidence with transient UI labels:
    //     detection must proceed despite transient UI keywords in evidence.
    @Test
    fun `youtube shorts player detected despite transient ui labels in evidence`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeMainActivity, 0L)
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        // Content evidence with both Shorts player AND transient UI labels.
        p.onForegroundContentObserved("com.google.android.youtube", shortsPlayerWithTransientEvidence())

        // The adapter must detect Shorts despite transient UI labels.
        assertTrue(
            "Expected Shorts detection despite transient UI, got: $states",
            states.any { it != null },
        )
        val activeState = states.firstNotNullOfOrNull { it }
        assertEquals(ShortPlatform.YOUTUBE, activeState?.platform)
        assertEquals(ShortSurface.YOUTUBE_SHORTS, activeState?.surface)
    }

    // 31. Facebook Reels: scroll fallback must fire despite transient UI.
    @Test
    fun `facebook reels detected via scroll fallback despite transient ui`() {
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = { signals ->
            if (signals.interactionCount >= 1) {
                ShortDetectionResult(
                    ShortPlatform.FACEBOOK, ShortSurface.FACEBOOK_REELS,
                    isShortForm = true, confidence = 0.65f, DetectionMethod.PLATFORM_ADAPTER,
                )
            } else {
                ShortDetectionResult(
                    ShortPlatform.FACEBOOK, ShortSurface.UNKNOWN,
                    isShortForm = false, confidence = 0.15f, DetectionMethod.PLATFORM_ADAPTER,
                )
            }
        }
        val p = ShortsMonitoringPipeline(
            detect = detect, store = InMemoryShortsLocalStore(), nowMillis = clock,
        )
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.facebook.katana", null, 0L)
        now = 500L
        p.onForegroundContentObserved("com.facebook.katana", transientUiEvidence())
        p.onForegroundScrolled("com.facebook.katana")
        now = 1_000L
        p.onForegroundContentObserved("com.facebook.katana", transientUiEvidence())

        assertTrue(
            "Expected Facebook Reels detection via scroll fallback, got: $states",
            states.any { it != null },
        )
        val activeState = states.firstNotNullOfOrNull { it }
        assertEquals(ShortPlatform.FACEBOOK, activeState?.platform)
        assertEquals(ShortSurface.FACEBOOK_REELS, activeState?.surface)
    }

    // 32. Snapchat Spotlight: activity class detection still works
    //     (TRANSIENT_UI must not interfere with already-detected session).
    @Test
    fun `snapchat spotlight detection via activity class is preserved through transient ui`() {
        // Snapchat detects via activity class ("spotlight" in class name),
        // so it should work regardless of TRANSIENT_UI evidence.
        val detect: (ShortDetectionSignals) -> ShortDetectionResult = { signals ->
            if (signals.activityClassName?.lowercase()?.contains("spotlight") == true) {
                ShortDetectionResult(
                    ShortPlatform.SNAPCHAT, ShortSurface.SNAPCHAT_SPOTLIGHT,
                    isShortForm = true, confidence = 0.80f, DetectionMethod.PLATFORM_ADAPTER,
                )
            } else {
                ShortDetectionResult(
                    ShortPlatform.SNAPCHAT, ShortSurface.UNKNOWN,
                    isShortForm = false, confidence = 0.15f, DetectionMethod.PLATFORM_ADAPTER,
                )
            }
        }
        val p = ShortsMonitoringPipeline(
            detect = detect, store = InMemoryShortsLocalStore(), nowMillis = clock,
        )
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        p.enter("com.snapchat.android", "com.snapchat.android.SpotlightActivity", 0L)

        // Snapchat detection must succeed immediately via activity class.
        assertTrue(
            "Expected Snapchat Spotlight detection, got: $states",
            states.any { it != null },
        )
        val activeState = states.firstNotNullOfOrNull { it }
        assertEquals(ShortPlatform.SNAPCHAT, activeState?.platform)
        assertEquals(ShortSurface.SNAPCHAT_SPOTLIGHT, activeState?.surface)

        // Now send transient UI evidence — session must survive.
        now = 1_000L
        p.onForegroundContentObserved("com.snapchat.android", transientUiEvidence())

        // HUD must still be visible (session preserved through transient UI).
        assertTrue(
            "HUD must survive transient UI during active Snapchat session",
            states.last() != null,
        )
    }

    // 33. Non-short content with transient UI: HUD must stay hidden.
    @Test
    fun `non-short youtube content with transient ui stays hidden`() {
        val p = pipeline()
        p.enter("com.google.android.youtube", youtubeHomeClass, 0L)
        val states = mutableListOf<ShortFormSurfaceState?>()
        p.addSurfaceListener(ShortFormSurfaceListener { states.add(it) })

        now = 1_000L
        p.onForegroundContentObserved("com.google.android.youtube", transientUiEvidence())

        // YouTube Home is not Shorts — HUD must stay hidden.
        assertTrue(
            "Expected all surface states null for YouTube Home, got: $states",
            states.all { it == null },
        )
    }
}
