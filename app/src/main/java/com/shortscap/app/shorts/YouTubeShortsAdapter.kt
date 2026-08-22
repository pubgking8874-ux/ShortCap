package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * YouTube Shorts adapter.
 *
 * Platform identity is package-based (high confidence). Surface detection
 * uses the YouTube Shorts player activity class name from window-state
 * events when available, plus — for the generic windows confirmed to host
 * Shorts on-device — structural content evidence from the active window.
 *
 * Detection (tiers, so unrelated YouTube screens are never counted):
 *  1. Primary — the canonical Shorts player window class
 *     (`...Shell$ShortsActivity`), confidence 0.85.
 *  2. Safe fallback — ANY window class whose name contains "Shorts"
 *     (version/device currency gap). Confidence 0.7 — still surface-specific,
 *     still never a package-only guess.
 *  3. Confirmed on-device currency gap — Shorts observed running inside the
 *     `watchwhile` activity family (`...watchwhile.MainActivity` on the vivo
 *     device, `...watchwhile.InternalMainActivity` observed earlier). Those
 *     SAME windows also host long-form Watch and Live, so a bare class match
 *     would classify normal videos as Shorts. Evidence is REQUIRED, never a
 *     bare class:
 *       a. InternalMainActivity only may additionally use scroll-feed
 *          interaction evidence (`interactionCount > 0` from TYPE_VIEW_SCROLLED),
 *          confidence 0.6 — kept compatible with the existing rule. The vivo
 *          device delivers NO scroll events, so this alone never fires there,
 *          and it is deliberately NOT applied to MainActivity (a scrolling
 *          Watch page must stay uncounted).
 *       b. STRUCTURAL content evidence — the active window exposes a Shorts
 *          PLAYER node (node class matching a Shorts keyword + a player
 *          container keyword, or a known reel-player view resource id),
 *          confidence 0.75. This is the channel that fixes the vivo device
 *          without guessing: it distinguishes the Shorts player from Home's
 *          Shorts shelf / the bottom-nav Shorts tab / Watch / Live / Search.
 *
 * Without a Shorts-named class AND without the watchwhile+evidence rules
 * above the surface stays UNKNOWN: YouTube is far more than Shorts (Home /
 * Watch / Live / Stories / Search), and a scrolling Home feed is NOT evidence
 * of Shorts, so the app never assumes "YouTube is open = Shorts are being
 * watched" from package or scroll signals alone.
 */
object YouTubeShortsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.YOUTUBE
    override val packageNames: Set<String> = setOf("com.google.android.youtube")

    /** YouTube's canonical Shorts player window class (from accessibility window-state events). */
    private val shortsActivityClasses = setOf(
        "com.google.android.apps.youtube.app.application.Shell\$ShortsActivity",
    )

    /** The vivo device's actual Shorts window class (Phase 13.2 evidence). */
    private const val MAIN_ACTIVITY = "com.google.android.apps.youtube.app.watchwhile.MainActivity"

    /**
     * The earlier-confirmed Shorts host, which ALSO delivered scroll events —
     * it keeps the existing `+ scroll evidence` rule (compatibility).
     */
    private const val INTERNAL_MAIN_ACTIVITY = "com.google.android.apps.youtube.app.watchwhile.InternalMainActivity"

    /**
     * Generic `watchwhile` window classes confirmed to host Shorts on-device —
     * but ALSO long-form Watch / Live, so Shorts is only accepted there with
     * structural content evidence (the Shorts player must be present in the
     * window tree). The vivo device reported
     * `...watchwhile.MainActivity` (Phase 13.2).
     */
    private val shortsHostingWatchwhileClasses = setOf(
        MAIN_ACTIVITY,
        INTERNAL_MAIN_ACTIVITY,
    )

    /** Confidence for a Shorts player confirmed via window STRUCTURE (class/id). */
    private const val CONTENT_EVIDENCE_CONFIDENCE = 0.75f

    /**
     * Confidence for Shorts confirmed via content-description text signals only
     * (Phase 14: no Shorts-specific class names or resource ids in the tree,
     * but unique Shorts UI labels like "Remix this Short" are present).
     */
    private const val CONTENT_TEXT_EVIDENCE_CONFIDENCE = 0.70f

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName
        // ===== SC_YT_NODE_DIAG: ADAPTER_INPUT =====
        Log.i("SC_YT_NODE_DIAG",
            "SC_YT_NODE_DIAG ADAPTER_DETECT_INPUT pkg=${signals.packageName} cls=$className " +
                "interactionCount=${signals.interactionCount} " +
                "evidenceClasses=${signals.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${signals.contentEvidence.nodeViewIds.size}"
        )
        // ===== end SC_YT_NODE_DIAG =====
        if (className != null) {
            // Primary: the canonical Shorts player activity.
            if (className in shortsActivityClasses) {
                return shortsResult(
                    confidence = 0.85f,
                    surfaceSignal = "activity_class",
                )
            }
            // Safe fallback: any Shorts-named window class — covers YouTube
            // builds that host Shorts in a differently-named activity while
            // still excluding Home / Watch / Live / Search windows.
            if (className.contains("Shorts", ignoreCase = true)) {
                return shortsResult(
                    confidence = 0.7f,
                    surfaceSignal = "shorts_class_fallback",
                )
            }
            // Confirmed on-device cases: Shorts hosted inside the generic
            // `watchwhile` activity family. These SAME windows also host
            // long-form Watch / Live / Search, so evidence is REQUIRED before
            // this counts — a bare class match is never enough.
            //
            // InternalMainActivity (observed earlier): accepted with
            // scroll-feed interaction evidence, as the existing rule did
            // (kept compatible). Note the vivo device delivers NO scroll
            // events, so this alone never fires there — and it is NOT applied
            // to MainActivity, where a scrolling Watch page must stay uncounted.
            if (className == INTERNAL_MAIN_ACTIVITY && signals.interactionCount > 0) {
                return shortsResult(
                    confidence = 0.6f,
                    surfaceSignal = "watchwhile_class_plus_scroll",
                )
            }
            // MainActivity (the vivo device class) + InternalMainActivity:
            // structural content evidence only — the Shorts PLAYER must be
            // present in the window tree. This is the channel that fixes the
            // vivo device without guessing.
            if (className in shortsHostingWatchwhileClasses) {
                evaluateContentEvidence(signals.contentEvidence)?.let { return it }
            }
        }
        // For ANY YouTube class (including watchwhile, ViewGroup, FrameLayout,
        // or null), evaluate structural content evidence — the Shorts-unique
        // text signals are safe regardless of what the event-level class
        // reports. On some devices the WINDOW_STATE_CHANGED event reports the
        // source node class (e.g. "android.view.ViewGroup") instead of the
        // activity class, so the watchwhile-class check above is skipped even
        // though the user IS on Shorts. The content-description signals
        // ("Remix this Short", "See more videos using this sound") are
        // Shorts-exclusive and correctly identify the surface.
        evaluateContentEvidence(signals.contentEvidence)?.let { return it }
        // Package is YouTube but the surface is not confirmable — do not guess.
        // ===== SC_YT_NODE_DIAG: ADAPTER_OUTPUT (unconfirmed) =====
        Log.i("SC_YT_NODE_DIAG",
            "SC_YT_NODE_DIAG ADAPTER_DETECT_OUTPUT pkg=${signals.packageName} cls=$className " +
                "result=UNCONFIRMED isShortForm=false confidence=0.2"
        )
        // ===== end SC_YT_NODE_DIAG =====
        return unconfirmedResult(ShortPlatform.YOUTUBE, 0.2f)
    }

    private fun shortsResult(confidence: Float, surfaceSignal: String): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.YOUTUBE,
            surface = ShortSurface.YOUTUBE_SHORTS,
            isShortForm = true,
            confidence = confidence,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to surfaceSignal),
        )

    /**
     * Structural Shorts-player evidence: the active window exposes a node that
     * can ONLY plausibly be the Shorts player —
     *  - a node CLASS containing a Shorts keyword (shorts/reel) AND a player
     *    container keyword (player/pager/recycler/container/fragment), while
     *    NOT being a shelf / tab / chip (the Home Shorts shelf and the
     *    bottom-nav Shorts tab match the Shorts keyword but are NOT players);
     *  - OR a node VIEW RESOURCE ID in the known reel-player allowlist
     *    (unambiguous player containers like `reel_recycler` /
     *    `reel_watch_fragment_root` — a Home shelf id like
     *    `shorts_shelf_recycler` is deliberately not in it).
     *
     * Returns null when no player node is present, so the surface stays
     * UNKNOWN rather than guessing.
     */
    private fun evaluateContentEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        // ===== SC_YT_NODE_DIAG: log each class and id check =====
        Log.i("SC_YT_NODE_DIAG",
            "SC_YT_NODE_DIAG EVALUATE_START classes=${evidence.nodeClasses.size} ids=${evidence.nodeViewIds.size} descs=${evidence.nodeContentDescriptions.size}"
        )
        evidence.nodeClasses.forEach { cls ->
            val matched = cls.isShortPlayerNodeClass()
            if (matched || cls.lowercase().let { it.contains("shorts") || it.contains("reel") || it.contains("player") || it.contains("pager") || it.contains("recycler") || it.contains("fragment") }) {
                Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG CLASS_CHECK cls=$cls matched=$matched")
            }
        }
        evidence.nodeViewIds.forEach { id ->
            val matched = id.isShortPlayerNodeId()
            if (matched || id.lowercase().let { it.contains("reel") || it.contains("shorts") || it.contains("player") }) {
                Log.i("SC_YT_NODE_DIAG", "SC_YT_NODE_DIAG ID_CHECK id=$id matched=$matched")
            }
        }
        // ===== end SC_YT_NODE_DIAG =====
        val playerClassHits = evidence.nodeClasses.count { it.isShortPlayerNodeClass() }
        val playerIdHits = evidence.nodeViewIds.count { it.isShortPlayerNodeId() }
        // Phase 14 — Shorts text-signal detection: on devices where YouTube Shorts
        // uses generic Android widget classes (no Shorts-specific class names or
        // resource ids), the Shorts player can still be identified by content
        // descriptions that are UNIQUE to the Shorts surface and never appear on
        // Home / Watch / Live / Search.
        val textHit = evidence.nodeContentDescriptions.any { it.isShortsContentDescription() }
        // ===== SC_YT_NODE_DIAG: evaluation result =====
        Log.i("SC_YT_NODE_DIAG",
            "SC_YT_NODE_DIAG EVALUATE_RESULT playerClassHits=$playerClassHits playerIdHits=$playerIdHits " +
                "textHit=$textHit return=${if (playerClassHits == 0 && playerIdHits == 0 && !textHit) "null" else "MATCH"}"
        )
        // ===== end SC_YT_NODE_DIAG =====
        if (playerClassHits == 0 && playerIdHits == 0 && !textHit) return null
        return ShortDetectionResult(
            platform = ShortPlatform.YOUTUBE,
            surface = ShortSurface.YOUTUBE_SHORTS,
            isShortForm = true,
            confidence = if (textHit && playerClassHits == 0 && playerIdHits == 0) {
                CONTENT_TEXT_EVIDENCE_CONFIDENCE
            } else {
                CONTENT_EVIDENCE_CONFIDENCE
            },
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf(
                "surfaceSignal" to if (textHit && playerClassHits == 0 && playerIdHits == 0) {
                    "watchwhile_content_text_evidence"
                } else {
                    "watchwhile_content_player_evidence"
                },
                "playerClassHits" to playerClassHits,
                "playerIdHits" to playerIdHits,
                "textHit" to textHit,
            ),
        )
    }

    /**
     * Node CLASS is Shorts-specific AND player-shaped. The two-keyword rule is
     * the one validated on-device by the community (a single "shorts"/"reel"
     * keyword matches the bottom-nav Shorts TAB on every YouTube screen); the
     * shelf/tab/chip exclusion adds defense against the Home Shorts shelf.
     */
    private fun String.isShortPlayerNodeClass(): Boolean {
        val lower = lowercase()
        val shortsKeyword = lower.contains("shorts") || lower.contains("reel")
        if (!shortsKeyword) return false
        val playerContainer = lower.contains("player") || lower.contains("pager") ||
            lower.contains("recycler") || lower.contains("container") || lower.contains("fragment")
        if (!playerContainer) return false
        val shelfTabChip = lower.contains("shelf") || lower.contains("tab") || lower.contains("chip")
        return !shelfTabChip
    }

    /** Node VIEW RESOURCE ID is a known, unambiguous Shorts player container. */
    private fun String.isShortPlayerNodeId(): Boolean {
        val lower = lowercase()
        return SHORT_PLAYER_VIEW_IDS.any { lower.contains(it) }
    }

    /**
     * Phase 14: Content description that is UNIQUE to the YouTube Shorts player
     * surface and never appears on Home / Watch / Live / Search. The vivo
     * device confirmed that Shorts uses generic Android widget classes with no
     * Shorts-specific class names or resource ids, but the Shorts player's
     * action buttons expose text-only signals like "Remix this Short" and
     * "See more videos using this sound" that are Shorts-exclusive.
     *
     * A single hit is sufficient — these strings are never rendered on any
     * other YouTube surface.
     */
    private fun String.isShortsContentDescription(): Boolean {
        val lower = lowercase()
        return SHORTS_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
    }

    /**
     * Content description substrings that are UNIQUE to the YouTube Shorts
     * player surface. A match on any one of these is sufficient to confirm
     * Shorts. None of these appear on Home / Watch / Live / Search / Stories.
     */
    private val SHORTS_UNIQUE_DESCRIPTIONS = setOf(
        "remix this short",          // Shorts-only action button
        "see more videos using this sound",  // Shorts-only audio reuse button
    )

    /**
     * Unambiguous Shorts player resource ids (community-confirmed across
     * YouTube versions; the short-play surface for a video already playing).
     * Kept conservative — a Home shelf id (`shorts_shelf_*`) is intentionally
     * absent.
     */
    private val SHORT_PLAYER_VIEW_IDS = setOf(
        "reel_watch_fragment_root",
        "reel_recycler",
        "reel_player_page_container",
        "shorts_video_pager",
        "shorts_player",
        "reel_player",
    )
}
