package com.shortscap.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.shortscap.app.hud.ShortsHudController
import com.shortscap.app.monitoring.MonitoringEventHub

/**
 * ShortsCap's own Accessibility Service — a MONITORING component, not a UI
 * automation tool.
 *
 * It exists so ShortsCap appears as its own service under Android
 * Settings → Accessibility (never relying on TalkBack, Switch Access or any
 * other system accessibility feature) and so the app can detect the service's
 * real enabled/disabled state from the OS.
 *
 * Minimum-access design:
 *  - It only observes [TYPE_WINDOW_STATE_CHANGED] events (the standard,
 *    privacy-minimal signal for "which app is now in the foreground").
 *  - It NEVER retrieves window content, performs clicks/taps/swipes/scrolls,
 *    fills forms, filters keys, or interacts with other applications.
 *  - It holds no state and stores nothing — each event is dispatched to the
 *    centralized [MonitoringEventHub], which is the seam future monitoring
 *    features (Shorts usage, website/app monitoring) subscribe to before
 *    feeding the existing centralized Activity/Web data layer. No second
 *    database, no duplicate monitoring system.
 *
 * The service itself is deliberately UI-agnostic: it only knows the event
 * layer, never individual screens. The Shorts HUD (a presentation layer) is
 * driven by the existing detection pipeline's surface-state broadcasts — the
 * service never shows/hides overlays itself.
 */
class ShortsCapAccessibilityService :
    AccessibilityService(),
    MonitoringEventHub.MonitoringEventListener {

    override fun onServiceConnected() {
        super.onServiceConnected()
        MonitoringEventHub.subscribe(this)
        // Cross-platform Shorts detection (Phase 11B) subscribes through the
        // same hub and classifies foreground windows via the platform
        // registry — the service itself stays a dumb, privacy-minimal
        // observer (package + window class metadata only).
        com.shortscap.app.shorts.ShortsMonitoringPipeline.start()
        // The Shorts HUD consumes the detection pipeline's surface-state
        // broadcasts (presentation only — never detects Shorts itself).
        ShortsHudController.start(this)
    }

    /**
     * Foreground change — handled by the detection pipeline (which the HUD
     * subscribes to). The service itself stays passive; it never decides
     * overlay visibility from the raw package list (a package is NOT a
     * short-form surface).
     */
    override fun onForegroundAppChanged(packageName: String, activityClassName: String?) {
        // Detection + HUD presentation are handled by the subscribed pipeline.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            // The active window changed → the user is now in a different
            // surface/app. Only package + window-class METADATA is read (no
            // window content), keeping the service privacy-minimal; the
            // class name lets the Shorts detector separate surfaces inside
            // the same app (e.g. YouTube Shorts vs YouTube Home).
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.let { pkg ->
                    MonitoringEventHub.dispatchForegroundAppChanged(
                        pkg.toString(),
                        event.className?.toString(),
                    )
                }
            }
        }
    }

    override fun onInterrupt() {
        // No audio/haptic feedback or ongoing actions to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ShortsHudController.stop()
        MonitoringEventHub.unsubscribe(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ShortsHudController.stop()
        MonitoringEventHub.unsubscribe(this)
        super.onDestroy()
    }
}
