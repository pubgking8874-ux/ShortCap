package com.shortscap.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.shortscap.app.monitoring.BrainOverlayManager
import com.shortscap.app.monitoring.MonitoringEventHub
import com.shortscap.app.monitoring.SupportedShortVideoPackages

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
 * layer, never individual screens. It also drives the small Brain overlay:
 * when the foreground app is a supported short-video platform the Brain
 * appears (only with the Display Over Other Apps permission granted) and is
 * removed the moment the user leaves that context — never globally.
 */
class ShortsCapAccessibilityService :
    AccessibilityService(),
    MonitoringEventHub.MonitoringEventListener {

    override fun onServiceConnected() {
        super.onServiceConnected()
        MonitoringEventHub.subscribe(this)
    }

    /**
     * Brain indicator visibility — tied strictly to the foreground context:
     * shown only inside supported short-video apps, hidden everywhere else
     * (including ShortsCap itself and any other app). Runs on the service's
     * main thread, so WindowManager calls are safe.
     */
    override fun onForegroundAppChanged(packageName: String) {
        if (packageName in SupportedShortVideoPackages) {
            BrainOverlayManager.show(this)
        } else {
            BrainOverlayManager.hide()
        }
    }

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

    override fun onUnbind(intent: Intent?): Boolean {
        BrainOverlayManager.hide()
        MonitoringEventHub.unsubscribe(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        BrainOverlayManager.hide()
        MonitoringEventHub.unsubscribe(this)
        super.onDestroy()
    }
}
