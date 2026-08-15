package com.shortscap.app.shorts

/**
 * The signals a detector can use, reflecting what the EXISTING Android
 * architecture actually provides today.
 *
 * Today the Accessibility Service observes only
 * [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] —
 * the package name of the foreground app (privacy-minimal, no window
 * content, no synthetic interaction). So [packageName] is the primary signal;
 * the remaining fields are either provided by future detectors or default to
 * neutral values. Nothing here ever carries screen contents or personal data.
 *
 * Detectors must never invent signals the app does not actually collect.
 */
data class ShortDetectionSignals(
    /** Foreground app package name (always available from window-state events). */
    val packageName: String? = null,

    /** Foreground activity/window class name — optional, when the event carries it. */
    val activityClassName: String? = null,

    /** How long the current content has been in the foreground, in ms. */
    val foregroundDurationMillis: Long = 0L,

    /** Swipe/scroll interactions observed on the current content (future signal). */
    val interactionCount: Int = 0,

    /** Visible content descriptors where available — must stay non-sensitive. */
    val visibleText: List<String> = emptyList(),
)
