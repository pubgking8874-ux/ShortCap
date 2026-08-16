package com.shortscap.app.hud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for the Shorts HUD's logic (no Android runtime needed):
 * brain-state thresholds, appearance parsing and the normalized-position
 * clamping math used when persisting the draggable HUD position.
 */
class ShortsHudLogicTest {

    // ---- Brain state thresholds (0–40% HEALTHY, 40–75% TIRED, 75–99%
    //      NEAR_LIMIT, >= 100% LIMIT_REACHED) ----

    @Test
    fun `zero usage is healthy`() {
        assertEquals(BrainState.HEALTHY, BrainState.forRatio(0f))
    }

    @Test
    fun `below 40 percent is healthy`() {
        assertEquals(BrainState.HEALTHY, BrainState.forRatio(0.39f))
    }

    @Test
    fun `at exactly 40 percent is tired`() {
        assertEquals(BrainState.TIRED, BrainState.forRatio(0.40f))
    }

    @Test
    fun `mid range is tired`() {
        assertEquals(BrainState.TIRED, BrainState.forRatio(0.60f))
    }

    @Test
    fun `at exactly 75 percent is near limit`() {
        assertEquals(BrainState.NEAR_LIMIT, BrainState.forRatio(0.75f))
    }

    @Test
    fun `just below 100 percent is near limit`() {
        assertEquals(BrainState.NEAR_LIMIT, BrainState.forRatio(0.99f))
    }

    @Test
    fun `at 100 percent is limit reached`() {
        assertEquals(BrainState.LIMIT_REACHED, BrainState.forRatio(1.00f))
    }

    @Test
    fun `above 100 percent is limit reached`() {
        assertEquals(BrainState.LIMIT_REACHED, BrainState.forRatio(3.5f))
    }

    @Test
    fun `negative ratio is treated as healthy`() {
        assertEquals(BrainState.HEALTHY, BrainState.forRatio(-1f))
    }

    // ---- Brain video asset mapping (the user's four FINAL videos) ----

    @Test
    fun `every brain state maps to its final video asset`() {
        assertEquals("shorts_brain/brain_1_healthy.mp4.mp4", BrainVideoAssets.pathFor(BrainState.HEALTHY))
        assertEquals("shorts_brain/brain_2_tired.mp4.mp4", BrainVideoAssets.pathFor(BrainState.TIRED))
        assertEquals("shorts_brain/brain_3_near_limit.mp4.mp4", BrainVideoAssets.pathFor(BrainState.NEAR_LIMIT))
        assertEquals("shorts_brain/brain_4_limit_reached.mp4.mp4", BrainVideoAssets.pathFor(BrainState.LIMIT_REACHED))
    }

    @Test
    fun `each brain state maps to a distinct video`() {
        val paths = BrainState.entries.map { BrainVideoAssets.pathFor(it) }
        assertEquals(paths.size, paths.toSet().size)
    }

    // ---- Appearance parsing (unknown values fall back to the default) ----

    @Test
    fun `appearance resolves by name`() {
        assertEquals(ShortsHudAppearance.BRAIN, ShortsHudAppearance.fromName("BRAIN"))
        assertEquals(ShortsHudAppearance.LIVE_COUNTER, ShortsHudAppearance.fromName("LIVE_COUNTER"))
    }

    @Test
    fun `unknown appearance name falls back to ShortsCap`() {
        assertEquals(ShortsHudAppearance.SHORTSCAP, ShortsHudAppearance.fromName("TOTALLY_UNKNOWN"))
        assertEquals(ShortsHudAppearance.SHORTSCAP, ShortsHudAppearance.fromName(null))
    }

    // ---- Normalized position clamping (dragged HUD stays on screen) ----

    @Test
    fun `position is clamped to the zero-to-one range`() {
        assertEquals(0f, ShortsHudPosition.clampNormalized(-0.5f), 0f)
        assertEquals(1f, ShortsHudPosition.clampNormalized(1.7f), 0f)
        assertEquals(0.25f, ShortsHudPosition.clampNormalized(0.25f), 0f)
    }

    @Test
    fun `pixel position is clamped inside the screen`() {
        // 1080 x 2400 screen, 120 x 60 HUD: x in [0, 960], y in [0, 2340].
        assertEquals(0, ShortsHudPosition.clampPixelX(-50, 1080, 120))
        assertEquals(960, ShortsHudPosition.clampPixelX(5000, 1080, 120))
        assertEquals(300, ShortsHudPosition.clampPixelX(300, 1080, 120))
        assertEquals(0, ShortsHudPosition.clampPixelY(-10, 2400, 60))
        assertEquals(2340, ShortsHudPosition.clampPixelY(9999, 2400, 60))
    }

    @Test
    fun `tiny screen never yields negative clamped position`() {
        // HUD bigger than the screen: clamp to 0 rather than negative.
        assertEquals(0, ShortsHudPosition.clampPixelX(10, 100, 120))
        assertEquals(0, ShortsHudPosition.clampPixelY(10, 100, 120))
    }

    @Test
    fun `pixel to normalized conversion is exact`() {
        assertEquals(0.5f, ShortsHudPosition.pixelToNormalized(540, 1080), 1e-6f)
        assertEquals(0.0f, ShortsHudPosition.pixelToNormalized(0, 1080), 1e-6f)
        assertEquals(1.0f, ShortsHudPosition.pixelToNormalized(1080, 1080), 1e-6f)
    }
}
