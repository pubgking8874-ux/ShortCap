package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * Instagram Reels adapter.
 *
 * Reels is ONE surface inside the main Instagram app (Feed, Stories, DM,
 * Explore, Live). Detection tiers:
 *
 *  1. Activity class match — specific Reels-hosting activity classes,
 *     confidence 0.80.
 *  2. Shorts/Reels keyword in activity class — any class name containing
 *     "reel" (case-insensitive), confidence 0.65.
 *  3. Structural content evidence — node class or resource id contains
 *     "reel" (case-insensitive) AND is a player/container (not a shelf
 *     or tab), confidence 0.70.
 *  4. Content description text signals — Shorts-unique labels like
 *     "Reel", confidence 0.60.
 *  5. Scroll interaction fallback — for builds where no Reels-specific
 *     class/ID/text is exposed but the user is scrolling a vertical feed,
 *     confidence from scrollInteractionConfidence().
 *
 * Without any of these signals → UNKNOWN / low confidence (never counted).
 */
object InstagramReelsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.INSTAGRAM
    override val packageNames: Set<String> = setOf("com.instagram.android")

    /** Known Reels-hosting activity class names (from device evidence). */
    private val reelsActivityClasses = setOf(
        "com.instagram.mainactivity.MainTabActivity",
        "com.instagram.reels.tab.ReelsTabActivity",
    )

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_INPUT pkg=${signals.packageName} cls=$className " +
                "interactionCount=${signals.interactionCount} " +
                "evidenceClasses=${signals.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${signals.contentEvidence.nodeViewIds.size} " +
                "descs=${signals.contentEvidence.nodeContentDescriptions.size}"
        )

        // Tier 1: known Reels activity class
        if (className != null && className in reelsActivityClasses) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=ACTIVITY_CLASS surface=INSTAGRAM_REELS confidence=0.80"
            )
            return shortsResult(confidence = 0.80f, surfaceSignal = "activity_class")
        }

        // Tier 2: activity class contains "reel"
        if (className != null && className.lowercase().contains("reel")) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=REEL_KEYWORD surface=INSTAGRAM_REELS confidence=0.65"
            )
            return shortsResult(confidence = 0.65f, surfaceSignal = "reel_keyword_in_class")
        }

        // Tier 3: structural content evidence (node class or ID contains "reel")
        val result = evaluateContentEvidence(signals.contentEvidence)
        if (result != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=CONTENT_EVIDENCE surface=INSTAGRAM_REELS confidence=${result.confidence}"
            )
            return result
        }

        // Tier 4: content description text signals
        val textResult = evaluateTextEvidence(signals.contentEvidence)
        if (textResult != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=TEXT_EVIDENCE surface=INSTAGRAM_REELS confidence=${textResult.confidence}"
            )
            return textResult
        }

        // Tier 5: scroll interaction fallback
        val scrollConf = scrollInteractionConfidence(signals.interactionCount)
        if (scrollConf >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=SCROLL_FALLBACK surface=INSTAGRAM_REELS confidence=$scrollConf"
            )
            return scrollDetectedResult(ShortPlatform.INSTAGRAM, ShortSurface.INSTAGRAM_REELS, signals.interactionCount)
        }

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                "result=UNCONFIRMED isShortForm=false confidence=0.15 reason=NO_REELS_SIGNALS"
        )
        return unconfirmedResult(ShortPlatform.INSTAGRAM, 0.15f)
    }

    private fun shortsResult(confidence: Float, surfaceSignal: String): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.INSTAGRAM,
            surface = ShortSurface.INSTAGRAM_REELS,
            isShortForm = true,
            confidence = confidence,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to surfaceSignal),
        )

    /**
     * Structural evidence: node class or resource id contains "reel" AND
     * is a player/container shape (not a shelf, tab, or chip).
     */
    private fun evaluateContentEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val classHits = evidence.nodeClasses.count { it.lowercase().let {
            it.contains("reel") && (it.contains("player") || it.contains("pager") ||
                it.contains("recycler") || it.contains("container") || it.contains("fragment"))
        }}
        val idHits = evidence.nodeViewIds.count { it.lowercase().let {
            it.contains("reel") && (it.contains("player") || it.contains("pager") ||
                it.contains("recycler") || it.contains("container"))
        }}
        if (classHits == 0 && idHits == 0) return null
        return ShortDetectionResult(
            platform = ShortPlatform.INSTAGRAM,
            surface = ShortSurface.INSTAGRAM_REELS,
            isShortForm = true,
            confidence = 0.70f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf(
                "surfaceSignal" to "content_evidence",
                "playerClassHits" to classHits,
                "playerIdHits" to idHits,
            ),
        )
    }

    /** Text signals unique to Instagram Reels. */
    private fun evaluateTextEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val textHit = evidence.nodeContentDescriptions.any { desc ->
            val lower = desc.lowercase()
            REELS_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
        }
        if (!textHit) return null
        return ShortDetectionResult(
            platform = ShortPlatform.INSTAGRAM,
            surface = ShortSurface.INSTAGRAM_REELS,
            isShortForm = true,
            confidence = 0.60f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to "text_evidence"),
        )
    }

    /** Content descriptions unique to Instagram Reels (never on Feed/Stories/DM). */
    private val REELS_UNIQUE_DESCRIPTIONS = setOf(
        "reel",
        "watch reels",
    )
}
