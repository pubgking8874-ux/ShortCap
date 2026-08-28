package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * ShortSurfaceClassifier — common accessibility UI classification gate.
 *
 * Classifies the current accessibility evidence into one of:
 *   PRIMARY_SHORT_PLAYER  — the main Short/Reel/Spotlight player surface
 *   TRANSIENT_UI          — comments, share sheet, menu, popup, or other overlay
 *   NON_SHORT_SURFACE     — confirmed non-Short surface
 *   UNKNOWN                — insufficient evidence to classify
 *
 * Purpose: prevent false Short counts caused by transient UI interactions
 * (opening comments, share sheet, etc.) being mistaken for Short-to-Short
 * advances.
 *
 * TRANSIENT_UI OVERRIDES weak PRIMARY_SHORT_PLAYER evidence.
 * Opening comments/share/menu must NOT create a new Short session.
 *
 * All platforms share this single classifier — no per-platform counting engines.
 */
object ShortSurfaceClassifier {

    private const val TAG = "SC_SURFACE_CLASSIFY"

    // ---- Transient UI detection signals ----

    /**
     * Content description keywords that indicate transient UI overlays.
     * These are UI control labels, NOT Short identity signals.
     * A match on any of these (case-insensitive) in the evidence indicates
     * a transient UI surface.
     *
     * Must be conservative: only label patterns that are definitively
     * transient UI, never Shorts player content.
     */
    private val TRANSIENT_DESC_KEYWORDS = setOf(
        // Comments / replies
        "comment", "reply", "replies", "add a comment",
        "comments section", "view comments",
        // Engagement actions
        "like count", "unlike", "dislike",
        "save", "bookmark", "saved",
        "share", "share this", "send to",
        // Navigation / menus
        "subscribe", "subscribed",
        "more actions", "more options", "show more", "show less",
        "description", "expand", "collapse",
        // Player controls
        "play", "pause", "mute", "unmute",
        "fullscreen", "exit fullscreen",
        "captions", "subtitles", "quality", "speed",
        "settings", "autoplay",
        // Keyboard / input
        "keyboard", "text input", "type a message",
        "compose", "write a comment",
        // Bottom sheet / dialog / popup indicators
        "bottom sheet", "dialog", "popup", "modal",
        "close", "dismiss",
    )

    /**
     * View ID resource name patterns that indicate transient UI containers.
     * These are Android resource IDs commonly used for overlays, sheets, and dialogs.
     */
    private val TRANSIENT_ID_PATTERNS = setOf(
        "comment", "reply", "replies",
        "share", "share_sheet",
        "bottom_sheet", "bottom_dialog",
        "popup", "dialog", "modal", "overlay_menu",
        "menu", "more_menu", "action_sheet",
        "reaction", "emoji_picker",
        "keyboard", "input", "composer", "text_entry",
        "like_button", "save_button", "bookmark",
        "player_control", "media_control",
        "description_panel", "info_panel",
    )

    /**
     * Accessibility node class name patterns that indicate transient UI containers.
     * These are Android widget class names commonly used for overlays.
     */
    private val TRANSIENT_CLASS_KEYWORDS = setOf(
        "BottomSheet",
        "Dialog",
        "Popup",
        "Modal",
        "Snackbar",
        "Tooltip",
        "ContextMenu",
        "PopupMenu",
        "AlertDialog",
    )

    // ---- Primary Short Player detection signals ----

    /**
     * Content description keywords that are UNIQUE to Shorts player surfaces.
     * A match indicates PRIMARY_SHORT_PLAYER (with structural evidence).
     */
    private val PLAYER_DESC_KEYWORDS = setOf(
        "remix this short",
        "see more videos using this sound",
    )

    /**
     * Node class keywords that indicate a Shorts player container.
     */
    private val PLAYER_CLASS_KEYWORDS = setOf(
        "shorts", "reel",
    )

    /**
     * View ID keywords that indicate a Shorts player container.
     */
    private val PLAYER_ID_KEYWORDS = setOf(
        "reel_watch_fragment_root",
        "reel_recycler",
        "reel_player_page_container",
        "shorts_video_pager",
        "shorts_player",
        "reel_player",
    )

    /**
     * The result of classifying accessibility evidence into a surface role.
     *
     * @property role The classified surface role
     * @property allowMonitoring Whether monitoring/counting is allowed
     * @property allowAdvanceDetection Whether advance detection (scroll/fingerprint) is allowed
     * @property reason Human-readable classification reason
     */
    data class SurfaceDecision(
        val role: SurfaceRole,
        val allowMonitoring: Boolean,
        val allowAdvanceDetection: Boolean,
        val reason: String,
    )

