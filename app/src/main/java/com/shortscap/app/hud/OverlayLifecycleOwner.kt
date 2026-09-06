package com.shortscap.app.hud

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal lifecycle owner for ComposeView in system overlay windows.
 *
 * System overlays (TYPE_APPLICATION_OVERLAY via WindowManager) have no
 * Activity in the view tree, so a ComposeView cannot find a
 * ViewTreeLifecycleOwner / ViewTreeViewModelStoreOwner /
 * ViewTreeSavedStateRegistryOwner and crashes when its composition resolves
 * them (`ViewTreeLifecycleOwner not found` — observed on-device before this
 * owner was introduced for the Shorts HUD overlay, Phase 15/21).
 *
 * This shared component provides the three owners Compose needs and is used
 * by BOTH system-overlay ComposeViews in the app: the Shorts HUD
 * ([ShortsHudOverlayManager]) and the full-screen restriction overlay
 * ([com.shortscap.app.shorts.ShortsRestrictionOverlayManager]) — one proven
 * lifecycle mechanism, not two.
 */
internal class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
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

    /**
     * Drives the owner to DESTROYED. Call when the overlay window is removed
     * so the ViewModelStore / SavedStateRegistry owned here cannot outlive
     * the ComposeView they were attached to.
     */
    fun dispose() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
