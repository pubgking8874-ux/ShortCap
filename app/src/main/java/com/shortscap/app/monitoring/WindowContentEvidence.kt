package com.shortscap.app.monitoring

/**
 * A bounded, non-sensitive snapshot of STRUCTURAL window evidence observed by
 * the Accessibility Service in a foreground app's active window.
 *
 * Phase 13.2: the vivo device confirmed that YouTube's Shorts runs inside the
 * generic `watchwhile.MainActivity` window — the same window that also hosts
 * Home / Watch / Live / Search — and that no TYPE_VIEW_SCROLLED events are
 * delivered on that device. To tell Shorts apart from those other surfaces
 * the service now (only while the YouTube package is foreground) reads the
 * active window's accessibility node STRUCTURE: node class names and resource
 * ids (e.g. `com.google.android.youtube:id/reel_recycler`). Those are window
 * identifiers, NOT user content.
 *
 * The service NEVER collects screen text, passwords or personal data into
 * detection signals; [nodeClasses] and [nodeViewIds] are deduplicated and
 * size-capped so the payload stays small no matter how large the window tree
 * is. Consumers (today the Shorts detector) match them against known
 * short-player structures to classify a surface that window-level metadata
 * alone cannot identify.
 */
data class WindowContentEvidence(
    /** Deduplicated accessibility node CLASS names observed (bounded). */
    val nodeClasses: List<String> = emptyList(),
    /** Deduplicated accessibility node VIEW resource ids observed (bounded). */
    val nodeViewIds: List<String> = emptyList(),
    /**
     * Bounded, deduplicated content descriptions observed on nodes (bounded,
     * non-sensitive — only structural UI labels like "Subscribe", "Share this
     * video", "Remix this Short", etc., never free-form user text). Used as
     * an additional Shorts surface signal when class/id evidence alone is
     * insufficient (Phase 14: vivo device confirmed YouTube Shorts uses generic
     * Android widget classes without Shorts-specific class names or resource
     * ids, but exposes Shorts-unique content descriptions).
     */
    val nodeContentDescriptions: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = nodeClasses.isEmpty() && nodeViewIds.isEmpty() && nodeContentDescriptions.isEmpty()
}