    /**
     * Surface role classification.
     */
    enum class SurfaceRole {
        /** Main Short/Reel/Spotlight player surface — eligible for monitoring/advance. */
        PRIMARY_SHORT_PLAYER,
        /** Transient UI overlay — comments, share, menu, popup, etc. */
        TRANSIENT_UI,
        /** Confirmed non-Short surface. */
        NON_SHORT_SURFACE,
        /** Insufficient evidence to classify. */
        UNKNOWN,
    }

    /**
     * Classify the current accessibility evidence into a surface role.
     *
     * Uses combined structural evidence (classes, IDs, descriptions) to
     * determine whether the foreground is a Short player or a transient UI.
     *
     * TRANSIENT_UI OVERRIDES weak PRIMARY_SHORT_PLAYER evidence:
     *   If comments are open on a Short player, the result is TRANSIENT_UI,
     *   not PRIMARY_SHORT_PLAYER.
     *
     * @param packageName The foreground app package
     * @param evidence The current content evidence snapshot
     * @param platformDetectionResult The platform adapter's detection result (may be UNKNOWN)
     * @param sessionInProgress Whether a Short session is currently active
     * @return SurfaceDecision with role, monitoring/advance flags, and reason
     */
    fun classify(
        packageName: String,
        evidence: WindowContentEvidence,
        platformDetectionResult: ShortDetectionResult = ShortDetectionResult.UNKNOWN,
        sessionInProgress: Boolean = false,
    ): SurfaceDecision {
        val allDescs = evidence.nodeContentDescriptions.map { it.lowercase() }
        val allIds = evidence.nodeViewIds.map { it.lowercase() }
        val allClasses = evidence.nodeClasses.map { it.lowercase() }

        // ---- Step 1: Check for TRANSIENT_UI signals ----
        val transientReason = detectTransientUI(allDescs, allIds, allClasses)
        if (transientReason != null) {
            val decision = SurfaceDecision(
                role = SurfaceRole.TRANSIENT_UI,
                allowMonitoring = true,  // keep monitoring alive
                allowAdvanceDetection = false,  // suppress advance detection
                reason = transientReason,
            )
            Log.i(TAG,
                "SC_SURFACE_CLASSIFY pkg=$packageName role=TRANSIENT_UI " +
                    "reason=$transientReason descs=${evidence.nodeContentDescriptions.size} " +
                    "ids=${evidence.nodeViewIds.size} classes=${evidence.nodeClasses.size}",
            )
            return decision
        }

        // ---- Step 2: Check for PRIMARY_SHORT_PLAYER signals ----
        val playerReason = detectPrimaryPlayer(
            allDescs, allIds, allClasses,
            platformDetectionResult, sessionInProgress,
        )
        if (playerReason != null) {
            val decision = SurfaceDecision(
                role = SurfaceRole.PRIMARY_SHORT_PLAYER,
                allowMonitoring = true,
                allowAdvanceDetection = true,
                reason = playerReason,
            )
            Log.i(TAG,
                "SC_SURFACE_CLASSIFY pkg=$packageName role=PRIMARY_SHORT_PLAYER " +
                    "reason=$playerReason descs=${evidence.nodeContentDescriptions.size} " +
                    "ids=${evidence.nodeViewIds.size} classes=${evidence.nodeClasses.size}",
            )
            return decision
        }

        // ---- Step 3: No transient UI and no player evidence ----
        // If we have a session in progress and the platform adapter confirms
        // Shorts, treat as PRIMARY_SHORT_PLAYER (session continuity).
        if (sessionInProgress && platformDetectionResult.isShortForm &&
            platformDetectionResult.confidence >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD
        ) {
            val decision = SurfaceDecision(
                role = SurfaceRole.PRIMARY_SHORT_PLAYER,
                allowMonitoring = true,
                allowAdvanceDetection = true,
                reason = "SESSION_CONTINUITY platform=${platformDetectionResult.platform}",
            )
            Log.i(TAG,
                "SC_SURFACE_CLASSIFY pkg=$packageName role=PRIMARY_SHORT_PLAYER " +
                    "reason=SESSION_CONTINUITY platform=${platformDetectionResult.platform} " +
                    "confidence=${platformDetectionResult.confidence}",
            )
            return decision
        }

        // ---- Step 4: Fallback — UNKNOWN ----
        val decision = SurfaceDecision(
            role = SurfaceRole.UNKNOWN,
            allowMonitoring = false,
            allowAdvanceDetection = false,
            reason = "NO_SIGNALS descs=${evidence.nodeContentDescriptions.size} " +
                "ids=${evidence.nodeViewIds.size} classes=${evidence.nodeClasses.size}",
        )
        Log.i(TAG,
            "SC_SURFACE_CLASSIFY pkg=$packageName role=UNKNOWN " +
                "descs=${evidence.nodeContentDescriptions.size} " +
                "ids=${evidence.nodeViewIds.size} classes=${evidence.nodeClasses.size}",
        )
        return decision
    }

