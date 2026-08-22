package com.shortscap.app.shorts

import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * The signals a detector can use, reflecting what the EXISTING Android
 * architecture actually provides today.
 *
 * Today the Accessibility Service observes
 * [android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] —
 * the package name + window class of the foreground app — and, only while the
 * YouTube package is foreground, the active window's STRUCTURE
 * ([WindowContentEvidence]: node class names + resource ids, never user
 * content) so the Shorts player can be distinguished inside a generic window.
 * [packageName] is the primary signal; the remaining fields are provided by
 * the service or default to neutral values. Nothing here ever carries screen
 * contents or personal data.
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

    /**
     * Structural window evidence observed on the current content
     * ([monitoring.WindowContentEvidence] — deduplicated node class names and
     * resource ids only, never user text). Used by adapters whose event-level
     * window class is NOT surface-specific (YouTube confirmed this on-device:
     * Shorts runs inside the generic `watchwhile.MainActivity`), so the
     * Shorts surface must be told apart from Home / Watch / Live / Search by
     * what the window structure exposes. Empty when unavailable — detectors
     * must never invent evidence that was not observed.
     */
    val contentEvidence: WindowContentEvidence = WindowContentEvidence(),
)
