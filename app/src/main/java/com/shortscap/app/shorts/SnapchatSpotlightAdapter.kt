package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * Snapchat Spotlight adapter.
 *
 * Spotlight is one surface among many in Snapchat (chat, camera, stories,
 * maps, Discover). Stories are NOT counted as short-form unless the product
 * requirements explicitly define them as countable — currently they are NOT.
 *
 * Detection tiers:
 *  1. Activity class match — class contains "spotlight", confidence 0.80.
 *  2. Structural content evidence — Spotlight-specific node classes/IDs,
 *     confidence 0.70.
 *  3. Content description text signals — Spotlight-unique labels,
 *     confidence 0.60.
 *
 * Stories are explicitly REJECTED when detected (surface = SNAPCHAT_STORIES,
 * isShortForm = false).
 *
 * IMPORTANT: scroll interaction is NOT used for Snapchat detection because
 * the home screen, Stories, Discover and Chat all scroll. Generic classes
 * like MainActivity are NOT treated as Spotlight evidence.
 */
object SnapchatSpotlightAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.SNAPCHAT
    override val packageNames: Set<String> = setOf("com.snapchat.android")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_INPUT pkg=${signals.packageName} cls=$className " +
                "interactionCount=${signals.interactionCount} " +
                "evidenceClasses=${signals.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${signals.contentEvidence.nodeViewIds.size} " +
                "descs=${signals.contentEvidence.nodeContentDescriptions.size}"
        )

        // Tier 1: Spotlight-specific activity class
        if (className != null && className.lowercase().contains("spotlight")) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=ACTIVITY_CLASS surface=SNAPCHAT_SPOTLIGHT confidence=0.80"
            )
            return spotlightResult(confidence = 0.80f, surfaceSignal = "activity_class")
        }

        // Check if this is a Stories surface (reject)
        if (className != null && className.lowercase().contains("story")) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=STORIES_REJECTED surface=SNAPCHAT_STORIES confidence=0.10"
            )
            return unconfirmedResult(ShortPlatform.SNAPCHAT, 0.10f)
        }

        // NOTE: generic classes like MainActivity, Feed, Discover are NOT
        // evidence of Spotlight. The Snapchat home screen, Stories, and
        // Discover all scroll, so scroll interaction alone is also NOT evidence.
        // Only actual Spotlight-specific signals (Tiers 3, 4) confirm the surface.

        // Tier 3: structural content evidence
        val result = evaluateContentEvidence(signals.contentEvidence)
        if (result != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=CONTENT_EVIDENCE surface=SNAPCHAT_SPOTLIGHT confidence=${result.confidence}"
            )
            return result
        }

        // Tier 4: content description text signals
        val textResult = evaluateTextEvidence(signals.contentEvidence)
        if (textResult != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=TEXT_EVIDENCE surface=SNAPCHAT_SPOTLIGHT confidence=${textResult.confidence}"
            )
            return textResult
        }

        // NO scroll fallback for Snapchat — the home screen, Stories,
        // Discover, and Chat all scroll. Scroll alone is NOT evidence of
        // Spotlight. Only explicit Spotlight-specific signals (Tiers 1, 3, 4)
        // confirm the surface.

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                "result=UNCONFIRMED isShortForm=false confidence=0.15 reason=NO_SPOTLIGHT_SIGNALS"
        )
        return unconfirmedResult(ShortPlatform.SNAPCHAT, 0.15f)
    }

    private fun spotlightResult(confidence: Float, surfaceSignal: String): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.SNAPCHAT,
            surface = ShortSurface.SNAPCHAT_SPOTLIGHT,
            isShortForm = true,
            confidence = confidence,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to surfaceSignal),
        )

    /** Structural evidence: Spotlight-specific node classes/IDs. */
    private fun evaluateContentEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val classHits = evidence.nodeClasses.count { it.lowercase().let {
            it.contains("spotlight") || (it.contains("reel") && !it.contains("story"))
        }}
        val idHits = evidence.nodeViewIds.count { it.lowercase().let {
            it.contains("spotlight") || it.contains("reel")
        }}
        if (classHits == 0 && idHits == 0) return null
        return ShortDetectionResult(
            platform = ShortPlatform.SNAPCHAT,
            surface = ShortSurface.SNAPCHAT_SPOTLIGHT,
            isShortForm = true,
            confidence = 0.70f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf(
                "surfaceSignal" to "content_evidence",
                "spotlightClassHits" to classHits,
                "spotlightIdHits" to idHits,
            ),
        )
    }

    /** Text signals unique to Snapchat Spotlight (not Stories). */
    private fun evaluateTextEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val textHit = evidence.nodeContentDescriptions.any { desc ->
            val lower = desc.lowercase()
            SPOTLIGHT_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
        }
        if (!textHit) return null
        return ShortDetectionResult(
            platform = ShortPlatform.SNAPCHAT,
            surface = ShortSurface.SNAPCHAT_SPOTLIGHT,
            isShortForm = true,
            confidence = 0.60f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to "text_evidence"),
        )
    }

    // ---- Genuine user advance detection ----

    /**
     * Snapchat does not generate `TYPE_VIEW_SCROLLED` (custom ViewPager).
     * We detect genuine advances using two complementary signals:
     *
     *  1. Shorts-specific structural class comparison (existing).
     *     During normal playback these classes are stable. A genuine swipe
     *     can shift the view hierarchy, but identical classes between Shorts
     *     are common — so this signal alone is insufficient.
     *
     *  2. Content description fingerprint (new). Creator/channel text,
     *     caption, and unique content descriptions change between Shorts.
     *     Static UI labels (like, share, comment, navigation) are excluded.
     *
     * Advance is detected when EITHER signal shows a meaningful change.
     * The pipeline's 3-second qualification + 500ms scroll debounce provide
     * additional defense against false positives.
     */
    override fun detectUserAdvance(
        previousEvidence: WindowContentEvidence,
        currentEvidence: WindowContentEvidence,
    ): Boolean {
        // --- Signal 1: Shorts-specific class comparison (existing) ---
        val prevShorts = previousEvidence.nodeClasses.filter { isShortsSpecific(it) }.toSet()
        val currShorts = currentEvidence.nodeClasses.filter { isShortsSpecific(it) }.toSet()
        val classChanged = prevShorts != currShorts

        // --- Signal 2: Content description fingerprint (new) ---
        val prevContent = contentFingerprint(previousEvidence.nodeContentDescriptions)
        val currContent = contentFingerprint(currentEvidence.nodeContentDescriptions)
        val contentChanged = prevContent != currContent

        val advance = classChanged || contentChanged

        Log.i("SC_SNAP_ADVANCE",
            "SC_SNAP_ADVANCE prevClassFingerprint=${prevShorts.size} " +
                "currClassFingerprint=${currShorts.size} classChanged=$classChanged " +
                "prevContentFingerprint=${prevContent.size} " +
                "currContentFingerprint=${currContent.size} contentChanged=$contentChanged " +
                "advance=$advance",
        )

        if (advance) {
            Log.i("SC_INTERACTION",
                "SC_INTERACTION USER_ADVANCE pkg=$packageNames platform=SNAPCHAT " +
                    "source=SNAPCHAT_CONTENT_FINGERPRINT classChanged=$classChanged " +
                    "contentChanged=$contentChanged",
            )
        } else {
            Log.i("SC_INTERACTION",
                "SC_INTERACTION USER_ADVANCE_REJECTED pkg=$packageNames platform=SNAPCHAT " +
                    "reason=SAME_CONTENT",
            )
        }

        return advance
    }

    /**
     * Builds a content fingerprint from accessibility content descriptions,
     * excluding static UI labels, navigation items, and transient overlay
     * text. Only meaningful content-specific descriptions survive.
     *
     * Filters out:
     *  - Descriptions shorter than 4 characters (button labels, icons)
     *  - Static Snapchat navigation: map, chat, camera, stories, spotlight, search
     *  - Generic UI actions: like, share, comment, follow, subscribe, etc.
     *  - Overlay/chrome: back, close, send, save, more, dismiss
     */
    private fun contentFingerprint(descriptions: List<String>): Set<String> {
        return descriptions
            .filter { it.length >= MIN_CONTENT_DESC_LENGTH }
            .map { it.lowercase().trim() }
            .filter { desc ->
                STATIC_LABELS.none { label -> desc == label || desc.contains(label) }
            }
            .toSet()
    }

    /** Class is Shorts/Spotlight-specific (not a generic Android widget). */
    private fun isShortsSpecific(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("spotlight") || lower.contains("reel") ||
            lower.contains("shorts") || lower.contains("player")
    }

    /** Content descriptions unique to Spotlight (never on Stories/Chat/Camera). */
    private val SPOTLIGHT_UNIQUE_DESCRIPTIONS = setOf(
        "spotlight",
        "send to spotlight",
    )

    /** Minimum content description length to be considered meaningful. */
    private const val MIN_CONTENT_DESC_LENGTH = 4

    /**
     * Static UI labels to exclude from content fingerprint.
     * These appear across all Shorts and do not identify specific content.
     */
    private val STATIC_LABELS = setOf(
        // Snapchat navigation
        "map", "chat", "camera", "stories", "spotlight", "search",
        "add friends", "story sent", "discover",
        // Generic UI actions
        "like", "share", "comment", "comments", "reply", "replies",
        "follow", "subscribe", "subscribed",
        "more", "back", "close", "send", "save", "dismiss",
        "bookmark", "saved", "unlike", "dislike",
        // Player controls
        "play", "pause", "mute", "unmute",
        "fullscreen", "exit fullscreen",
        // Overlays / chrome
        "description", "expand", "collapse",
        "settings", "autoplay",
        "keyboard", "text input", "type a message",
        "compose", "write a comment",
        "bottom sheet", "dialog", "popup", "modal",
    )
}
