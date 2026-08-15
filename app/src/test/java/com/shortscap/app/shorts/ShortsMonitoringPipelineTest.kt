package com.shortscap.app.shorts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