    /**
     * Convenience overload: classify using raw event parameters.
     * Used when full WindowContentEvidence is not yet assembled.
     */
    @Suppress("UNUSED_PARAMETER")
    fun classifySimple(
        packageName: String,
        @Suppress("UNUSED_PARAMETER") eventType: Int? = null,
        className: String? = null,
        viewId: String? = null,
        contentDescription: String? = null,
        @Suppress("UNUSED_PARAMETER") text: String? = null,
        sessionInProgress: Boolean = false,
    ): SurfaceDecision {
        val descLower = contentDescription?.lowercase() ?: ""
        val idLower = viewId?.lowercase() ?: ""
        val classLower = className?.lowercase() ?: ""

        // Quick transient UI check on raw event
        val isTransient = TRANSIENT_DESC_KEYWORDS.any { descLower.contains(it) } ||
            TRANSIENT_ID_PATTERNS.any { idLower.contains(it) } ||
            TRANSIENT_CLASS_KEYWORDS.any { classLower.contains(it.lowercase()) }

        if (isTransient) {
            return SurfaceDecision(
                role = SurfaceRole.TRANSIENT_UI,
                allowMonitoring = true,
                allowAdvanceDetection = false,
                reason = "RAW_EVENT_TRANSIENT pkg=$packageName desc=$descLower id=$idLower cls=$classLower",
            )
        }

        return SurfaceDecision(
            role = if (sessionInProgress) SurfaceRole.PRIMARY_SHORT_PLAYER else SurfaceRole.UNKNOWN,
            allowMonitoring = sessionInProgress,
            allowAdvanceDetection = false,  // can't determine from raw event alone
            reason = "RAW_EVENT_NO_MATCH pkg=$packageName",
        )
    }

    // ---- Internal detection helpers ----

    /**
     * Detect transient UI indicators in the evidence.
     * Returns a reason string if transient UI is detected, null otherwise.
     */
    private fun detectTransientUI(
        descs: List<String>,
        ids: List<String>,
        classes: List<String>,
    ): String? {
        // Check content descriptions for transient UI keywords
        for (desc in descs) {
            for (keyword in TRANSIENT_DESC_KEYWORDS) {
                if (desc.contains(keyword)) {
                    return "COMMENTS_OR_OVERLAY desc_match=$keyword"
                }
            }
        }

        // Check view IDs for transient UI container patterns
        for (id in ids) {
            for (pattern in TRANSIENT_ID_PATTERNS) {
                if (id.contains(pattern)) {
                    return "TRANSIENT_CONTAINER id_match=$pattern"
                }
            }
        }

        // Check class names for transient UI container widgets
        for (cls in classes) {
            for (keyword in TRANSIENT_CLASS_KEYWORDS) {
                if (cls.contains(keyword.lowercase())) {
                    return "TRANSIENT_WIDGET class_match=$keyword"
                }
            }
        }

        return null
    }

    /**
     * Detect primary Short player signals in the evidence.
     * Returns a reason string if a primary player is detected, null otherwise.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun detectPrimaryPlayer(
        descs: List<String>,
        ids: List<String>,
        classes: List<String>,
        @Suppress("UNUSED_PARAMETER") platformResult: ShortDetectionResult,
        @Suppress("UNUSED_PARAMETER") sessionInProgress: Boolean,
    ): String? {
        // Check content descriptions for Shorts-unique labels
        for (desc in descs) {
            for (keyword in PLAYER_DESC_KEYWORDS) {
                if (desc.contains(keyword)) {
                    return "SHORTS_PLAYER desc_match=$keyword"
                }
            }
        }

        // Check view IDs for known player containers
        for (id in ids) {
            for (keyword in PLAYER_ID_KEYWORDS) {
                if (id.contains(keyword)) {
                    return "SHORTS_PLAYER id_match=$keyword"
                }
            }
        }

        // Check class names for Shorts player indicators (need structural match)
        var hasShortsClass = false
        var hasPlayerClass = false
        for (cls in classes) {
            if (PLAYER_CLASS_KEYWORDS.any { cls.contains(it) }) {
                hasShortsClass = true
            }
            if (cls.contains("player") || cls.contains("pager") ||
                cls.contains("recycler") || cls.contains("fragment")
            ) {
                hasPlayerClass = true
            }
        }
        if (hasShortsClass && hasPlayerClass) {
            return "SHORTS_PLAYER structural_class_match"
        }

        return null
    }
}
