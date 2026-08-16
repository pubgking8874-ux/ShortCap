package com.shortscap.app.hud

import android.content.Context
import com.shortscap.app.shorts.ShortFormSurfaceListener
import com.shortscap.app.shorts.ShortFormSurfaceState
import com.shortscap.app.shorts.ShortsControlEngine
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
 * accordingly. Since P1-5 the count/limit come from the AUTHORITATIVE
 * [ShortsControlEngine] (ONE count across all platforms feeding the same
 * 24-hour cycle, persisted across restarts) — the HUD never maintains its
 * own count. The engine limit is used when an active cycle exists;
 * otherwise the configured HUD daily limit (the settings default) is
 * displayed.
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
        // P1-5: the authoritative 24-hour cycle is the HUD's count/limit
        // source. When no active cycle exists (control disabled / not yet
        // activated) the configured daily limit is displayed instead.
        val controlState = ShortsControlEngine.shared.currentState()
        uiState.count = controlState.currentCount
        uiState.limit = if (controlState.limitCount > 0) controlState.limitCount else store.dailyLimit()
    }

    /** Refreshes the daily limit from the local settings store. */
    private fun refreshLimit() {
        val ctx = context ?: return
        uiState.limit = ShortsHudSettingsStore(ctx).dailyLimit()
    }
}
