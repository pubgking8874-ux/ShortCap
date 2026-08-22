package com.shortscap.app.monitoring

import java.util.concurrent.CopyOnWriteArrayList

/**
 * MonitoringEventHub — the centralized Monitoring/Event layer seam.
 *
 * This is the single funnel between monitoring event sources (today:
 * [com.shortscap.app.accessibility.ShortsCapAccessibilityService]) and
 * consumers (future Shorts-usage / app / website monitoring features).
 *
 * Architecture (one directional flow, no UI coupling):
 *
 *      Accessibility Service
 *              ↓
 *      MonitoringEventHub  (this layer)
 *              ↓
 *      Existing centralized data layer (ActivityRepository / WebRepository /
 *      future backend API) — NEVER a second database or duplicate system
 *
 * Future monitoring features subscribe via [subscribe] and write their
 * structured events into the app's existing Activity/Monitoring data layer so
 * the future backend can consume the same data. Until such a feature exists,
 * listeners simply receive [MonitoringEventListener.onForegroundAppChanged]
 * and the hub stores nothing.
 *
 * Short-form detection lives in the `com.shortscap.app.shorts` package
 * (ShortPlatform / ShortSurface / ShortPlatformRegistry / adapters): a
 * future Shorts detector subscribes here for foreground changes, classifies
 * them through the registry, and feeds the results to a ShortUsageAggregator
 * before any counting/sync — the hub itself stays a dumb event funnel.
 */
object MonitoringEventHub {

    /** A component interested in monitoring events. */
    fun interface MonitoringEventListener {
        /**
         * The user switched to the foreground window identified by
         * [packageName] (from a TYPE_WINDOW_STATE_CHANGED accessibility
         * event). [activityClassName] is the window's class name — the same
         * privacy-minimal metadata family as the package name (it identifies
         * WHICH window, never its content); it may be null when the event
         * carries no class. Called on the accessibility service's thread —
         * listeners must dispatch work themselves if they need a different
         * thread.
         */
        fun onForegroundAppChanged(packageName: String, activityClassName: String?)

        /**
         * An interaction was observed in the foreground app: the user scrolled
         * a view (from a TYPE_VIEW_SCROLLED accessibility event). Package-only
         * metadata, never content. Default no-op — only listeners that need
         * the interaction signal (e.g. the Shorts detector, to distinguish
         * a short-form feed from a static screen) override it.
         */
        fun onForegroundScrolled(packageName: String) {}

        /**
         * Structural content evidence was observed in [packageName]'s ACTIVE
         * window (see [WindowContentEvidence] — node class names and resource
         * ids only, deduplicated and bounded, never user content). Default
         * no-op — only listeners that need window-structure signals (e.g. the
         * Shorts detector, to tell the Shorts player apart from Home / Watch /
         * Live / Search inside a shared YouTube activity) override it.
         * Called on the accessibility service's thread.
         */
        fun onForegroundContentObserved(packageName: String, evidence: WindowContentEvidence) {}
    }

    // Copy-on-write: safe for concurrent reads while the service dispatches.
    private val listeners = CopyOnWriteArrayList<MonitoringEventListener>()

    /** Registers [listener] (idempotent — duplicates are ignored). */
    fun subscribe(listener: MonitoringEventListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /** Removes [listener]. */
    fun unsubscribe(listener: MonitoringEventListener) {
        listeners.remove(listener)
    }

    /**
     * Dispatches a foreground-window change to every subscriber. Called by
     * the Accessibility Service; safe to call from any thread.
     */
    fun dispatchForegroundAppChanged(packageName: String, activityClassName: String?) {
        listeners.forEach { it.onForegroundAppChanged(packageName, activityClassName) }
    }

    /**
     * Dispatches a scroll interaction in [packageName] to every subscriber.
     * Called by the Accessibility Service; safe to call from any thread.
     */
    fun dispatchForegroundScrolled(packageName: String) {
        listeners.forEach { it.onForegroundScrolled(packageName) }
    }

    /**
     * Dispatches structural window evidence for [packageName] to every
     * subscriber. Called by the Accessibility Service; safe to call from any
     * thread.
     */
    fun dispatchForegroundContentObserved(packageName: String, evidence: WindowContentEvidence) {
        listeners.forEach { it.onForegroundContentObserved(packageName, evidence) }
    }
}
