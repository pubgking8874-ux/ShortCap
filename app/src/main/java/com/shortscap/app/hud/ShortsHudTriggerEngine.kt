package com.shortscap.app.hud

import android.content.Context
import com.shortscap.app.accessibility.AccessibilityServiceStatus
import com.shortscap.app.monitoring.MonitoringService
import com.shortscap.app.shorts.ShortFormSurfaceState
import com.shortscap.app.shorts.ShortPlatform
import com.shortscap.app.shorts.ShortsControlEngine
import com.shortscap.app.shorts.ShortsControlState
import com.shortscap.app.shorts.ShortsLimitCycleStatus

/**
 * ShortsHudTriggerEngine — the dedicated SHOW/HIDE decision boundary for the
 * Shorts HUD.
 *
 * Pipeline (per the architecture):
 *
 *   Shorts Detection Engine (ShortsMonitoringPipeline + adapters)
 *     -> ShortFormSurfaceState broadcast
 *     -> ShortsHudTriggerEngine          ← this engine (decides only)
 *     -> Show / Hide HUD
 *     -> Existing Shorts Control Engine  ← live count/limit/remaining/state
 *
 * It CONSUMES the existing detection results — it NEVER runs detection
 * itself (no registry, no adapters, no package-name rules here). Detection
 * confidence is already enforced upstream: the pipeline only broadcasts a
 * [ShortFormSurfaceState] when `isShortForm == true` AND confidence >= the
 * existing [ShortFormSurfaceState.CONFIDENCE_THRESHOLD], so this engine
 * trusts that contract instead of duplicating it.
 *
 * VISIBILITY RULE — the HUD is hidden by default and becomes visible ONLY
 * when ALL of these hold (else HIDDEN):
 *
 *   1. The current foreground surface is positively short-form (non-null
 *      [ShortFormSurfaceState] — the detection-engine result).
 *   2. ShortsCap monitoring is switched on (not just the service running).
 *   3. The Accessibility / required monitoring service is actually active in
 *      the OS.
 *   4. The HUD is enabled in Settings.
 *
 * So granting Accessibility (or any other permission), opening ShortsCap,
 * opening Settings, the launcher, normal app screens, or merely having a
 * supported app open NEVER shows the HUD. Exiting the short-form surface
 * (state == null) hides it immediately. Future platforms inherit the same
 * behavior automatically because they arrive through the same detection
 * interface.
 *
 * LIVE DATA — all counts come from the authoritative [ShortsControlEngine]
 * (ONE count across all platforms feeding the same 24-hour cycle): watched
 * today, limit, remaining, cycle state. The current platform comes from the
 * detection result. The engine never maintains its own counter.
 */
object ShortsHudTriggerEngine {

    /** The live HUD state the overlay renders (updated in place by this engine). */
    val uiState = ShortsHudUiState()

    private var lastSurface: ShortFormSurfaceState? = null

    /**
     * Re-evaluates visibility for [state] and refreshes the live HUD data.
     * Returns true when the HUD should be VISIBLE now (caller shows the
     * overlay), false when it must stay/be hidden.
     */
    fun onSurfaceChanged(state: ShortFormSurfaceState?, context: Context): Boolean {
        lastSurface = state
        if (!shouldShow(state, context)) {
            uiState.visible = false
            return false
        }
        val controlState = ShortsControlEngine.shared.currentState()
        val store = ShortsHudSettingsStore(context)
        val limit = if (controlState.limitCount > 0) controlState.limitCount else store.dailyLimit()
        // Live data — all derived from the existing sources, never a second counter.
        uiState.count = controlState.currentCount
        uiState.limit = limit
        uiState.remaining = (limit - controlState.currentCount).coerceAtLeast(0)
        uiState.cycleState = cycleStateLabel(controlState)
        uiState.platformLabel = platformLabel(state!!.platform)
        uiState.visible = true
        return true
    }

    /**
     * Re-runs the decision with the last known surface state — used after
     * HUD setting changes (mode / daily limit / enabled) so the overlay
     * reflects the new configuration without waiting for another detection.
     */
    fun refresh(context: Context): Boolean = onSurfaceChanged(lastSurface, context)

    /** Drops the remembered surface (called on controller stop). */
    fun reset() {
        lastSurface = null
        uiState.visible = false
    }

    // ---- Visibility prerequisites (never triggers alone) ----

    private fun shouldShow(state: ShortFormSurfaceState?, context: Context): Boolean {
        if (state == null) return false
        if (!MonitoringService.isMonitoringEnabled(context)) return false
        if (!AccessibilityServiceStatus.isEnabled(context)) return false
        return ShortsHudSettingsStore(context).isEnabled()
    }

    // ---- Live-data labels (presentation only — never counting) ----

    /** Short cycle-state label for the HUD, from the authoritative engine. */
    private fun cycleStateLabel(state: ShortsControlState): String = when {
        state.status == ShortsLimitCycleStatus.ACTIVE && state.warningTriggered -> "WARNING"
        state.status == ShortsLimitCycleStatus.ACTIVE -> "ACTIVE"
        state.status == ShortsLimitCycleStatus.LIMIT_REACHED -> "LIMIT REACHED"
        state.status == ShortsLimitCycleStatus.EXPIRED -> "EXPIRED"
        state.status == ShortsLimitCycleStatus.CONFIGURED -> "READY TO ACTIVATE"
        else -> "DISABLED"
    }

    /** Friendly platform label for the HUD (matches the app's platform names). */
    private fun platformLabel(platform: ShortPlatform): String? = when (platform) {
        ShortPlatform.YOUTUBE -> "YouTube Shorts"
        ShortPlatform.INSTAGRAM -> "Instagram Reels"
        ShortPlatform.TIKTOK -> "TikTok"
        ShortPlatform.SNAPCHAT -> "Snapchat Spotlight"
        ShortPlatform.FACEBOOK -> "Facebook Reels"
        ShortPlatform.MOJ -> "Moj"
        ShortPlatform.X -> "X"
        ShortPlatform.LINKEDIN -> "LinkedIn"
        ShortPlatform.SHARE_CHAT -> "ShareChat"
        ShortPlatform.UNKNOWN -> null
    }
}
