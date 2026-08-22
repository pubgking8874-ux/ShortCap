package com.shortscap.app.hud

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shortscap.app.theme.ThemePreferenceStore

/**
 * ShortsHudOverlayManager — owns the floating HUD overlay lifecycle.
 *
 * The overlay is a [ComposeView] added to the WindowManager with
 * TYPE_APPLICATION_OVERLAY (the SYSTEM_ALERT_WINDOW permission is declared in
 * the manifest and checked here via [Settings.canDrawOverlays] — the HUD
 * fails gracefully and never crashes when permission is missing or revoked).
 *
 * The HUD is DRAGGABLE: pointer drags move the overlay by updating the
 * window params; on release the chosen position is persisted as normalized
 * (0..1) X/Y fractions so it survives device size / orientation changes.
 *
 * The HUD is pure presentation: [uiState] carries the detection/aggregation
 * results from the existing pipeline (this manager never detects or counts
 * Shorts, and never talks to the backend). The content is set ONCE and
 * updates flow through Compose state, so count/limit/appearance changes
 * recompose in place.
 */
object ShortsHudOverlayManager {

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var settingsStore: ShortsHudSettingsStore? = null
    private var uiState: ShortsHudUiState? = null

    /** Whether the overlay is currently on screen. */
    val isShowing: Boolean get() = composeView != null

    /**
     * Shows the HUD bound to [uiState] with the current [appearance]. No-op
     * when the overlay permission is missing or the HUD is disabled.
     */
    fun show(context: Context, uiState: ShortsHudUiState, appearance: ShortsHudAppearance) {
        if (composeView != null) return
        // Guard: SYSTEM_ALERT_WINDOW is the only gate — the user must grant it
        // through Android settings. Never assume it exists.
        if (!Settings.canDrawOverlays(context)) return

        val store = ShortsHudSettingsStore(context)
        if (!store.isEnabled()) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val view = ComposeView(context).apply {
            // System overlays have no Activity in the view tree, so ComposeView
            // cannot find ViewTreeLifecycleOwner. Supply one explicitly.
            val overlayLifecycle = OverlayLifecycleOwner()
            setViewTreeLifecycleOwner(overlayLifecycle)
            setViewTreeViewModelStoreOwner(overlayLifecycle)
            setViewTreeSavedStateRegistryOwner(overlayLifecycle)
            overlayLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            overlayLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            overlayLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            setContent {
                // The HUD respects the global ShortsCap theme (dark / light /
                // system) — read from the same store the app uses.
                com.shortscap.app.theme.ShortsCapTheme(
                    mode = ThemePreferenceStore(context).loadThemeMode()
                ) {
                    ShortsHudContent(
                        uiState = uiState,
                        appearance = appearance,
                        onDrag = { dx, dy -> moveBy(dx, dy) },
                        onDragEnd = { persistPosition() },
                    )
                }
            }
        }

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Restore the persisted normalized position as pixels.
            val metrics = context.resources.displayMetrics
            x = (store.positionX() * metrics.widthPixels).toInt()
            y = (store.positionY() * metrics.heightPixels).toInt()
        }

        runCatching { wm.addView(view, layout) }
            .onSuccess {
                composeView = view
                windowManager = wm
                params = layout
                settingsStore = store
                this.uiState = uiState
                uiState.visible = true
            }
    }

    /**
     * Re-renders with a new [appearance] (the shared [uiState] is bound at
     * [show] time; appearance changes need a content refresh). No-op when
     * hidden.
     */
    fun refresh(context: Context, appearance: ShortsHudAppearance) {
        val view = composeView ?: return
        val state = uiState ?: return
        val store = settingsStore ?: ShortsHudSettingsStore(context)
        view.setContent {
            com.shortscap.app.theme.ShortsCapTheme(
                mode = ThemePreferenceStore(context).loadThemeMode()
            ) {
                ShortsHudContent(
                    uiState = state,
                    appearance = appearance,
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onDragEnd = { persistPosition() },
                )
            }
        }
    }

    /** Removes the overlay if it is on screen (safe to call repeatedly). */
    fun hide() {
        val view = composeView ?: return
        val wm = windowManager
        composeView = null
        windowManager = null
        params = null
        settingsStore = null
        uiState?.let { it.visible = false }
        uiState = null
        if (wm != null) runCatching { wm.removeView(view) }
    }

    /**
     * Minimal lifecycle owner for ComposeView in system overlay windows.
     * Overlays have no Activity in the view tree, so ComposeView cannot find
     * ViewTreeLifecycleOwner. This provides the three owners Compose needs.
     */    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val vmStore = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init { savedStateRegistryController.performRestore(null) }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = vmStore
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }

    /**
     * Moves the overlay by a raw pixel delta. Compose drag deltas share the
     * same top-origin coordinate space as WindowManager params (y grows
     * downward), so the deltas map 1:1 onto x/y — no inversion needed.
     */
    private fun moveBy(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val layout = params ?: return
        layout.x += dx.toInt()
        layout.y += dy.toInt()
        runCatching { wm.updateViewLayout(composeView, layout) }
    }

    /** Persists the current pixel position as normalized 0..1 fractions. */
    private fun persistPosition() {
        val store = settingsStore ?: return
        val view = composeView ?: return
        val layout = params ?: return
        val metrics = view.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        if (width <= 0 || height <= 0) return
        // Clamp so the HUD can never be dragged fully off-screen.
        val viewW = view.width.coerceAtLeast(1)
        val viewH = view.height.coerceAtLeast(1)
        val safeX = ShortsHudPosition.clampPixelX(layout.x, width, viewW)
        val safeY = ShortsHudPosition.clampPixelY(layout.y, height, viewH)
        store.setPosition(
            ShortsHudPosition.pixelToNormalized(safeX, width),
            ShortsHudPosition.pixelToNormalized(safeY, height),
        )
    }
}
