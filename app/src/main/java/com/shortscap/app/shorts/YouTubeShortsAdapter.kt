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

    /**
     * Overlap threshold for identity comparison.
     * If >50% of identity descriptors overlap, it's the same Short.
     */
    private const val IDENTITY_OVERLAP_THRESHOLD = 0.5

    /**
     * UI-state descriptions that change with overlays but NOT with
     * Short identity. These must be excluded from the identity fingerprint.
     */
    private val UI_STATE_DESCRIPTIONS = setOf(
        // Comments / replies
        "comment", "reply", "replies", "add a comment",
        // Engagement actions
        "like", "unlike", "dislike", "save", "bookmark",
        "share", "remix",
        // Navigation / menus
        "subscribe", "subscribed", "more", "menu", "settings",
        "description", "show more", "show less",
        // Player controls
        "play", "pause", "mute", "unmute", "fullscreen",
        "captions", "quality", "speed",
        // Counts / metrics
        "likes", "views", "videos",
    )

    // =========================================================================
    // YouTube-specific overlay detection (replaces generic ShortSurfaceClassifier)
    // =========================================================================

    /**
     * View ID keywords that indicate a YouTube overlay (comments panel, share
     * sheet, description panel, menu). These are distinct from the Shorts
     * player's own button IDs (like_button, share_button) — they represent
     * overlay CONTAINERS, not the player's built-in controls.
     *
     * The Shorts player's own buttons ("like", "share", "comment", "subscribe")
     * are ALWAYS present and are NOT overlays. Only the overlay panels that
     * OPEN ON TOP of the player are transient UI.
     */
    private val YT_OVERLAY_VIEW_ID_KEYWORDS = setOf(
        // Comments panel (distinct from the "comment" button)
        "comment_input", "comment_list", "comment_thread",
        "reply_container", "reply_input",
        // Share sheet
        "share_sheet", "share_dialog", "share_panel", "share_menu",
        "send_to",
        // Description panel
        "description_panel", "description_sheet",
        // Menu / bottom sheet overlays
        "bottom_sheet", "bottom_dialog", "popup_menu",
        "more_menu", "action_sheet", "overlay_menu",
        // Dialog / modal
        "dialog", "modal", "alert_dialog",
    )

    /**
     * Class name keywords that indicate overlay widget containers (BottomSheet,
     * Dialog, Popup, etc.). These are Android framework widgets used for
     * overlays, distinct from the Shorts player's own view classes.
     */
    private val YT_OVERLAY_CLASS_KEYWORDS = setOf(
        "BottomSheet", "Dialog", "Popup", "Modal",
        "Snackbar", "Tooltip", "ContextMenu", "PopupMenu",
        "AlertDialog",
    )

    /**
     * View IDs that identify the Shorts PLAYER container (used for structural
     * fingerprint signals). These are always present while the Shorts player
     * is visible and distinguish the player from overlay panels.
     */
    private val YT_SHORTS_PLAYER_VIEW_IDS = setOf(
        "reel_watch_fragment_root",
        "reel_recycler",
        "reel_player_page_container",
        "shorts_video_pager",
        "shorts_player",
        "reel_player",
    )

    /**
     * Class name keywords that identify Shorts player structural elements.
     * Used for structural fingerprinting (detecting genuine Short-to-Short
     * transitions via accessibility tree changes).
     */
    private val YT_SHORTS_PLAYER_CLASS_KEYWORDS = setOf(
        "reel", "shorts",
    )

    /**
     * YouTube Shorts player's own button labels. These are ALWAYS present in
     * the Shorts player's accessibility tree and are NOT transient UI.
     *
     * Distinguishing these from true overlays:
     *  - Shorts player buttons: "like", "share", "comment", "subscribe"
     *    (always present, part of the player)
     *  - Comments overlay: "add a comment", "reply", "replies" (only when
     *    comments panel is open)
     *  - Share overlay: "send to", "share this" (only when share sheet is open)
     *  - Menu overlay: "more actions", "more options" (only when menu is open)
     *
     * The generic ShortSurfaceClassifier treats ALL of these as TRANSIENT_UI,
     * which incorrectly blocks advance detection for the Shorts player itself.
     * YouTube-specific overlay detection focuses on the OVERLAY-specific signals.
     */
    private val YT_SHORTS_PLAYER_BUTTON_LABELS = setOf(
        "like", "share", "comment", "subscribe",
    )

    /**
     * Shorts player button labels for content fingerprint filtering.
     * These are ALWAYS present in the Shorts player accessibility tree and
     * must be excluded from the content identity fingerprint — they are
     * player controls, not Short-specific content.
     *
     * Checked case-insensitively against the LOWERCASED content description.
     */
    private val SHORTS_PLAYER_BUTTON_LABELS = setOf(
        "like", "dislike", "share", "comment", "subscribe",
        "remix", "save", "download",
    )

    /**
     * Check if a content description is a Shorts player control button.
     * These are ALWAYS present in the Shorts player and must NOT be used
     * as Short identity content.
     */
    private fun String.isShortsPlayerButton(): Boolean {
        val lower = this.lowercase().trim()
        return SHORTS_PLAYER_BUTTON_LABELS.any { lower == it }
    }

    // =========================================================================
    // Genuine user advance detection
    // =========================================================================

    /**
     * YouTube does not always generate reliable TYPE_VIEW_SCROLLED events
     * (e.g., vivo device delivers NO scroll events). We detect genuine
     * advances by comparing content evidence between the previous and current
     * snapshots.
     *
     * Strategy (multi-signal, YouTube-specific):
     *
     * 1. YouTube-specific overlay detection: reject genuine overlays (comments
     *    panel, share sheet, menus) while allowing the Shorts player's own
     *    controls ("like", "share", "comment", "subscribe") to pass through.
     *    The generic ShortSurfaceClassifier is too aggressive — it classifies
     *    the Shorts player itself as TRANSIENT_UI because its buttons contain
     *    keywords like "like" and "share".
     *
     * 2. Content fingerprint comparison: compare Shorts-specific content
     *    descriptions (creator name, caption) plus Shorts-unique labels
     *    ("Remix this Short") and structural view IDs. When content identity
     *    changes significantly, it indicates a genuine Short-to-Short transition.
     *
     * 3. Structural-delta fallback: when content descriptions are too sparse
     *    or similar (e.g., same creator, similar captions), detect genuine
     *    advances by structural changes in the accessibility tree (new player
     *    container classes, different view IDs). This handles the case where
     *    two consecutive Shorts from the same creator have similar text but
     *    different underlying structure.
     *
     * The pipeline's 3-second protection window provides additional defense
     * against false positives.
     */
    override fun detectUserAdvance(
        previousEvidence: WindowContentEvidence,
        currentEvidence: WindowContentEvidence,
    ): Boolean {
        // ===== YouTube-specific overlay detection =====
        // Reject genuine overlays (comments panel, share sheet, menu) while
        // allowing the Shorts player's own controls to pass through.
        // Checking BOTH evidence snapshots prevents false advances when the
        // user opens and closes an overlay (e.g., comments → close).
        if (hasTransientOverlay(currentEvidence)) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames advance=false " +
                    "reason=TRANSIENT_OVERLAY evidence=CURRENT"
            )
            return false
        }
        if (hasTransientOverlay(previousEvidence)) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames advance=false " +
                    "reason=TRANSIENT_OVERLAY evidence=PREVIOUS"
            )
            return false
        }

        // ===== YouTube Shorts player presence check =====
        val currentOnShorts = hasShortsPlayerSignal(currentEvidence)
        val previousOnShorts = hasShortsPlayerSignal(previousEvidence)

        // ===== Content fingerprint comparison =====
        // Multi-signal fingerprint: content identity descriptions (creator,
        // caption) + Shorts-unique labels + Shorts player view IDs.
        // Shorts player button labels (like/share/comment/subscribe) are
        // EXCLUDED from the content identity portion — they are static UI,
        // not content.
        val prevFingerprint = identityFingerprint(previousEvidence)
        val currFingerprint = identityFingerprint(currentEvidence)

        // Overlap is computed using ONLY content identity descriptions
        // (creator, caption) — Shorts-unique labels and player view IDs
        // are structural signals, not identity signals.
        val prevContentIds = prevFingerprint.filter { it.startsWith("content:") }.toSet()
        val currContentIds = currFingerprint.filter { it.startsWith("content:") }.toSet()

        val contentChanged: Boolean = if (prevContentIds.isEmpty() && currContentIds.isEmpty()) {
            // No content identity descriptions available in either snapshot.
            // Fall through to structural check.
            false
        } else if (prevContentIds.isEmpty() || currContentIds.isEmpty()) {
            // Asymmetric: one has content identity, other doesn't.
            // This is a strong signal of a transition.
            true
        } else {
            val intersection = prevContentIds.intersect(currContentIds)
            val union = prevContentIds.union(currContentIds)
            val overlap = if (union.isEmpty()) 1.0 else intersection.size.toDouble() / union.size
            overlap <= IDENTITY_OVERLAP_THRESHOLD
        }

        // ===== Structural-delta fallback =====
        // When content descriptions are sparse or similar (same creator,
        // similar captions), detect genuine advances by structural changes:
        // Shorts player view IDs, class names, Shorts-specific node counts,
        // and content identity description counts.
        val structuralChanged = hasStructuralDelta(previousEvidence, currentEvidence)

        // ===== New content descriptions check =====
        // If content identity descriptions appear in current that were not
        // in previous (and aren't Shorts player buttons), this is a strong
        // signal of a new Short — the new Short's creator/caption loaded.
        val newContentDescriptions = currContentIds.subtract(prevContentIds)
        val newContentAppear = newContentDescriptions.isNotEmpty()

        val overlap = if (prevContentIds.isEmpty() && currContentIds.isEmpty()) {
            1.0
        } else if (prevContentIds.isEmpty() || currContentIds.isEmpty()) {
            0.0
        } else {
            val intersection = prevContentIds.intersect(currContentIds)
            val union = prevContentIds.union(currContentIds)
            if (union.isEmpty()) 1.0 else intersection.size.toDouble() / union.size
        }

        Log.i("SC_YT_ADVANCE",
            "SC_YT_ADVANCE pkg=$packageNames " +
                "prevFinger=${prevFingerprint.take(5)} currFinger=${currFingerprint.take(5)} " +
                "prevSize=${prevFingerprint.size} currSize=${currFingerprint.size} " +
                "prevContentIds=${prevContentIds.size} currContentIds=${currContentIds.size} " +
                "prevOnShorts=$previousOnShorts currOnShorts=$currentOnShorts " +
                "overlap=${"%.2f".format(overlap)} contentChanged=$contentChanged " +
                "structuralChanged=$structuralChanged newContentAppear=$newContentAppear"
        )

        if (contentChanged) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames decision=ADVANCE " +
                    "reason=CONTENT_ID_CHANGED prevContent=${prevContentIds.take(3)} " +
                    "currContent=${currContentIds.take(3)} overlap=${"%.2f".format(overlap)}"
            )
            return true
        }

        if (structuralChanged) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames decision=ADVANCE " +
                    "reason=STRUCTURE_CHANGED prevFinger=${prevFingerprint.take(3)} " +
                    "currFinger=${currFingerprint.take(3)}"
            )
            return true
        }

        if (newContentAppear) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames decision=ADVANCE " +
                    "reason=NEW_CONTENT_APPEARED newContent=${newContentDescriptions.take(3)}"
            )
            return true
        }

        // ===== No advance detected =====
        // Content identity is identical AND no structural change AND no new
        // content descriptions → same Short with UI updates (overlay
        // open/close, ad, rendering refresh, control state change).
        Log.i("SC_YT_ADVANCE",
            "SC_YT_ADVANCE pkg=$packageNames decision=REJECT " +
                "reason=SAME_SHORT prevFinger=${prevFingerprint.take(3)} " +
                "currFinger=${currFingerprint.take(3)} overlap=${"%.2f".format(overlap)} " +
                "structuralChanged=$structuralChanged newContentAppear=$newContentAppear"
        )
        return false
    }

    // =========================================================================
    // YouTube-specific helpers
    // =========================================================================

    /**
     * Check whether the evidence shows a genuine YouTube overlay (comments
     * panel, share sheet, menu) — NOT the Shorts player's own controls.
     *
     * The Shorts player always has buttons like "like", "share", "comment",
     * "subscribe" in its accessibility tree. These are part of the player
     * and must NOT be treated as transient UI.
     *
     * True overlays add ADDITIONAL signals:
     *  - Comments panel: "add a comment", "reply", "replies", comment input IDs
     *  - Share sheet: "send to", "share this", share sheet IDs
     *  - Menu: "more actions", "more options", popup/dialog class names
     *
     * Detection requires BOTH a description/ID signal AND a structural signal
     * (overlay class name or overlay container ID) to reduce false positives.
     */
    private fun hasTransientOverlay(evidence: WindowContentEvidence): Boolean {
        // Check for overlay-specific content descriptions
        for (desc in evidence.nodeContentDescriptions) {
            val lower = desc.lowercase()
            if (YT_OVERLAY_DESC_KEYWORDS.any { lower.contains(it) }) {
                return true
            }
        }

        // Check for overlay container view IDs
        for (id in evidence.nodeViewIds) {
            val lower = id.lowercase()
            if (YT_OVERLAY_VIEW_ID_KEYWORDS.any { lower.contains(it) }) {
                return true
            }
        }

        // Check for overlay widget class names (BottomSheet, Dialog, Popup, etc.)
        for (cls in evidence.nodeClasses) {
            val lower = cls.lowercase()
            if (YT_OVERLAY_CLASS_KEYWORDS.any { lower.contains(it.lowercase()) }) {
                return true
            }
        }

        return false
    }

    /**
     * Content description keywords that indicate a YouTube OVERLAY is open.
     * These are distinct from the Shorts player's own button labels.
     *
     * The Shorts player has: "like", "share", "comment", "subscribe"
     * (always present, NOT overlays).
     *
     * Overlays add: "add a comment" (input field), "reply"/"replies" (thread),
     * "send to" (share sheet), "more actions"/"more options" (menu).
     */
    private val YT_OVERLAY_DESC_KEYWORDS = setOf(
        // Comments panel (input field + thread indicators)
        "add a comment", "write a comment",
        "reply", "replies",
        "comments section", "view comments",
        // Share sheet
        "send to", "share this",
        // Menu / more actions
        "more actions", "more options",
        // Description panel
        "show more", "show less",
        // Keyboard / input
        "keyboard", "text input", "type a message", "compose",
    )

    /**
     * Check whether the evidence contains a Shorts player signal.
     *
     * Used to confirm we're on the Shorts player (not Home, Watch, Search)
     * before comparing fingerprints. Shorts-unique labels ("Remix this Short",
     * "See more videos using this sound") are the most reliable signal.
     * Structural player IDs provide a secondary check.
     */
    private fun hasShortsPlayerSignal(evidence: WindowContentEvidence): Boolean {
        // Shorts-unique content descriptions (most reliable)
        if (evidence.nodeContentDescriptions.any { it.isShortsContentDescription() }) return true
        // Known Shorts player view IDs
        if (evidence.nodeViewIds.any { id ->
                val lower = id.lowercase()
                YT_SHORTS_PLAYER_VIEW_IDS.any { lower.contains(it) }
            }) return true
        // Shorts-specific structural classes (reel/shorts keyword in player-like class)
        if (evidence.nodeClasses.any { cls ->
                val lower = cls.lowercase()
                YT_SHORTS_PLAYER_CLASS_KEYWORDS.any { lower.contains(it) } &&
                    (lower.contains("player") || lower.contains("pager") ||
                        lower.contains("recycler") || lower.contains("container") ||
                        lower.contains("fragment"))
            }) return true
        return false
    }

    /**
     * Check for structural differences between previous and current evidence
     * that indicate a genuine Short-to-Short transition.
     *
     * A genuine swipe changes the Shorts player's internal node structure
     * (new container views, different resource IDs) even when the visible
     * text is similar. This is the fallback for cases where content
     * descriptions are too sparse or identical (same creator, similar captions).
     *
     * Structural delta detection:
     *  - New Shorts player view IDs (e.g., new pager/container)
     *  - Changed Shorts player class names (e.g., new player container)
     *  - Net change in Shorts-specific structural nodes
     *
     * Generic Android widget classes (FrameLayout, LinearLayout, etc.) are
     * excluded — they change with any UI update, not just Short transitions.
     */
    private fun hasStructuralDelta(
        previous: WindowContentEvidence,
        current: WindowContentEvidence,
    ): Boolean {
        // --- Shorts player view ID delta ---
        val prevPlayerIds = previous.nodeViewIds.filter { id ->
            val lower = id.lowercase()
            YT_SHORTS_PLAYER_VIEW_IDS.any { lower.contains(it) }
        }.toSet()
        val currPlayerIds = current.nodeViewIds.filter { id ->
            val lower = id.lowercase()
            YT_SHORTS_PLAYER_VIEW_IDS.any { lower.contains(it) }
        }.toSet()
        val newPlayerIds = currPlayerIds - prevPlayerIds
        if (newPlayerIds.isNotEmpty()) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames structuralDelta=NEW_PLAYER_IDS " +
                    "new=${newPlayerIds.take(5)}"
            )
            return true
        }

        // --- Shorts player class name delta ---
        val prevPlayerClasses = previous.nodeClasses.filter { cls ->
            val lower = cls.lowercase()
            YT_SHORTS_PLAYER_CLASS_KEYWORDS.any { lower.contains(it) }
        }.toSet()
        val currPlayerClasses = current.nodeClasses.filter { cls ->
            val lower = cls.lowercase()
            YT_SHORTS_PLAYER_CLASS_KEYWORDS.any { lower.contains(it) }
        }.toSet()
        val newPlayerClasses = currPlayerClasses - prevPlayerClasses
        if (newPlayerClasses.isNotEmpty()) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames structuralDelta=NEW_PLAYER_CLASSES " +
                    "new=${newPlayerClasses.take(5)}"
            )
            return true
        }

        // --- Shorts-specific node count net change ---
        val prevStructuralCount = previous.nodeClasses.count { cls ->
            val lower = cls.lowercase()
            YT_SHORTS_PLAYER_CLASS_KEYWORDS.any { lower.contains(it) }
        }
        val currStructuralCount = current.nodeClasses.count { cls ->
            val lower = cls.lowercase()
            YT_SHORTS_PLAYER_CLASS_KEYWORDS.any { lower.contains(it) }
        }
        val structuralNetChange = kotlin.math.abs(currStructuralCount - prevStructuralCount)
        if (structuralNetChange > 2) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames structuralDelta=NET_CHANGE " +
                    "prev=$prevStructuralCount curr=$currStructuralCount delta=$structuralNetChange"
            )
            return true
        }

        // --- Content identity description count delta ---
        // Count content identity descriptions (excluding Shorts player buttons,
        // generic UI controls, and Shorts-unique labels). A significant change
        // in this count indicates a new Short loaded with different content.
        val prevContentCount = countContentIdentityDescriptions(previous)
        val currContentCount = countContentIdentityDescriptions(current)
        val contentCountDelta = kotlin.math.abs(currContentCount - prevContentCount)
        if (contentCountDelta > 2) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames structuralDelta=CONTENT_COUNT " +
                    "prev=$prevContentCount curr=$currContentCount delta=$contentCountDelta"
            )
            return true
        }

        // --- Shorts player node count delta ---
        // Count total Shorts-related nodes (player + Shorts-unique labels).
        // A significant change indicates structural content replacement.
        val prevShortsNodeCount = countShortsNodes(previous)
        val currShortsNodeCount = countShortsNodes(current)
        val shortsNodeDelta = kotlin.math.abs(currShortsNodeCount - prevShortsNodeCount)
        if (shortsNodeDelta > 3) {
            Log.i("SC_YT_ADVANCE",
                "SC_YT_ADVANCE pkg=$packageNames structuralDelta=SHORTS_NODES " +
                    "prev=$prevShortsNodeCount curr=$currShortsNodeCount delta=$shortsNodeDelta"
            )
            return true
        }

        return false
    }

    /**
     * Count content identity descriptions — content descriptions that are
     * NOT Shorts player buttons, NOT generic UI controls, NOT Shorts-unique
     * labels, and are >= 8 chars. These represent the Short's actual content
     * (creator, caption, etc.).
     */
    private fun countContentIdentityDescriptions(evidence: WindowContentEvidence): Int {
        return evidence.nodeContentDescriptions.count { desc ->
            val lower = desc.lowercase().trim()
            !lower.isShortsPlayerButton() &&
                UI_STATE_DESCRIPTIONS.none { lower.contains(it) } &&
                !isShortsSpecificDescription(lower) &&
                lower.length >= 8
        }
    }

    /**
     * Count Shorts-related nodes: Shorts player structural classes +
     * Shorts-unique labels. A significant change indicates structural
     * content replacement (new Short loaded).
     */
    private fun countShortsNodes(evidence: WindowContentEvidence): Int {
        val playerClassCount = evidence.nodeClasses.count { cls ->
            val lower = cls.lowercase()
            YT_SHORTS_PLAYER_CLASS_KEYWORDS.any { lower.contains(it) }
        }
        val shortsLabelCount = evidence.nodeContentDescriptions.count { desc ->
            val lower = desc.lowercase().trim()
            isShortsSpecificDescription(lower)
        }
        return playerClassCount + shortsLabelCount
    }

    /**
     * Extract a SHORT IDENTITY fingerprint from content evidence.
     *
     * This intentionally excludes:
     *  - UI-state labels (Comments, Like, Share, Save, Reply, Subscribe, etc.)
     *    that change with overlays, not with Short identity.
     *  - Short/generic labels (< 8 chars) that are likely UI controls.
     *
     * Retains:
     *  - Content-specific descriptions (creator name, caption, video title)
     *    that change when Short A → Short B.
     *  - Shorts-unique labels ("Remix this Short", "See more videos using
     *    this sound") that confirm the Shorts player is present and provide
     *    structural identity.
     *  - Shorts player structural view IDs that provide additional identity
     *    signals and help detect structural transitions.
     *
     * Including Shorts-unique labels in the fingerprint serves two purposes:
     *  1. Confirms the Shorts player is present (safety signal).
     *  2. Provides stable structural identity — when the Shorts player is
     *     replaced by an overlay (comments/share), these labels disappear
     *     from the fingerprint, reducing overlap and signaling a state change.
     */
    private fun identityFingerprint(evidence: WindowContentEvidence): Set<String> {
        val result = mutableSetOf<String>()

        // Content identity descriptions: creator name, caption, video title,
        // and any other text that identifies WHAT Short is playing.
        // Exclude:
        //  - Shorts player button labels (like/share/comment/subscribe — static UI)
        //  - Generic UI controls (play/pause/mute/fullscreen/etc.)
        //  - Shorts-unique labels ("Remix this Short" — present on ALL Shorts)
        //  - Short descriptions (< 8 chars) that are likely UI controls
        for (desc in evidence.nodeContentDescriptions) {
            val lower = desc.lowercase().trim()
            if (lower.isShortsPlayerButton()) continue
            if (UI_STATE_DESCRIPTIONS.any { lower.contains(it) }) continue
            if (isShortsSpecificDescription(lower)) continue
            if (lower.length >= 8) result.add("content:$lower")
        }

        // Shorts-unique labels: confirm Shorts player presence.
        // These are structural signals (present on ALL Shorts), NOT content
        // identity. Included in the fingerprint for structural confirmation
        // but excluded from content overlap calculation.
        for (desc in evidence.nodeContentDescriptions) {
            val lower = desc.lowercase().trim()
            if (SHORTS_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }) {
                result.add("yt_shorts:$lower")
            }
        }

        // Shorts player structural view IDs: present on ALL Shorts.
        // Structural signal, not content identity.
        for (id in evidence.nodeViewIds) {
            val lower = id.lowercase().trim()
            if (YT_SHORTS_PLAYER_VIEW_IDS.any { lower.contains(it) }) {
                result.add("yt_id:$lower")
            }
        }

        return result
    }

    /**
     * Check if a lowercased content description is a Shorts-unique label
     * ("Remix this Short", "See more videos using this sound").
     * These appear on ALL Shorts and are NOT content identity.
     */
    private fun isShortsSpecificDescription(lower: String): Boolean {
        return SHORTS_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
    }

}
