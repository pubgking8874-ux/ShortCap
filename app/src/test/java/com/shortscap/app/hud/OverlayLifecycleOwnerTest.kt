package com.shortscap.app.hud

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Phase 4A.6 — shared [OverlayLifecycleOwner] used by BOTH system-overlay
 * ComposeViews (Shorts HUD + full-screen restriction overlay).
 *
 * Robolectric runs the test on the main thread, which the
 * androidx.lifecycle.LifecycleRegistry requires for event dispatch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OverlayLifecycleOwnerTest {

    @Test
    fun `owner reaches resumed and dispose drives it to destroyed`() {
        val owner = OverlayLifecycleOwner()

        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        // hide() path: the owner must be disposable for repeated show/hide.
        owner.dispose()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
}
