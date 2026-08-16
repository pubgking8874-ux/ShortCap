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
 *     -> ShortsHudTriggerEngine  ← the dedicated show/hide + live-data engine
 *     -> ShortsHudController     ← overlay lifecycle only
 *     -> floating overlay ([ShortsHudOverlayManager])
 *
 * The controller NEVER detects Shorts and NEVER decides visibility itself —
 * that is [ShortsHudTriggerEngine]'s job (it consumes the pipeline's
 * broadcast [ShortFormSurfaceState], which is non-null only when the
 * foreground context is positively short-form, and applies every visibility
 * rule). The controller only registers the surface listener and manages the
 * overlay window.
 *
 * HUD VISIBILITY CONTRACT (enforced by [ShortsHudTriggerEngine], every
 * condition must hold, else the overlay is hidden — granting
 * Accessibility/monitoring permissions alone NEVER shows the HUD):
 *
 *   1. The current foreground surface is positively short-form (non-null
 *      surface state with sufficient confidence, decided by the detection
 *      pipeline — never the raw package list).
 *   2. ShortsCap monitoring is switched on ([MonitoringService] enabled
 *      flag — not just the accessibility service running).
 *   3. The Accessibility / required monitoring service is actually active in
 *      the OS.
 *   4. The HUD is enabled in Settings.
 *
 * Opening ShortsCap, Settings, the Permissions page, the Home screen, the
 * launcher, starting MonitoringService, granting the overlay permission,
 * app/device restart or changing Shorts settings only PREPARE monitoring —
 * they never trigger the overlay. Unknown / low-confidence content and
 * normal long-form surfaces (YouTube video, Instagram feed, ...) keep the
 * HUD hidden, and the overlay is removed the moment the short-form context
 * ends.
 *
 * The overlay is additionally only shown when the SYSTEM_ALERT_WINDOW
 * permission is granted; otherwise it fails gracefully.
 */
object ShortsHudController {

    private var started = false
    private var context: Context? = null

    private val surfaceListener = ShortFormSurfaceListener { state ->
        onSurfaceStateChanged(state)
    }

    /**
     * Subscribes to the shared pipeline's surface-state notifications.
     * Idempotent — safe to call from the accessibility service's
     * onServiceConnected.
     */
    fun start(context: Context) {
        this.context = context.applicationContext
        if (started) return
        started = true
        val pipeline = ShortsMonitoringPipeline.sharedInstance
        pipeline.addSurfaceListener(surfaceListener)
    }

    /** Unsubscribes and hides the overlay. Safe to call repeatedly. */
    fun stop() {
        if (started) {
            ShortsMonitoringPipeline.sharedInstance.removeSurfaceListener(surfaceListener)
            started = false
        }
        ShortsHudTriggerEngine.reset()
        ShortsHudOverlayManager.hide()
    }

    /** Called by the settings screen after any HUD setting changes. */
    fun refresh() {
        val ctx = context ?: return
        val store = ShortsHudSettingsStore(ctx)
        // Re-run the trigger with the last known surface — the engine applies
        // every visibility rule (including the HUD being enabled) and
        // refreshes the live count/limit/remaining/cycle/platform data.
        if (!ShortsHudTriggerEngine.refresh(ctx)) {
            ShortsHudOverlayManager.hide()
            return
        }
        if (ShortsHudOverlayManager.isShowing) {
            ShortsHudOverlayManager.refresh(ctx, store.appearance())
        } else {
            ShortsHudOverlayManager.show(ctx, ShortsHudTriggerEngine.uiState, store.appearance())
        }
    }

    /** The last positively-detected short-form surface (null when hidden). */
    var currentSurfaceState: ShortFormSurfaceState? = null
        private set

    private fun onSurfaceStateChanged(state: ShortFormSurfaceState?) {
        currentSurfaceState = state
        val ctx = context ?: return
        val store = ShortsHudSettingsStore(ctx)
        // The trigger engine decides visibility + assembles live data from
        // the detection result and the authoritative ShortsControlEngine;
        // the controller only manages the overlay lifecycle.
        if (!ShortsHudTriggerEngine.onSurfaceChanged(state, ctx)) {
            ShortsHudOverlayManager.hide()
            return
        }
        ShortsHudOverlayManager.show(ctx, ShortsHudTriggerEngine.uiState, store.appearance())
    }
}
