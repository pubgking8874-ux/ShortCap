package com.shortscap.app.shorts

/**
 * The currently-active short-form surface, broadcast by
 * [ShortsMonitoringPipeline] to presentation-layer listeners (e.g. the
 * Shorts HUD).
 *
 * The HUD MUST consume these results — it never detects Shorts itself. The
 * pipeline runs the existing detection (platform registry + adapters) on the
 * foreground context and only reports a state here when the detection is
 * positive: `isShortForm == true` with sufficient confidence. UNKNOWN /
 * low-confidence classifications broadcast `null`, so the HUD stays hidden
 * rather than guessing.
 */
data class ShortFormSurfaceState(
    val platform: ShortPlatform,
    val surface: ShortSurface,
    val confidence: Float,
) {
    companion object {
        /**
         * Minimum detection confidence for the surface to be reported as
         * active — mirrors the aggregator's counting threshold, so the HUD
         * never shows for content the detector itself would not trust.
         */
        const val CONFIDENCE_THRESHOLD = 0.5f
    }
}

/** Receives active short-form surface changes from [ShortsMonitoringPipeline]. */
fun interface ShortFormSurfaceListener {
    /**
     * Called when the foreground context's short-form status changes.
     * [state] is non-null only while a short-form surface is active;
     * `null` means the current foreground context is not short-form
     * (normal video, home screens, chat, unknown content, or ShortsCap
     * itself) and the HUD must hide.
     */
    fun onShortFormSurfaceChanged(state: ShortFormSurfaceState?)
}
