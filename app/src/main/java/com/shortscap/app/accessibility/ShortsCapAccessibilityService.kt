package com.shortscap.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.shortscap.app.hud.ShortsHudController
import com.shortscap.app.monitoring.MonitoringEventHub
import com.shortscap.app.monitoring.MonitoringService
import com.shortscap.app.monitoring.WindowContentEvidence
import com.shortscap.app.screenactivity.ScreenActivityEngine
import com.shortscap.app.shorts.ShortsRestrictionEngine

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
 *  - It observes [TYPE_WINDOW_STATE_CHANGED] events (the standard,
 *    privacy-minimal signal for "which app is now in the foreground") and
 *    [TYPE_VIEW_SCROLLED] events (an ADDITIONAL interaction signal — the
 *    Shorts detector uses it to distinguish a scrolling short-form feed
 *    from a static screen; package metadata only, never content).
 *  - Phase 13.2 (on-device evidence): the vivo device runs YouTube Shorts
 *    inside the generic `watchwhile.MainActivity` window — the SAME window as
 *    Home / Watch / Live / Search — and delivers NO TYPE_VIEW_SCROLLED events.
 *    To still tell Shorts apart, the service reads the ACTIVE WINDOW'S
 *    STRUCTURE (accessibility node CLASS names + view RESOURCE ids) while
 *    (and only while) the YouTube package is foreground. That structural walk
 *    is bounded and throttled, and it NEVER collects screen text/passwords:
 *    only node identifiers are used as detection signals (diagnostic logs may
 *    briefly show truncated content descriptions/text during Phase A).
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
        // ===== TEMP-DIAG (remove after device diagnosis) =====
        Log.i("SC_SERVICE_TEST", "onServiceConnected called")
        // ===== end TEMP-DIAG =====
        MonitoringEventHub.subscribe(this)
        // Cross-platform Shorts detection (Phase 11B) subscribes through the
        // same hub and classifies foreground windows via the platform
        // registry — the service itself stays a dumb, privacy-minimal
        // observer (package + window class metadata only).
        com.shortscap.app.shorts.ShortsMonitoringPipeline.start()
        // Shorts Restriction Engine — the enforcement consumer between the
        // existing ShortsControlEngine (LIMIT_REACHED) and the device: shows
        // a full-screen touch-blocking overlay while a short-form surface is
        // active and the limit is reached. Consumes the EXISTING pipeline
        // surface listener + control state; never detects or counts.
        com.shortscap.app.shorts.ShortsRestrictionEngine.start(this)
        // Screen Activity — GENERAL app/screen usage collection (which app is
        // active and for how long). STRICTLY INDEPENDENT of Shorts Control:
        // it runs only while the persisted Screen Activity toggle is ON, and
        // turning it off never stops Shorts detection/counting (and vice
        // versa). The gate reads the same persisted monitoring toggle the
        // Settings screen writes.
        ScreenActivityEngine.start { MonitoringService.isMonitoringEnabled(this@ShortsCapAccessibilityService) }
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
        // ===== TEMP-DIAG (remove after device diagnosis) =====
        val diagType = eventTypeName(event.eventType)
        Log.i("SC_SERVICE_TEST", "onAccessibilityEvent type=$diagType pkg=${event.packageName} cls=${event.className}")
        Log.i("SC_DIAG", "EVENT type=$diagType pkg=${event.packageName} cls=${event.className}")
        // ===== end TEMP-DIAG =====

        val isYouTube = event.packageName?.toString() == YOUTUBE_PACKAGE
        if (isYouTube) {
            // ===== TEMP-DIAG Phase A (remove after device diagnosis) =====
            logYouTubeEventDiagnostics(event)
            // ===== end TEMP-DIAG =====
        }

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
                if (isYouTube) collectYouTubeWindowContent(event)
            }
            // Scroll interaction in the foreground app — used ONLY as an
            // additional interaction signal by the Shorts detection pipeline
            // (package metadata only, never content).
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                event.packageName?.let { pkg ->
                    MonitoringEventHub.dispatchForegroundScrolled(pkg.toString())
                }
            }
            // Phase 13.2: window STRUCTURE changed (content node updates,
            // ~30Hz while scrolling). For YouTube only, a throttled structural
            // walk keeps the Shorts-surface signal fresh inside the generic
            // watchwhile window. Never dispatched for other packages.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isYouTube) collectYouTubeWindowContent(event)
            }
        }
    }

    // =========================================================================
    // Phase A — YouTube runtime diagnostics (temporary)
    // =========================================================================

    /** Friendly accessibility event-type names for diagnostics. */
    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TOUCH_INTERACTION_START"
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TOUCH_INTERACTION_END"
        else -> "type=$type"
    }

    /**
     * Phase A diagnostic — every YouTube event logged with its full
     * non-sensitive metadata so the real on-device signals can be read from
     * logcat. Very high-frequency content-changed events are logged compactly
     * (one line, source + truncated desc/text when present); everything else
     * gets the full multi-field record.
     */
    private fun logYouTubeEventDiagnostics(event: AccessibilityEvent) {
        // event.source may throw SecurityException or UnsupportedOperationException
        // on some devices/OS versions. Guard so the exception never crashes the
        // YouTube event chain and blocks collectYouTubeWindowContent() below.
        val source = try { event.source } catch (_: Throwable) { null }
        val typeName = eventTypeName(event.eventType)
        val sourceClass = source?.className?.toString()
        val shortRelevant = sourceClass?.lowercase()?.let {
            it.contains("shorts") || it.contains("reel") || it.contains("player") ||
                it.contains("pager") || it.contains("recycler") || it.contains("container") ||
                it.contains("video")
        } == true

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !shortRelevant) {
            val desc = event.contentDescription?.toString()?.take(80) ?: ""
            val txt = event.text?.joinToString("|") { it?.toString()?.take(40) ?: "" }
            Log.i(
                "SC_YT_DIAG",
                "SC_YT_DIAG type=$typeName pkg=${event.packageName} cls=${event.className} " +
                    "srcJava=${source?.javaClass?.name} srcCls=$sourceClass " +
                    "desc=\"$desc\" text=\"$txt\" eventTime=${event.eventTime}",
            )
            return
        }

        Log.i(
            "SC_YT_DIAG",
            buildString {
                append("SC_YT_DIAG type=$typeName\n")
                append("pkg=${event.packageName}\n")
                append("cls=${event.className}\n")
                append("srcJava=${source?.javaClass?.name}\n")
                append("srcCls=$sourceClass\n")
                append("desc=\"${event.contentDescription?.toString()?.take(100) ?: ""}\"\n")
                append("text=\"${event.text?.joinToString("|") { it?.toString()?.take(60) ?: "" } ?: ""}\"\n")
                append("eventTime=${event.eventTime}")
            },
        )
    }

    // =========================================================================
    // Phase B — YouTube window-structure evidence (bounded + throttled)
    // =========================================================================

    /** Last structural evidence dispatched — used for delta diagnostics. */
    private var lastYouTubeClasses: Set<String> = emptySet()
    private var lastYouTubeIds: Set<String> = emptySet()

    /** Last structural walk time (eventTime ms) — throttles content-changed walks. */
    private var lastYouTubeWalkAt = 0L

    /**
     * Walks the ACTIVE window structure for the YouTube package only and
     * dispatches [WindowContentEvidence] (node class names + view resource
     * ids, deduplicated and bounded — never user text). Window-state-changed
     * walks are immediate (rare; that's how Shorts entry presents);
     * content-changed walks are throttled so ~30Hz updates do not destroy
     * frame rate. The walk is capped in nodes/depth and the evidence lists are
     * capped in size.
     */
    private fun collectYouTubeWindowContent(event: AccessibilityEvent) {
        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isStateChange && (event.eventTime - lastYouTubeWalkAt) < YOUTUBE_WALK_THROTTLE_MILLIS) {
            Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG WALK_SKIP_THROTTLE elapsed=${event.eventTime - lastYouTubeWalkAt} throttle=$YOUTUBE_WALK_THROTTLE_MILLIS")
            return
        }
        val root = rootInActiveWindow
        if (root == null) {
            Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG WALK_SKIP_ROOT_NULL stateChange=$isStateChange pkg=${event.packageName}")
            return
        }
        if (root.packageName?.toString() != YOUTUBE_PACKAGE) {
            Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG WALK_SKIP_PKG_MISMATCH expected=$YOUTUBE_PACKAGE got=${root.packageName}")
            return
        }
        lastYouTubeWalkAt = event.eventTime

        Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG CONTENT_EVIDENCE_START pkg=$YOUTUBE_PACKAGE stateChange=$isStateChange rootPkg=${root.packageName}")
        val classes = LinkedHashSet<String>()
        val ids = LinkedHashSet<String>()
        val descs = LinkedHashSet<String>()
        val detail = mutableListOf<String>()
        val nodeDiagEntries = mutableListOf<String>()
        var visited = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || visited >= MAX_WALK_NODES || depth > MAX_WALK_DEPTH) return
            visited++
            node.className?.toString()?.let { cls ->
                if (classes.size < MAX_EVIDENCE_ENTRIES) classes.add(cls)
            }
            node.viewIdResourceName?.let { id ->
                if (ids.size < MAX_EVIDENCE_ENTRIES) ids.add(id)
            }
            node.contentDescription?.toString()?.let { desc ->
                if (desc.length in 3..100 && descs.size < MAX_EVIDENCE_ENTRIES) descs.add(desc)
            }
            // ===== SC_YT_NODE_DIAG: log every node with relevant metadata =====
            if (nodeDiagEntries.size < 120) {
                val cls = node.className?.toString() ?: ""
                val id = node.viewIdResourceName ?: ""
                val desc = node.contentDescription?.toString()?.take(40) ?: ""
                val txt = node.text?.toString()?.take(40) ?: ""
                val scrollable = node.isScrollable
                val clickable = node.isClickable
                val focusable = node.isFocusable
                val lc = cls.lowercase()
                val shortsRelevant = lc.contains("shorts") || lc.contains("reel") ||
                    lc.contains("player") || lc.contains("pager") ||
                    lc.contains("recycler") || lc.contains("container") ||
                    lc.contains("video") || lc.contains("fragment")
                // Log every node: always include class + id + depth + scrollable.
                // Only include desc/text for nodes with content or shorts-relevant keywords.
                if (shortsRelevant || desc.isNotEmpty() || txt.isNotEmpty() || id.isNotEmpty()) {
                    nodeDiagEntries += "d=$depth cls=$cls id=$id scr=$scrollable clk=$clickable fcs=$focusable desc=\"$desc\" text=\"$txt\""
                }
            }
            if (isStateChange && detail.size < MAX_DETAIL_LINES) {
                val cls = node.className?.toString() ?: ""
                val id = node.viewIdResourceName ?: ""
                val desc = node.contentDescription?.toString()?.take(40) ?: ""
                val txt = node.text?.toString()?.take(40) ?: ""
                val relevant = cls.lowercase().contains("shorts") || cls.lowercase().contains("reel") ||
                    cls.lowercase().contains("player") || cls.lowercase().contains("pager") ||
                    cls.lowercase().contains("recycler") || cls.lowercase().contains("container") ||
                    cls.lowercase().contains("video")
                if (relevant || desc.isNotEmpty() || txt.isNotEmpty()) {
                    detail += "node[cls=$cls id=$id desc=\"$desc\" text=\"$txt\"]"
                }
            }
            for (i in 0 until node.childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let { child ->
                    walk(child, depth + 1)
                }
            }
        }
        runCatching { walk(root, 0) }

        val newClasses = classes.filterNot { it in lastYouTubeClasses }
        val newIds = ids.filterNot { it in lastYouTubeIds }
        // ===== SC_YT_NODE_DIAG: log collected evidence + per-node entries =====
        Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG CONTENT_EVIDENCE_RESULT nodes=$visited classes=${classes.size} ids=${ids.size} entries=${nodeDiagEntries.size}")
        nodeDiagEntries.take(80).forEach { Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG NODE $it") }
        // ===== TEMP-DIAG Phase B (remove after device diagnosis) =====
        Log.i(
            "SC_YT_DIAG",
            "SC_YT_DIAG HIERARCHY pkg=$YOUTUBE_PACKAGE nodes=$visited classes=${classes.size} " +
                "ids=${ids.size} newClasses=${newClasses.size} newIds=${newIds.size} stateChange=$isStateChange",
        )
        if (isStateChange) {
            Log.i("SC_YT_DIAG", "SC_YT_DIAG HIERARCHY classes=${classes.take(80).joinToString(",")}")
            Log.i("SC_YT_DIAG", "SC_YT_DIAG HIERARCHY ids=${ids.take(80).joinToString(",")}")
            detail.forEach { Log.i("SC_YT_DIAG", "SC_YT_DIAG $it") }
        } else if (newClasses.isNotEmpty() || newIds.isNotEmpty()) {
            Log.i("SC_YT_DIAG", "SC_YT_DIAG HIERARCHY newClasses=${newClasses.take(20).joinToString(",")}")
            Log.i("SC_YT_DIAG", "SC_YT_DIAG HIERARCHY newIds=${newIds.take(20).joinToString(",")}")
        }
        // ===== end TEMP-DIAG =====

        lastYouTubeClasses = classes
        lastYouTubeIds = ids
        Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG DISPATCHING_EVIDENCE classes=${classes.size} ids=${ids.size} classList=${classes.take(40).joinToString(",")}")
        MonitoringEventHub.dispatchForegroundContentObserved(
            YOUTUBE_PACKAGE,
            WindowContentEvidence(
                nodeClasses = classes.toList(),
                nodeViewIds = ids.toList(),
                nodeContentDescriptions = descs.toList(),
            ),
        )
    }

    override fun onInterrupt() {
        // No audio/haptic feedback or ongoing actions to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ShortsRestrictionEngine.stop()
        ShortsHudController.stop()
        ScreenActivityEngine.stop()
        MonitoringEventHub.unsubscribe(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ShortsRestrictionEngine.stop()
        ShortsHudController.stop()
        ScreenActivityEngine.stop()
        MonitoringEventHub.unsubscribe(this)
        super.onDestroy()
    }

    private companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        /** Throttle for high-frequency content-changed structural walks. */
        const val YOUTUBE_WALK_THROTTLE_MILLIS = 150L

        /** Structural walk bounds (keep the ~30Hz content updates cheap). */
        const val MAX_WALK_NODES = 600
        const val MAX_WALK_DEPTH = 60
        const val MAX_EVIDENCE_ENTRIES = 80
        const val MAX_DETAIL_LINES = 40
    }
}
