package com.shortscap.app.hud

import android.content.Context
import com.shortscap.app.accessibility.AccessibilityServiceStatus
import com.shortscap.app.monitoring.MonitoringService
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
 * HUD VISIBILITY CONTRACT (every condition must hold, else the overlay is
 * hidden — granting Accessibility/monitoring permissions alone NEVER shows
 * the HUD):
 *
 *   1. ShortsCap monitoring is switched on ([MonitoringService] enabled
 *      flag — not just the accessibility service running).
 *   2. The Accessibility / required monitoring service is actually active
 *      in the OS ([AccessibilityServiceStatus] — the events that drive the
 *      HUD only flow while it is).
 *   3. The current foreground app is a SUPPORTED Shorts application
 *      (pipeline detection — never the raw package list).
 *   4. The detection result says `isShortForm == true`.
 *   5. Detection confidence >= the existing detector policy threshold
 *      ([ShortFormSurfaceState.CONFIDENCE_THRESHOLD]).
 *
 * Opening ShortsCap, Settings, the Permissions page, the Home screen,
 * starting MonitoringService, granting the overlay permission, app/device
 * restart or changing Shorts settings only PREPARE monitoring — they never
 * trigger the overlay. Unknown / low-confidence content and normal
 * long-form surfaces (YouTube video, Instagram feed, ...) keep the HUD
 * hidden, and the overlay is removed the moment the short-form context
 * ends.
 *
 * The overlay is additionally only shown when the HUD is enabled in
 * settings AND the SYSTEM_ALERT_WINDOW permission is granted; otherwise it
 * fails gracefully.
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

    /**
     * The visibility PREREQUISITES (conditions 1–2 of the visibility
     * contract): monitoring must be switched ON and the accessibility
     * service must actually be active in the OS. These only ENABLE the HUD
     * to appear — they never trigger it. The trigger remains a positive
     * short-form detection broadcast from the pipeline.
     */
    private fun canShowHud(context: Context): Boolean =
        MonitoringService.isMonitoringEnabled(context) &&
            AccessibilityServiceStatus.isEnabled(context)

    /** Called by the settings screen after any HUD setting changes. */
    fun refresh() {
        refreshLimit()
        val ctx = context ?: return
        val store = ShortsHudSettingsStore(ctx)
        if (!store.isEnabled() || !canShowHud(ctx)) {
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
        // ALL visibility conditions must hold: a positive short-form
        // detection AND the monitoring/accessibility prerequisites AND the
        // HUD being enabled in settings. Granting Accessibility alone never
        // shows the overlay.
        if (state == null || !store.isEnabled() || !canShowHud(ctx)) {
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
