package com.shortscap.app.hud

import android.content.Context
import com.shortscap.app.shorts.ShortFormSurfaceListener
import com.shortscap.app.shorts.ShortFormSurfaceState
import com.shortscap.app.shorts.ShortsMonitoringPipeline

/**
 * ShortsHudController — the bridge between the EXISTING Shorts detection
 * pipeline and the floating HUD overlay.
 *
 * Flow (per the architecture):
 *
 *   MonitoringEventHub -> existing detection pipeline (registry + adapters)
 *     -> ShortFormSurfaceState  (via [ShortsMonitoringPipeline] surface listeners)
 *     -> ShortsHudController
 *     -> floating overlay ([ShortsHudOverlayManager])
 *
 * The controller NEVER detects Shorts itself — it consumes the pipeline's
 * broadcast [ShortFormSurfaceState] (non-null only when the foreground
 * context is positively short-form) and shows/hides the overlay
 * accordingly. The global count comes from the pipeline's budget tracker
 * (ONE count across all platforms — YouTube + Instagram + TikTok + ... all
 * contribute to the same total, never per-platform HUD counters).
 *
 * The overlay is only shown when the HUD is enabled in settings AND the
 * SYSTEM_ALERT_WINDOW permission is granted; otherwise it fails gracefully.
 */
object ShortsHudController {

    private val uiState = ShortsHudUiState()

    private var started = false
    private var context: Context? = null

    private val surfaceListener = ShortFormSurfaceListener { state ->
        onSurfaceStateChanged(state)
    }

    /**
     * Subscribes to the shared pipeline's surface-state notifications and
     * refreshes the current HUD state. Idempotent — safe to call from the
     * accessibility service's onServiceConnected.
     */
    fun start(context: Context) {
        this.context = context.applicationContext
        if (started) return
        started = true
        val pipeline = ShortsMonitoringPipeline.sharedInstance
        pipeline.addSurfaceListener(surfaceListener)
        refreshLimit()
    }

    /** Unsubscribes and hides the overlay. Safe to call repeatedly. */
    fun stop() {
        if (started) {
            ShortsMonitoringPipeline.sharedInstance.removeSurfaceListener(surfaceListener)
            started = false
        }
        ShortsHudOverlayManager.hide()
    }

    /** Called by the settings screen after any HUD setting changes. */
    fun refresh() {
        refreshLimit()
        val ctx = context ?: return
        val store = ShortsHudSettingsStore(ctx)
        if (!store.isEnabled()) {
            ShortsHudOverlayManager.hide()
            return
        }
        val state = currentSurfaceState
        if (state != null) {
            updateUiState(state)
            if (ShortsHudOverlayManager.isShowing) {
                ShortsHudOverlayManager.refresh(ctx, store.appearance())
            } else {
                ShortsHudOverlayManager.show(ctx, uiState, store.appearance())
            }
        }
    }

    /** The last positively-detected short-form surface (null when hidden). */
    var currentSurfaceState: ShortFormSurfaceState? = null
        private set

    private fun onSurfaceStateChanged(state: ShortFormSurfaceState?) {
        currentSurfaceState = state
        val ctx = context ?: return
        val store = ShortsHudSettingsStore(ctx)
        if (state == null || !store.isEnabled()) {
            ShortsHudOverlayManager.hide()
            return
        }
        updateUiState(state)
        ShortsHudOverlayManager.show(ctx, uiState, store.appearance())
    }

    private fun updateUiState(state: ShortFormSurfaceState) {
        val store = context?.let { ShortsHudSettingsStore(it) } ?: return
        uiState.count = globalCount()
        uiState.limit = store.dailyLimit()
    }

    /** Global Shorts count across ALL platforms (from the pipeline budget). */
    private fun globalCount(): Int =
        ShortsMonitoringPipeline.sharedInstance.currentBudget().totalShorts.toInt()

    /** Refreshes the daily limit from the local settings store. */
    private fun refreshLimit() {
        val ctx = context ?: return
        uiState.limit = ShortsHudSettingsStore(ctx).dailyLimit()
    }
}
