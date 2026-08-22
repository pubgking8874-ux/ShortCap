package com.shortscap.app.shorts

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ShortsCapTheme
import com.shortscap.app.theme.ThemePreferenceStore

/**
 * ShortsRestrictionEngine — the missing enforcement consumer between the
 * EXISTING [ShortsControlEngine] and the device.
 *
 * It is the enforcement layer the control engine's KDoc reserves ("The
 * engine decides STATE. The Android enforcement layer decides how to react"):
 *
 *   ShortsControlEngine
 *       -> currentState()  (limitReached / LIMIT_REACHED, 24h-cycle derived)
 *   ShortsMonitoringPipeline  (EXISTING detection — never modified)
 *       -> surface listener  (active short-form surface)
 *   ShortsRestrictionEngine   (this) — SHOW when BOTH hold, HIDE otherwise
 *       -> full-screen touch-blocking overlay
 *
 * Rules (exactly the required behavior, all derived — NO timer is created):
 *  - SHOW only when a short-form surface is active AND the authoritative
 *    control state reports the limit reached (`limitReached == true`).
 *  - HIDE when the user leaves the short-form surface (surface == null).
 *  - HIDE automatically when the 24-hour cycle becomes EXPIRED / ALLOW: the
 *    engine NEVER caches a "blocked" flag — every surface-state callback
 *    re-reads [ShortsControlEngine.shared.currentState()], and an
 *    EXPIRED/DISABLED window reports `limitReached == false`, so the very
 *    next evaluation hides the overlay (no second timer, no second cycle).
 *
 * Reuses, without modification: [ShortsControlEngine] (state machine),
 * [ShortsMonitoringPipeline] surface listeners (detection), the
 * SYSTEM_ALERT_WINDOW overlay infrastructure pattern from the Shorts HUD,
 * and the existing Accessibility Service lifecycle (start/stop hooks).
 * The overlay is touch-blocking (full-screen, consumes all touches) so the
 * restricted app cannot be interacted with while it is up; system
 * navigation (Home/Back/Recents) still works and ends the restriction view.
 *
 * Android limitation (documented, not hidden): with the no-timer design, if
 * the cycle expires while the user is IDLE under the overlay, the overlay
 * lifts on their next navigation interaction (window transition → surface
 * re-evaluation → EXPIRED ⇒ hidden). The overlay permission
 * (SYSTEM_ALERT_WINDOW) is required; without it the engine fails gracefully
 * (no crash, no fake block).
 */
/**
 * Pure restriction decision: the overlay SHOWS only when a short-form
 * surface is active AND the authoritative control state reports the limit
 * reached. EXPIRED / DISABLED / ALLOW windows report `limitReached == false`,
 * so they automatically evaluate to hidden — no timer, no cached flag.
 */
internal fun shouldRestrict(surfaceActive: Boolean, controlState: ShortsControlState): Boolean =
    surfaceActive && controlState.limitReached

object ShortsRestrictionEngine {

    private var started = false
    private var context: Context? = null

    private val surfaceListener = ShortFormSurfaceListener { state ->
        onSurfaceStateChanged(state)
    }

    /**
     * Subscribes to the shared pipeline's surface-state notifications.
     * Idempotent — called from the existing Accessibility Service's
     * onServiceConnected, alongside the pipeline and HUD start.
     */
    fun start(context: Context) {
        this.context = context.applicationContext
        if (started) return
        started = true
        ShortsMonitoringPipeline.sharedInstance.addSurfaceListener(surfaceListener)
    }

    /** Unsubscribes and removes the overlay. Safe to call repeatedly. */
    fun stop() {
        if (started) {
            ShortsMonitoringPipeline.sharedInstance.removeSurfaceListener(surfaceListener)
            started = false
        }
        ShortsRestrictionOverlayManager.hide()
    }

    /**
     * The single decision point: re-read the AUTHORITATIVE control state on
     * every surface change (never a cached flag), then show/hide.
     */
    private fun onSurfaceStateChanged(state: ShortFormSurfaceState?) {
        val ctx = context ?: return
        val controlState = ShortsControlEngine.shared.currentState()
        if (shouldRestrict(state != null, controlState)) {
            ShortsRestrictionOverlayManager.show(ctx)
        } else {
            ShortsRestrictionOverlayManager.hide()
        }
    }
}

/**
 * Owns the full-screen touch-blocking restriction overlay lifecycle —
 * mirrors the ShortsHudOverlayManager pattern (ComposeView +
 * TYPE_APPLICATION_OVERLAY) but as a modal full-screen blocker: it consumes
 * ALL touches so the restricted short-form app cannot be interacted with,
 * and it never takes input focus (no keyboard/IME steal).
 */
object ShortsRestrictionOverlayManager {

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null

    /** Whether the restriction overlay is currently on screen. */
    val isShowing: Boolean get() = composeView != null

    /** Shows the full-screen restriction overlay (no-op when already up or permission missing). */
    fun show(context: Context) {
        if (composeView != null) return
        // SYSTEM_ALERT_WINDOW is the only gate — fail gracefully, never assume.
        if (!Settings.canDrawOverlays(context)) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val view = ComposeView(context).apply {
            setContent {
                ShortsCapTheme(mode = ThemePreferenceStore(context).loadThemeMode()) {
                    RestrictionOverlayContent()
                }
            }
        }

        // Full-screen, touch-blocking (no FLAG_NOT_TOUCH_MODAL → all touches
        // are consumed by this window), no input focus, stays on while shown.
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )

        runCatching { wm.addView(view, layout) }
            .onSuccess {
                composeView = view
                windowManager = wm
            }
    }

    /** Removes the overlay if it is on screen (safe to call repeatedly). */
    fun hide() {
        val view = composeView ?: return
        val wm = windowManager
        composeView = null
        windowManager = null
        if (wm != null) runCatching { wm.removeView(view) }
    }
}

/** The restriction overlay content — existing strings + theme only, no new UI system. */
@Composable
private fun RestrictionOverlayContent() {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* consume touches — the restricted app must not receive them */ },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                strings.shortsLimitStateLimitReached,
                color = colors.Danger,
                style = ScTextStyles.H1,
                textAlign = TextAlign.Center,
            )
            Text(
                strings.shortsLimitReachedDesc,
                color = colors.TextSecondary,
                style = ScTextStyles.Body,
                textAlign = TextAlign.Center,
            )
        }
    }
}
