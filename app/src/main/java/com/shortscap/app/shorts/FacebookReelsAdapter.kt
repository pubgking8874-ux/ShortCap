package com.shortscap.app.shorts

import android.util.Log
import com.shortscap.app.monitoring.WindowContentEvidence

/**
 * Facebook Reels adapter.
 *
 * Facebook is already part of the app's platform catalog. Reels is one
 * surface inside the main Facebook app (Feed, Stories, Watch, Groups, …).
 * Detection tiers:
 *
 *  1. Activity class match — Reels-hosting activity, confidence 0.80.
 *  2. Activity class keyword — "reel" in class name, confidence 0.65.
 *  3. Structural content evidence — Reels-specific node classes/IDs,
 *     confidence 0.70.
 *  4. Content description text signals — Reels-unique labels,
 *     confidence 0.60.
 *  5. Scroll interaction fallback — confidence from scrollInteractionConfidence().
 *
 * Without any of these signals → UNKNOWN / low confidence (never counted).
 */
object FacebookReelsAdapter : ShortPlatformAdapter {

    override val platform: ShortPlatform = ShortPlatform.FACEBOOK
    override val packageNames: Set<String> = setOf("com.facebook.katana")

    override fun detect(signals: ShortDetectionSignals): ShortDetectionResult {
        val className = signals.activityClassName

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_INPUT pkg=${signals.packageName} cls=$className " +
                "interactionCount=${signals.interactionCount} " +
                "evidenceClasses=${signals.contentEvidence.nodeClasses.size} " +
                "evidenceIds=${signals.contentEvidence.nodeViewIds.size} " +
                "descs=${signals.contentEvidence.nodeContentDescriptions.size}"
        )

        // Tier 1: Reels-specific activity class
        if (className != null && className.lowercase().contains("reel")) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=ACTIVITY_CLASS surface=FACEBOOK_REELS confidence=0.80"
            )
            return shortsResult(confidence = 0.80f, surfaceSignal = "activity_class")
        }

        // Tier 2: activity class contains feed/video keywords
        if (className != null && className.lowercase().let {
                it.contains("feed") || it.contains("video") || it.contains("watch") || it.contains("main")
            }) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_CHECK pkg=${signals.packageName} " +
                    "method=HOSTING_CLASS cls=$className checking=content_evidence"
            )
        }

        // Tier 3: structural content evidence
        val result = evaluateContentEvidence(signals.contentEvidence)
        if (result != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=CONTENT_EVIDENCE surface=FACEBOOK_REELS confidence=${result.confidence}"
            )
            return result
        }

        // Tier 4: content description text signals
        val textResult = evaluateTextEvidence(signals.contentEvidence)
        if (textResult != null) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=TEXT_EVIDENCE surface=FACEBOOK_REELS confidence=${textResult.confidence}"
            )
            return textResult
        }

        // Tier 5: scroll interaction fallback
        val scrollConf = scrollInteractionConfidence(signals.interactionCount)
        if (scrollConf >= ShortFormSurfaceState.CONFIDENCE_THRESHOLD) {
            Log.i("SC_PLATFORM_OBS",
                "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                    "method=SCROLL_FALLBACK surface=FACEBOOK_REELS confidence=$scrollConf"
            )
            return scrollDetectedResult(ShortPlatform.FACEBOOK, ShortSurface.FACEBOOK_REELS, signals.interactionCount)
        }

        Log.i("SC_PLATFORM_OBS",
            "SC_PLATFORM_OBS ADAPTER_OUTPUT pkg=${signals.packageName} " +
                "result=UNCONFIRMED isShortForm=false confidence=0.15 reason=NO_REELS_SIGNALS"
        )
        return unconfirmedResult(ShortPlatform.FACEBOOK, 0.15f)
    }

    private fun shortsResult(confidence: Float, surfaceSignal: String): ShortDetectionResult =
        ShortDetectionResult(
            platform = ShortPlatform.FACEBOOK,
            surface = ShortSurface.FACEBOOK_REELS,
            isShortForm = true,
            confidence = confidence,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to surfaceSignal),
        )

    /** Structural evidence: Reels-specific node classes/IDs. */
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
            platform = ShortPlatform.FACEBOOK,
            surface = ShortSurface.FACEBOOK_REELS,
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

    /** Text signals unique to Facebook Reels. */
    private fun evaluateTextEvidence(evidence: WindowContentEvidence): ShortDetectionResult? {
        val textHit = evidence.nodeContentDescriptions.any { desc ->
            val lower = desc.lowercase()
            REELS_UNIQUE_DESCRIPTIONS.any { lower.contains(it) }
        }
        if (!textHit) return null
        return ShortDetectionResult(
            platform = ShortPlatform.FACEBOOK,
            surface = ShortSurface.FACEBOOK_REELS,
            isShortForm = true,
            confidence = 0.60f,
            detectionMethod = DetectionMethod.PLATFORM_ADAPTER,
            metadata = mapOf("surfaceSignal" to "text_evidence"),
        )
    }

    /** Content descriptions unique to Facebook Reels. */
    private val REELS_UNIQUE_DESCRIPTIONS = setOf(
        "reel",
        "watch reels",
        "share reel",
    )
}
