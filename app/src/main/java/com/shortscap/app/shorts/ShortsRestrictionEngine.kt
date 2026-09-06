package com.shortscap.app.shorts

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shortscap.app.hud.OverlayLifecycleOwner
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

    /**
     * Phase 3 — in-memory surface context for the count-driven path. The
     * [CountChangeListener] does not receive a [ShortFormSurfaceState], so
     * this flag records whether the last known surface was an active short-
     * form surface. Updated ONLY by [onSurfaceStateChanged]; never persisted,
     * never an enforcement state — it only feeds the existing decision
     * function its surface precondition.
     */
    private var lastSurfaceActive = false

    private val surfaceListener = ShortFormSurfaceListener { state ->
        onSurfaceStateChanged(state)
    }

    /**
     * Phase 3 — count-driven enforcement seam. The pipeline notifies this
     * listener after every successful countShort() + persistence; the handler
     * re-reads the AUTHORITATIVE control state and re-evaluates the SAME
     * [shouldRestrict] decision, so the blocker appears immediately when the
     * limit is crossed while a Short is playing (no wait for the next
     * surface broadcast). The listener's count/limit arguments are ignored —
     * the fresh currentState() is the only source of truth.
     */
    private val countListener = ShortsMonitoringPipeline.CountChangeListener { _, _ ->
        onCountSignal()
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
        ShortsMonitoringPipeline.sharedInstance.addCountListener(countListener)
    }

    /** Unsubscribes and removes the overlay. Safe to call repeatedly. */
    fun stop() {
        if (started) {
            ShortsMonitoringPipeline.sharedInstance.removeSurfaceListener(surfaceListener)
            ShortsMonitoringPipeline.sharedInstance.removeCountListener(countListener)
            started = false
        }
        ShortsRestrictionOverlayManager.hide()
    }

    /**
     * The single decision point: re-read the AUTHORITATIVE control state on
     * every surface change (never a cached flag), then show/hide.
     */
    private fun onSurfaceStateChanged(state: ShortFormSurfaceState?) {
        // Phase 3 — remember the surface context for the count-driven path.
        lastSurfaceActive = state != null
        val ctx = context ?: return
        val effectiveState = effectiveControlState()
        val decision = shouldRestrict(state != null, effectiveState)
        // Phase 4A.2/4A.3 — DEBUG-only diagnostic (log only, no behavior
        // change beyond the persistent test condition itself).
        if (com.shortscap.app.BuildConfig.DEBUG) {
            val productionLimitReached = ShortsControlEngine.shared.currentState().limitReached
            Log.i("SHORTS_DIAG",
                "SURFACE_EVAL testEnforced=" + ShortsEnforcementTestHarness.isTestEnforced() +
                    " surfaceActive=" + (state != null) +
                    " productionLimitReached=" + productionLimitReached +
                    " effectiveLimitReached=" + effectiveState.limitReached +
                    " decision=" + decision +
                    " overlayShowingBefore=" + ShortsRestrictionOverlayManager.isShowing)
        }
        if (decision) {
            ShortsRestrictionOverlayManager.show(ctx)
        } else {
            if (com.shortscap.app.BuildConfig.DEBUG && ShortsRestrictionOverlayManager.isShowing) {
                Log.i("SHORTS_DIAG", "PRODUCTION_HIDE_REQUEST reason=PRODUCTION_EVAL_FALSE")
            }
            ShortsRestrictionOverlayManager.hide()
        }
    }

    /**
     * Phase 3 — count-driven evaluation. Called on the Accessibility Service
     * callback thread after every successful countShort() + persistence.
     * Synchronous by design: no Handler/coroutine/thread of its own. Re-reads
     * the authoritative control state (never a cached flag) and reuses the
     * SAME decision function and overlay manager as the surface path —
     * exactly one blocking decision, exactly one overlay.
     */
    private fun onCountSignal() {
        val ctx = context ?: return
        val effectiveState = effectiveControlState()
        val decision = shouldRestrict(lastSurfaceActive, effectiveState)
        // Phase 4A.2/4A.3 — DEBUG-only diagnostic (log only).
        if (com.shortscap.app.BuildConfig.DEBUG) {
            Log.i("SHORTS_DIAG",
                "COUNT_EVAL testEnforced=" + ShortsEnforcementTestHarness.isTestEnforced() +
                    " surfaceActive=$lastSurfaceActive" +
                    " effectiveLimitReached=" + effectiveState.limitReached +
                    " decision=$decision" +
                    " overlayShowingBefore=" + ShortsRestrictionOverlayManager.isShowing)
        }
        if (decision) {
            ShortsRestrictionOverlayManager.show(ctx)
        } else {
            ShortsRestrictionOverlayManager.hide()
        }
    }

    /**
     * Phase 4A.3 — the single state used by BOTH production evaluation
     * paths. Returns the authoritative production state, except when the
     * DEBUG controlled test is currently enforced: then a LOCAL copy with
     * `limitReached = true` is returned so the persistent test condition
     * survives re-evaluations (the Phase 4A.2 root-cause fix). The copy is
     * never persisted, never written back to the engine or Room, and the
     * override is impossible in release builds
     * ([ShortsEnforcementTestHarness.isTestEnforced] is DEBUG-gated). The
     * SAME production [shouldRestrict] decision function and the SAME real
     * [ShortsRestrictionOverlayManager] remain in use — no second engine,
     * no second decision, no duplicate `shouldRestrict` logic.
     */
    private fun effectiveControlState(): ShortsControlState {
        val productionState = ShortsControlEngine.shared.currentState()
        return if (com.shortscap.app.BuildConfig.DEBUG &&
            ShortsEnforcementTestHarness.isTestEnforced()
        ) {
            productionState.copy(limitReached = true)
        } else {
            productionState
        }
    }

    /**
     * Phase 4A.1 — DEBUG-only test enforcement seam.
     *
     * Lets the controlled test harness exercise the REAL enforcement action
     * with a TEST decision input: when [testCondition] is true it replaces
     * ONLY the production `limitReached` decision input (the production
     * state's limitReached is overridden via [ShortsControlState.copy] so
     * the SAME production [shouldRestrict] decision function still runs).
     * The enforcement ACTION is never faked: the real
     * [ShortsRestrictionOverlayManager.show] executes — same overlay, same
     * WindowManager flags, same touch blocking, same permission gate, same
     * isShowing guard, same production surface-path cleanup.
     *
     * Note: the overridden decision input is NOT persisted anywhere, so the
     * next production evaluation (any surface-state change) re-derives the
     * REAL state — with test mode at a small limit the overlay may lift on
     * the next surface transition. That is correct: only the decision input
     * is testable in DEBUG, never the production state.
     *
     * Release builds are inert (`NOT_DEBUG`, touches nothing).
     *
     * @param testCondition the TEST enforcement condition provided by the
     *   harness (true when the DEBUG test limit has been reached).
     * @return diagnostic result string for the SHORTS_TEST log:
     *   `SHOWN` / `SHOW_FAILED` / `DECISION_FALSE` / `NO_CONTEXT` / `NOT_DEBUG`.
     */
    fun debugTriggerEnforcement(testCondition: Boolean = false): String {
        if (!com.shortscap.app.BuildConfig.DEBUG) return "NOT_DEBUG"
        val ctx = context
        if (ctx == null) {
            Log.w("SHORTS_TEST", "TEST_ENFORCEMENT_BLOCKED reason=NO_SERVICE_CONTEXT")
            return "NO_CONTEXT"
        }
        val realState = ShortsControlEngine.shared.currentState()
        // Test seam: override ONLY the limitReached decision input; the
        // production decision function and action remain authoritative.
        val effectiveState = if (testCondition) realState.copy(limitReached = true) else realState
        val surfaceActive = lastSurfaceActive
        val decision = shouldRestrict(surfaceActive, effectiveState)
        // Phase 4A.2 — DEBUG-only diagnostic trace (log only).
        Log.i("SHORTS_DIAG", "DEBUG_TRIGGER_ENTERED testCondition=$testCondition")
        Log.i("SHORTS_DIAG",
            "ENFORCEMENT_STATE surfaceActive=$surfaceActive" +
                " testEnforced=" + ShortsEnforcementTestHarness.isTestEnforced() +
                " productionLimitReached=" + realState.limitReached +
                " effectiveLimitReached=" + effectiveState.limitReached +
                " productionCount=" + realState.currentCount +
                " productionLimit=" + realState.limitCount)
        Log.i("SHORTS_DIAG", "SHOULD_RESTRICT_RESULT result=$decision")
        Log.i("SHORTS_TEST",
            "TEST_ENFORCEMENT_EVAL surfaceActive=$surfaceActive " +
                "testCondition=$testCondition realLimitReached=${realState.limitReached} " +
                "decision=$decision")
        return if (decision) {
            Log.i("SHORTS_DIAG",
                "OVERLAY_SHOW_REQUEST context=" + ctx.javaClass.simpleName +
                    " canDrawOverlays=" + android.provider.Settings.canDrawOverlays(ctx))
            Log.i("SHORTS_DIAG", "DEBUG_TRIGGER_DECISION shouldRestrict=true")
            ShortsRestrictionOverlayManager.show(ctx)
            val shown = ShortsRestrictionOverlayManager.isShowing
            Log.i("SHORTS_DIAG", "OVERLAY_SHOW_RESULT shown=$shown")
            Log.i("SHORTS_TEST", "TEST_ENFORCEMENT_ACTION show success=$shown")
            if (shown) "SHOWN" else "SHOW_FAILED"
        } else {
            Log.i("SHORTS_DIAG",
                "DEBUG_TRIGGER_DECISION shouldRestrict=false surfaceActive=$surfaceActive " +
                    "testCondition=$testCondition")
            Log.w("SHORTS_TEST",
                "TEST_ENFORCEMENT_BLOCKED reason=DECISION_FALSE surfaceActive=$surfaceActive " +
                    "testCondition=$testCondition")
            "DECISION_FALSE"
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

    /**
     * The lifecycle owner attached to the current overlay ComposeView (set on
     * every successful [show], disposed on [hide]). System-overlay ComposeViews
     * require the ViewTree lifecycle owners (Phase 4A.6) — a fresh owner is
     * created per show so repeated show()/hide() cycles stay clean.
     */
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** Whether the restriction overlay is currently on screen. */
    val isShowing: Boolean get() = composeView != null

    /** Shows the full-screen restriction overlay (no-op when already up or permission missing). */
    fun show(context: Context) {
        // Phase 4A.2 — DEBUG-only diagnostics at every guard (log only, no
        // behavior change): distinguishes skipped vs failed vs attached.
        if (com.shortscap.app.BuildConfig.DEBUG) {
            Log.i("SHORTS_DIAG",
                "OVERLAY_SHOW_ENTER alreadyShowing=" + (composeView != null) +
                    " canDrawOverlays=" + Settings.canDrawOverlays(context))
        }
        if (composeView != null) {
            if (com.shortscap.app.BuildConfig.DEBUG) {
                Log.i("SHORTS_DIAG", "OVERLAY_SKIP reason=ALREADY_SHOWING")
            }
            return
        }
        // SYSTEM_ALERT_WINDOW is the only gate — fail gracefully, never assume.
        if (!Settings.canDrawOverlays(context)) {
            if (com.shortscap.app.BuildConfig.DEBUG) {
                Log.w("SHORTS_DIAG", "OVERLAY_SKIP reason=NO_OVERLAY_PERMISSION")
            }
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            if (com.shortscap.app.BuildConfig.DEBUG) {
                Log.w("SHORTS_DIAG", "OVERLAY_SKIP reason=NO_WINDOW_MANAGER")
            }
            return
        }

        // System overlays have no Activity in the view tree, so this
        // ComposeView cannot find a ViewTreeLifecycleOwner. Supply the SAME
        // explicit owners the Shorts HUD overlay uses (shared
        // OverlayLifecycleOwner) BEFORE the first composition so the overlay
        // cannot crash with "ViewTreeLifecycleOwner not found" (Phase 4A.6).
        // One owner per show() so repeated show()/hide() cycles never reuse a
        // disposed owner.
        val overlayLifecycle = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayLifecycle)
            setViewTreeViewModelStoreOwner(overlayLifecycle)
            setViewTreeSavedStateRegistryOwner(overlayLifecycle)
            overlayLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            overlayLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            overlayLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

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

        // Phase 4A.5 — boundary log: immediately before window attachment.
        if (com.shortscap.app.BuildConfig.DEBUG) {
            Log.i("SHORTS_DIAG", "OVERLAY_ATTACHED_ATTEMPT")
        }
        try {
            wm.addView(view, layout)
        } catch (error: Exception) {
            // Phase 4A.5 — observable, non-swallowed: log the exact failure
            // class/message, then RETHROW so the exception propagates exactly
            // as it naturally would (crash buffer keeps the FATAL record).
            if (com.shortscap.app.BuildConfig.DEBUG) {
                Log.w("SHORTS_DIAG",
                    "OVERLAY_SHOW_EXCEPTION class=${error.javaClass.name} message=${error.message}")
            }
            throw error
        }
        composeView = view
        windowManager = wm
        lifecycleOwner = overlayLifecycle
        if (com.shortscap.app.BuildConfig.DEBUG) {
            Log.i("SHORTS_DIAG", "OVERLAY_ATTACHED success=true")
        }
    }

    /** Removes the overlay if it is on screen (safe to call repeatedly). */
    fun hide() {
        val view = composeView
        if (com.shortscap.app.BuildConfig.DEBUG) {
            Log.i("SHORTS_DIAG", "OVERLAY_HIDE_REQUEST wasShowing=" + (view != null))
        }
        if (view == null) return
        val wm = windowManager
        composeView = null
        windowManager = null
        if (wm != null) runCatching { wm.removeView(view) }
        // Dispose the per-show lifecycle owner so its ViewModelStore /
        // SavedStateRegistry cannot outlive the removed ComposeView
        // (Phase 4A.6). A fresh owner is created on the next show().
        lifecycleOwner?.dispose()
        lifecycleOwner = null
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
