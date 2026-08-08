package com.shortscap.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
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
 * layer, never individual screens.
 */
class ShortsCapAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            // The active window changed → the user is now in a different app.
            // The package name alone identifies the foreground app without
            // needing any window content.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.let { pkg ->
                    MonitoringEventHub.dispatchForegroundAppChanged(pkg.toString())
                }
            }
        }
    }

    override fun onInterrupt() {
        // No audio/haptic feedback or ongoing actions to interrupt.
    }

    // onServiceConnected / onUnbind use the defaults: the OS's enabled state is
    // the source of truth (re-read by the app on every resume), and nothing in
    // this process subscribes to events yet — future monitoring components can
    // override them when they need connection lifecycle hooks.
}
